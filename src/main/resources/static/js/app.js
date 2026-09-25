import {BoardApiClient} from './api/board-api-client.js';
import {createBoardState} from './state/board-state.js';
import {createBoardView} from './ui/board-view.js';
import {createBoardRealtimeClient} from './realtime/board-realtime-client.js';
import {BoardEvents} from './events/board-event.js';

const state=createBoardState();
const view=createBoardView(document.querySelector('#boardCanvas'));
const $=id=>document.getElementById(id);
const actorId=localStorage.getItem('arsw-actor-id') ?? `client-${crypto.randomUUID()}`;
localStorage.setItem('arsw-actor-id',actorId);
let connecting=false;
let liveStatus='disconnected';

const realtime=createBoardRealtimeClient({
  onStatus(status){ liveStatus=status; refresh(); },
  onEvent(event){
    // TODO LAB-06: apply the accepted event to BoardState and render the new snapshot.
    // Do not mutate SVG directly from this callback.
    console.info('Remote BoardEvent received',event);
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

function requireBoard(){
  const board=state.snapshot().board;
  if(!board.id) throw new Error('Create or load a Board first');
  return board;
}

function requireLiveBoard(){
  const board=requireBoard();
  if(!realtime.isConnected()) throw new Error('Connect the Board to the live channel first');
  return board;
}

function publish(event){
  // TODO LAB-06: delegate to BoardRealtimeClient.publish(event).
  // Keep this helper so UI actions remain independent of STOMP details.
  console.info('BoardEvent ready to publish',event);
}

view.on({
  select(id){ state.select(id); refresh(); },
  move(id,x,y){ state.select(id); state.moveSelected(x,y); refresh(); },
  moveEnd(id,x,y){
    const board=state.snapshot().board;
    if(board.id && realtime.isConnected()) publish(BoardEvents.elementMoved(board.id,actorId,id,x,y));
  },
  connectTarget(id){
    if(connecting){
      const connector=state.completeConnect(id); connecting=false;
      if(connector){
        const board=state.snapshot().board;
        if(board.id && realtime.isConnected()) publish(BoardEvents.connectorCreated(board.id,actorId,connector));
        refresh('Connector created locally; publish it to collaborate.');
      }
    }
  }
});

$('newBoardBtn').onclick=async()=>{
  const b=await remote('Creating',()=>BoardApiClient.create($('boardName').value.trim()));
  state.setBoard(b); refresh('Board created. Connect live when ready.');
};
$('loadBtn').onclick=async()=>{
  const id=$('boardId').value.trim();
  const b=await remote('Loading',()=>BoardApiClient.load(id));
  state.setBoard(b); refresh('Board loaded. Connect live to collaborate.');
};
$('saveBtn').onclick=async()=>{
  state.setName($('boardName').value.trim());
  const b=await remote('Saving snapshot',()=>BoardApiClient.save(state.toPersistedBoard()));
  state.setBoard(b); refresh('Snapshot saved');
};
$('retryBtn').onclick=async()=>{ const action=state.snapshot().remote.lastAction; if(action) await remote('Retrying',action); };

$('connectLiveBtn').onclick=async()=>{
  try{
    const board=requireBoard();
    await realtime.connect(board.id);
    refresh(`Subscribed to /topic/boards/${board.id}`);
  }catch(error){ liveStatus='error'; refresh(error.message); }
};
$('disconnectLiveBtn').onclick=async()=>{ await realtime.disconnect(); refresh('Live collaboration disconnected'); };

$('addRectBtn').onclick=()=>{
  const e=state.addRectangle(); const board=state.snapshot().board;
  if(board.id && realtime.isConnected()) publish(BoardEvents.elementCreated(board.id,actorId,e));
  refresh('Rectangle added locally');
};
$('addTextBtn').onclick=()=>{
  const e=state.addText(); const board=state.snapshot().board;
  if(board.id && realtime.isConnected()) publish(BoardEvents.elementCreated(board.id,actorId,e));
  refresh('Text added locally');
};
$('connectBtn').onclick=()=>{ state.beginConnect(); connecting=true; refresh('Select the target element'); };
$('deleteBtn').onclick=()=>{
  const removed=state.removeSelected(); const board=state.snapshot().board;
  if(removed && board.id && realtime.isConnected()) publish(BoardEvents.elementDeleted(board.id,actorId,removed));
  refresh('Element removed locally');
};

refresh();
