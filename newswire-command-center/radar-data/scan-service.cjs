'use strict';
/* IEM RIDGE Level III scan listings. These are observed scan times, never predictions.
   https://mesonet.agron.iastate.edu/GIS/ridge.phtml
*/
const S=require('../site-radar.js');
function safe(site,product){
 if(!S.siteFor(site)||!S.productFor(product))throw Error('Unsupported RADAR site/product');
}
function isoMinute(t){return new Date(t).toISOString().slice(0,16)+'Z'}
function urlFor(site,product,now=Date.now()){
 safe(site,product);
 if(!Number.isFinite(now))throw Error('Invalid clock');
 const url=new URL('https://mesonet.agron.iastate.edu/json/radar.py');
 const until=isoMinute(now),start=isoMinute(now-95*60000);
 url.searchParams.set('operation','list');
 url.searchParams.set('radar',site.slice(1));
 url.searchParams.set('product',product);
 url.searchParams.set('start',start);
 url.searchParams.set('end',until);
 return url.toString();
}
function parseTime(raw){
 if(raw&&typeof raw==='object'){
  for(const k of ['valid','time','datetime','timestamp','scan','volume_scan','date','dt','ts']){
   if(raw[k]!=null)return parseTime(raw[k]);
  }
  return NaN;
 }
 if(typeof raw==='number')return Number.isFinite(raw)?(raw<1e11?raw*1000:raw):NaN;
 if(typeof raw!=='string')return NaN;
 const value=raw.trim();
 if(/^\d{12}$/.test(value))
  return Date.parse(value.slice(0,4)+'-'+value.slice(4,6)+'-'+value.slice(6,8)+'T'+value.slice(8,10)+':'+value.slice(10)+'Z');
 if(/^\d{10}$/.test(value))return Number(value)*1000;
 const parsed=Date.parse(value);
 return Number.isFinite(parsed)?parsed:NaN;
}
function parseScans(payload,now=Date.now()){
 const arrays=[payload,payload?.scans,payload?.times,payload?.data,payload?.results,payload?.result,payload?.timestamps,payload?.timeseries,payload?.list];
 let a=arrays.find(x=>Array.isArray(x));
 if(!a&&payload&&typeof payload==='object'){
  const entries=Object.values(payload);
  a=entries.find(Array.isArray);
 }
 if(!Array.isArray(a))return [];
 const earliest=now-120*60000;
 const unique=new Set();
 for(const raw of a.slice(0,600)){
  const time=parseTime(raw);
  if(Number.isFinite(time)&&time<=now+120000&&time>=earliest)unique.add(isoMinute(time));
 }
 return [...unique].sort().slice(-12);
}
async function fetchScans(site,product,{fetchImpl=fetch,now=Date.now(),timeoutMs=12000}={}){
 const uri=urlFor(site,product,now);
 const ctl=new AbortController();
 const timer=setTimeout(()=>ctl.abort(),timeoutMs);
 try{
  const resp=await fetchImpl(uri,{method:'GET',headers:{Accept:'application/json'},signal:ctl.signal,redirect:'error'});
  if(!resp.ok)throw Error('Scan list HTTP '+resp.status);
  const txt=await resp.text();
  if(txt.length>1024*1024)throw Error('Oversized radar scan metadata');
  const payload=JSON.parse(txt);
  const scans=parseScans(payload,now);
  return {site,product,scans,source:'IEM RIDGE Level III',latest:scans.at(-1)||null,
   ageMinutes:scans.length?Math.round((now-Date.parse(scans.at(-1)))/60000):null};
 }finally{clearTimeout(timer)}
}
module.exports={urlFor,parseTime,parseScans,fetchScans};
