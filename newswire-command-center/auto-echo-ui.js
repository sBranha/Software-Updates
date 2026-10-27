/* NewsWire original v0.8.5 experimental automatic reflectivity echo workstation.
   Automatic identification is from published reflectivity image colors, NOT calibrated
   reflectivity, official storm IDs, rotation, tornado detection, or safety alerts. */
(function(root,factory){
 const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;
 root.NewsWireAutoEchoUI=api;
})(typeof window!=='undefined'?window:globalThis,function(){
'use strict';
function init({L,map,snapshot,selectReflectivity,onInteraction}){
 const $=id=>document.getElementById(id);
 const bridge=window.NewsWireDesktopBridge;
 const key='nwcc-auto-echo-enabled';
 let enabled=false,request=0,overlay=null,last=null,timer=null,working=false;
 const status=t=>{$('mainAutoEchoStatus').textContent=t};
 function clear(){if(overlay){map.removeLayer(overlay);overlay=null}}
 function buttons(){
  const b=$('mainAutoEchoToggle');
  b.textContent=enabled?'◉ Auto Echoes: ON':'◌ Auto Echoes: OFF';
  b.setAttribute('aria-pressed',String(enabled));
  b.disabled=working&&enabled;
  $('mainAutoEchoRefresh').disabled=!enabled||working;
 }
 function paint(data){
  clear();overlay=L.layerGroup().addTo(map);
  const tracks=Array.isArray(data.tracks)?data.tracks:[];
  const unpaired=Array.isArray(data.unmatched)?data.unmatched:[];
  for(let i=0;i<Math.min(12,tracks.length);i++){
   const t=tracks[i],now=[t.lat,t.lng],past=[t.first.lat,t.first.lng];
   L.polyline([past,now],{color:'#89f0f9',weight:4,opacity:.94}).addTo(overlay)
    .bindTooltip('AUTO-MATCHED REFLECTIVITY ECHO: '+t.mph+' mph · '+t.bearingDegrees+'° · NOT tornado motion');
   L.polyline([now,...t.future.map(p=>[p.point.lat,p.point.lng])],
    {color:'#ffb962',weight:2,dashArray:'6 7',opacity:.85}).addTo(overlay)
    .bindTooltip('EXPERIMENTAL LINEAR EXTRAPOLATION. NOT a storm arrival or warning.');
   L.circleMarker(now,{radius:8,color:'#e8ffff',weight:3,fillColor:'#2ce4c8',fillOpacity:.92})
    .addTo(overlay).bindTooltip('AUTO ECHO #'+(i+1)+' · '+t.mph+' mph · observed '+data.latestScan+' UTC');
  }
  for(let i=0;i<Math.min(12,unpaired.length);i++){
   const p=unpaired[i];
   L.circleMarker([p.lat,p.lng],{radius:5,color:'#c7c7f0',weight:2,fillColor:'#4e69a8',fillOpacity:.62})
    .addTo(overlay).bindTooltip('Unmatched radar reflectivity area · NOT a tracked storm');
  }
  const ul=$('mainAutoEchoList');ul.replaceChildren();
  for(let i=0;i<tracks.length;i++){
   const t=tracks[i],li=document.createElement('li'),b=document.createElement('button');
   b.type='button';b.className='autoEchoSelect';b.textContent='Echo '+(i+1)+
    ' · '+t.mph+' mph · '+t.bearingDegrees+'° · approx '+t.approxAreaSqKm+' km²';
   b.title='Center map on this experimentally matched reflectivity area';
   b.addEventListener('click',()=>{map.flyTo([t.lat,t.lng],Math.max(8,map.getZoom()),{animate:true});onInteraction?.()});
   li.append(b);ul.append(li);
  }
  if(!tracks.length){
   const li=document.createElement('li');li.textContent=
    'No defensible echo movement matches. This is NOT an all-clear or proof of no storms.';
   ul.append(li);
  }
 }
 async function refresh(){
  if(!enabled||working)return;
  const token=++request;
  working=true;buttons();
  const opts=snapshot(),site=opts.site;
  if(opts.mode!=='site'||opts.product!=='N0B'){
   working=false;clear();last=null;
   status('Choose single-site N0B reflectivity. Automatic echo tracking requires actual reflectivity scans.');
   buttons();return;
  }
  status('Downloading actual '+site+' N0B archived radar images and searching for moving reflectivity clusters…');
  try{
   if(typeof bridge?.requestAutoEcho!=='function')throw Error('Automatic radar analysis bridge unavailable');
   const result=await bridge.requestAutoEcho(site);
   if(token!==request||!enabled||snapshot().site!==site)return;
   if(!result?.ok)throw Error(result?.error||'Radar analysis source unavailable');
   last=result;paint(result);
   const stale=result.stale?'STALE DATA · ':'';
   status(stale+'EXPERIMENTAL: '+result.trackedCount+' automatically matched colored echo areas · '+
    result.echoesLatest+' latest radar areas · '+result.examinedTiles+' real archived tiles · '+
    result.latestScan+' UTC ('+result.ageMinutes+' min old). No dBZ calibration, tornado/rotation detection or official track.');
  }catch(error){
   if(token!==request)return;
   clear();last=null;
   status('RADAR ANALYSIS UNAVAILABLE · '+String(error.message||error).slice(0,190)+
    '. Do not interpret missing analysis as safe weather.');
  }finally{if(token===request){working=false;buttons()}}
 }
 function toggle(){
  enabled=!enabled;
  try{localStorage.setItem(key,String(enabled))}catch(_){}
  if(enabled){
   selectReflectivity?.();
   refresh();
  }else{
   request++;working=false;clear();last=null;
   $('mainAutoEchoList').replaceChildren();
   status('Automatic reflectivity echo monitoring OFF. Official warning polygons remain independent.');
  }
  buttons();
 }
 function radarChanged(){
  if(!enabled)return;
  request++;working=false;clear();last=null;
  status('Radar source changed. Preparing new reflectivity analysis…');
  buttons();
  setTimeout(refresh,1200);
 }
 $('mainAutoEchoToggle')?.addEventListener('click',toggle);
 $('mainAutoEchoRefresh')?.addEventListener('click',refresh);
 try{enabled=localStorage.getItem(key)==='true'}catch(_){}
 buttons();
 if(enabled){
  selectReflectivity?.();
  setTimeout(refresh,1800);
 }
 timer=setInterval(()=>{if(enabled&&!working)refresh()},120000);
 window.addEventListener('beforeunload',()=>{request++;clearInterval(timer);clear()});
 return {refresh,radarChanged,isEnabled:()=>enabled};
}
return {init};
});