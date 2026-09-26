// Run with: node --test src/test/js/*.test.mjs
import test from 'node:test';
import assert from 'node:assert/strict';
import {createBoardState} from '../../main/resources/static/js/state/board-state.js';
import {BoardEvents} from '../../main/resources/static/js/events/board-event.js';

const rect=(id,x=0,y=0)=>({id,type:'RECTANGLE',x,y,width:170,height:70,text:'Component',sourceId:null,targetId:null});
const conn=(id,s,t)=>({id,type:'CONNECTOR',x:0,y:0,width:0,height:0,text:'',sourceId:s,targetId:t});

function stateWith(...elements){
  const s=createBoardState();
  s.setBoard({id:'board-1',name:'Demo',elements});
  return s;
}

test('ELEMENT_CREATED adds a remote element', ()=>{
  const s=stateWith();
  assert.equal(s.applyEvent(BoardEvents.elementCreated('board-1','client-b',rect('r1'))),true);
  assert.deepEqual(s.snapshot().board.elements.map(e=>e.id),['r1']);
});

test('echo of an optimistic CREATED does not duplicate the element', ()=>{
  const s=stateWith();
  const e=s.addRectangle();
  s.applyEvent(BoardEvents.elementCreated('board-1','me',e));
  assert.equal(s.snapshot().board.elements.length,1);
});

test('ELEMENT_MOVED applied twice gives the same Board', ()=>{
  const s=stateWith(rect('r1',10,10));
  const move=BoardEvents.elementMoved('board-1','client-b','r1',420,180);
  s.applyEvent(move); const once=s.snapshot().board;
  s.applyEvent(move);
  assert.deepEqual(s.snapshot().board,once);
  assert.equal(once.elements[0].x,420);
});

test('CONNECTOR_CREATED keeps the same endpoints', ()=>{
  const s=stateWith(rect('a'),rect('b',300));
  s.applyEvent(BoardEvents.connectorCreated('board-1','client-b',conn('c','a','b')));
  const c=s.snapshot().board.elements.find(e=>e.id==='c');
  assert.equal(c.sourceId,'a'); assert.equal(c.targetId,'b');
});

test('ELEMENT_DELETED removes dependent connectors and clears selection', ()=>{
  const s=stateWith(rect('a'),rect('b',300),rect('d',600),conn('ab','a','b'),conn('bd','b','d'));
  s.select('b');
  s.applyEvent(BoardEvents.elementDeleted('board-1','client-b','b'));
  assert.deepEqual(s.snapshot().board.elements.map(e=>e.id),['a','d']);
  assert.equal(s.snapshot().selectedId,null);
  assert.equal(s.applyEvent(BoardEvents.elementDeleted('board-1','client-b','b')),true,'deleting twice is a no-op');
});

test('ELEMENT_UPDATED replaces editable properties', ()=>{
  const s=stateWith(rect('a'));
  s.applyEvent(BoardEvents.elementUpdated('board-1','client-b',{...rect('a'),text:'API Gateway'}));
  assert.equal(s.snapshot().board.elements[0].text,'API Gateway');
});

test('events from another board are ignored (session isolation)', ()=>{
  const s=stateWith();
  assert.equal(s.applyEvent(BoardEvents.elementCreated('board-2','client-b',rect('r1'))),false);
  assert.equal(s.snapshot().board.elements.length,0);
});

test('malformed or unknown events are ignored', ()=>{
  const s=stateWith(rect('a'));
  assert.equal(s.applyEvent(null),false);
  assert.equal(s.applyEvent({boardId:'board-1',type:'SOMETHING',payload:{}}),false);
  assert.equal(s.applyEvent(BoardEvents.elementMoved('board-1','x','missing',1,1)),false);
});

test('snapshot retains a retry callback without cloning it', ()=>{
  const s=stateWith();
  const retry=()=>{};
  s.setRemote('loading',retry);
  const snapshot=s.snapshot();
  assert.equal(snapshot.remote.lastAction,retry);
  assert.equal(snapshot.remote.status,'loading');
});
