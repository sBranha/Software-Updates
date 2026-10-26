/* NewsWire Storm Command Center — in-app foreground nationwide monitoring. */
(function(){
'use strict';
const E=window.NewsWireNationwide, $=id=>document.getElementById(id);
const EVERY_MS=90000, AUTO_GAP_MS=45000, MANUAL_PAUSE_MS=120000;
const NOAA='https://api.weather.gov/alerts/active';
let map,polygonLayer,homePin,alerts=[],seen=new Set(),initialized=false,checking=false;
let auto=true,voice=false,satellite=true,lastFly=0,pauseUntil=0;
let home=null,pointIds=new Set(),localAlerts=[],localVerified=false,localError='',lastChecked=null;
const sayStatus=message=>{$('status').textContent=message};
function setting(k,v){try{if(v===undefined)return localStorage.getItem('nwcc-'+k);localStorage.setItem('nwcc-'+k,v)}catch(e){return null}}
function fmtDate(raw){if(!raw)return 'Not stated';const d=new Date(raw);return Number.isFinite(d.getTime())?d.toLocaleString():'Not stated'}
function setText(id,value){$(id).textContent=value}
function layerUrl(){return satellite?'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}':
 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}'}
let base;
function mapStyle(){
 if(!map)return;
 if(base)map.removeLayer(base);
 base=L.tileLayer(layerUrl(),{maxZoom:17,attribution:'Tiles © Esri & partners'}).addTo(map);base.bringToBack();
 $('style').textContent=satellite?'Satellite map':'Street map';$('style').setAttribute('aria-pressed',String(satellite));
}
function mapInit(){
 if(typeof L==='undefined'){sayStatus('Map library unavailable. Warning list may still load.');return}
 map=L.map('map',{preferCanvas:true,zoomControl:true,minZoom:3,maxZoom:17}).setView([39,-97],4);
 mapStyle();
 map.on('dragstart',()=>{pauseUntil=Date.now()+MANUAL_PAUSE_MS;setText('mapHint','Manual map control: auto-fly paused for 2 minutes. Resume any time.')});
 if(home)markHome(); else $('mapHint').textContent='Red outlines mark official warning areas, not an observed tornado path.';
}
function markHome(){
 if(!map||!home)return;
 if(!homePin)homePin=L.circleMarker([home.lat,home.lon],{radius:8,color:'#fff',weight:3,fillColor:'#298fff',fillOpacity:1}).addTo(map).bindTooltip('Your selected location');
 else homePin.setLatLng([home.lat,home.lon]);
}
function setHome(lat,lon,label,type){
 if(!E.validLocation(lat,lon))return;
 home={lat,lon,label,type};
 setting('home',JSON.stringify(home));markHome();
 if(map)map.setView([lat,lon],8,{animate:true});
 setText('localStatus','Checking NWS warnings for '+label+'…');
 refresh();
}
function loadHome(){
 try{const h=JSON.parse(setting('home')||'null');if(h&&E.validLocation(h.lat,h.lon)&&h.type==='zip')home=h;}catch(e){}
 const z=setting('zip');if(z&&/^\d{5}$/.test(z))$('zip').value=z;
}
function speech(text){
 if(!voice||!('speechSynthesis' in window))return;
 try{speechSynthesis.cancel();const u=new SpeechSynthesisUtterance(text);u.rate=1.03;u.pitch=1;u.lang='en-US';speechSynthesis.speak(u)}catch(e){}
}
function setButtons(){
 $('auto').setAttribute('aria-pressed',String(auto));$('auto').textContent='Auto-fly: '+(auto?'ON':'OFF');
 $('voice').setAttribute('aria-pressed',String(voice));$('voice').textContent='Voice: '+(voice?'ON':'OFF');
}
function warningCenter(a){
 if(!map||!a||!a.geometry||!a.center)return false;
 const f={type:'Feature',geometry:a.geometry,properties:{}};
 const bounds=L.geoJSON(f).getBounds();
 if(!bounds.isValid())return false;
 map.flyToBounds(bounds.pad(.35),{maxZoom:9,duration:1.2});return true;
}
function detail(a){
 if(!a)return;
 $('detail').textContent='AREA: '+a.area+'\nISSUED: '+fmtDate(a.sent)+'\nEXPIRES: '+fmtDate(a.expires)+
   '\n\n'+a.headline+'\n\n'+(a.instruction||'Read the full official NWS bulletin for safety instructions.')+
   (a.geometry?'\n\nBoundary available on map.':'\n\nThis alert has no supplied map polygon.');
 $('official').href=E.officialUrl(a);
}
function select(a,userClicked=false){
 detail(a);
 if(a.geometry){warningCenter(a);if(userClicked)pauseUntil=Date.now()+MANUAL_PAUSE_MS}
 else if(userClicked)sayStatus('Official warning has no polygon; details are shown in the sidebar.');
}
function draw(){
 if(!map)return;
 if(polygonLayer){map.removeLayer(polygonLayer);polygonLayer=null}
 polygonLayer=L.geoJSON({type:'FeatureCollection',features:alerts.filter(a=>a.geometry).map(a=>({
  type:'Feature',geometry:a.geometry,properties:{id:a.id,event:a.event,area:a.area,expires:a.expires}
 }))},{style:()=>({color:'#ff4569',weight:3,fillColor:'#e72c55',fillOpacity:.16}),
 onEachFeature:(f,layer)=>{
  const a=alerts.find(x=>x.id===f.properties.id);if(!a)return;
  const div=document.createElement('div'),b=document.createElement('b'),p=document.createElement('p');
  b.textContent='NWS TORNADO WARNING';p.textContent=a.area+' · Expires '+fmtDate(a.expires);
  div.append(b,p);layer.bindPopup(div);layer.on('click',()=>detail(a));
 }}).addTo(map);
}
function render(){
 const list=$('list');list.replaceChildren();
 setText('count',String(alerts.length));
 if(!alerts.length){const e=document.createElement('div');e.className='empty';
 e.textContent='No active nationwide tornado warnings returned at the last successful check. Other hazards may still exist.';list.append(e);return}
 for(const a of alerts){
  const b=document.createElement('button');b.className='warning';b.type='button';
  const title=document.createElement('b');title.textContent=a.area;
  const sub=document.createElement('small');sub.textContent='NWS Tornado Warning · Expires '+fmtDate(a.expires)+(a.geometry?' · Map available':' · No polygon');
  b.append(title,sub);b.addEventListener('click',()=>select(a,true));list.append(b);
 }
}
async function officialFetch(url,signal){
 const r=await fetch(url,{headers:{Accept:'application/geo+json'},cache:'no-store',signal});
 if(!r.ok)throw Error('NWS HTTP '+r.status);
 const data=await r.json();if(!data||!Array.isArray(data.features))throw Error('NWS returned unexpected data');
 // Warn rather than implying complete coverage when server provides a paginated response.
 if(data.pagination&&data.pagination.next)throw Error('Nationwide list is paginated; complete coverage not verified');
 return data;
}
function localWarningRank(a){
 if(a.event==='Tornado Warning')return 0;
 if(a.event==='Flash Flood Warning')return 1;
 if(a.event==='Severe Thunderstorm Warning')return 2;
 return 3;
}
async function fetchLocal(signal){
 if(!home){localVerified=false;localError='Select a ZIP or use GPS to enable local warning priority';return}
 const endpoint=NOAA+'?point='+home.lat.toFixed(4)+','+home.lon.toFixed(4);
 try{
  const data=await officialFetch(endpoint,signal);
  localAlerts=data.features.filter(f=>{
   const p=f&&f.properties||{};
   return p.event&&(!p.status||p.status.toLowerCase()==='actual')&&
     !['test','exercise','cancel'].includes(String(p.messageType||'').toLowerCase())&&
     !(Date.parse(p.expires||'')<=Date.now());
  }).map(f=>({id:String((f.properties||{}).id||f.id||''),event:f.properties.event,area:f.properties.areaDesc||'Your area',
   headline:f.properties.headline||f.properties.event,geometry:f.geometry||null,expires:f.properties.expires||''}))
  .sort((a,b)=>localWarningRank(a)-localWarningRank(b));
  pointIds=new Set(localAlerts.map(a=>a.id));localVerified=true;localError='';
 }catch(e){localVerified=false;localError=e.message;pointIds.clear();localAlerts=[]}
}
function showLocal(){
 const box=$('localStatus');box.className=localVerified&&localAlerts.length?'urgent':'unknown';
 if(!home){box.textContent='Select ZIP or GPS to check official local warnings. Nationwide warnings remain available.';return}
 const heading=home.label+(home.type==='zip'?' (ZIP approximate center)':' (GPS)');
 if(!localVerified){box.textContent='LOCAL WARNING STATUS UNKNOWN for '+heading+'. '+localError+'. Confirm official NWS/WEA alerts.';return}
 if(localAlerts.length){
  box.textContent='⚠ '+heading+': '+localAlerts.map(a=>a.event+' — '+a.area).join(' · ')+'. LOCAL ALERTS TAKE PRIORITY. Follow official instructions.';
 }else box.textContent='No active NWS alerts reported for '+heading+' at the last successful check. This is not an all-clear.';
}
function maybeFly(fresh){
 if(!auto||!map)return;
 if(home&&(!localVerified||localAlerts.length))return; // Never pan away while local threats are active or status is unknown.
 if(Date.now()<pauseUntil)return;
 if(Date.now()-lastFly<AUTO_GAP_MS)return;
 const c=fresh.find(a=>a.geometry&&a.center)||null;
 if(c&&warningCenter(c)){detail(c);lastFly=Date.now();setText('mapHint','Viewing NEW official tornado warning: '+c.area);speech('New National Weather Service tornado warning for '+c.area);}
}
async function refresh(){
 if(checking)return;
 checking=true;$('refresh').disabled=true;sayStatus('Checking official NWS warnings…');
 const controller=new AbortController(),timeout=setTimeout(()=>controller.abort(),20000);
 try{
  // Two official requests in parallel: nationwide event filter and safety-first local point warnings.
  const responses=await Promise.allSettled([
   officialFetch(NOAA+'?event=Tornado%20Warning&status=actual&limit=500',controller.signal),
   fetchLocal(controller.signal)
  ]);
  showLocal();
  if(responses[0].status==='rejected')throw responses[0].reason;
  const next=E.alertsFromResponse(responses[0].value);
  const fresh=initialized?E.newAlerts(next,seen):next.slice(0,1);
  const localNwsTornado=next.find(a=>E.isLocal(a,home&&home.lat,home&&home.lon,pointIds));
  if(localNwsTornado&&localVerified){ // Map is drawn before trying to focus a newly issued local warning.
   const newlyLocal=!seen.has(localNwsTornado.id);
   if(newlyLocal){speech('Tornado warning affecting your selected location. Follow National Weather Service instructions.');}
  }
  alerts=next;draw();render();lastChecked=new Date();initialized=true;
  const currentIds=new Set(next.map(a=>a.id)); // Keep IDs seen across updates in this session.
  for(const id of currentIds)seen.add(id);
  if(seen.size>1000)seen=new Set(currentIds);
  setText('checked','Last successful NWS check: '+lastChecked.toLocaleTimeString());
  sayStatus(alerts.length+' active US tornado warnings · updated '+lastChecked.toLocaleTimeString());
  if(localNwsTornado&&localVerified&&localNwsTornado.geometry){
   if(Date.now()-lastFly>15000){warningCenter(localNwsTornado);detail(localNwsTornado);lastFly=Date.now();setText('mapHint','LOCAL TORNADO WARNING TAKES PRIORITY');}
  }else maybeFly(fresh);
 }catch(err){
  sayStatus('NWS nationwide feed unavailable — warning status UNKNOWN ('+err.message+').');
  setText('checked','Last successful NWS check: '+(lastChecked?lastChecked.toLocaleTimeString():'none')+' · currently stale/unknown');
  if(!localVerified)showLocal();
 }finally{clearTimeout(timeout);checking=false;$('refresh').disabled=false}
}
function restoreAuto(){pauseUntil=0;auto=true;setButtons();setText('mapHint','Auto-fly resumed for new nationwide tornado warnings.');maybeFly(alerts.slice(0,1))}
function bind(){
 $('setZip').addEventListener('click',async()=>{
  const zip=$('zip').value.trim();if(!/^\d{5}$/.test(zip)){setText('localStatus','Enter a valid 5-digit US ZIP code.');return}
  setText('localStatus','Looking up ZIP '+zip+'…');
  const controller=new AbortController(),t=setTimeout(()=>controller.abort(),10000);
  try{const r=await fetch('https://api.zippopotam.us/us/'+zip,{signal:controller.signal});if(!r.ok)throw Error('ZIP lookup returned '+r.status);
   const data=await r.json(),place=data.places&&data.places[0],lat=Number(place.latitude),lon=Number(place.longitude);
   if(!E.validLocation(lat,lon))throw Error('ZIP coordinates unavailable');
   setting('zip',zip);setHome(lat,lon,zip+' · '+(place['place name']||'US'), 'zip');
  }catch(e){setText('localStatus','ZIP lookup unavailable: '+e.message+'. You can try GPS.')}finally{clearTimeout(t)}
 });
 $('myGps').addEventListener('click',()=>{
  if(!navigator.geolocation){setText('localStatus','Geolocation not supported; enter ZIP.');return}
  setText('localStatus','Requesting location permission…');
  navigator.geolocation.getCurrentPosition(pos=>setHome(pos.coords.latitude,pos.coords.longitude,'GPS location','gps'),
   e=>setText('localStatus','GPS unavailable or permission denied: '+e.message+'. Enter a ZIP instead.'),
   {enableHighAccuracy:true,timeout:15000,maximumAge:60000});
 });
 $('refresh').addEventListener('click',refresh);
 $('home').addEventListener('click',()=>{if(!home){setText('localStatus','Enter a ZIP or allow GPS to set home first.');return}pauseUntil=Date.now()+MANUAL_PAUSE_MS;map&&map.flyTo([home.lat,home.lon],8)});
 $('country').addEventListener('click',()=>{pauseUntil=Date.now()+MANUAL_PAUSE_MS;map&&map.flyTo([39,-97],4)});
 $('resume').addEventListener('click',restoreAuto);
 $('auto').addEventListener('click',()=>{auto=!auto;setting('auto',String(auto));setButtons()});
 $('voice').addEventListener('click',()=>{voice=!voice;setting('voice',String(voice));setButtons()});
 $('style').addEventListener('click',()=>{satellite=!satellite;setting('satellite',String(satellite));mapStyle()});
}
// Native Android can forward GPS through this entrypoint while WebView is visible.
window.updateNewsWirePosition=(lat,lon)=>{if(E.validLocation(lat,lon))setHome(lat,lon,'Device GPS','gps')};
function start(){
 loadHome();auto=setting('auto')!=='false';satellite=setting('satellite')!=='false';
 setButtons();mapInit();bind();markHome();refresh();
 setInterval(()=>{if(!document.hidden)refresh()},EVERY_MS);
 document.addEventListener('visibilitychange',()=>{if(!document.hidden&&(!lastChecked||Date.now()-lastChecked.getTime()>EVERY_MS))refresh()});
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',start);else start();
})();
