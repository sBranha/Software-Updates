/* NWS warning-boundary proximity, not storm velocity or future path. */
(function(root){
'use strict';
const events=new Set(['Tornado Warning','Severe Thunderstorm Warning','Flash Flood Warning','Flood Warning','Extreme Wind Warning','Snow Squall Warning','Winter Storm Warning','Tornado Watch','Severe Thunderstorm Watch']);
function stormEvent(name){return events.has(name);}
function valid(p){return Array.isArray(p)&&p.length>=2&&Number.isFinite(p[0])&&Number.isFinite(p[1])&&Math.abs(p[0])<=180&&Math.abs(p[1])<=90;}
function withinRing(lat,lon,ring){
 let inside=false;
 for(let i=0,j=ring.length-1;i<ring.length;j=i++){
  const a=ring[i],b=ring[j];
  if(!valid(a)||!valid(b))continue;
  if((a[1]>lat)!==(b[1]>lat)&&lon<(b[0]-a[0])*(lat-a[1])/(b[1]-a[1])+a[0])inside=!inside;
 }
 return inside;
}
function segmentMiles(lat,lon,a,b){
 const scale=69.172*Math.max(.01,Math.cos(lat*Math.PI/180));
 const x=(a[0]-lon)*scale,y=(a[1]-lat)*69.0;
 const dx=(b[0]-a[0])*scale,dy=(b[1]-a[1])*69.0;
 const squared=dx*dx+dy*dy;
 const t=squared?Math.max(0,Math.min(1,-(x*dx+y*dy)/squared)):0;
 return Math.hypot(x+t*dx,y+t*dy);
}
function polygonMiles(lat,lon,rings){
 if(!Array.isArray(rings)||!Array.isArray(rings[0])||rings[0].length<3)return Infinity;
 let nearest=Infinity;
 for(const ring of rings){
  if(!Array.isArray(ring)||ring.length<3)continue;
  for(let i=0;i<ring.length;i++){
   const a=ring[i],b=ring[(i+1)%ring.length];
   if(valid(a)&&valid(b))nearest=Math.min(nearest,segmentMiles(lat,lon,a,b));
  }
 }
 if(withinRing(lat,lon,rings[0])&&!rings.slice(1).some(r=>Array.isArray(r)&&withinRing(lat,lon,r)))return 0;
 return nearest;
}
function featureMiles(feature,lat,lon){
 const g=feature&&feature.geometry;
 if(!g)return Infinity;
 const polygons=g.type==='Polygon'?[g.coordinates]:g.type==='MultiPolygon'?g.coordinates:[];
 if(!Array.isArray(polygons))return Infinity;
 let nearest=Infinity;
 for(const rings of polygons)nearest=Math.min(nearest,polygonMiles(lat,lon,rings));
 return nearest;
}
function nearby(features,lat,lon,maxMiles=100,maxItems=6){
 if(!Array.isArray(features)||!Number.isFinite(lat)||!Number.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)return [];
 const byId=new Map();
 for(const f of features){
  const p=f&&f.properties||{};
  if(!stormEvent(p.event))continue;
  const miles=featureMiles(f,lat,lon);
  if(!Number.isFinite(miles)||miles>maxMiles)continue;
  const id=String(p.id||f.id||p.event+'|'+(p.areaDesc||''));
  const item={feature:f,event:p.event,area:p.areaDesc||'Area not supplied',expires:p.expires||'',miles,inside:miles<.05};
  if(!byId.has(id)||miles<byId.get(id).miles)byId.set(id,item);
 }
 return [...byId.values()].sort((a,b)=>a.miles-b.miles||a.event.localeCompare(b.event)).slice(0,maxItems);
}
const api={nearby,featureMiles,stormEvent};
if(typeof module==='object'&&module.exports)module.exports=api;
root.NewsWireStormTracker=api;
})(typeof window!=='undefined'?window:globalThis);
