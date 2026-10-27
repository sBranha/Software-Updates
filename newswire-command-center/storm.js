/* Each storm slot is an independent radar desk: site/products, map, warning, hand-drawn polygon. */
(()=>{
'use strict';
const $=id=>document.getElementById(id);
const bridge=window.NewsWireDesktopBridge;
const slot=Number(new URLSearchParams(location.search).get('slot'))||1;
$('title').textContent='Storm Tracker '+(String(slot));
const viewKey='nwcc-storm-viewport-'+slot;
let savedView=null;
try{
 const v=JSON.parse(localStorage.getItem(viewKey)||'null');
 if(v&&Number.isFinite(v.lat)&&Number.isFinite(v.lng)&&Number.isInteger(v.zoom)&&v.zoom>=3&&v.zoom<=17&&
    Math.abs(v.lat)<=90&&Math.abs(v.lng)<=180)savedView=v;
}catch(_){}
const map=L.map('map',{zoomControl:true}).setView(savedView?[savedView.lat,savedView.lng]:[39,-97],savedView?savedView.zoom:5);
let viewportTimer=null;
map.on('moveend zoomend',()=>{
 clearTimeout(viewportTimer);
 viewportTimer=setTimeout(()=>{
  try{
   const c=map.getCenter();
   localStorage.setItem(viewKey,JSON.stringify({lat:c.lat,lng:c.lng,zoom:map.getZoom()}));
  }catch(_){}
 },350);
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
const R=window.NewsWireSiteRadar;
const radarComposite=(window.NewsWireRadarLoop||NewsWireRadar).createRadar(L,map,s=>{
 if(radarMode==='mosaic')$('radarState').textContent=s;
});
const radarLocal=R.createSiteRadar(L,map,s=>{
 if(radarMode==='site')$('radarState').textContent=s;
});
let radarMode='mosaic',radar=radarComposite,selectedSite='KHTX',selectedProduct='N0B';
const radarSettingKey='nwcc-radar-config-'+slot;
try{
 const pref=JSON.parse(localStorage.getItem(radarSettingKey)||'null');
 if(pref&&pref.mode==='site')radarMode='site';
 if(R.siteFor(pref?.site))selectedSite=pref.site;
 if(R.productFor(pref?.product))selectedProduct=pref.product;
}catch(_){}
function saveRadar(){
 try{localStorage.setItem(radarSettingKey,JSON.stringify({mode:radarMode,site:selectedSite,product:selectedProduct}))}catch(_){}
}
for(const site of R.SITES){
 const opt=document.createElement('option');opt.value=site.id;opt.textContent=site.id+' · '+site.name;
 $('radarSite').append(opt);
}
for(const product of R.PRODUCTS){
 const opt=document.createElement('option');opt.value=product.id;opt.textContent=product.name;
 $('radarProduct').append(opt);
}
$('radarSite').value=selectedSite;$('radarProduct').value=selectedProduct;
function statusProduct(){
 $('radarSource').value=radarMode;
 $('radarSite').disabled=radarMode!=='site';
 $('radarProduct').disabled=radarMode!=='site';
 $('centerSite').disabled=radarMode!=='site';
 $('loop').disabled=radarMode==='site';
 $('loop').textContent=radarMode==='site'?'Latest single scan':'⏸ Pause Loop';
 $('loop').setAttribute('aria-pressed',String(radarMode==='mosaic'&&radarComposite.isPlaying?.()));
 $('siteProductStatus').textContent=radarMode==='mosaic'?
  'Nationwide NEXRAD reflectivity mosaic loop. Choose Single NWS radar to inspect Doppler velocity.' :
  selectedSite+' · '+R.productFor(selectedProduct).name+
  ' · Level III latest image (scan timestamp not independently verified).';
}
function chooseRadar(mode,site=selectedSite,product=selectedProduct){
 if(!R.siteFor(site)||!R.productFor(product))return;
 const on=radar.isShown();
 radar.show(false);
 radarMode=mode==='site'?'site':'mosaic';
 selectedSite=site;selectedProduct=product;
 radar=radarMode==='site'?radarLocal:radarComposite;
 if(radarMode==='site')radarLocal.select(site,product);
 if(on)radar.show(true);
 $('radarSite').value=site;$('radarProduct').value=product;
 $('radar').textContent='Radar: '+(radar.isShown()?'ON':'OFF');
 $('radar').setAttribute('aria-pressed',String(radar.isShown()));
 statusProduct();saveRadar();
}
function zoomToSite(id,zoom=8){
 const site=R.siteFor(id);
 if(site)map.flyTo([site.lat,site.lng],zoom,{animate:true});
}
const markers=L.layerGroup().addTo(map);
for(const site of R.SITES){
 const pin=L.circleMarker([site.lat,site.lng],{
  radius:7,color:'#d7f4ff',fillColor:'#1b8dd8',fillOpacity:.96,weight:2,bubblingMouseEvents:false
 }).addTo(markers);
 pin.bindTooltip(site.id+' — '+site.name+' · click for Doppler radar',{direction:'top'});
 pin.on('click',()=>{
  if(window.NWPolygonIsDrawing)return;
  chooseRadar('site',site.id,selectedProduct);
  map.flyTo([site.lat,site.lng],Math.max(7,map.getZoom()),{animate:true});
 });
}
radar=radarMode==='site'?radarLocal:radarComposite;
if(radarMode==='site')radarLocal.select(selectedSite,selectedProduct);
radar.show(true);
statusProduct();
$('radarSource').addEventListener('change',()=>{
 chooseRadar($('radarSource').value);
 if(radarMode==='site')zoomToSite(selectedSite);
});
$('radarSite').addEventListener('change',()=>{
 chooseRadar('site',$('radarSite').value,selectedProduct);
 zoomToSite(selectedSite);
});
$('radarProduct').addEventListener('change',()=>chooseRadar('site',selectedSite,$('radarProduct').value));
$('centerSite').addEventListener('click',()=>zoomToSite(selectedSite));

map.createPane('warning');
map.getPane('warning').style.zIndex=500;
let warningLayer=null,selection=null,feedOk=false;
let fixedMarker=null;
function paint(item,feed){
 feedOk=Boolean(feed);
 const changed=!selection||selection.id!==item?.id;
 if(changed&&selection&&selection.id!==item?.id)window.dispatchEvent(new CustomEvent('newswire:slot-changed'));
 selection=item;
 if(warningLayer){map.removeLayer(warningLayer);warningLayer=null}
 if(fixedMarker){map.removeLayer(fixedMarker);fixedMarker=null}
 if(!item){
  $('status').className='status';
  $('status').textContent='Independent radar ready. Select a NEXRAD site or draw your own polygon; no NWS warning is assigned.';
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
  if(changed&&!savedView)map.setView([item.point.lat,item.point.lon],9);
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
  warningLayer=L.geoJSON(geo,{pane:'warning',style:{color:slot===1?'#ff3b5c':'#50d9ff',weight:4,fillOpacity:.12,fillColor:slot===1?'#ff2944':'#38a9ff'}}).addTo(map);
  const bounds=warningLayer.getBounds();if(changed&&!savedView&&bounds.isValid())map.fitBounds(bounds.pad(.7),{maxZoom:9});
 }else $('description').textContent+='\n\nNo NWS polygon supplied: location not guessed.';
}
$('radar').addEventListener('click',()=>{
 const on=radar.show(!radar.isShown());
 $('radar').setAttribute('aria-pressed',String(on));$('radar').textContent='Radar: '+(on?'ON':'OFF');
 $('loop').setAttribute('aria-pressed',String(on&&radarMode==='mosaic'&&radar.isPlaying?.()));
});
$('loop').addEventListener('click',()=>{
 if(radarMode!=='mosaic')return;
 if(!radar.isShown()){radar.show(true);$('radar').setAttribute('aria-pressed','true');$('radar').textContent='Radar: ON'}
 const playing=radar.play?radar.play(!radar.isPlaying()):false;
 $('loop').setAttribute('aria-pressed',String(playing));
 $('loop').textContent=playing?'⏸ Pause Loop':'▶ Play Loop';
});
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
setInterval(()=>{if(!document.hidden&&radar.isShown())radar.refresh()},300000);
setInterval(()=>{if(selection&&NewsWireTracking.hasExpired(selection))paint(selection,feedOk)},60000);
window.addEventListener('beforeunload',()=>{
 clearTimeout(viewportTimer);
 radarComposite.dispose?.();
 radarLocal.dispose?.();
});
})();
