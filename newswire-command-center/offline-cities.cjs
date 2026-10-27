'use strict';
/* Offline city-centre locations from Census Gazetteer (compiled source pinned at build).
   IMPORTANT: a city center is not the city boundary; if not inside, report separately as nearby. */
const fs=require('node:fs'),path=require('node:path');
const D=require('./drawn-tracks.js');
const FILE=path.join(__dirname,'data','census-places-2025.json');
let cached=null;
function load(){
 if(cached)return cached;
 if(!fs.existsSync(FILE))throw Error('Offline Census cities data is not bundled. This build is incomplete.');
 const data=JSON.parse(fs.readFileSync(FILE,'utf8'));
 if(!Array.isArray(data)||data.length<17500)throw Error('Offline Census cities file incomplete');
 return cached=data;
}
function distanceKm(a,b){
 const r=Math.PI/180, dlat=(a.lat-b.lat)*r,dlon=(a.lng-b.lng)*r;
 const x=Math.sin(dlat/2)**2+Math.cos(a.lat*r)*Math.cos(b.lat*r)*Math.sin(dlon/2)**2;
 return 12742*Math.asin(Math.min(1,Math.sqrt(x)));
}
function query(points,{cities}={}){
 // Still restrict polygon complexity just as external lookup did.
 D.safeQuery(points);
 const source=cities||load();
 const bounds=D.bbox(points);
 const center={lat:(bounds.south+bounds.north)/2,lng:(bounds.east+bounds.west)/2};
 const places=[],outside=[];
 const keys=new Set();
 for(const city of source){
  const pos={lat:Number(city.lat),lng:Number(city.lng??city.lon)};
  const name=String(city.name||'').trim().slice(0,110);
  if(!name||!D.validPoint(pos))continue;
  if(Math.abs(pos.lat-center.lat)>1.5||Math.abs(pos.lng-center.lng)>1.5)continue;
  const key=(city.state||'')+'/'+name.toLowerCase();
  if(keys.has(key))continue;
  const candidate={name,state:String(city.state||'').slice(0,2),type:'census_place',lat:pos.lat,lng:pos.lng,
   source:'2025 U.S. Census-based places',distanceKm:Math.round(distanceKm(pos,center)*10)/10};
  if(D.within(pos,points)){keys.add(key);places.push(candidate)}
  else if(candidate.distanceKm<=32)outside.push(candidate);
 }
 const sort=(a,b)=>a.name.localeCompare(b.name)||a.state.localeCompare(b.state);
 places.sort(sort);outside.sort((a,b)=>a.distanceKm-b.distanceKm);
 return {places:places.slice(0,300),nearby:outside.slice(0,12),offline:true,complete:false,
  source:'2025 Census Gazetteer-based city centers bundled offline',
  note:'These are representative city points, not municipal boundaries. Smaller rural neighborhoods may be absent.'};
}
module.exports={FILE,load,query,distanceKm};
