/* NewsWire original motion math: local radar echo measurements and NWS bulletin direction guides.
 * A warning polygon's center is NOT a storm cell. Operators identify echo centers themselves.
 * Calculations are illustrative, never official forecasts or tornado detection. */
(function(root,factory){
 const api=factory(root);
 if(typeof module==='object'&&module.exports)module.exports=api;
 root.NewsWireStormMotion=api;
})(typeof window!=='undefined'?window:globalThis,function(root){
'use strict';
const R=Math.PI/180, EARTH_MI=3958.7613;
const DIR=Object.freeze({N:0,NNE:22.5,NE:45,ENE:67.5,E:90,ESE:112.5,SE:135,SSE:157.5,S:180,SSW:202.5,SW:225,WSW:247.5,W:270,WNW:292.5,NW:315,NNW:337.5,
 NORTH:0,NORTHEAST:45,EAST:90,SOUTHEAST:135,SOUTH:180,SOUTHWEST:225,WEST:270,NORTHWEST:315});
function point(p){
 const lat=Number(p?.lat),lng=Number(p?.lng??p?.lon);
 if(!Number.isFinite(lat)||!Number.isFinite(lng)||Math.abs(lat)>90||Math.abs(lng)>180)throw Error('Invalid latitude/longitude');
 return {lat,lng};
}
function miles(a,b){
 const p=point(a),q=point(b),la=(q.lat-p.lat)*R,lo=(q.lng-p.lng)*R;
 const x=Math.sin(la/2)**2+Math.cos(p.lat*R)*Math.cos(q.lat*R)*Math.sin(lo/2)**2;
 return 2*EARTH_MI*Math.asin(Math.min(1,Math.sqrt(x)));
}
function bearing(a,b){
 const p=point(a),q=point(b),a1=p.lat*R,a2=q.lat*R,d=(q.lng-p.lng)*R;
 const y=Math.sin(d)*Math.cos(a2);
 const x=Math.cos(a1)*Math.sin(a2)-Math.sin(a1)*Math.cos(a2)*Math.cos(d);
 return (Math.atan2(y,x)/R+360)%360;
}
function destination(from,degrees,distanceMiles){
 const p=point(from);
 if(!Number.isFinite(degrees)||!Number.isFinite(distanceMiles)||distanceMiles<0||distanceMiles>400)throw Error('Invalid projected motion');
 const d=distanceMiles/EARTH_MI,t=degrees*R,a=p.lat*R,b=p.lng*R;
 const n=Math.asin(Math.sin(a)*Math.cos(d)+Math.cos(a)*Math.sin(d)*Math.cos(t));
 const l=b+Math.atan2(Math.sin(t)*Math.sin(d)*Math.cos(a),Math.cos(d)-Math.sin(a)*Math.sin(n));
 return {lat:n/R,lng:((l/R+540)%360)-180};
}
function forecast(start,heading,mph,steps=[10,20,30]){
 if(!Number.isFinite(mph)||mph<0||mph>200)throw Error('Invalid motion speed');
 return steps.map(minutes=>({minutes,point:destination(start,heading,mph*minutes/60)}));
}
function observedPair(first,second,now=Date.now()){
 const a=point(first),b=point(second);
 const t1=Date.parse(first?.time||''),t2=Date.parse(second?.time||'');
 if(!Number.isFinite(t1)||!Number.isFinite(t2)||t2<=t1)throw Error('Select two different observed scans in chronological order');
 const dt=(t2-t1)/60000;
 if(dt<1||dt>45)throw Error('Observed scans must be 1–45 minutes apart');
 if(t2>now+2*60000)throw Error('Future radar observation is not valid');
 if(first.site!==second.site||first.product!==second.product)throw Error('Keep the same radar site and product');
 const distance=miles(a,b),mph=distance/dt*60;
 if(distance<0.1)throw Error('Echo movement too small to measure reliably');
 if(mph>150)throw Error('Movement over 150 mph: check the identified radar targets');
 const heading=bearing(a,b);
 return {kind:'operator-measured',start:a,end:b,firstScan:new Date(t1).toISOString(),
  latestScan:new Date(t2).toISOString(),site:first.site,product:first.product,
  elapsedMinutes:Math.round(dt*10)/10,distanceMiles:Math.round(distance*10)/10,
  mph:Math.round(mph*10)/10,bearingDegrees:Math.round(heading),
  staleMinutes:Math.max(0,Math.floor((now-t2)/60000)),
  forecast:forecast(b,heading,mph),
  disclaimer:'Operator-selected radar echo positions, not automatic cell tracking, tornado detection or NWS arrival times.'};
}
function geometryCenter(g){
 const polys=g?.type==='Polygon'?[g.coordinates]:g?.type==='MultiPolygon'?g.coordinates:[];
 let north=-91,south=91,east=-181,west=181,n=0;
 for(const poly of polys)for(const ring of Array.isArray(poly)?poly:[])for(const c of Array.isArray(ring)?ring:[]){
  if(!Array.isArray(c)||c.length<2)continue;
  const x=Number(c[0]),y=Number(c[1]);
  if(!Number.isFinite(x)||!Number.isFinite(y)||Math.abs(x)>180||Math.abs(y)>90)continue;
  west=Math.min(west,x);east=Math.max(east,x);south=Math.min(south,y);north=Math.max(north,y);n++;
 }
 return n>0&&east-west<180?{lat:(north+south)/2,lng:(west+east)/2}:null;
}
function bulletinGuide(item){
 if(!item||!item.geometry||!item.description)return null;
 const t=root.NewsWireTracking||(typeof require==='function'?require('./tracking-engine.js'):null);
 const motion=t?.parseMotion(item.description);
 const center=geometryCenter(item.geometry);
 if(!center||!motion)return null;
 const degrees=DIR[motion.direction];
 if(!Number.isFinite(degrees))return null;
 return {start:center,end:destination(center,degrees,motion.mph/3),
  mph:motion.mph,bearingDegrees:degrees,direction:motion.direction,
  source:'NWS bulletin stated motion · starts at WARNING POLYGON center, NOT storm location',
  disclaimer:'Illustrative 20-minute direction guide only. NOT a predicted storm or tornado track.'};
}
return {point,miles,bearing,destination,forecast,observedPair,geometryCenter,bulletinGuide};
});
