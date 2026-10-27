/* Single-site NEXRAD Level III imagery via IEM RIDGE WMS.
   Data from NOAA radar sites and IEM processed images, not a decoded Level II dataset.
   Products are as-published; no velocity interpolation or inferred storm rotation.
   WMS RIDGE 'single' is most-recent only, NOT a historic playback source.
*/
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;root.NewsWireSiteRadar=api})(typeof window!=='undefined'?window:globalThis,function(){
'use strict';
const WMS='https://mesonet.agron.iastate.edu/cgi-bin/wms/nexrad/ridge.cgi';
const SITES=Object.freeze([
 Object.freeze({id:'KHTX',name:'Hytop / Northeast Alabama',state:'AL',lat:34.930556,lng:-86.083611}),
 Object.freeze({id:'KOHX',name:'Nashville / Old Hickory',state:'TN',lat:36.247222,lng:-86.562500}),
 Object.freeze({id:'KFFC',name:'Peachtree City / Atlanta',state:'GA',lat:33.363550,lng:-84.565950}),
 Object.freeze({id:'KMRX',name:'Knoxville / Morristown',state:'TN',lat:36.168330,lng:-83.401940})
]);
const PRODUCTS=Object.freeze([
 Object.freeze({id:'N0B',name:'Base Reflectivity (dBZ)',kind:'reflectivity',units:'dBZ'}),
 Object.freeze({id:'N0U',name:'Base Radial Velocity',kind:'velocity',units:'radial velocity'}),
 Object.freeze({id:'N0S',name:'Storm-Relative Velocity',kind:'storm-relative',units:'radial velocity'})
]);
function siteFor(id){return SITES.find(x=>x.id===id)||null}
function productFor(id){return PRODUCTS.find(x=>x.id===id)||null}
function ridgeOptions(site,product){if(!siteFor(site)||!productFor(product))throw Error('Unsupported radar or Level III product');
 return {sector:site.slice(1),prod:product,layers:'single',format:'image/png',
 transparent:true,version:'1.1.1',opacity:.84,zIndex:400,
 attribution:'NOAA NEXRAD Level III image via Iowa Environmental Mesonet RIDGE',updateWhenIdle:true};
}
function createSiteRadar(L,map,onState){
 if(!L?.tileLayer?.wms)throw Error('Leaflet WMS required');
 const notify=typeof onState==='function'?onState:()=>{};
 let site='KHTX',product='N0B',layer=null,shown=false,lastLoaded=null,errorTiles=0;
 function status(msg){notify(msg)}
 function remove(){
  if(layer&&map.hasLayer(layer))map.removeLayer(layer);
  layer=null;
 }
 function select(siteID,productID){
  if(!siteFor(siteID))throw Error('Unknown NEXRAD radar site');
  if(!productFor(productID))throw Error('Unsupported radar product');
  site=siteID;product=productID;lastLoaded=null;errorTiles=0;
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
  if(shown&&layer){
   layer.setParams({_nw_refresh:Math.floor(Date.now()/300000)},false);
   status('Refreshing '+site+' '+product+' from IEM RIDGE (timestamp unverified)…');
  }
 }
 function dispose(){show(false);remove()}
 return {select,show,refresh,dispose,site:()=>site,product:()=>product,isShown:()=>shown,lastLoaded:()=>lastLoaded};
}
return {WMS,SITES,PRODUCTS,siteFor,productFor,ridgeOptions,createSiteRadar};
});