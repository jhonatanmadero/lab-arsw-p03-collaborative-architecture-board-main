import test from 'node:test';
import assert from 'node:assert/strict';
import {createBoardRealtimeClient} from '../../main/resources/static/js/realtime/board-realtime-client.js';

// Minimal fake of the stomp.js 2.3.x API used by the client.
function installFakeStomp(){
  const fake={sent:[],subs:{},client:null};
  globalThis.location={protocol:'http:',host:'localhost:8080'};
  globalThis.WebSocket=class { constructor(url){ this.url=url; } };
  globalThis.window={Stomp:{over(){
    const c={connected:false,debug:null,
      connect(h,ok){ c.connected=true; ok(); },
      subscribe(dest,cb){ fake.subs[dest]=cb; return {unsubscribe(){ delete fake.subs[dest]; }}; },
      send(dest,headers,body){ fake.sent.push({dest,headers,body}); },
      disconnect(cb){ c.connected=false; cb(); }};
    fake.client=c; return c;
  }}};
  return fake;
}

test('connect subscribes to the board topic and the private error queue', async ()=>{
  const fake=installFakeStomp();
  const rt=createBoardRealtimeClient();
  await rt.connect('board-1');
  assert.ok(fake.subs['/topic/boards/board-1']);
  assert.ok(fake.subs['/user/queue/errors']);
  assert.equal(rt.isConnected(),true);
});

test('publish sends the serialized contract to /app/boards/{id}/events', async ()=>{
  const fake=installFakeStomp();
  const rt=createBoardRealtimeClient();
  await rt.connect('board-1');
  const event={eventId:'e1',boardId:'board-1',type:'ELEMENT_DELETED',actorId:'a',occurredAt:'2026-09-01T12:30:00Z',payload:{elementId:'r1'}};
  rt.publish(event);
  assert.equal(fake.sent[0].dest,'/app/boards/board-1/events');
  assert.deepEqual(JSON.parse(fake.sent[0].body),event);
});

test('publish rejects when disconnected or for a different board', async ()=>{
  installFakeStomp();
  const rt=createBoardRealtimeClient();
  assert.throws(()=>rt.publish({boardId:'board-1'}),/not connected/);
  await rt.connect('board-1');
  assert.throws(()=>rt.publish({boardId:'board-2'}),/cannot be sent/);
});

test('incoming messages are parsed and handed to callbacks', async ()=>{
  const fake=installFakeStomp();
  const events=[], rejections=[];
  const rt=createBoardRealtimeClient({onEvent:e=>events.push(e),onRejected:r=>rejections.push(r)});
  await rt.connect('board-1');
  fake.subs['/topic/boards/board-1']({body:'{"type":"ELEMENT_MOVED"}'});
  fake.subs['/user/queue/errors']({body:'{"code":"INVALID_EVENT"}'});
  fake.subs['/topic/boards/board-1']({body:'not json'});
  assert.deepEqual(events,[{type:'ELEMENT_MOVED'}]);
  assert.deepEqual(rejections,[{code:'INVALID_EVENT'}]);
});
