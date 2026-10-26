/* NewsWire nationwide alerts: pure logic, shared by desktop and Android. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;root.NewsWireNationwide=api;})(typeof window!=='undefined'?window:globalThis,function(){
'use strict';
const TORNADO='Tornado Warning';
function validLocation(lat,lon){return Number.isFinite(lat)&&Number.isFinite(lon)&&Math.abs(lat)<=90&&Math.abs(lon)<=180}
function validPoint(p){return Array.isArray(p)&&p.length>=2&&validLocation(Number(p[1]),Number(p[0]))}
function insideRing(lat,lon,ring){let hit=false;if(!Array.isArray(ring))return false;
 for(let i=0,j=ring.length-1;i<ring.length;j=i++){const a=ring[i],b=ring[j];if(!validPoint(a)||!validPoint(b))continue;
 if((a[1]>lat)!==(b[1]>lat) && lon<(b[0]-a[0])*(lat-a[1])/(b[1]-a[1])+a[0])hit=!hit;}return hit;}
function insideGeometry(g,lat,lon){if(!g||!validLocation(lat,lon))return false;
 const polygons=g.type==='Polygon'?[g.coordinates]:g.type==='MultiPolygon'?g.coordinates:[];
 return polygons.some(poly=>Array.isArray(poly)&&poly.length>0&&insideRing(lat,lon,poly[0])&&!poly.slice(1).some(r=>insideRing(lat,lon,r)));}
function geometryCenter(g){if(!g)return null;const polygons=g.type==='Polygon'?[g.coordinates]:g.type==='MultiPolygon'?g.coordinates:[];
 let west=Infinity,east=-Infinity,south=Infinity,north=-Infinity,found=false;
 for(const poly of polygons){if(!Array.isArray(poly))continue;for(const ring of poly){if(!Array.isArray(ring))continue;
 for(const p of ring){if(!validPoint(p))continue;west=Math.min(west,p[0]);east=Math.max(east,p[0]);south=Math.min(south,p[1]);north=Math.max(north,p[1]);found=true;}}}
 return found?[(south+north)/2,(west+east)/2]:null;}
function asDate(s){const t=Date.parse(s||'');return Number.isFinite(t)?t:null}
function normalize(feature,now=Date.now()){const p=feature&&feature.properties;if(!p||p.event!==TORNADO)return null;
 if(p.status&&p.status.toLowerCase()!=='actual')return null;
 if(['cancel','test','exercise'].includes(String(p.messageType||'').toLowerCase()))return null;
 const expires=asDate(p.expires);if(expires!==null&&expires<=now)return null;
 const start=asDate(p.effective);if(start!==null&&start>now+2*60*60*1000)return null;
 const id=String(p.id||feature.id||'');if(!id)return null;
 return {id,event:TORNADO,area:String(p.areaDesc||'Location unavailable'),headline:String(p.headline||'Official tornado warning'),
  description:String(p.description||''),instruction:String(p.instruction||''),expires:String(p.expires||''),
  sent:String(p.sent||''),updated:String(p.sent||p.effective||''),geometry:feature.geometry||null,
  center:geometryCenter(feature.geometry),url:String(p['@id']||p.id||''),source:'National Weather Service'};
}
function alertsFromResponse(data,now=Date.now()){if(!data||!Array.isArray(data.features))throw Error('Invalid NWS feature collection');
 const byId=new Map();for(const f of data.features){const a=normalize(f,now);if(!a)continue;
 const old=byId.get(a.id);if(!old||(asDate(a.updated)||0)>(asDate(old.updated)||0))byId.set(a.id,a);}
 return [...byId.values()].sort((a,b)=>(asDate(b.updated)||0)-(asDate(a.updated)||0)||a.id.localeCompare(b.id));}
function newAlerts(alerts,seen){return alerts.filter(a=>!seen.has(a.id))}
function prioritize(alerts,lat,lon,pointIds=new Set()){return [...alerts].sort((a,b)=>{
 const al=pointIds.has(a.id)||(a.geometry&&insideGeometry(a.geometry,lat,lon));
 const bl=pointIds.has(b.id)||(b.geometry&&insideGeometry(b.geometry,lat,lon));
 return Number(!!bl)-Number(!!al)||(asDate(b.updated)||0)-(asDate(a.updated)||0);});}
function isLocal(a,lat,lon,pointIds=new Set()){return !!a&&(pointIds.has(a.id)||insideGeometry(a.geometry,lat,lon));}
function officialUrl(alert){return alert&&/^https:\/\/(api\.weather\.gov|www\.weather\.gov)\//.test(alert.url)?alert.url:'https://www.weather.gov/alerts';}
return {TORNADO,validLocation,insideGeometry,geometryCenter,normalize,alertsFromResponse,newAlerts,prioritize,isLocal,officialUrl};
});
