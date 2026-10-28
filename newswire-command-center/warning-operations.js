/* NewsWire warning operational timing and bulletin extraction.
 * Only official NWS bulletin text is displayed. No storm positions, tracks,
 * tornado confirmations, or ETA values are inferred from the warning polygon.
 */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;root.NewsWireWarningOps=api})(typeof window!=='undefined'?window:globalThis,function(){
'use strict';
function countdown(expires,now=Date.now()){
 const ts=Date.parse(expires||'');
 if(!Number.isFinite(ts))return 'Expiration not provided';
 const diff=ts-now;
 if(diff<=0)return 'EXPIRED — NOT ACTIVE';
 const minutes=Math.ceil(diff/60000);
 return minutes>=60?Math.floor(minutes/60)+'h '+String(minutes%60).padStart(2,'0')+'m remaining':minutes+' min remaining';
}
function portion(text,label){
 const s=String(text||'');
 const lines=s.split(/\r?\n/);
 const marker=new RegExp('^\\s*(?:\\*\\s*)?'+label+'\\s*(?:\\.{2,}|:)\\s*(.+)','i');
 const line=lines.find(x=>marker.test(x));
 return line?(line.match(marker)?.[1]||'').trim().slice(0,230):null;
}
function motion(text){
 const s=String(text||'');
 const m=s.match(/(?:MOVING|MOTION)\s+(?:TO\s+THE\s+)?(NORTH(?:EAST|WEST)?|SOUTH(?:EAST|WEST)?|EAST|WEST|NNE|NNW|ENE|ESE|SSE|SSW|WSW|WNW|N|S|E|W)\s+AT\s+(\d{1,3})\s*MPH/i);
 return m?{direction:m[1].toUpperCase(),mph:Number(m[2]),origin:'NWS bulletin statement'}:null;
}
function summarize(alert,now=Date.now()){
 if(!alert||typeof alert!=='object')return null;
 const bulletin=[alert.description||'',alert.headline||'',alert.instruction||''].join('\n');
 const hazard=portion(bulletin,'HAZARD');
 const source=portion(bulletin,'SOURCE');
 const impact=portion(bulletin,'IMPACT');
 const move=motion(bulletin);
 return {area:String(alert.area||'Not specified'),headline:String(alert.headline||'NWS warning'),
  issued:alert.sent||null,expires:alert.expires||null,countdown:countdown(alert.expires,now),
  hazard,source,impact,move,description:String(alert.description||''),
  instruction:String(alert.instruction||''),
  hasPolygon:Boolean(alert.geometry),id:alert.id||null};
}
function activeById(alerts,id,now=Date.now()){
 const a=Array.isArray(alerts)?alerts.find(v=>v&&v.id===id):null;
 return a&&Number.isFinite(Date.parse(a.expires||''))&&Date.parse(a.expires)>now?a:null;
}
return {countdown,portion,motion,summarize,activeById};
});