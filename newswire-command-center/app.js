/* NewsWire Storm Command Center — in-app foreground nationwide monitoring. */
(function(){
'use strict';
const E=window.NewsWireNationwide, $=id=>document.getElementById(id);
const EVERY_MS=90000, AUTO_GAP_MS=45000, MANUAL_PAUSE_MS=120000;
const NOAA='https://api.weather.gov/alerts/active';
let map,polygonLayer,homePin,alerts=[],seen=new Set(),initialized=false,checking=false;
let motionGuideLayer=null,motionGuidesOn=false;
let auto=true,voice=false,satellite=true,mapMode='satellite',streetPick=false,lastFly=0,pauseUntil=0;
let deckSelectedIndex=-1,feedOk=false;
let liveRadar=null,pinSlot=null,mainRadarDesk=null;
const selectedTracks={1:null,2:null,3:null,4:null};
let home=null,pointIds=new Set(),localAlerts=[],localVerified=false,localError='',lastChecked=null;
const sayStatus=message=>{$('status').textContent=message};
function setting(k,v){try{if(v===undefined)return localStorage.getItem('nwcc-'+k);localStorage.setItem('nwcc-'+k,v)}catch(e){return null}}
function fmtDate(raw){if(!raw)return 'Not stated';const d=new Date(raw);return Number.isFinite(d.getTime())?d.toLocaleString():'Not stated'}
function setText(id,value){$(id).textContent=value}
const MV=window.NewsWireMapViews;
let base;
function mapStyle(next){
 if(!map||!MV)return;
 mapMode=MV.normalize(next||mapMode);
 satellite=mapMode==='satellite';
 if(base)map.removeLayer(base);
 base=MV.createLayer(L,mapMode).addTo(map);base.bringToBack();
 // All radar and official warning overlays remain independent of this basemap.
 for(const [id,mode] of [['style','satellite'],['streetMap','streets'],['terrainMap','terrain']]){
  const el=$(id);if(el)el.setAttribute('aria-pressed',String(mapMode===mode));
 }
 const footer=$('baseMapLabel');if(footer)footer.textContent=MV.STYLES[mapMode].label;
 setting('map-mode',mapMode);
}
function streetViewPick(){
 if(!map||!MV)return;
 streetPick=!streetPick;
 if(streetPick)pinSlot=null;
 const btn=$('streetView');
 btn.textContent=streetPick?'✕ Cancel Street View':'Street View ↗';
 btn.setAttribute('aria-pressed',String(streetPick));
 map.getContainer().style.cursor=streetPick?'crosshair':'';
 setText('mapHint',streetPick?'Click a specific road or location to open Google Street View in your browser. Photography is not live radar.':
  'Street View selection canceled.');
}
function openStreetView(lat,lng){
 try{
  window.open(MV.streetViewUrl(lat,lng),'_blank','noopener,noreferrer');
  setText('mapHint','Opened Google Street View for this point. Ground-level photographs may not be available here.');
 }catch(error){setText('mapHint','Street View location unavailable: '+error.message)}
}
function mapInit(){
 if(typeof L==='undefined'){sayStatus('Map library unavailable. Warning list may still load.');return}
 map=L.map('map',{preferCanvas:true,zoomControl:true,minZoom:3,maxZoom:17}).setView([39,-97],4);
 mapStyle(mapMode);
 motionGuideLayer=L.layerGroup().addTo(map);
 $('mainMotionGuide')?.addEventListener('click',()=>{
  motionGuidesOn=!motionGuidesOn;
  $('mainMotionGuide').textContent='NWS Motion Guides: '+(motionGuidesOn?'ON':'OFF');
  $('mainMotionGuide').setAttribute('aria-pressed',String(motionGuidesOn));
  drawMotionGuides();
 });
 if(window.NewsWireRadar){
  liveRadar=(window.NewsWireRadarLoop||NewsWireRadar).createRadar(L,map,message=>{
   const el=$('radarFeed');if(el)el.textContent=message;
  });
  const radarOn=setting('radar')!=='false';liveRadar.show(radarOn);
  const btn=$('radarToggle');if(btn){btn.setAttribute('aria-pressed',String(radarOn));btn.textContent='Radar: '+(radarOn?'ON':'OFF');}
  if(window.NewsWireMainRadarDesk){
   mainRadarDesk=window.NewsWireMainRadarDesk.init({
    map,L,composite:liveRadar,
    setActive:source=>{liveRadar=source},
    onWarningToggle:()=>draw(),
    onInteraction:()=>{
     pauseUntil=Date.now()+MANUAL_PAUSE_MS;streetPick=false;pinSlot=null;
     if($('streetView')){$('streetView').textContent='Street View ↗';$('streetView').setAttribute('aria-pressed','false')}
     map.getContainer().style.cursor='';
    }
   });
  }
 }
 map.on('dragstart',()=>{pauseUntil=Date.now()+MANUAL_PAUSE_MS;setText('mapHint','Manual map control: auto-fly paused for 2 minutes. Resume any time.')});
 map.on('click',e=>{
  if(mainRadarDesk?.consumeClick?.(e)){pauseUntil=Date.now()+MANUAL_PAUSE_MS;return}
  if(streetPick){
   streetPick=false;$('streetView').textContent='Street View ↗';
   $('streetView').setAttribute('aria-pressed','false');
   map.getContainer().style.cursor='';
   openStreetView(e.latlng.lat,e.latlng.lng);return;
  }
  if(pinSlot){const slot=pinSlot;pinSlot=null;trackManualSlot(slot,e.latlng)}
 });

 if(home)markHome(); else $('mapHint').textContent='Red outlines mark official warning areas, not an observed tornado path.';
}
function markHome(){
 if(!map||!home)return;
 if(!homePin)homePin=L.circleMarker([home.lat,home.lon],{radius:8,color:'#fff',weight:3,fillColor:'#298fff',fillOpacity:1}).addTo(map).bindTooltip('Your selected location');
 else homePin.setLatLng([home.lat,home.lon]);
}
function setHome(lat,lon,label,type){
 if(!E.validLocation(lat,lon))return;
 home={lat,lon,label,type};localVerified=false;localAlerts=[];pointIds.clear();
 setting('home',JSON.stringify(home));markHome();
 if(map)map.setView([lat,lon],8,{animate:true});
 setText('localStatus','Checking NWS warnings for '+label+'…');
 if(checking)setTimeout(refresh,1500);else refresh();
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
 if(userClicked)mainRadarDesk?.analyzeWarning?.(a);
 if(a.geometry){warningCenter(a);if(userClicked)pauseUntil=Date.now()+MANUAL_PAUSE_MS}
 else if(userClicked)sayStatus('Official warning has no polygon; details are shown in the sidebar.');
}
function drawMotionGuides(){
 if(!map||!motionGuideLayer||!window.NewsWireStormMotion)return;
 motionGuideLayer.clearLayers();
 if(!motionGuidesOn||!feedOk)return;
 let count=0;
 for(const alert of alerts.slice(0,75)){
  const guide=window.NewsWireStormMotion.bulletinGuide(alert);
  if(!guide)continue;
  const start=[guide.start.lat,guide.start.lng],end=[guide.end.lat,guide.end.lng];
  L.polyline([start,end],{color:'#cb9bff',weight:2,dashArray:'8 6',opacity:.85})
   .addTo(motionGuideLayer).bindTooltip('NWS '+guide.direction+' '+guide.mph+' mph · from WARNING AREA center, not a tornado path');
  L.circleMarker(start,{radius:4,color:'#dbbaff',weight:2,fillOpacity:.5})
   .addTo(motionGuideLayer).bindTooltip('Warning polygon center — NOT a storm or tornado location');
  count++;
 }
 setText('mapHint',count+' NWS bulletin direction guide(s), from WARNING CENTROIDS; NOT radar-detected storm/tornado tracks.');
}
function draw(){
 if(!map)return;
 if(polygonLayer){map.removeLayer(polygonLayer);polygonLayer=null}
 if(mainRadarDesk&&!mainRadarDesk.isWarningsShown()){drawMotionGuides();return}
 polygonLayer=L.geoJSON({type:'FeatureCollection',features:alerts.filter(a=>a.geometry).map(a=>({
  type:'Feature',geometry:a.geometry,properties:{id:a.id,event:a.event,area:a.area,expires:a.expires}
 }))},{style:()=>({color:'#ff4569',weight:3,fillColor:'#e72c55',fillOpacity:.16}),
 onEachFeature:(f,layer)=>{
  const a=alerts.find(x=>x.id===f.properties.id);if(!a)return;
  const div=document.createElement('div'),b=document.createElement('b'),p=document.createElement('p');
  b.textContent='NWS TORNADO WARNING';p.textContent=a.area+' · Expires '+fmtDate(a.expires);
  div.append(b,p);layer.bindPopup(div);layer.on('click',()=>detail(a));
 }}).addTo(map);
 drawMotionGuides();
}
function deckReport(){
 const bridge=window.NewsWireDesktopBridge;
 if(!bridge)return;
 bridge.reportState({
  warningCount:alerts.length,
  localTornado:localVerified&&localAlerts.some(a=>a.event==='Tornado Warning'),
  localVerified,feedOk,lastChecked:lastChecked?lastChecked.getTime():null,
  auto,voice,satellite
 });
 if(typeof bridge.publishWorkspace==='function')bridge.publishWorkspace({
  feedOk,warningCount:alerts.length,
  localStatus:$('localStatus').textContent,
  location:home?home.label:'Set a ZIP or use GPS',
  checked:$('checked').textContent,
  warnings:alerts.map(a=>({id:a.id,area:a.area,expires:a.expires,
   headline:a.headline,instruction:a.instruction,url:E.officialUrl(a)}))
 });
 if(typeof bridge.publishTrackUpdates==='function')bridge.publishTrackUpdates([1,2,3,4]
  .map(slot=>({slot,item:alerts.find(a=>a.id===selectedTracks[slot])||null}))
  .filter(x=>x.item));
}
function deckBind(){
 if(!window.NewsWireDesktopBridge)return;
 const ids={'home':'home','country':'country','resume':'resume','auto':'auto',
  'voice':'voice','satellite':'style','radar':'radarToggle','refresh':'refresh'};
 window.NewsWireDesktopBridge.onAction(action=>{
  if(action==='warning-latest'||action==='warning-next'){
   if(alerts.length){
    deckSelectedIndex=action==='warning-latest'?0:(deckSelectedIndex+1)%alerts.length;
    select(alerts[deckSelectedIndex],true);
   }else sayStatus(feedOk?'No active tornado warnings returned.':'Warning feed unavailable. Status unknown.');
  }else if(action==='zoom-in'||action==='zoom-out'){
   if(map){pauseUntil=Date.now()+MANUAL_PAUSE_MS;map.setZoom(map.getZoom()+(action==='zoom-in'?1:-1));}
  }else if(ids[action])$(ids[action]).click();
  deckReport();
 });
}
function trackManualSlot(slot,point){
 const bridge=window.NewsWireDesktopBridge;
 if(!bridge||!Number.isFinite(point?.lat)||!Number.isFinite(point?.lng)||![1,2,3,4].includes(slot))return;
 const item={id:'manual-radar-watch-'+slot+'-'+Date.now(),source:'manual',point:{lat:point.lat,lon:point.lng}};
 selectedTracks[slot]=item.id;
 bridge.trackWarning(slot,item);
 setText('mapHint','Pinned fixed radar watch '+slot+' at '+point.lat.toFixed(3)+', '+point.lng.toFixed(3)+'. This pin does not auto-follow a moving storm.');
 const e=$('trackedState');if(e)e.textContent='Watch '+slot+' pinned (stationary) · real radar updates';
 pauseUntil=Date.now()+MANUAL_PAUSE_MS;
}
function trackSelected(slot,item){
 const bridge=window.NewsWireDesktopBridge;
 if(!bridge||typeof bridge.trackWarning!=='function'||!feedOk)return;
 if(![1,2,3,4].includes(slot)||!item?.id)return;
 selectedTracks[slot]=item.id;
 bridge.trackWarning(slot,{id:item.id,area:item.area,headline:item.headline,
  description:item.description,instruction:item.instruction,expires:item.expires,
  sent:item.sent,geometry:item.geometry,url:E.officialUrl(item)});
 const badge=$('trackedState');if(badge)badge.textContent='Slot '+slot+': '+item.area;
}
function render(){
 const list=$('list');list.replaceChildren();
 setText('count',String(alerts.length));
 if(!alerts.length){
  const e=document.createElement('div');e.className='empty';
  e.textContent='No active nationwide tornado warnings returned at the last successful check. Other hazards may still exist.';
  list.append(e);return
 }
 for(const a of alerts){
  const group=document.createElement('div');group.className='warningGroup';
  const b=document.createElement('button');b.className='warning';b.type='button';
  const title=document.createElement('b');title.textContent=a.area;
  const sub=document.createElement('small');sub.textContent='NWS Tornado Warning · Expires '+fmtDate(a.expires)+(a.geometry?' · Map available':' · No polygon');
  b.append(title,sub);b.addEventListener('click',()=>select(a,true));group.append(b);
  if(window.NewsWireDesktopBridge){
   const ctl=document.createElement('div');ctl.className='trackControls';
   for(const slot of [1,2,3,4]){
    const t=document.createElement('button');t.type='button';t.textContent='Track '+slot+' ↗';
    t.title='Open tracked NWS warning area with independent live radar on storm window '+slot;
    t.addEventListener('click',()=>trackSelected(slot,a));ctl.append(t);
   }
   group.append(ctl);
  }
  list.append(group);
 }
}
async function officialFetch(url,signal){
 let next=url,all=[],pages=0;
 while(next&&pages<15){
  const r=await fetch(next,{headers:{Accept:'application/geo+json'},cache:'no-store',signal});
  if(!r.ok)throw Error('NWS HTTP '+r.status);
  const data=await r.json();
  if(!data||!Array.isArray(data.features))throw Error('NWS returned unexpected data');
  all.push(...data.features);pages++;
  next=data.pagination&&data.pagination.next||null;
  if(next){
   const u=new URL(next);
   if(u.protocol!=='https:'||u.hostname!=='api.weather.gov'||!u.pathname.startsWith('/alerts'))
    throw Error('Untrusted NWS pagination URL');
  }
 }
 if(next)throw Error('NWS pagination exceeded safe page limit; coverage unknown');
 return {type:'FeatureCollection',features:all};
}
async function nationwideFetch(signal){
 try{return await officialFetch(NOAA+'?event=Tornado%20Warning',signal)}
 catch(e){
  if(!String(e.message).includes('HTTP 400'))throw e;
  return officialFetch(NOAA+'?limit=500',signal);
 }
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
   nationwideFetch(controller.signal),
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
  alerts=next;draw();render();lastChecked=new Date();feedOk=true;initialized=true;deckSelectedIndex=-1;
  const currentIds=new Set(next.map(a=>a.id)); // Keep IDs seen across updates in this session.
  for(const id of currentIds)seen.add(id);
  if(seen.size>1000)seen=new Set(currentIds);
  setText('checked','Last successful NWS check: '+lastChecked.toLocaleTimeString());
  sayStatus(alerts.length+' active US tornado warnings · updated '+lastChecked.toLocaleTimeString());
  if(localNwsTornado&&localVerified&&localNwsTornado.geometry){
   if(Date.now()-lastFly>15000){warningCenter(localNwsTornado);detail(localNwsTornado);lastFly=Date.now();setText('mapHint','LOCAL TORNADO WARNING TAKES PRIORITY');}
  }else maybeFly(fresh);
 }catch(err){
  feedOk=false;
  sayStatus('NWS nationwide feed unavailable — warning status UNKNOWN ('+err.message+').');
  setText('checked','Last successful NWS check: '+(lastChecked?lastChecked.toLocaleTimeString():'none')+' · currently stale/unknown');
  if(!localVerified)showLocal();
 }finally{clearTimeout(timeout);checking=false;$('refresh').disabled=false;deckReport()}
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
  if(location.protocol==='file:'&&/Android/i.test(navigator.userAgent)){location.href='newswire://gps';return}
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
 $('style').addEventListener('click',()=>{mapStyle('satellite');setting('satellite','true')});
 $('streetMap').addEventListener('click',()=>{mapStyle('streets');setting('satellite','false')});
 $('terrainMap').addEventListener('click',()=>{mapStyle('terrain');setting('satellite','false')});
 $('streetView').addEventListener('click',streetViewPick);
 $('googleMaps').addEventListener('click',()=>{
  if(!map)return;
  try{
   const p=MV.mapCoordinates(map);
   window.open(MV.googleMapUrl(p.lat,p.lng,map.getZoom(),mapMode),'_blank','noopener,noreferrer');
   setText('mapHint','Google Maps opened in your browser at the current radar location. Radar and warning overlays stay in NewsWire.');
  }catch(error){setText('mapHint','Google Maps unavailable: '+error.message)}
 });
 for(const slot of [1,2,3,4]){
  $('pinStorm'+slot)?.addEventListener('click',()=>{
   if(!map){setText('mapHint','Map unavailable; cannot pin a watch area.');return}
   streetPick=false;$('streetView').textContent='Street View ↗';$('streetView').setAttribute('aria-pressed','false');map.getContainer().style.cursor='';
   pinSlot=slot;setText('mapHint','Click a radar location to watch in Storm Tracker '+slot+'. This is a fixed radar viewing location.');
  });
 }
 $('radarLoop')?.addEventListener('click',()=>{
  if(mainRadarDesk){mainRadarDesk.onLoop();return}
  if(!liveRadar)return;
  if(!liveRadar.isShown())liveRadar.show(true);
  const play=liveRadar.play?.(!liveRadar.isPlaying?.())||false;
  $('radarLoop').textContent=play?'⏸ Pause Loop':'▶ Play Loop';
  $('radarLoop').setAttribute('aria-pressed',String(play));
 });
 $('radarToggle')?.addEventListener('click',()=>{
  if(mainRadarDesk){mainRadarDesk.onRadarToggle();return}
  if(!liveRadar)return;
  const shown=liveRadar.show(!liveRadar.isShown());setting('radar',String(shown));
  $('radarToggle').textContent='Radar: '+(shown?'ON':'OFF');
  $('radarToggle').setAttribute('aria-pressed',String(shown));
 });
 for(const id of ['auto','voice','style'])$(id).addEventListener('click',deckReport);
}
// Native Android can forward GPS through this entrypoint while WebView is visible.
window.updateNewsWirePosition=(lat,lon)=>{if(!E.validLocation(lat,lon))return;
 if(home&&home.type==='gps'&&Math.abs(home.lat-lat)<.015&&Math.abs(home.lon-lon)<.015){return;}
 setHome(lat,lon,'Device GPS','gps')};
window.NewsWireLocationError=message=>{setText('localStatus',String(message||'GPS unavailable. Enter ZIP instead.'))};
function start(){
 loadHome();auto=setting('auto')!=='false';mapMode=MV?.normalize(setting('map-mode'));satellite=mapMode==='satellite';
 setButtons();mapInit();bind();deckBind();markHome();
 if(window.NewsWireDesktopBridge){
  const bridge=window.NewsWireDesktopBridge;
  if(typeof bridge.onRequestTrack==='function')bridge.onRequestTrack((slot,id)=>{
   const item=alerts.find(a=>a.id===id);
   if(item)trackSelected(slot,item);
  });
  if(typeof bridge.onFocusWarning==='function')bridge.onFocusWarning(id=>{
   const item=alerts.find(a=>a.id===id);
   if(item)select(item,true);else sayStatus('Selected warning is no longer in the active feed.');
  });
  if(typeof bridge.onSetZip==='function')bridge.onSetZip(zip=>{
   if(/^\d{5}$/.test(zip)){$('zip').value=zip;$('setZip').click();}
  });
 }
 deckReport();refresh();
 setInterval(()=>{if(!document.hidden||window.NewsWireDesktopBridge)refresh()},EVERY_MS);
 if(window.NewsWireDesktopBridge)setInterval(deckReport,5000);
 if(window.NewsWireRadar)setInterval(()=>{if(liveRadar&&(!document.hidden||window.NewsWireDesktopBridge)){if(mainRadarDesk)mainRadarDesk.onRefresh();else liveRadar.refresh()}},300000);
 document.addEventListener('visibilitychange',()=>{if(!document.hidden&&(!lastChecked||Date.now()-lastChecked.getTime()>EVERY_MS))refresh()});
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',start);else start();
})();
