/* Real Iowa Environmental Mesonet/NWS NEXRAD reflectivity WMS tiles. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;root.NewsWireRadar=api})(typeof window!=='undefined'?window:globalThis,function(){
'use strict';
const WMS='https://mesonet.agron.iastate.edu/cgi-bin/wms/nexrad/n0q.cgi';
const LAYER='nexrad-n0q-900913';
const ATTRIBUTION='NEXRAD composite: Iowa Environmental Mesonet / NOAA NWS';
function createRadar(L,map,onState){
 if(!L||!L.tileLayer||typeof L.tileLayer.wms!=='function')throw Error('Radar WMS requires Leaflet');
 const radar=L.tileLayer.wms(WMS,{
  layers:LAYER,format:'image/png',transparent:true,version:'1.1.1',
  opacity:.79,zIndex:390,attribution:ATTRIBUTION,updateWhenIdle:true
 });
 let shown=false,errors=0,lastTile=null;
 const status=message=>{if(typeof onState==='function')onState(message)};
 radar.on('tileload',()=>{lastTile=Date.now();errors=0;status('Radar tiles loaded · live NEXRAD composite (scan time not verified)')});
 radar.on('tileerror',()=>{errors++;if(errors>=3)status('Radar tiles could not load — source unavailable. This is not an all-clear.')});
 function show(on){
  shown=Boolean(on);
  if(shown){if(!map.hasLayer(radar))radar.addTo(map);radar.bringToFront();status(lastTile?'Radar refreshing…':'Connecting to real NEXRAD radar…')}
  else{if(map.hasLayer(radar))map.removeLayer(radar);status('Radar overlay OFF')}
  return shown;
 }
 function refresh(){
  if(!shown)return;
  // ArcGIS and Mesonet images may be cached; request a new WMS tile set every 5 minutes.
  radar.setParams({_nw_refresh:Math.floor(Date.now()/300000)},false);
  status('Refreshing NEXRAD radar tiles…');
 }
 return {show,refresh,isShown:()=>shown,lastTile:()=>lastTile};
}
return {WMS,LAYER,ATTRIBUTION,createRadar};
});
