/* NewsWire Drive: deterministic heading/position helpers (phone and test).
   Heading is GPS course-over-ground, not a magnetic compass when parked. */
(function(root,factory){
 const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;
 root.NewsWireDriveMath=api;
})(typeof window!=='undefined'?window:globalThis,function(){
 'use strict';
 function normalized(deg){return Number.isFinite(deg)?((deg%360)+360)%360:null}
 function smoothBearing(previous,next,alpha=.65){
  const n=normalized(next),p=normalized(previous);
  if(n===null)return p;if(p===null)return n;
  const delta=((n-p+540)%360)-180;
  return normalized(p+delta*Math.max(.1,Math.min(1,alpha)));
 }
 function validFix(lat,lng){return Number.isFinite(lat)&&Number.isFinite(lng)&&Math.abs(lat)<=85&&Math.abs(lng)<=180}
 function kmh(mps){return Number.isFinite(mps)?Math.max(0,Math.round(mps*3.6)):null}
 function compass(deg){
  if(!Number.isFinite(deg))return '—';
  return ['N','NE','E','SE','S','SW','W','NW'][Math.round(normalized(deg)/45)%8];
 }
 return {normalized,smoothBearing,validFix,kmh,compass};
});