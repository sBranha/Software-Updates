/* NewsWire Radar-First Studio view.
   Maximized map by default, with professional radar controls always present.
   All original dashboards are accessible by exiting Studio View.
*/
(()=>{
'use strict';
const $=id=>document.getElementById(id);
const key='nwcc-radar-first-studio',bridge=window.NewsWireDesktopBridge;
let studio=false,alertsVisible=false;
try{studio=localStorage.getItem(key)!=='false'}catch(_){studio=true}
function resizeMap(){
 // Leaflet listens to window resize; run after layout transition is applied.
 window.dispatchEvent(new Event('resize'));
 setTimeout(()=>window.dispatchEvent(new Event('resize')),170);
}
function state(){
 document.body.classList.toggle('studioMode',studio);
 document.body.classList.toggle('studioAlertsVisible',studio&&alertsVisible);
 $('studioToggle').textContent=studio?'↩ Standard Layout':'▣ Studio View';
 $('studioToggle').setAttribute('aria-pressed',String(studio));
 $('studioToggle').setAttribute('aria-label',studio?'Leave full-map studio layout':'Show large radar studio');
 $('studioAlerts').textContent=studio?(alertsVisible?'✕ Hide Alerts':'⚠ Warnings Panel'):'⚠ Warnings Panel';
 $('studioAlerts').setAttribute('aria-pressed',String(studio&&alertsVisible));
 $('studioFullscreen').textContent='⛶ Full Screen (F11)';
 resizeMap();
}
function toggleStudio(next){
 studio=Boolean(next);
 if(!studio)alertsVisible=false;
 try{localStorage.setItem(key,String(studio))}catch(_){}
 state();
}
function toggleAlerts(){
 if(!studio){toggleStudio(true);alertsVisible=true}
 else alertsVisible=!alertsVisible;
 state();
 if(alertsVisible)$('list')?.focus?.();
}
function toggleFullscreen(){bridge?.toggleFullscreen?.()}
function exitIfNeeded(){
 // Close the least disruptive overlay before leaving studio layout.
 const drawer=$('mainAnalysisDrawer');
 if(drawer&&!drawer.hidden){
  drawer.hidden=true;
  $('mainAnalysisToggle').setAttribute('aria-expanded','false');
  resizeMap();return;
 }
 if(alertsVisible){alertsVisible=false;state();return}
 bridge?.exitFullscreen?.();
 if(studio)toggleStudio(false);
}
$('studioToggle')?.addEventListener('click',()=>toggleStudio(!studio));
$('studioAlerts')?.addEventListener('click',toggleAlerts);
$('studioFullscreen')?.addEventListener('click',toggleFullscreen);
document.addEventListener('keydown',event=>{
 const node=event.target;
 const editing=node&&(/^(INPUT|TEXTAREA|SELECT)$/.test(node.tagName)||node.isContentEditable);
 if(event.key==='F11'){
  event.preventDefault();toggleFullscreen();return;
 }
 if(event.key==='Escape'&&!editing){exitIfNeeded()}
});
state();
})();