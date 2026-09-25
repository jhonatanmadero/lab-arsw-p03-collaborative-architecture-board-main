import {BoardApiClient} from './api/board-api-client.js';
import {createBoardState} from './state/board-state.js';
import {createBoardView} from './ui/board-view.js';
import {createBoardRealtimeClient} from './realtime/board-realtime-client.js';
import {BoardEvents} from './events/board-event.js';

/*
 * app.js orchestrates: UI intentions -> BoardState (optimistic) -> BoardEvent -> BoardRealtimeClient.
 * Incoming accepted events -> BoardState.applyEvent -> view.render. No STOMP details and no DOM
 * mutation of the SVG happen here.
 */

const state=createBoardState();
const view=createBoardView(document.querySelector('#boardCanvas'));
const $=id=>document.getElementById(id);

// One actor per browser tab/window (not per browser), so two tabs are two participants.
const actorId=`client-${crypto.randomUUID()}`;
let connecting=false;
let liveStatus='disconnected';
let dirty=false; // local changes made while offline and not yet saved by REST

const realtime=createBoardRealtimeClient({
  onStatus(status){ liveStatus=status; refresh(); },
  onEvent(event){
    // Accepted event -> state transition -> render. Never touch the SVG here.
    if(state.applyEvent(event)){
      const who=event.actorId===actorId?'you':event.actorId.slice(0,15);
      refresh(`${event.type} applied (${who})`);
    }
  },
  async onRejected(rejection){
    // The server did not accept one of our events: our optimistic state may be wrong.
    refresh(`Server rejected event: ${rejection.message}. Re-synchronizing from REST...`);
    await resyncFromRest(`Rejected (${rejection.code}): ${rejection.message}. Board re-synchronized.`);
  }
});

function refresh(message=''){
  const s=state.snapshot();
  view.render(s);
  $('remoteStatus').textContent=s.remote.status;
  $('liveStatus').textContent=liveStatus;
  $('actorId').textContent=actorId;
  $('message').textContent=message || s.remote.error?.message || '';
  $('retryBtn').hidden=!s.remote.lastAction || s.remote.status!=='error';
  $('boardId').value=s.board.id??$('boardId').value;
  $('boardName').value=s.board.name;
  $('connectLiveBtn').disabled=!s.board.id || realtime.isConnected();
  $('disconnectLiveBtn').disabled=!realtime.isConnected();
}

async function remote(label,action){
  state.setRemote('loading',action,null); refresh(`${label}...`);
  try{ const result=await action(); state.setRemote('success',null,null); refresh(`${label} OK`); return result; }
  catch(error){ state.setRemote('error',action,error); refresh(); throw error; }
}

async function resyncFromRest(message){
  const id=state.snapshot().board.id;
  if(!id) return;
  try{
    const b=await remote('Loading snapshot',()=>BoardApiClient.load(id));
    state.setBoard(b); dirty=false; refresh(message);
  }catch{ /* remote() already displays the error */ }
}

function isLive(){
  const board=state.snapshot().board;
  return Boolean(board.id) && realtime.isConnected() && realtime.boardId()===board.id;
}

/** Publishes when live; otherwise the change stays local until "Save snapshot". */
function publish(event){
  try{ realtime.publish(event); return true; }
  catch(error){ refresh(`Could not publish: ${error.message}`); return false; }
}

function afterLocalChange(buildEvent,label){
  if(isLive()){ publish(buildEvent(state.snapshot().board.id)); refresh(`${label} (sent)`); }
  else { dirty=true; refresh(`${label} locally — connect live or save snapshot`); }
}

async function leaveLiveIfSwitching(nextBoardId){
  if(realtime.isConnected() && realtime.boardId()!==nextBoardId) await realtime.disconnect();
}

view.on({
  select(id){ state.select(id); refresh(); },
  move(id,x,y){ state.select(id); state.moveSelected(x,y); refresh(); },
  moveEnd(id,x,y){
    afterLocalChange(boardId=>BoardEvents.elementMoved(boardId,actorId,id,x,y),'Element moved');
  },
  connectTarget(id){
    if(!connecting) return;
    const connector=state.completeConnect(id); connecting=false;
    if(connector) afterLocalChange(boardId=>BoardEvents.connectorCreated(boardId,actorId,connector),'Connector created');
    else refresh('Connector cancelled: choose a different, non-connector target');
  }
});

$('newBoardBtn').onclick=async()=>{
  const b=await remote('Creating',()=>BoardApiClient.create($('boardName').value.trim()));
  await leaveLiveIfSwitching(b.id);
  state.setBoard(b); dirty=false; refresh('Board created. Share the boardId and connect live.');
};
$('loadBtn').onclick=async()=>{
  const id=$('boardId').value.trim();
  if(!id){ refresh('Write a boardId first'); return; }
  const b=await remote('Loading',()=>BoardApiClient.load(id));
  await leaveLiveIfSwitching(b.id);
  state.setBoard(b); dirty=false; refresh('Board loaded (REST). Connect live to collaborate.');
};
$('saveBtn').onclick=async()=>{
  state.setName($('boardName').value.trim());
  const b=await remote('Saving snapshot',()=>BoardApiClient.save(state.toPersistedBoard()));
  state.setBoard(b); dirty=false; refresh('Snapshot saved');
};
$('retryBtn').onclick=async()=>{ const action=state.snapshot().remote.lastAction; if(action) await remote('Retrying',action); };

$('connectLiveBtn').onclick=async()=>{
  try{
    const board=state.snapshot().board;
    if(!board.id) throw new Error('Create or load a Board first');
    if(dirty){ // do not lose offline work: persist it before joining the session
      const saved=await remote('Saving local changes',()=>BoardApiClient.save(state.toPersistedBoard()));
      state.setBoard(saved); dirty=false;
    }
    await realtime.connect(board.id);
    // Close the gap between the initial GET and the subscription.
    await resyncFromRest(`Live on /topic/boards/${board.id}`);
  }catch(error){ liveStatus='error'; refresh(error.message); }
};
$('disconnectLiveBtn').onclick=async()=>{ await realtime.disconnect(); refresh('Live collaboration disconnected'); };

$('addRectBtn').onclick=()=>{
  const e=state.addRectangle();
  afterLocalChange(boardId=>BoardEvents.elementCreated(boardId,actorId,e),'Rectangle added');
};
$('addTextBtn').onclick=()=>{
  const e=state.addText();
  afterLocalChange(boardId=>BoardEvents.elementCreated(boardId,actorId,e),'Text added');
};
$('editTextBtn').onclick=()=>{
  const current=state.selected();
  if(!current || current.type==='CONNECTOR'){ refresh('Select a rectangle or text first'); return; }
  const text=prompt('New text',current.text);
  if(text===null) return;
  const e=state.updateSelectedText(text.trim());
  if(e) afterLocalChange(boardId=>BoardEvents.elementUpdated(boardId,actorId,e),'Text updated');
};
$('connectBtn').onclick=()=>{
  if(!state.selected()){ refresh('Select the source element first'); return; }
  state.beginConnect(); connecting=true; refresh('Select the target element');
};
$('deleteBtn').onclick=()=>{
  const removed=state.removeSelected();
  if(!removed){ refresh('Select an element first'); return; }
  afterLocalChange(boardId=>BoardEvents.elementDeleted(boardId,actorId,removed),'Element deleted');
};

refresh();
