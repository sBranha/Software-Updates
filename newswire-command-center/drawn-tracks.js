/* NewsWire user-drawn advisory areas + OpenStreetMap place queries. No forecast or official alert implied. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;root.NewsWireDrawnTracks=api;})(typeof window!=='undefined'?window:globalThis,function(){
'use strict';
const TYPES=new Set(['city','town','village','hamlet','suburb','neighbourhood']);
const HOSTS=['https://overpass.private.coffee/api/interpreter','https://overpass-api.de/api/interpreter'];
function validPoint(p){return p&&Number.isFinite(p.lat)&&Number.isFinite(p.lng)&&Math.abs(p.lat)<=90&&Math.abs(p.lng)<=180}
function validPolygon(points){
 return Array.isArray(points)&&points.length>=3&&points.length<=40&&points.every(validPoint)
  && Math.abs(signedArea(points))>1e-7;
}
function signedArea(points){
 if(!Array.isArray(points)||points.length<3)return 0;
 let s=0;for(let i=0;i<points.length;i++){const a=points[i],b=points[(i+1)%points.length];s+=a.lng*b.lat-b.lng*a.lat}return s/2;
}
function bbox(points){
 if(!validPolygon(points))return null;
 return {south:Math.min(...points.map(p=>p.lat)),west:Math.min(...points.map(p=>p.lng)),
 north:Math.max(...points.map(p=>p.lat)),east:Math.max(...points.map(p=>p.lng))};
}
function within(point,points){
 if(!validPoint(point)||!validPolygon(points))return false;
 // Ray cast in lon/lat; suitable for local (<1.5 degree) manually selected watch areas.
 let inside=false;const x=point.lng,y=point.lat;
 for(let i=0,j=points.length-1;i<points.length;j=i++){
  const a=points[i],b=points[j];
  // Boundary counts as included. Tolerance is coordinate degrees, not meters.
  const cross=(x-a.lng)*(b.lat-a.lat)-(y-a.lat)*(b.lng-a.lng);
  if(Math.abs(cross)<1e-9&&x>=Math.min(a.lng,b.lng)-1e-9&&x<=Math.max(a.lng,b.lng)+1e-9
   &&y>=Math.min(a.lat,b.lat)-1e-9&&y<=Math.max(a.lat,b.lat)+1e-9)return true;
  if((a.lat>y)!==(b.lat>y)){
   const boundary=a.lng+(b.lng-a.lng)*(y-a.lat)/(b.lat-a.lat);
   if(x<boundary)inside=!inside;
  }
 }
 return inside;
}
function safeQuery(points){
 const b=bbox(points);if(!b)throw Error('Draw at least three non-collinear corners.');
 if(b.north-b.south>1.5||b.east-b.west>1.5)throw Error('Draw a smaller storm area (up to 1.5° in each direction).');
 return '[out:json][timeout:20];(nwr["place"~"^(city|town|village|hamlet|suburb|neighbourhood)$"]('+
 [b.south,b.west,b.north,b.east].map(n=>n.toFixed(5)).join(',')+'););out center 700;';
}
function placeList(osm,points){
 const seen=new Set(),list=[];
 if(!osm||!Array.isArray(osm.elements))throw Error('Community service returned an invalid response');
 for(const e of osm.elements){
  const name=e.tags&&String(e.tags.name||'').trim();
  const kind=e.tags&&e.tags.place;
  const lat=Number(e.lat??e.center?.lat),lng=Number(e.lon??e.center?.lon);
  if(!name||!TYPES.has(kind)||!validPoint({lat,lng})||!within({lat,lng},points))continue;
  const key=name.toLowerCase()+'|'+lat.toFixed(3)+'|'+lng.toFixed(3);
  if(seen.has(key))continue;seen.add(key);
  list.push({name:name.slice(0,110),type:kind,lat,lng});
 }
 return list.sort((a,b)=>a.name.localeCompare(b.name)).slice(0,200);
}
async function fetchPlaces(points,{fetchImpl,timeoutMs=23000}={}){
 const query=safeQuery(points),fetcher=fetchImpl||fetch;
 let lastError;
 for(const server of HOSTS){
  const ctl=new AbortController();const timer=setTimeout(()=>ctl.abort(),timeoutMs);
  try{
   // Simple CORS GET avoids the file-origin preflight sometimes blocked in Electron.
   const response=await fetcher(server+'?data='+encodeURIComponent(query),{signal:ctl.signal,cache:'no-store'});
   if(!response.ok)throw Error('Overpass HTTP '+response.status);
   return placeList(await response.json(),points);
  }catch(e){lastError=e}finally{clearTimeout(timer)}
 }
 throw Error('OpenStreetMap community lookup unavailable: '+(lastError?.message||'unknown error'));
}
return {validPoint,validPolygon,bbox,within,safeQuery,placeList,fetchPlaces,HOSTS};
});
