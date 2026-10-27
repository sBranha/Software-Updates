/* NewsWire scan-to-scan operator echo measurement controller.
   Only actual archive timestamps and two operator-identified radar core locations are used.
   This is NOT automatic storm recognition, tornado detection, or arrival forecasting. */
(function(root,factory){
 const api=factory();
 if(typeof module==='object'&&module.exports)module.exports=api;
 root.NewsWireMotionUI=api;
})(typeof window!=='undefined'?window:globalThis,function(){
'use strict';
function init({L,map,snapshot,stopLoop,onInteraction}){
 const $=id=>document.getElementById(id),M=window.NewsWireStormMotion;
 const storageKey='nwcc-main-motion-track';
 let active=false,samples=[],track=null,overlay=null;
 const status=t=>{$('mainMotionStatus').textContent=t};
 function clearLayer(){if(overlay){map.removeLayer(overlay);overlay=null}}
 function paint(){
  clearLayer();if(!samples.length&&!track)return;
  overlay=L.layerGroup().addTo(map);
  samples.forEach((p,i)=>L.circleMarker([p.lat,p.lng],{radius:7,color:i?'#ffd27b':'#8df3ff',
   fillOpacity:.95,weight:3}).addTo(overlay).bindTooltip('Operator-selected echo '+(i?'B':'A')+' · '+p.time));
  if(!track)return;
  L.polyline([[track.start.lat,track.start.lng],[track.end.lat,track.end.lng]],
   {color:'#a6faff',weight:4}).addTo(overlay)
   .bindTooltip('Observed echo displacement, operator-identified');
  L.polyline([[track.end.lat,track.end.lng],...track.forecast.map(x=>[x.point.lat,x.point.lng])],
   {color:'#ffcb78',weight:3,dashArray:'7 7'}).addTo(overlay)
   .bindTooltip('ILLUSTRATIVE extrapolation, NOT a tornado track or arrival time');
  track.forecast.forEach(x=>L.circleMarker([x.point.lat,x.point.lng],{radius:5,color:'#ffca74',
   fillOpacity:.75}).addTo(overlay).bindTooltip('+'+x.minutes+' min linear guide, NOT an observed position'));
 }
 function buttons(){
  $('mainMotionPick').textContent=active?'✕ Cancel Core Selection':'⌖ Mark Two Scan Cores';
  $('mainMotionFocus').disabled=!track;
 }
 function clear(){
  active=false;samples=[];track=null;clearLayer();
  try{localStorage.removeItem(storageKey)}catch(_){}
  status('Motion measurement cleared. No movement is assumed.');buttons();
 }
 function begin(){
  if(active){active=false;status('Selection canceled');buttons();return}
  const s=snapshot();
  if(s.mode!=='site'||s.frames.length<2){
   status('Select SINGLE-SITE radar with at least two observed archive scan frames.');return;
  }
  stopLoop();active=true;samples=[];track=null;clearLayer();onInteraction?.();
  status('1) Click an echo CORE on an older observed radar scan. 2) Step to a later scan and click the SAME echo.');
  buttons();
 }
 function consumeClick(event){
  if(!active)return false;
  const s=snapshot(),time=s.frames[s.index];
  if(s.mode!=='site'||!time){status('Observed scan not selected. Choose a radar frame.');return true}
  const p={lat:event.latlng.lat,lng:event.latlng.lng,time,site:s.site,product:s.product};
  try{M.point(p)}catch(_){return true}
  if(!samples.length){
   samples=[p];paint();status('Echo A saved at '+time+'. Use Next to choose a later scan, then click the same echo.');
  }else{
   try{
    const result=M.observedPair(samples[0],p);
    samples.push(p);track=result;active=false;paint();
    try{localStorage.setItem(storageKey,JSON.stringify(samples))}catch(_){}
    status('OPERATOR-MEASURED '+result.mph+' mph · '+result.bearingDegrees+'° · '+
     result.distanceMiles+' miles / '+result.elapsedMinutes+' min. Latest scan age '+result.staleMinutes+
     ' min. Dashed +10/+20/+30 min projections are NOT automatic tornado tracking or arrival times.');
   }catch(err){status('Core B rejected: '+String(err.message||err)+'. Select a later scan; retry.')}
  }
  buttons();return true;
 }
 function focus(){
  if(!track)return;
  map.fitBounds(L.latLngBounds([[track.start.lat,track.start.lng],
   ...track.forecast.map(x=>[x.point.lat,x.point.lng])]).pad(.3),{maxZoom:12});
  onInteraction?.();
 }
 $('mainMotionPick').addEventListener('click',begin);
 $('mainMotionClear').addEventListener('click',clear);
 $('mainMotionFocus').addEventListener('click',focus);
 try{
  const saved=JSON.parse(localStorage.getItem(storageKey)||'null');
  if(Array.isArray(saved)&&saved.length===2){
   const s=snapshot();
   if(s.site===saved[0].site&&s.product===saved[0].product){
    track=M.observedPair(saved[0],saved[1]);samples=saved;paint();
    status('Restored OPERATOR-MEASURED '+track.mph+' mph; projection is illustrative, NOT observed.');
   }
  }
 }catch(_){try{localStorage.removeItem(storageKey)}catch(_){}}
 buttons();
 return {consumeClick,reset:clear};
}
return {init};
});
