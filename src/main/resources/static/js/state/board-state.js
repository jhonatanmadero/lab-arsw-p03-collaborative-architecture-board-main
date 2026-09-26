function uid(prefix){ return `${prefix}-${crypto.randomUUID()}`; }

export function createBoardState(){
  let board={id:null,name:'Architecture Board',elements:[]};
  let selectedId=null;
  let connectSourceId=null;
  let remote={status:'idle',lastAction:null,error:null};

  function replaceElement(next){
    board={...board,elements:board.elements.map(e=>e.id===next.id?structuredClone(next):e)};
  }

  function upsertElement(next){
    if(board.elements.some(e=>e.id===next.id)) replaceElement(next);
    else board={...board,elements:[...board.elements,structuredClone(next)]};
  }

  function removeElement(id){
    board={...board,elements:board.elements.filter(e=>e.id!==id && e.sourceId!==id && e.targetId!==id)};
    // Forget UI references to anything that no longer exists.
    const exists=x=>board.elements.some(e=>e.id===x);
    if(selectedId && !exists(selectedId)) selectedId=null;
    if(connectSourceId && !exists(connectSourceId)) connectSourceId=null;
  }

  return {
    snapshot(){
      // lastAction is a retry callback and cannot be passed to structuredClone.
      return {board:structuredClone(board),selectedId,connectSourceId,remote:{...remote}};
    },
    setBoard(next){ board=structuredClone(next); selectedId=null; connectSourceId=null; },
    setName(name){ board={...board,name}; },
    select(id){ selectedId=id; },
    selected(){ return board.elements.find(e=>e.id===selectedId) ?? null; },
    setRemote(status,lastAction=null,error=null){ remote={status,lastAction,error}; },
    addRectangle(){
      const e={id:uid('rect'),type:'RECTANGLE',x:100+board.elements.length*12,y:90+board.elements.length*12,width:170,height:70,text:'Component',sourceId:null,targetId:null};
      board={...board,elements:[...board.elements,e]}; selectedId=e.id; return structuredClone(e);
    },
    addText(){
      const e={id:uid('text'),type:'TEXT',x:120,y:210,width:150,height:30,text:'Text',sourceId:null,targetId:null};
      board={...board,elements:[...board.elements,e]}; selectedId=e.id; return structuredClone(e);
    },
    moveSelected(x,y){
      board={...board,elements:board.elements.map(e=>e.id===selectedId && e.type!=='CONNECTOR'?{...e,x,y}:e)};
      return this.selected();
    },
    updateSelectedText(text){
      const current=this.selected();
      if(!current || current.type==='CONNECTOR') return null;
      const next={...current,text};
      replaceElement(next);
      return structuredClone(next);
    },
    beginConnect(){ if(selectedId) connectSourceId=selectedId; },
    completeConnect(targetId){
      if(!connectSourceId || !targetId || connectSourceId===targetId) return null;
      const source=board.elements.find(e=>e.id===connectSourceId && e.type!=='CONNECTOR');
      const target=board.elements.find(e=>e.id===targetId && e.type!=='CONNECTOR');
      if(!source || !target) return null;
      const e={id:uid('conn'),type:'CONNECTOR',x:0,y:0,width:0,height:0,text:'',sourceId:source.id,targetId:target.id};
      board={...board,elements:[...board.elements,e]}; connectSourceId=null; selectedId=e.id; return structuredClone(e);
    },
    removeSelected(){
      if(!selectedId) return null;
      const removed=selectedId;
      removeElement(removed);
      selectedId=null;
      return removed;
    },
    /**
     * Applies an ACCEPTED BoardEvent as a pure state transition (no DOM access).
     * Every case is idempotent, because the sender also receives its own accepted
     * event after having applied it optimistically:
     *   - CREATED / CONNECTOR_CREATED -> upsert by id
     *   - MOVED                       -> set absolute x,y (applying twice = same result)
     *   - UPDATED                     -> replace if present
     *   - DELETED                     -> remove element + dependent connectors (no-op if absent)
     * Returns true when the event belongs to the current Board and was applied.
     */
    applyEvent(event){
      if(!event || !event.type || !event.payload) return false;
      if(!board.id || event.boardId!==board.id) return false; // session isolation guard
      const p=event.payload;
      switch(event.type){
        case 'ELEMENT_CREATED':
        case 'CONNECTOR_CREATED':
          if(!p.element?.id) return false;
          upsertElement(p.element);
          return true;
        case 'ELEMENT_MOVED': {
          if(!p.elementId || typeof p.x!=='number' || typeof p.y!=='number') return false;
          const target=board.elements.find(e=>e.id===p.elementId);
          if(!target || target.type==='CONNECTOR') return false;
          replaceElement({...target,x:p.x,y:p.y});
          return true;
        }
        case 'ELEMENT_UPDATED':
          if(!p.element?.id || !board.elements.some(e=>e.id===p.element.id)) return false;
          replaceElement(p.element);
          return true;
        case 'ELEMENT_DELETED':
          if(!p.elementId) return false;
          removeElement(p.elementId);
          return true;
        default:
          console.warn('Unknown BoardEvent type',event.type);
          return false;
      }
    },
    toPersistedBoard(){ return structuredClone(board); }
  };
}
