/* Single-site NEXRAD Level III imagery via IEM RIDGE WMS.
   Data from NOAA radar sites and IEM processed images, not a decoded Level II dataset.
   Products are as-published; no velocity interpolation or inferred storm rotation.
   WMS RIDGE 'single' is most-recent only, NOT a historic playback source.
*/
(function(root,factory){const api=factory(root);if(typeof module==='object'&&module.exports)module.exports=api;root.NewsWireSiteRadar=api})(typeof window!=='undefined'?window:globalThis,function(root){
'use strict';
const WMS='https://mesonet.agron.iastate.edu/cgi-bin/wms/nexrad/ridge.cgi';
const RadarSites=root.NewsWireRadarSites||(typeof require==='function'?require('./radar-sites.js'):null);
if(!RadarSites)throw Error('Offline NEXRAD station directory is missing');
const SITES=RadarSites.SITES;
const PRODUCTS=Object.freeze([
 Object.freeze({id:'N0B',name:'Base Reflectivity (dBZ)',kind:'reflectivity',units:'dBZ'}),
 Object.freeze({id:'N0U',name:'Base Radial Velocity',kind:'velocity',units:'radial velocity'}),
 Object.freeze({id:'N0S',name:'Storm-Relative Velocity',kind:'storm-relative',units:'radial velocity'})
]);
function siteFor(id){return RadarSites.siteFor(id)}
function productFor(id){return PRODUCTS.find(x=>x.id===id)||null}
function ridgeOptions(site,product){if(!siteFor(site)||!productFor(product))throw Error('Unsupported radar or Level III product');
 return {sector:site.slice(1),prod:product,layers:'single',format:'image/png',
 transparent:true,version:'1.1.1',opacity:.84,zIndex:400,
 attribution:'NOAA NEXRAD Level III image via Iowa Environmental Mesonet RIDGE',updateWhenIdle:true};
}
function createSiteRadar(L,map,onState){
 if(!L?.tileLayer?.wms)throw Error('Leaflet WMS required');
 const notify=typeof onState==='function'?onState:()=>{};
 let site='KHTX',product='N0B',layer=null,shown=false,lastLoaded=null,errorTiles=0,scanTime=null;
 function status(msg){notify(msg)}
 function remove(){
  if(layer&&map.hasLayer(layer))map.removeLayer(layer);
  layer=null;
 }
 function select(siteID,productID){
  if(!siteFor(siteID))throw Error('Unknown NEXRAD radar site');
  if(!productFor(productID))throw Error('Unsupported radar product');
  site=siteID;product=productID;lastLoaded=null;errorTiles=0;scanTime=null;
  remove();
  const cfg=ridgeOptions(site,product);
  const layerNew=L.tileLayer.wms(WMS+'?sector='+cfg.sector+'&prod='+cfg.prod,{
   layers:cfg.layers,format:cfg.format,transparent:true,version:cfg.version,
   opacity:cfg.opacity,zIndex:cfg.zIndex,attribution:cfg.attribution,updateWhenIdle:true
  });
  layerNew.on('tileload',()=>{
   if(layer!==layerNew)return;
   lastLoaded=Date.now();errorTiles=0;
   status(site+' '+product+' radar image received (observation timestamp unverified)');
  });
  layerNew.on('tileerror',()=>{
   if(layer!==layerNew)return;
   errorTiles++;
   if(errorTiles>=3)status(site+' '+product+' RADAR SOURCE UNAVAILABLE — no all-clear implied');
  });
  layer=layerNew;
  if(shown){layer.addTo(map);layer.bringToFront()}
  status(site+' '+product+' single-site RIDGE image requested; scan time not verified');
  return {site,product};
 }
 // Display a specific REAL UTC archived Level III scan, not a duplicated latest frame.
 function showScan(iso){
  if(!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}Z$/.test(String(iso))||
     !Number.isFinite(Date.parse(iso)))throw Error('Invalid observation UTC scan time');
  if(!L.tileLayer)throw Error('Leaflet raster tile layers unavailable');
  const timestamp=iso.replace(/[-:TZ]/g,'');
  const layerID='ridge::'+site.slice(1)+'-'+product+'-'+timestamp;
  const url='https://mesonet.agron.iastate.edu/c/tile.py/1.0.0/'+layerID+'/{z}/{x}/{y}.png';
  remove();scanTime=iso;errorTiles=0;
  const newLayer=L.tileLayer(url,{maxZoom:18,maxNativeZoom:12,opacity:.86,
   attribution:'NOAA NEXRAD Level III archived radar via Iowa Environmental Mesonet'});
  newLayer.on('tileload',()=>{
   if(layer!==newLayer)return;lastLoaded=Date.now();errorTiles=0;
   status(site+' '+product+' observed '+scanTime+' (UTC, IEM archive)');
  });
  newLayer.on('tileerror',()=>{
   if(layer!==newLayer)return;
   if(++errorTiles>=3)status(site+' '+product+' archive image missing for '+scanTime);
  });
  layer=newLayer;
  if(shown){layer.addTo(map);layer.bringToFront()}
  status('Loading '+site+' '+product+' observed '+scanTime+' from archive');
  return scanTime;
 }
 function showLatest(){select(site,product);return scanTime}
 function show(on){
  shown=Boolean(on);
  if(shown){
   if(!layer)select(site,product);
   if(layer&&!map.hasLayer(layer))layer.addTo(map);
   layer?.bringToFront?.();
   status('Loading '+site+' '+product+' single-site radar (latest available, timestamp unverified)…');
  }else{if(layer&&map.hasLayer(layer))map.removeLayer(layer);status('Single-site radar OFF')}
  return shown;
 }
 function refresh(){
  if(shown&&layer&&!scanTime){
   layer.setParams({_nw_refresh:Math.floor(Date.now()/300000)},false);
   status('Refreshing '+site+' '+product+' from IEM RIDGE (timestamp unverified)…');
  }
 }
 function dispose(){show(false);remove()}
 return {select,show,showScan,showLatest,refresh,dispose,site:()=>site,product:()=>product,isShown:()=>shown,lastLoaded:()=>lastLoaded,scanTime:()=>scanTime};
}
return {WMS,SITES,PRODUCTS,siteFor,productFor,ridgeOptions,createSiteRadar};
});