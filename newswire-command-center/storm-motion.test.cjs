'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const M=require('./storm-motion.js');
const a={lat:34.93,lng:-86.1},b={lat:34.93,lng:-85.95};
test('distance, bearing and destination calculate realistic eastward echo motion',()=>{
 const d=M.miles(a,b);
 assert.ok(d>8&&d<9);
 const h=M.bearing(a,b);assert.ok(h>88&&h<92);
 const x=M.destination(a,90,d);
 assert.ok(Math.abs(x.lat-b.lat)<0.002&&Math.abs(x.lng-b.lng)<0.002);
});
test('two real observed scan selections produce speed and 10/20/30 minute forward points',()=>{
 const p=M.observedPair({...a,time:'2026-10-27T17:20:00Z',site:'KHTX',product:'N0B'},
  {...b,time:'2026-10-27T17:30:00Z',site:'KHTX',product:'N0B'},
  Date.parse('2026-10-27T17:31:00Z'));
 assert.ok(p.mph>40&&p.mph<60);
 assert.equal(p.elapsedMinutes,10);
 assert.deepEqual(p.forecast.map(x=>x.minutes),[10,20,30]);
 assert.ok(p.forecast[2].point.lng>b.lng);
 assert.match(p.disclaimer,/not automatic/i);
});
test('reject false projections from static points, bad times, unrealistic speed and mismatched radar products',()=>{
 const first={...a,time:'2026-10-27T17:20Z',site:'KHTX',product:'N0B'};
 const second={...b,time:'2026-10-27T17:30Z',site:'KHTX',product:'N0B'};
 const now=Date.parse('2026-10-27T17:32Z');
 assert.throws(()=>M.observedPair(first,{...second,time:first.time},now),/chronological/);
 assert.throws(()=>M.observedPair(first,{...second,site:'KFFC'},now),/same radar/);
 assert.throws(()=>M.observedPair(first,{...second,lat:a.lat,lng:a.lng},now),/too small/);
 assert.throws(()=>M.observedPair(first,{...second,lng:-80},now),/150 mph/);
 assert.throws(()=>M.observedPair(first,{...second,time:'2026-10-27T19:00Z'},now),/45 minutes/);
 assert.throws(()=>M.destination(a,45,-3),/Invalid projected/);
 assert.throws(()=>M.point({lat:100,lng:5}),/Invalid latitude/);
});
test('NWS motion guide is anchored to WARNING AREA, not represented as actual storm location',()=>{
 const geometry={type:'Polygon',coordinates:[[[-86.2,34.8],[-85.8,34.8],[-85.8,35],[-86.2,35],[-86.2,34.8]]]};
 const v=M.bulletinGuide({description:'MOVING NORTHEAST AT 30 MPH',geometry});
 assert.ok(v);assert.equal(v.direction,'NORTHEAST');
 assert.ok(v.end.lat>v.start.lat&&v.end.lng>v.start.lng);
 assert.match(v.source,/NOT storm location/);assert.match(v.disclaimer,/NOT a predicted storm/);
 assert.equal(M.bulletinGuide({description:'STORM UNKNOWN',geometry}),null);
 assert.equal(M.bulletinGuide({description:'MOVING WEST AT 20 MPH',geometry:null}),null);
 assert.equal(M.geometryCenter({type:'Point',coordinates:[-86,35]}),null);
});
test('new v0.8.4 controls preserve radar source selection and packaged module',()=>{
 const pkg=require('./package.json'),main=fs.readFileSync(path.join(__dirname,'main-radar-desk.js'),'utf8'),
 html=fs.readFileSync(path.join(__dirname,'desktop-concept3.html'),'utf8'),
 storm=fs.readFileSync(path.join(__dirname,'storm.js'),'utf8');
 assert.equal(pkg.version,'0.8.4');
 assert.ok(pkg.build.files.includes('storm-motion.js'));
 for(const id of ['mainMotionPick','mainMotionClear','mainMotionFocus','mainMotionStatus','mainMotionGuide'])
  assert.match(html,new RegExp('id="'+id+'"'));
 assert.match(main,/NewsWireMotionUI/);
 assert.match(fs.readFileSync(path.join(__dirname,'main-motion-ui.js'),'utf8'),/observedPair/);
 assert.match(storm,/bulletinGuide/);
});
