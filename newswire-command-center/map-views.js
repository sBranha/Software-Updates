/* Shared map modes for NewsWire studio: basemap independent of weather overlays.
   Satellite is ALWAYS the default when there is no explicit saved selection.
   Street View is a separate historical/ground-level panorama on Google Maps,
   NOT live weather, traffic or an in-app video feed. */
(function(root,factory){
 const api=factory();
 if(typeof module==='object'&&module.exports)module.exports=api;
 root.NewsWireMapViews=api;
})(typeof window!=='undefined'?window:globalThis,function(){
'use strict';
const STYLES=Object.freeze({
 satellite:Object.freeze({
  label:'Satellite',
  url:'https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryTopo/MapServer/tile/{z}/{y}/{x}',
  maxNativeZoom:16,maxZoom:18,
  attribution:'Map images: U.S. Geological Survey / The National Map'
 }),
 streets:Object.freeze({
  label:'Streets',
  // Raster street source supported as of this preview; Esri plans to retire
  // World Street Map raster in 2029, so a long-term vector replacement is needed.
  url:'https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}',
  maxNativeZoom:17,maxZoom:18,
  attribution:'Street basemap: Esri, HERE, Garmin, USGS and contributors'
 }),
 terrain:Object.freeze({
  label:'Terrain',
  url:'https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer/tile/{z}/{y}/{x}',
  maxNativeZoom:16,maxZoom:18,
  attribution:'Topographic maps: U.S. Geological Survey / The National Map'
 })
});
function normalize(style){return Object.prototype.hasOwnProperty.call(STYLES,style)?style:'satellite'}
function createLayer(L,style){
 if(!L?.tileLayer)throw Error('Leaflet tile layer API unavailable');
 const cfg=STYLES[normalize(style)];
 return L.tileLayer(cfg.url,{
  maxZoom:cfg.maxZoom,maxNativeZoom:cfg.maxNativeZoom,
  attribution:cfg.attribution,updateWhenIdle:true
 });
}
function validPoint(lat,lng){
 return typeof lat==='number'&&typeof lng==='number'&&
  Number.isFinite(lat)&&Number.isFinite(lng)&&
  Math.abs(lat)<=90&&Math.abs(lng)<=180;
}
function streetViewUrl(lat,lng){
 if(!validPoint(lat,lng))throw Error('Street View needs a valid map location');
 // Google's Maps URL action does not require a developer API key.
 return 'https://www.google.com/maps/@?api=1&map_action=pano&viewpoint='+
  lat.toFixed(6)+'%2C'+lng.toFixed(6);
}
function mapCoordinates(map){
 const p=map?.getCenter?.();
 if(!p||!validPoint(p.lat,p.lng))throw Error('Map coordinates unavailable');
 return {lat:p.lat,lng:p.lng};
}
return {STYLES,normalize,createLayer,validPoint,streetViewUrl,mapCoordinates};
});