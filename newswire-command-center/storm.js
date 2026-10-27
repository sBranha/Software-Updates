/* A real independent radar map for Storm Slot 1 or 2. Does not derive storm position. */
(()=>{
'use strict';
const $=id=>document.getElementById(id);
const bridge=window.NewsWireDesktopBridge;
const slot=Number(new URLSearchParams(location.search).get('slot'))||1;
$('title').textContent='Storm Tracker '+(slot===2?'2':'1');
const map=L.map('map',{zoomControl:true}).setView([39,-97],5);
const base=L.tileLayer('https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryTopo/MapServer/tile/{z}/{y}/{x}',{
 attribution:'Imagery: U.S. Geological Survey',maxZoom:17,maxNativeZoom:16
}).addTo(map);
const radar=NewsWireRadar.createRadar(L,map,s=>$('radarState').textContent=s);
radar.show(true);
map.createPane('warning');
map.getPane('warning').style.zIndex=500;
let warningLayer=null,selection=null,feedOk=false;
function paint(item,feed){
 feedOk=Boolean(feed);
 selection=item;
 if(warningLayer){map.removeLayer(warningLayer);warningLayer=null}
 if(!item){
  $('status').className='status';
  $('status').textContent='No warning assigned. Choose Track 1 or Track 2 from the main command center warning list.';
  $('area').textContent='No storm selected';
  $('expires').textContent='—';$('motion').textContent='Not available';$('description').textContent='Waiting for an official NWS warning selection.';
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
  const bounds=warningLayer.getBounds();if(bounds.isValid())map.fitBounds(bounds.pad(.7),{maxZoom:9});
 }else $('description').textContent+='\n\nNo NWS polygon supplied: location not guessed.';
}
$('radar').addEventListener('click',()=>{$('radar').setAttribute('aria-pressed',String(radar.show(!radar.isShown())));$('radar').textContent='Radar: '+(radar.isShown()?'ON':'OFF')});
$('satellite').addEventListener('click',()=>{
 const on=map.hasLayer(base);if(on)map.removeLayer(base);else base.addTo(map).bringToBack();
 $('satellite').setAttribute('aria-pressed',String(!on));$('satellite').textContent='Satellite: '+(!on?'ON':'OFF');
});
$('focus').addEventListener('click',()=>{if(warningLayer&&warningLayer.getBounds().isValid())map.fitBounds(warningLayer.getBounds().pad(.6),{maxZoom:9})});
$('main').addEventListener('click',()=>bridge?.openPanel?.('map'));
if(bridge?.onTrackedWarning)bridge.onTrackedWarning((n,item,feed)=>{if(n===slot)paint(item,feed)});
setInterval(()=>{if(!document.hidden)radar.refresh()},300000);
setInterval(()=>{if(selection&&NewsWireTracking.hasExpired(selection))paint(selection,feedOk)},60000);
})();
