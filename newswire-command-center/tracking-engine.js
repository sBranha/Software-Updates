/* NWS-warning-based tracking helpers. WARNING: warning polygon is not the tornado location. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;root.NewsWireTracking=api})(typeof window!=='undefined'?window:globalThis,function(){
'use strict';
function parseMotion(description){
 const s=String(description||'');
 const m=s.match(/\bMOVING\s+(NORTH(?:EAST|WEST)?|SOUTH(?:EAST|WEST)?|EAST|WEST|NNE|ENE|ESE|SSE|SSW|WSW|WNW|NNW|NE|NW|SE|SW|N|S|E|W)\s+AT\s+(\d{1,3})\s+MPH\b/i);
 if(!m)return null;
 const mph=Number(m[2]);if(!Number.isInteger(mph)||mph>200)return null;
 return {direction:m[1].toUpperCase(),mph,source:'NWS bulletin text'};
}
function validGeometry(geometry){
 if(!geometry||!['Polygon','MultiPolygon'].includes(geometry.type)||!Array.isArray(geometry.coordinates))return false;
 try{return JSON.stringify(geometry).length<75000}catch(_){return false}
}
function safeWarning(a){
 if(!a||typeof a!=='object'||typeof a.id!=='string'||!a.id||a.id.length>350)return null;
 const out={
  id:a.id,area:String(a.area||'Warning area not stated').slice(0,320),
  expires:String(a.expires||'').slice(0,100),sent:String(a.sent||'').slice(0,100),
  headline:String(a.headline||'Tornado Warning').slice(0,450),
  instruction:String(a.instruction||'').slice(0,1200),
  description:String(a.description||'').slice(0,4000),
  url:String(a.url||'').slice(0,350),geometry:validGeometry(a.geometry)?a.geometry:null
 };
 out.motion=parseMotion(out.description);
 return out;
}
function hasExpired(item,now=Date.now()){
 const millis=Date.parse(item?.expires||'');
 return Number.isFinite(millis)&&millis<=now;
}
return {parseMotion,validGeometry,safeWarning,hasExpired};
});
