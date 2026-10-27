'use strict';
// CI optional diagnostic: real network request, no fixture/fabrication.
const {urlFor,parseScans}=require('./scan-service.cjs');
(async()=>{
 const now=Date.now();
 for(const product of ['N0B','N0U','N0S']){
  const url=urlFor('KHTX',product,now),ctl=new AbortController();
  const id=setTimeout(()=>ctl.abort(),15000);
  try{
   const response=await fetch(url,{headers:{Accept:'application/json'},signal:ctl.signal});
   const raw=await response.text();
   let info;
   try{info=JSON.parse(raw)}catch(_){info=null}
   const scans=parseScans(info,now);
   const shape=info&&typeof info==='object'?(Array.isArray(info)?'array':Object.keys(info).slice(0,15).join(',')):'none';
   console.log('LIVE KHTX '+product+' HTTP '+response.status+' keys='+shape+' scanTimes='+scans.length+
     ' latest='+(scans.at(-1)||'none')+' bodyHead='+JSON.stringify(raw.slice(0,160)));
  }catch(err){console.log('LIVE KHTX '+product+' network failure: '+String(err?.message||err))}
  finally{clearTimeout(id)}
 }
})().catch(e=>{console.error(e);process.exitCode=1});
