/* Detachable windows: display snapshots only from the primary NewsWire map. */
(()=>{
 'use strict';
 const $=id=>document.getElementById(id),bridge=window.NewsWireDesktopBridge;
 if(!bridge)return;
 const view=document.body.dataset.view;
 let last={warnings:[],feedOk:false},selectedId=null;
 const fmt=raw=>{const d=new Date(raw);return Number.isFinite(d.valueOf())?d.toLocaleString():String(raw||'Not stated')};
 document.querySelectorAll('[data-open]').forEach(btn=>btn.addEventListener('click',()=>bridge.openPanel(btn.dataset.open)));
 $('arrange').addEventListener('click',()=>bridge.arrangePanels());
 function select(w){
  selectedId=w.id;
  $('selectedName').textContent=w.area||'NWS Tornado Warning';
  $('warningDetail').textContent=(w.headline||'Tornado Warning')+'\n\nExpires: '+fmt(w.expires)+
   '\n\n'+(w.instruction||'Follow official NWS instructions.');
  let url='https://www.weather.gov/alerts';
  try {const parsed=new URL(w.url);if(parsed.protocol==='https:'&&
   ['www.weather.gov','api.weather.gov'].includes(parsed.hostname))url=parsed.toString()}catch(_){}
  $('official').href=url;$('focus').disabled=false;
  document.querySelectorAll('.warning').forEach(btn=>btn.classList.toggle('active',btn.dataset.id===w.id));
 }
 function redraw(s){
  last=s;
  $('count').textContent=s.feedOk?String(s.warningCount):'—';
  $('checked').textContent=s.checked||'NWS status not yet verified';
  $('feedStatus').textContent=s.feedOk?
   (view==='warnings'?'Official NWS feed responded successfully.':'NWS feed last checked successfully.'):
   'WARNING STATUS UNKNOWN: NWS feed unavailable or stale.';
  if(view==='local'){
   $('localStatus').textContent=s.localStatus||'Local warning status UNKNOWN.';
   $('location').textContent=s.location||'Select a ZIP in the main NewsWire map';
   return;
  }
  const list=$('warningsList');list.replaceChildren();
  if(!s.feedOk){list.textContent='Official NWS feed unavailable. Alert status unknown.';return}
  if(!s.warnings.length){list.textContent='No active tornado warnings returned by the last successful NWS check. Other hazards may exist.';return}
  s.warnings.forEach(w=>{
   const btn=document.createElement('button');btn.type='button';btn.className='warning';
   btn.dataset.id=w.id;
   const b=document.createElement('b');b.textContent=w.area||'Tornado Warning';
   const small=document.createElement('small');small.textContent='Expires '+fmt(w.expires);
   btn.append(b,small);btn.addEventListener('click',()=>select(w));list.append(btn);
  });
  const existing=s.warnings.find(w=>w.id===selectedId);
  if(existing)select(existing);
 }
 bridge.onWorkspaceState(redraw);
 if(view==='local'){
  const hour=new Date().getHours();$('greeting').textContent=hour<12?'GOOD MORNING':hour<17?'GOOD AFTERNOON':'GOOD EVENING';
  $('setZip').addEventListener('click',()=>{
   const zip=$('zip').value.trim();
   if(!/^\d{5}$/.test(zip)){$('location').textContent='Enter a valid 5-digit US ZIP';return}
   bridge.setZip(zip);bridge.openPanel('map');
  });
  $('zip').addEventListener('keydown',e=>{if(e.key==='Enter')$('setZip').click()});
 }else{
  $('focus').addEventListener('click',()=>{if(selectedId)bridge.focusWarning(selectedId)});
 }
})();
