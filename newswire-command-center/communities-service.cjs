'use strict';
// Main-process weather-desk community lookup, eliminating Electron file:// CORS restrictions.
// OSM community labels are approximate and this is not an official NWS forecast.
const D=require('./drawn-tracks.js');
const PROVIDERS=Object.freeze([
 'https://overpass.private.coffee/api/interpreter',
 'https://overpass-api.de/api/interpreter',
 'https://overpass.nchc.org.tw/api/interpreter'
]);
async function lookupCommunities(points,{fetchImpl,timeoutMs=12500,providers=PROVIDERS}={}){
 const query=D.safeQuery(points); // strictly validates client-provided coordinates and query size
 const fetcher=fetchImpl||fetch;
 const errors=[];
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
   return {
    places:D.placeList(result,points),
    // An object count at the limit may truncate results; never claim full coverage.
    complete:result.elements.length<2500,
    elementsReturned:result.elements.length,
    source:'OpenStreetMap via Overpass',
    provider:new URL(endpoint).hostname
   };
  }catch(err){
   errors.push(new URL(endpoint).hostname+': '+String(err?.message||'Network error').slice(0,100));
  }finally{clearTimeout(timeout)}
 }
 throw Error('All OpenStreetMap community servers failed. '+errors.join(' | '));
}
module.exports={lookupCommunities,PROVIDERS};
