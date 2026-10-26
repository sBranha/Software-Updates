/* Main desktop shell: each Open button launches a real OS window. */
(()=>{
 'use strict';
 const bridge=window.NewsWireDesktopBridge;
 document.querySelectorAll('[data-open-panel]').forEach(btn=>{
  btn.addEventListener('click',()=>{
   if(!bridge){alert('Detachable windows are available in the NewsWire desktop app.');return}
   bridge.openPanel(btn.dataset.openPanel);
  });
 });
 const arrange=document.getElementById('arrange-workspace');
 if(arrange)arrange.addEventListener('click',()=>{
  if(bridge)bridge.arrangePanels();
  else alert('Three-monitor arrangement requires the Windows or Mac NewsWire app.');
 });
 const clock=document.getElementById('nwClock');
 if(clock){
  const tick=()=>{clock.textContent=new Date().toLocaleString([],{
   weekday:'short',month:'short',day:'numeric',hour:'numeric',minute:'2-digit'
  });};
  tick();setInterval(tick,60000);
 }
})();
