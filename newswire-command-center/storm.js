/* A real independent radar map for Storm Slot 1 or 2. Does not derive storm position. */
(()=>{
'use strict';
const $=id=>document.getElementById(id);
const bridge=window.NewsWireDesktopBridge;
const slot=Number(new URLSearchParams(location.search).get('slot'))||1;
$('title').textContent='Storm Tracker '+(String(slot));
const setting=name=>'nwcc-storm-'+slot+'-'+name;
const savedBool=(name,fallback)=>{try{const value=localStorage.getItem(setting(name));return value===null?fallback:value==='true'}catch(_){return fallback}};
const saveBool=(name,value)=>{try{localStorage.setItem(setting(name),String(Boolean(value)))}catch(_){}};
let autoFocus=savedBool('auto-focus',true);
let showWarnings=savedBool('warning-outline',true);
let radarOnly=savedBool('radar-only',false);
const map=L.map('map',{zoomControl:true}).setView([39,-97],5);
try{
 const view=JSON.parse(localStorage.getItem(setting('viewport'))||'null');
 if(view&&Number.isFinite(view.lat)&&Math.abs(view.lat)<=90&&Number.isFinite(view.lng)&&Math.abs(view.lng)<=180&&Number.isInteger(view.zoom)&&view.zoom>=2&&view.zoom<=18)
  map.setView([view.lat,view.lng],view.zoom);
}catch(_){}
map.on('moveend',()=>{
 const p=map.getCenter();
 try{localStorage.setItem(setting('viewport'),JSON.stringify({lat:p.lat,lng:p.lng,zoom:map.getZoom()}))}catch(_){}
});
window.NewsWireStormMap=map;
const MV=window.NewsWireMapViews;
const settingKey='nwcc-storm-map-mode-'+slot;
let mapMode='satellite',base,streetPicking=false;
function mapStyle(mode){
 mapMode=MV.normalize(mode);
 if(base)map.removeLayer(base);
 base=MV.createLayer(L,mapMode).addTo(map).bringToBack();
 for(const [id,key] of [['satellite','satellite'],['streets','streets'],['terrain','terrain']]){
  const button=$(id);button.setAttribute('aria-pressed',String(mapMode===key));
 }
 try{localStorage.setItem(settingKey,mapMode)}catch(_){}
}
try{mapMode=MV.normalize(localStorage.getItem(settingKey))}catch(_){}
mapStyle(mapMode);
function cancelStreetPicking(){
 streetPicking=false;
 $('streetView').textContent='Street View ↗';
 $('streetView').setAttribute('aria-pressed','false');
 map.getContainer().style.cursor='';
}
window.NWStreetViewCancel=cancelStreetPicking;
function toggleStreetPicking(){
 if(window.NWPolygonIsDrawing){
  $('drawStatus').textContent='Finish or cancel drawing your polygon before choosing a Street View point.';
  return;
 }
 streetPicking=!streetPicking;
 $('streetView').setAttribute('aria-pressed',String(streetPicking));
 $('streetView').textContent=streetPicking?'✕ Cancel Street View':'Street View ↗';
 map.getContainer().style.cursor=streetPicking?'crosshair':'';
 $('streetViewHint').textContent=streetPicking?'Click a road or place on this map. Google Street View will open in your browser; images may be older and are not live.':
  'Pan or zoom to a location, then choose Street View and click the map.';
}
map.on('click',event=>{
 if(!streetPicking||window.NWPolygonIsDrawing)return;
 cancelStreetPicking();
 try{
  window.open(MV.streetViewUrl(event.latlng.lat,event.latlng.lng),'_blank','noopener,noreferrer');
  $('streetViewHint').textContent='Opening Google Street View at the selected point. Imagery is not available everywhere.';
 }catch(error){$('streetViewHint').textContent='Street View unavailable: '+error.message}
});
const radar=(window.NewsWireRadarLoop||NewsWireRadar).createRadar(L,map,s=>$('radarState').textContent=s);
radar.show(savedBool('radar-on',true));
if(radar.isShown())radar.play(savedBool('loop-on',true));
function radarButtons(){
 const on=radar.isShown(),playing=on&&radar.isPlaying();
 $('radar').setAttribute('aria-pressed',String(on));
 $('radar').textContent='Radar: '+(on?'ON':'OFF');
 $('loop').setAttribute('aria-pressed',String(playing));
 $('loop').textContent=playing?'⏸ Pause Loop':'▶ Play Loop';
}
function deskButtons(){
 $('autoFocus').setAttribute('aria-pressed',String(autoFocus));
 $('autoFocus').textContent='Auto-Focus: '+(autoFocus?'ON':'OFF');
 $('warningOverlay').setAttribute('aria-pressed',String(showWarnings));
 $('warningOverlay').textContent='NWS Outline: '+(showWarnings?'ON':'OFF');
 $('radarOnly').setAttribute('aria-pressed',String(radarOnly));
 $('radarOnly').textContent=radarOnly?'Show Analysis':'Radar Only';
 document.body.classList.toggle('radarOnly',radarOnly);
 setTimeout(()=>map.invalidateSize(),80);
}
radarButtons();deskButtons();
map.createPane('warning');
map.getPane('warning').style.zIndex=500;
let warningLayer=null,selection=null,feedOk=false;
let fixedMarker=null;
function paint(item,feed){
 feedOk=Boolean(feed);
 const changed=!selection||selection.id!==item?.id;
 selection=item;
 if(warningLayer){map.removeLayer(warningLayer);warningLayer=null}
 if(fixedMarker){map.removeLayer(fixedMarker);fixedMarker=null}
 if(!item){
  $('status').className='status';
  $('status').textContent='No warning assigned. Choose Track 1 or Track 2 from the main command center warning list.';
  $('area').textContent='No storm selected';
  $('expires').textContent='—';$('motion').textContent='Not available';$('description').textContent='Waiting for an official NWS warning selection.';
  return;
 }
 if(item.manual&&item.point){
  $('area').textContent='Manual Radar Watch '+slot;
  $('status').className='status live';
  $('status').textContent='FIXED RADAR WATCH — NOT AUTOMATIC STORM MOVEMENT TRACKING';
  $('expires').textContent='Not applicable';
  $('motion').textContent='No movement inferred from radar';
  $('description').textContent='Manually selected radar viewing point at '+item.point.lat.toFixed(3)+', '+item.point.lon.toFixed(3)+'. Radar images refresh; this marker stays fixed and must be repositioned manually as the storm moves.';
  $('official').href='https://radar.weather.gov/';
  fixedMarker=L.circleMarker([item.point.lat,item.point.lon],{radius:8,color:slot===1?'#ff3455':'#56dbff',weight:3,fillOpacity:.8}).addTo(map);
  if(changed&&autoFocus)map.setView([item.point.lat,item.point.lon],9);
  return;
 }
 $('area').textContent=item.area;
 const expired=NewsWireTracking.hasExpired(item);
 $('status').className=feedOk&&!expired?'status live':'status';
 $('status').textContent=!feedOk?'OFFICIAL NWS WARNING FEED UNAVAILABLE — LAST ASSIGNED WARNING MAY BE STALE':
  expired?'WARNING EXPIRED — select another active warning':'Monitoring selected official NWS warning area';
 $('expires').textContent=item.expires?new Date(item.expires).toLocaleString():'Not stated';
 const motion=NewsWireTracking.parseMotion(item.description);
 $('motion').textContent=motion?motion.direction+' at '+motion.mph+' mph (NWS bulletin)':'Not reported in bulletin; no track projected';
 $('description').textContent=item.headline+'\n\n'+(item.instruction||'Consult the official NWS bulletin for safety directions.');
 try{const u=new URL(item.url);$('official').href=u.protocol==='https:'&&['api.weather.gov','www.weather.gov'].includes(u.hostname)?u.toString():'https://www.weather.gov/alerts'}catch(_){$('official').href='https://www.weather.gov/alerts'}
 if(NewsWireTracking.validGeometry(item.geometry)){
  const geo={type:'Feature',geometry:item.geometry,properties:{}};
  if(showWarnings){
   warningLayer=L.geoJSON(geo,{pane:'warning',style:{color:slot===1?'#ff3b5c':'#50d9ff',weight:4,fillOpacity:.12,fillColor:slot===1?'#ff2944':'#38a9ff'}}).addTo(map);
   const bounds=warningLayer.getBounds();if(changed&&autoFocus&&bounds.isValid())map.fitBounds(bounds.pad(.7),{maxZoom:9});
  }
 }else $('description').textContent+='\n\nNo NWS polygon supplied: location not guessed.';
}
$('radar').addEventListener('click',()=>{
 const on=radar.show(!radar.isShown());
 saveBool('radar-on',on);
 if(on)radar.play(savedBool('loop-on',true));
 radarButtons();
});
$('loop').addEventListener('click',()=>{
 if(!radar.isShown()){radar.show(true);saveBool('radar-on',true)}
 const playing=radar.play(!radar.isPlaying());
 saveBool('loop-on',playing);radarButtons();
});
$('autoFocus').addEventListener('click',()=>{
 autoFocus=!autoFocus;saveBool('auto-focus',autoFocus);deskButtons();
});
$('warningOverlay').addEventListener('click',()=>{
 showWarnings=!showWarnings;saveBool('warning-outline',showWarnings);
 // Repaint only this slot's official outline. User drawings remain independent.
 paint(selection,feedOk);deskButtons();
});
$('radarOnly').addEventListener('click',()=>{
 radarOnly=!radarOnly;saveBool('radar-only',radarOnly);deskButtons();
});
$('fullScreen').addEventListener('click',()=>bridge?.toggleFullscreen?.());
$('satellite').addEventListener('click',()=>mapStyle('satellite'));
$('streets').addEventListener('click',()=>mapStyle('streets'));
$('terrain').addEventListener('click',()=>mapStyle('terrain'));
$('streetView').addEventListener('click',toggleStreetPicking);
$('googleMaps').addEventListener('click',()=>{
 try{
  const p=MV.mapCoordinates(map);
  window.open(MV.googleMapUrl(p.lat,p.lng,map.getZoom(),mapMode),'_blank','noopener,noreferrer');
  $('streetViewHint').textContent='Opened Google Maps at the current radar location in your browser. Radar remains visible in NewsWire.';
 }catch(error){$('streetViewHint').textContent='Google Maps unavailable: '+error.message}
});
$('focus').addEventListener('click',()=>{
 if(selection?.manual&&selection.point)map.flyTo([selection.point.lat,selection.point.lon],9);
 else if(warningLayer&&warningLayer.getBounds().isValid())map.fitBounds(warningLayer.getBounds().pad(.6),{maxZoom:9});
});
$('main').addEventListener('click',()=>bridge?.openPanel?.('map'));
if(bridge?.onTrackedWarning)bridge.onTrackedWarning((n,item,feed)=>{if(n===slot)paint(item,feed)});
setInterval(()=>{if(!document.hidden)radar.refresh()},300000);
setInterval(()=>{if(selection&&NewsWireTracking.hasExpired(selection))paint(selection,feedOk)},60000);
})();
