'use strict';
// FAST OFFLINE lookup, with optional expanded OpenStreetMap lookup.
// Never make successful offline results conditional on online server health.
const D=require('./drawn-tracks.js');
const OFF=require('./offline-cities.cjs');
const PROVIDERS=Object.freeze([
 'https://overpass.private.coffee/api/interpreter',
 'https://overpass-api.de/api/interpreter',
 'https://overpass.nchc.org.tw/api/interpreter'
]);
async function lookupCommunities(points,{fetchImpl,timeoutMs=9000,providers=PROVIDERS,expand=false,cities}={}){
 const query=D.safeQuery(points);
 const offline=OFF.query(points,{cities});
 if(!expand)return offline;
 const errors=[],fetcher=fetchImpl||fetch;
 for(const endpoint of providers){
  if(!PROVIDERS.includes(endpoint))throw Error('Unapproved community lookup endpoint');
  const ctl=new AbortController(),timeout=setTimeout(()=>ctl.abort(),timeoutMs);
  try{
   const response=await fetcher(endpoint+'?data='+encodeURIComponent(query),{
    method:'GET',headers:{Accept:'application/json'},signal:ctl.signal,redirect:'error'
   });
   if(!response.ok)throw Error('HTTP '+response.status);
   const bytes=await response.text();
   if(bytes.length>2_500_000)throw Error('Community response too large');
   let result;
   try{result=JSON.parse(bytes)}catch(_){throw Error('Invalid community response')}
   if(!result||!Array.isArray(result.elements))throw Error('Missing community data');
   const extra=D.placeList(result,points);
   const all=[...offline.places];
   const seen=new Set(all.map(p=>p.name.toLowerCase()));
   for(const p of extra){
    if(seen.has(p.name.toLowerCase()))continue;seen.add(p.name.toLowerCase());
    all.push({...p,source:'OpenStreetMap'});
   }
   all.sort((a,b)=>a.name.localeCompare(b.name));
   return {...offline,places:all.slice(0,300),online:true,complete:false,
    source:'Offline U.S. Census centers + expanded OpenStreetMap communities',
    note:offline.note+' Online OpenStreetMap entries are additional representative points.',
    elementsReturned:result.elements.length};
  }catch(err){
   errors.push(new URL(endpoint).hostname+': '+String(err?.message||'Network error').slice(0,100));
  }finally{clearTimeout(timeout)}
 }
 return {...offline,online:false,
  warning:'Optional online expansion unavailable. Offline Census places are still shown. '+errors.join(' | ')};
}
module.exports={lookupCommunities,PROVIDERS};
