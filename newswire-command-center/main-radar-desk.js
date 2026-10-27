/* Full meteorologist desk on the MAIN NewsWire radar, not a second pop-out.
   Uses the exact same IEM/NEXRAD site and archive engines as Storm Desks 1-4.
   Independent state namespace: nwcc-main-*. */
(function(root,factory){
 const api=factory();
 if(typeof module==='object'&&module.exports)module.exports=api;
 root.NewsWireMainRadarDesk=api;
})(typeof window!=='undefined'?window:globalThis,function(){
'use strict';
const storagePrefix='nwcc-main-';
const $=id=>document.getElementById(id);
const R=()=>window.NewsWireSiteRadar;
const D=()=>window.NewsWireDrawnTracks;
const V=()=>window.NewsWireVelocityCouplet;
function safeGet(name){try{return localStorage.getItem(storagePrefix+name)}catch(_){return null}}
function save(name,value){try{localStorage.setItem(storagePrefix+name,value)}catch(_){}}
function forget(name){try{localStorage.removeItem(storagePrefix+name)}catch(_){}}
function chooseConfig(json){
 try{const q=JSON.parse(json||'null');
  return {mode:q?.mode==='site'?'site':'mosaic',
   site:R()?.siteFor(q?.site)?q.site:'KHTX',
   product:R()?.productFor(q?.product)?q.product:'N0B'};
 }catch(_){return {mode:'mosaic',site:'KHTX',product:'N0B'}}
}
function init({map,L,composite,setActive,onWarningToggle,onInteraction}){
 if(!map||!R()?.createSiteRadar||!D()||!V())throw Error('NewsWire main meteorology modules not loaded');
 const bridge=window.NewsWireDesktopBridge;
 const cfg=chooseConfig(safeGet('radar-config'));
 let mode=cfg.mode,site=cfg.site,product=cfg.product,active=composite;
 let siteFrames=[],frameIndex=-1,playing=false,timer=null,requestToken=0;
 let warningOn=safeGet('warnings')!=='false';
 let radarOn=safeGet('radar')!=='false';
 let drawing=false,points=[],area=null,pointsLayer=null,communityLayer=null,lookupToken=0,lookupWorking=false;
 let picking=false,gates=[],gateLayer=null;
 const siteRadar=R().createSiteRadar(L,map,s=>{
  if(mode==='site'){const feed=$('radarFeed');if(feed)feed.textContent=s;
   const state=$('mainRadarState');if(state)state.textContent=s}
 });
 const markers=L.layerGroup().addTo(map);
 const areaSummary=$('mainAreaSummary');
 const update=(id,text)=>{const e=$(id);if(e)e.textContent=text};
 function persistCfg(){save('radar-config',JSON.stringify({mode,site,product}))}
 function cancelTimer(){playing=false;if(timer){clearInterval(timer);timer=null}}
 function stopSite(){cancelTimer();requestToken++}
 function displayFrame(){
  if(!siteFrames.length||frameIndex<0)return;
  const time=siteFrames[frameIndex];
  siteRadar.showScan(time);
  const age=Math.max(0,Math.round((Date.now()-Date.parse(time))/60000));
  update('mainScanTime','Observed '+time+' UTC'+(age>20?' · STALE '+age+' min':''));
  $('mainPrev').disabled=siteFrames.length<2;
  $('mainNext').disabled=siteFrames.length<2;
 }
 function stepFrame(direction){
  if(siteFrames.length<2)return;
  frameIndex=(frameIndex+direction+siteFrames.length)%siteFrames.length;displayFrame();
 }
 function startSiteLoop(){
  cancelTimer();
  if(siteFrames.length<2)return;
  playing=true;
  timer=setInterval(()=>{if(mode==='site'&&radarOn)stepFrame(1)},1300);
  buttons();
 }
 async function loadScans(){
  if(mode!=='site')return;
  const token=++requestToken,radar=site,p=product;
  update('mainScanTime','Checking observed '+radar+' '+p+' scan times…');
  try{
   if(!bridge?.requestRadarScans)throw Error('Radar scan bridge unavailable');
   const data=await bridge.requestRadarScans(radar,p);
   if(token!==requestToken||mode!=='site')return;
   if(!data?.ok)throw Error(data?.error||'No radar scan data');
   const stamps=Array.isArray(data.scans)?data.scans.filter(t=>typeof t==='string'&&/^\d{4}-\d\d-\d\dT\d\d:\d\dZ$/.test(t)):[];
   if(!stamps.length)throw Error('No recent '+p+' scans provided by radar archive');
   const current=siteFrames[frameIndex];
   siteFrames=stamps;
   frameIndex=current&&stamps.includes(current)?stamps.indexOf(current):stamps.length-1;
   displayFrame();
   startSiteLoop();
   update('mainRadarState',radar+' '+p+' · '+stamps.length+' actual scan times · most recent '+stamps.at(-1)+' UTC');
  }catch(error){
   if(token!==requestToken)return;
   cancelTimer();siteFrames=[];frameIndex=-1;
   siteRadar.showLatest();
   update('mainScanTime','Unavailable · '+String(error?.message||error).slice(0,110));
   update('mainRadarState','Latest image only; archive times unavailable for '+radar+' '+p);
  }finally{if(token===requestToken)buttons()}
 }
 function buttons(){
  $('mainSource').value=mode;
  $('mainSite').value=site;$('mainProduct').value=product;
  const local=mode==='site';
  for(const id of ['mainSite','mainProduct','mainFocusSite','mainLatest'])$(id).disabled=!local;
  $('mainPrev').disabled=!local||siteFrames.length<2;
  $('mainNext').disabled=!local||siteFrames.length<2;
  $('mainLoop').textContent=local?(playing?'⏸ Pause Site Loop':'▶ Play Site Loop'):
   (composite.isPlaying?.()?'⏸ Pause Loop':'▶ Play Loop');
  $('mainLoop').disabled=local&&siteFrames.length<2;
  $('radarLoop').disabled=local&&siteFrames.length<2;
  $('radarLoop').textContent=$('mainLoop').textContent;
  $('radarToggle').textContent='Radar: '+(radarOn?'ON':'OFF');
  $('radarToggle').setAttribute('aria-pressed',String(radarOn));
  $('mainWarningToggle').textContent='NWS Polygons: '+(warningOn?'ON':'OFF');
  $('mainWarningToggle').setAttribute('aria-pressed',String(warningOn));
  $('mainDraw').textContent=drawing?'Cancel Drawing':'✎ Draw Polygon';
  $('mainFinish').disabled=!drawing||points.length<3;
  $('mainUndo').disabled=!drawing||!points.length;
  $('mainFindMore').disabled=drawing||lookupWorking||!D().validPolygon(points);
  $('mainVelocityPick').textContent=picking?'Cancel Gate Selection':'⌖ Select Two Velocity Gates';
  $('mainVelocityCount').textContent=gates.length+' of 2 velocity gates';
 }
 function activate(next,radarID=site,productID=product){
  if(!R().siteFor(radarID)||!R().productFor(productID))return;
  const keepOn=radarOn;
  active.show(false);stopSite();siteFrames=[];frameIndex=-1;
  mode=next==='site'?'site':'mosaic';site=radarID;product=productID;
  active=mode==='site'?siteRadar:composite;
  if(mode==='site')siteRadar.select(site,product);
  if(keepOn)active.show(true);
  if(typeof setActive==='function')setActive(active);
  persistCfg();buttons();
  if(mode==='site')loadScans();
  else update('mainScanTime','National mosaic · approximate frame age, not verified observation time');
 }
 function focusSite(){
  const p=R().siteFor(site);if(!p)return;
  onInteraction?.();map.flyTo([p.lat,p.lng],Math.max(7,map.getZoom()),{animate:true});
 }
 function onRadarToggle(){
  radarOn=!radarOn;
  active.show(radarOn);
  if(!radarOn)cancelTimer();
  else if(mode==='site'&&siteFrames.length>=2)startSiteLoop();
  save('radar',String(radarOn));buttons();
 }
 function onLoop(){
  if(!radarOn){radarOn=true;active.show(true);save('radar','true')}
  if(mode==='site'){
   if(playing)cancelTimer();else startSiteLoop();
  }else composite.play?.(!composite.isPlaying?.());
  buttons();
 }
 function onRefresh(){
  if(!radarOn)return;
  if(mode==='site')loadScans();else active.refresh?.();
 }
 function clearPolygonShapes(){
  for(const item of [area,pointsLayer,communityLayer])if(item)map.removeLayer(item);
  area=pointsLayer=communityLayer=null;
 }
 function paintPolygon(){
  clearPolygonShapes();
  if(!points.length)return;
  if(!drawing&&D().validPolygon(points)){
   area=L.polygon(points,{color:'#ffd651',weight:4,dashArray:'8 4',fillOpacity:.13})
    .addTo(map).bindTooltip('USER-DRAWN AREA · NOT AN NWS ISSUED WARNING');
  }else{
   pointsLayer=L.layerGroup().addTo(map);
   L.polyline(points,{color:'#ffd651',weight:3,dashArray:'6 4'}).addTo(pointsLayer);
   for(const p of points)L.circleMarker(p,{radius:4,color:'#ffdc57',fillOpacity:.8}).addTo(pointsLayer);
  }
 }
 function renderTowns(data){
  const rows=$('mainPlaces');rows.replaceChildren();
  if(communityLayer)map.removeLayer(communityLayer);
  communityLayer=L.layerGroup().addTo(map);
  const names=Array.isArray(data.places)?data.places:[];
  for(const p of names){
   const li=document.createElement('li');li.textContent=p.name+(p.state?' · '+p.state:'');
   rows.append(li);
   if(Number.isFinite(p.lat)&&Number.isFinite(p.lng)){
    L.circleMarker([p.lat,p.lng],{radius:4,color:'#a8fbff',fillColor:'#1bc0ee',fillOpacity:1})
     .addTo(communityLayer).bindTooltip(p.name);
   }
  }
  areaSummary.textContent=names.length+' mapped town center'+(names.length===1?'':'s')+' inside your polygon';
  update('mainDrawStatus',data.warning?'Offline towns shown · optional online service unavailable':
   'Community lookup complete · town centers, not full municipal boundaries');
 }
 async function findTowns(expand=false){
  if(!D().validPolygon(points))return;
  lookupWorking=true;const token=++lookupToken;buttons();
  if(!expand){$('mainPlaces').replaceChildren();areaSummary.textContent='Looking up communities within your polygon…'}
  try{
   if(!bridge?.lookupCommunities)throw Error('Native Census lookup not connected');
   const result=await bridge.lookupCommunities(points.map(x=>({lat:x.lat,lng:x.lng})),expand);
   if(token!==lookupToken)return;
   if(!result?.ok)throw Error(result?.error||'No results');
   renderTowns(result);
  }catch(error){if(token===lookupToken)update('mainDrawStatus','Community lookup unavailable: '+String(error.message||error).slice(0,100))}
  finally{if(token===lookupToken){lookupWorking=false;buttons()}}
 }
 function startPolygon(){
  if(drawing){
   drawing=false;
   try{const saved=JSON.parse(safeGet('polygon')||'null');points=D().validPolygon(saved)?saved:[]}catch(_){points=[]}
   paintPolygon();update('mainDrawStatus','Drawing canceled; previous saved polygon restored');
  }else{
   picking=false;drawing=true;points=[];clearPolygonShapes();
   onInteraction?.();
   update('mainDrawStatus','Click map to add 3–40 corners; finish to find real mapped communities');
  }
  buttons();
 }
 function finishPolygon(){
  if(!drawing||!D().validPolygon(points))return;
  try{D().safeQuery(points)}catch(error){update('mainDrawStatus',String(error.message));return}
  drawing=false;save('polygon',JSON.stringify(points));paintPolygon();
  const bounds=L.latLngBounds(points);
  if(bounds.isValid())map.fitBounds(bounds.pad(.25),{maxZoom:11});
  findTowns(false);buttons();
 }
 function resetPolygon(){
  drawing=false;points=[];lookupToken++;lookupWorking=false;clearPolygonShapes();
  forget('polygon');areaSummary.textContent='No drawn storm area';
  $('mainPlaces').replaceChildren();update('mainDrawStatus','Personal storm area cleared');buttons();
 }
 function clearGateLayer(){if(gateLayer)map.removeLayer(gateLayer);gateLayer=null}
 function paintGates(){
  clearGateLayer();if(!gates.length)return;
  gateLayer=L.layerGroup().addTo(map);
  gates.forEach((p,i)=>L.circleMarker(p,{radius:7,color:i?'#ff7690':'#75e8ff',weight:3,fillOpacity:.83})
   .addTo(gateLayer).bindTooltip(i?'Velocity gate B':'Velocity gate A'));
 }
 function calculate(){
  if(gates.length!==2){update('mainVelocityResult','Select two separate velocity gates on the map');return}
  try{
   const r=V().analyze(gates[0],gates[1],$('mainVelA').value,$('mainVelB').value);
   update('mainVelocityResult','MANUAL estimate · ΔV '+r.velocityDifferenceMph+' mph · half ΔV '+
    r.inferredHalfDifferenceMph+' mph · spacing '+r.distanceKm+' km · shear '+r.radialShearPerSecond+
    ' s⁻¹. NOT automatic rotation detection or a tornado diagnosis.');
   save('gates',JSON.stringify({points:gates,a:$('mainVelA').value,b:$('mainVelB').value}));
  }catch(error){update('mainVelocityResult',String(error.message||error))}
 }
 function consumeClick(event){
  if(drawing){
   const p={lat:event.latlng.lat,lng:event.latlng.lng};
   if(points.length<40&&D().validPoint(p)){points.push(p);paintPolygon();buttons();
    update('mainDrawStatus',points.length+' corners placed; press Finish Polygon at three or more')}
   return true;
  }
  if(picking){
   const p={lat:event.latlng.lat,lng:event.latlng.lng};
   if(D().validPoint(p)&&gates.length<2)gates.push(p);
   if(gates.length>=2){picking=false;update('mainVelocityResult','Enter signed observed radial velocities and calculate (manual measurement)')}
   paintGates();buttons();return true;
  }
  return false;
 }
 function restore(){
  try{const a=JSON.parse(safeGet('polygon')||'null');if(D().validPolygon(a)){points=a;paintPolygon();findTowns(false)}}catch(_){}
  try{
   const a=JSON.parse(safeGet('gates')||'null');
   if(Array.isArray(a?.points)&&a.points.length===2&&a.points.every(D().validPoint)){
    gates=a.points;$('mainVelA').value=String(a.a||'');$('mainVelB').value=String(a.b||'');paintGates();
   }
  }catch(_){}
 }
 for(const radar of R().SITES){
  const opt=document.createElement('option');opt.value=radar.id;opt.textContent=radar.id+' · '+radar.name;
  $('mainSite').append(opt);
  const marker=L.circleMarker([radar.lat,radar.lng],{
   radius:7,color:'#fff',weight:2,fillColor:'#096faf',fillOpacity:.93,bubblingMouseEvents:false
  }).addTo(markers);
  marker.bindTooltip(radar.id+' · '+radar.name+' · CLICK for velocity and reflectivity');
  marker.on('click',()=>{
   if(drawing||picking)return;
   activate('site',radar.id,product);
   onInteraction?.();map.flyTo([radar.lat,radar.lng],Math.max(7,map.getZoom()),{animate:true});
  });
 }
 for(const product of R().PRODUCTS){
  const opt=document.createElement('option');opt.value=product.id;opt.textContent=product.name;
  $('mainProduct').append(opt);
 }
 $('mainSource').addEventListener('change',()=>activate($('mainSource').value));
 $('mainSite').addEventListener('change',()=>{activate('site',$('mainSite').value,product);focusSite()});
 $('mainProduct').addEventListener('change',()=>activate('site',site,$('mainProduct').value));
 $('mainFocusSite').addEventListener('click',focusSite);
 $('mainPrev').addEventListener('click',()=>{cancelTimer();stepFrame(-1);buttons()});
 $('mainNext').addEventListener('click',()=>{cancelTimer();stepFrame(1);buttons()});
 $('mainLatest').addEventListener('click',()=>{cancelTimer();loadScans()});
 $('mainLoop').addEventListener('click',onLoop);
 $('mainWarningToggle').addEventListener('click',()=>{
  warningOn=!warningOn;save('warnings',String(warningOn));onWarningToggle?.();buttons();
 });
 $('mainAnalysisToggle').addEventListener('click',()=>{
  const panel=$('mainAnalysisDrawer');panel.hidden=!panel.hidden;
  $('mainAnalysisToggle').setAttribute('aria-expanded',String(!panel.hidden));
  map.invalidateSize();
 });
 $('mainDraw').addEventListener('click',startPolygon);
 $('mainUndo').addEventListener('click',()=>{if(drawing&&points.length){points.pop();paintPolygon();buttons()}});
 $('mainFinish').addEventListener('click',finishPolygon);
 $('mainClear').addEventListener('click',resetPolygon);
 $('mainFindMore').addEventListener('click',()=>findTowns(true));
 $('mainVelocityPick').addEventListener('click',()=>{
  if(picking){picking=false;buttons();return}
  drawing=false;try{const old=JSON.parse(safeGet('polygon')||'null');points=D().validPolygon(old)?old:[]}catch(_){points=[]}
  paintPolygon();picking=true;gates=[];clearGateLayer();onInteraction?.();buttons();
  update('mainVelocityResult','Click two points on the velocity map. Enter measured signed velocities; map imagery is NOT decoded.');
 });
 $('mainVelocityCalculate').addEventListener('click',calculate);
 $('mainVelocityClear').addEventListener('click',()=>{picking=false;gates=[];clearGateLayer();
  $('mainVelA').value='';$('mainVelB').value='';forget('gates');buttons();
  update('mainVelocityResult','Manual velocity measurement cleared on main screen only')});
 // Main desk always has its own state, never shares Storm Desk 1–4 settings.
 restore();
 siteRadar.select(site,product);
 if(mode==='site'){composite.show(false);active=siteRadar;if(radarOn)active.show(true);loadScans()}
 else{active=composite;if(!radarOn)active.show(false)}
 setActive?.(active);buttons();
 if(mode==='mosaic')update('mainScanTime','National mosaic · approximate frame age');
 const refreshTimer=setInterval(()=>{if(mode==='site'&&radarOn)loadScans()},120000);
 window.addEventListener('beforeunload',()=>{clearInterval(refreshTimer);stopSite();siteRadar.dispose?.()});
 function analyzeWarning(warning){
  const center=warning?.center;
  if(!center||!Number.isFinite(center.lat)||!Number.isFinite(center.lon))return;
  const sites=R().SITES.map(entry=>({entry,
   meters:V().distanceMeters({lat:center.lat,lng:center.lon},{lat:entry.lat,lng:entry.lng})
  })).sort((a,b)=>a.meters-b.meters);
  if(sites.length&&sites[0].meters<=300000){
   activate('site',sites[0].entry.id,product);
   update('mainRadarState','Nearby site selected for issued warning: '+sites[0].entry.id+
    ' · not an inferred tornado location or motion');
  }else update('mainRadarState','No listed single-site Doppler radar within 300 km of this warning; use national mosaic.');
 }
 return {consumeClick,onRadarToggle,onLoop,onRefresh,isWarningsShown:()=>warningOn,
  selectSite:(id)=>{activate('site',id,product);focusSite()},analyzeWarning,
  getMode:()=>mode,getSite:()=>site,getProduct:()=>product};
}
return {init,chooseConfig};
});