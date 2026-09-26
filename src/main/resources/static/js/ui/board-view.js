const NS='http://www.w3.org/2000/svg';
function svgEl(name,attrs={}){ const e=document.createElementNS(NS,name); Object.entries(attrs).forEach(([k,v])=>e.setAttribute(k,v)); return e; }
function center(e){ return {x:e.x+e.width/2,y:e.y+e.height/2}; }

/**
 * BoardView renders a BoardState snapshot into SVG and translates pointer input
 * into intentions (select, move, moveEnd, connectTarget). It knows nothing about
 * REST, STOMP, topics or destinations.
 */
export function createBoardView(canvas){
  let handlers={select:()=>{},move:()=>{},moveEnd:()=>{},connectTarget:()=>{}};
  let drag=null;
  let last=null;

  function render(snapshot){
    last=snapshot;
    canvas.replaceChildren();
    const byId=new Map(snapshot.board.elements.filter(e=>e.type!=='CONNECTOR').map(e=>[e.id,e]));
    for(const e of snapshot.board.elements.filter(e=>e.type==='CONNECTOR')){
      const a=byId.get(e.sourceId), b=byId.get(e.targetId); if(!a||!b) continue; const ca=center(a), cb=center(b);
      canvas.append(svgEl('line',{x1:ca.x,y1:ca.y,x2:cb.x,y2:cb.y,class:`connector ${snapshot.selectedId===e.id?'selected':''}`,'data-id':e.id}));
    }
    for(const e of snapshot.board.elements.filter(e=>e.type!=='CONNECTOR')){
      const g=svgEl('g',{'data-id':e.id,class:'shape'});
      if(e.type==='RECTANGLE'){
        g.append(svgEl('rect',{x:e.x,y:e.y,width:e.width,height:e.height,rx:8,fill:'#e8f0f7',stroke:'#597995',class:snapshot.selectedId===e.id?'selected':''}));
        const t=svgEl('text',{x:e.x+12,y:e.y+40,class:'label'}); t.textContent=e.text||'Component'; g.append(t);
      }
      if(e.type==='TEXT'){
        // The label ignores pointer events in CSS; give text elements a hit area.
        g.append(svgEl('rect',{x:e.x,y:e.y,width:e.width,height:e.height,fill:'transparent',stroke:'none'}));
        const t=svgEl('text',{x:e.x,y:e.y+20,'font-size':20,fill:'#1d2733',class:`label ${snapshot.selectedId===e.id?'selected':''}`}); t.textContent=e.text||'Text'; g.append(t);
      }
      canvas.append(g);
    }
  }

  function point(ev){ const pt=canvas.createSVGPoint(); pt.x=ev.clientX; pt.y=ev.clientY; return pt.matrixTransform(canvas.getScreenCTM().inverse()); }

  canvas.addEventListener('pointerdown',ev=>{
    const node=ev.target.closest?.('[data-id]'); if(!node) return;
    const id=node.dataset.id; handlers.select(id); handlers.connectTarget(id);
    const element=last?.board.elements.find(e=>e.id===id);
    if(!element || element.type==='CONNECTOR') return; // connectors are not draggable
    const p=point(ev);
    // Keep the grab offset so the shape does not jump to the cursor.
    drag={id,dx:p.x-element.x,dy:p.y-element.y,x:element.x,y:element.y,moved:false};
    canvas.setPointerCapture(ev.pointerId);
  });
  canvas.addEventListener('pointermove',ev=>{
    if(!drag) return; const p=point(ev);
    drag.x=p.x-drag.dx; drag.y=p.y-drag.dy; drag.moved=true;
    handlers.move(drag.id,drag.x,drag.y);
  });
  const finish=()=>{
    if(!drag) return;
    const d=drag; drag=null;
    if(d.moved) handlers.moveEnd(d.id,d.x,d.y); // publish only real movements, once, at the end
  };
  canvas.addEventListener('pointerup',finish);
  canvas.addEventListener('pointercancel',finish);

  return {render,on(next){handlers={...handlers,...next};}};
}
