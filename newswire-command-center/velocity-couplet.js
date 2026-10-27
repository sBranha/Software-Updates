/* NewsWire manual radial-velocity couplet measurements (NOT automatic storm detection).
   Values MUST be independently read/entered by trained operator: imagery is not decoded. */
(function(root,factory){
 const api=factory();
 if(typeof module==='object'&&module.exports)module.exports=api;
 root.NewsWireVelocityCouplet=api;
})(typeof window!=='undefined'?window:globalThis,function(){
'use strict';
function valid(p){return p&&Number.isFinite(p.lat)&&Number.isFinite(p.lng)&&Math.abs(p.lat)<=90&&Math.abs(p.lng)<=180}
function distanceMeters(a,b){
 if(!valid(a)||!valid(b))throw Error('Select two valid locations');
 const deg=Math.PI/180,dlat=(b.lat-a.lat)*deg,dlon=(b.lng-a.lng)*deg;
 const x=Math.sin(dlat/2)**2+Math.cos(a.lat*deg)*Math.cos(b.lat*deg)*Math.sin(dlon/2)**2;
 return 12742000*Math.asin(Math.min(1,Math.sqrt(x)));
}
function analyze(a,b,firstMph,secondMph){
 const v1=Number(firstMph),v2=Number(secondMph);
 if(firstMph===''||secondMph===''||!Number.isFinite(v1)||!Number.isFinite(v2)||Math.abs(v1)>250||Math.abs(v2)>250)
  throw Error('Enter two signed operator-read radial velocities from -250 to +250 mph');
 const d=distanceMeters(a,b);
 if(d<100||d>50000)throw Error('Velocity points must be 0.1–50 km apart');
 const difference=Math.abs(v1-v2);
 const shear=(difference*0.44704)/d;
 return {distanceKm:Math.round(d/10)/100,velocityDifferenceMph:Math.round(difference*10)/10,
  inferredHalfDifferenceMph:Math.round(difference*5)/10,
  radialShearPerSecond:Math.round(shear*1e6)/1e6,
  method:'Manual, operator-entered, two-point velocity difference',
  warning:'NOT automatic rotation detection, not a tornado diagnosis or warning. Radial velocities may contain aliasing and beam geometry artifacts.'};
}
return {distanceMeters,analyze};
});