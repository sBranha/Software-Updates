/* Animated 30-minute IEM NEXRAD reflectivity loop using documented WMS mXXm layers.
   Timestamp labels are relative layer ages, NOT verified scan/observation timestamps. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;root.NewsWireRadarLoop=api})(typeof window!=='undefined'?window:globalThis,function(){
'use strict';
const WMS='https://mesonet.agron.iastate.edu/cgi-bin/wms/nexrad/n0q.cgi';
const MINUTES=[30,25,20,15,10,5,0];
const LAYER_FOR_AGE=age=>'nexrad-n0q-900913'+(age?'-m'+String(age).padStart(2,'0')+'m':'');
const ATTRIBUTION='NEXRAD reflectivity (Iowa Environmental Mesonet / NOAA NWS)';
function createRadar(L,map,onState){
 if(!L?.tileLayer?.wms)throw Error('Leaflet WMS support is required');
 const callback=typeof onState==='function'?onState:()=>{};
 const frames=MINUTES.map(age=>{
  const layer=L.tileLayer.wms(WMS,{layers:LAYER_FOR_AGE(age),format:'image/png',version:'1.1.1',
   transparent:true,opacity:0,zIndex:390,attribution:ATTRIBUTION,updateWhenIdle:true});
  return {age,layer,loaded:false,errors:0};
 });
 let shown=false,playing=false,index=frames.length-1,timer=null,lastTile=null;
 function label(){return frames[index].age===0?'Latest mosaic (scan time unverified)':frames[index].age+' minutes before latest (approx.)'}
 function framePaint(){
  if(!shown)return;
  frames.forEach((f,i)=>f.layer.setOpacity(i===index?.78:0));
  callback((playing?'RADAR LOOP — ':'RADAR PAUSED — ')+label());
 }
 frames.forEach((f,i)=>{
  f.layer.on('tileload',()=>{f.loaded=true;f.errors=0;lastTile=Date.now();
   if(shown&&i===index)callback((playing?'RADAR LOOP — ':'RADAR PAUSED — ')+label());});
  f.layer.on('tileerror',()=>{f.errors++;if(f.errors>=3&&shown&&index===i)
   callback('Radar image unavailable at this interval — not an all-clear');});
 });
 function play(on){
  playing=Boolean(on);
  if(timer){clearInterval(timer);timer=null}
  if(playing&&shown){
   timer=setInterval(()=>{
    index=(index+1)%frames.length;framePaint();
   },850);
  }
  if(shown)framePaint();
  return playing;
 }
 function show(on){
  shown=Boolean(on);
  if(shown){
   for(const f of frames)if(!map.hasLayer(f.layer))f.layer.addTo(map);
   // Keep transparent overlay above USGS basemap, below warning boundaries.
   for(const f of frames)f.layer.bringToFront();
   index=frames.length-1;framePaint();play(true);
  }else{
   play(false);for(const f of frames)if(map.hasLayer(f.layer))map.removeLayer(f.layer);
   callback('Radar overlay OFF');
  }
  return shown;
 }
 function refresh(){
  if(!shown)return;
  frames.forEach(f=>f.layer.setParams({_nw_refresh:Math.floor(Date.now()/300000)},false));
  callback('Loading updated IEM NEXRAD frames…');
 }
 function next(){if(!shown)return;index=(index+1)%frames.length;framePaint()}
 function dispose(){play(false);show(false)}
 return {show,refresh,play,next,dispose,isShown:()=>shown,isPlaying:()=>playing,lastTile:()=>lastTile,frameLabel:label,frames:MINUTES.slice()};
}
return {createRadar,WMS,MINUTES,LAYER_FOR_AGE,ATTRIBUTION};
});
