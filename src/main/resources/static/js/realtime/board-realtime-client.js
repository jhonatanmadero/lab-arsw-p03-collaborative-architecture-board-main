/**
 * BoardRealtimeClient — the ONLY module that knows about STOMP, topics and destinations.
 *
 *   SEND       /app/boards/{boardId}/events   publish a BoardEvent
 *   SUBSCRIBE  /topic/boards/{boardId}        accepted events of that Board
 *   SUBSCRIBE  /user/queue/errors             private rejections for this session
 *
 * It never touches BoardState or the DOM: it only hands parsed contracts to callbacks.
 */
export function createBoardRealtimeClient({onEvent=()=>{},onStatus=()=>{},onRejected=()=>{}}={}){
  let client=null;
  let subscriptions=[];
  let currentBoardId=null;

  const destinations={
    send:boardId=>`/app/boards/${encodeURIComponent(boardId)}/events`,
    topic:boardId=>`/topic/boards/${encodeURIComponent(boardId)}`,
    errors:'/user/queue/errors'
  };

  function webSocketUrl(){
    const protocol=location.protocol==='https:'?'wss':'ws';
    return `${protocol}://${location.host}/ws`;
  }

  function parse(message){
    try { return JSON.parse(message.body); }
    catch(error){ console.error('Invalid STOMP message body',error,message?.body); return null; }
  }

  function reset(){
    subscriptions=[]; client=null; currentBoardId=null;
  }

  function connect(boardId){
    if(!boardId) return Promise.reject(new Error('boardId is required before connecting'));
    if(!window.Stomp) return Promise.reject(new Error('STOMP client library was not loaded'));
    if(client?.connected && currentBoardId===boardId) return Promise.resolve();

    const previous=client?.connected ? disconnect() : Promise.resolve();
    return previous.then(()=>new Promise((resolve,reject)=>{
      onStatus('connecting');
      const socket=new WebSocket(webSocketUrl());
      client=window.Stomp.over(socket);
      client.debug=()=>{};
      let opened=false;
      client.connect({},()=>{
        opened=true;
        currentBoardId=boardId;
        subscriptions=[
          client.subscribe(destinations.topic(boardId),message=>{
            const event=parse(message);
            if(event) onEvent(event);
          }),
          client.subscribe(destinations.errors,message=>{
            const rejection=parse(message);
            if(rejection) onRejected(rejection);
          })
        ];
        onStatus('connected');
        resolve();
      },error=>{
        // Called on handshake failure AND on later connection loss.
        reset();
        onStatus(opened?'disconnected':'error');
        if(!opened) reject(error instanceof Error?error:new Error(String(error?.headers?.message ?? error)));
      });
    }));
  }

  function publish(event){
    if(!client?.connected) throw new Error('Live channel is not connected');
    if(!event?.boardId) throw new Error('BoardEvent.boardId is required');
    if(event.boardId!==currentBoardId) throw new Error(`Event for board ${event.boardId} cannot be sent on session ${currentBoardId}`);
    client.send(destinations.send(event.boardId),{'content-type':'application/json'},JSON.stringify(event));
  }

  function disconnect(){
    return new Promise(resolve=>{
      subscriptions.forEach(s=>s?.unsubscribe?.());
      const active=client;
      reset();
      if(active?.connected){ active.disconnect(()=>{ onStatus('disconnected'); resolve(); }); }
      else { onStatus('disconnected'); resolve(); }
    });
  }

  return {
    connect,
    publish,
    disconnect,
    isConnected(){ return Boolean(client?.connected); },
    boardId(){ return currentBoardId; }
  };
}
