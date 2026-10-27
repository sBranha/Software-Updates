'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),D=require('./drawn-tracks.js'),R=require('./radar-loop.js');
const poly=[{lat:34,lng:-87},{lat:34,lng:-86},{lat:35,lng:-86},{lat:35,lng:-87}];
test('polygon accepts valid bounded local watch area',()=>{
 assert.equal(D.validPolygon(poly),true);
 assert.equal(D.validPolygon(poly.slice(0,2)),false);
 assert.deepEqual(D.bbox(poly),{south:34,west:-87,north:35,east:-86});
 assert.match(D.safeQuery(poly),/out:json/);
 assert.throws(()=>D.safeQuery([{lat:34,lng:-87},{lat:34,lng:-84},{lat:35,lng:-84}]),/smaller/);
});
test('boundary and interior included, outside points excluded',()=>{
 assert.equal(D.within({lat:34.5,lng:-86.5},poly),true);
 assert.equal(D.within({lat:34,lng:-86.3},poly),true);
 assert.equal(D.within({lat:37,lng:-86.4},poly),false);
 assert.equal(D.within({lat:34.5,lng:-88},poly),false);
});
test('communities are real OSM representative coordinates filtered to drawn polygon',()=>{
 const out=D.placeList({elements:[
  {type:'node',id:1,lat:34.3,lon:-86.4,tags:{name:'Inside Town',place:'town'}},
  {type:'node',id:2,lat:36,lon:-86.4,tags:{name:'Outside Town',place:'town'}},
  {type:'way',id:3,center:{lat:34.2,lon:-86.2},tags:{name:'Way Village',place:'village'}},
  {type:'node',id:4,lat:34.3,lon:-86.4,tags:{name:'Inside Town',place:'town'}},
  {type:'node',id:5,lat:34.6,lon:-86.6,tags:{name:'Store',place:'shop'}}
 ]},poly);
 assert.deepEqual(out.map(x=>x.name),['Inside Town','Way Village']);
});
test('community lookup uses fallback provider after primary failure',async()=>{
 let calls=0;
 const found=await D.fetchPlaces(poly,{fetchImpl:async()=>{calls++;if(calls===1)throw Error('primary offline');return {ok:true,json:async()=>({elements:[{lat:34.3,lon:-86.4,tags:{place:'town',name:'Inside Town'}}]})}},timeoutMs:1000});
 assert.equal(calls,2);assert.equal(found[0].name,'Inside Town');
});
test('seven real NEXRAD historical frame layer names use IEM documented age suffix',()=>{
 assert.deepEqual(R.MINUTES,[30,25,20,15,10,5,0]);
 assert.equal(R.LAYER_FOR_AGE(15),'nexrad-n0q-900913-m15m');
 assert.equal(R.LAYER_FOR_AGE(0),'nexrad-n0q-900913');
});
test('animated radar can pause, resume, advance, refresh and dispose',()=>{
 const frames=[];const active=new Set(),msg=[];
 const L={tileLayer:{wms(_url,options){const layer={
  on(){return this},setOpacity(n){this.opacity=n;return this},addTo(){active.add(this);return this},
  bringToFront(){return this},setParams(){return this}
 };frames.push({layer,options});return layer}}};
 const map={hasLayer:x=>active.has(x),removeLayer:x=>active.delete(x)};
 const radar=R.createRadar(L,map,s=>msg.push(s));
 assert.equal(frames.length,7);
 radar.show(true);assert.equal(active.size,7);assert.equal(radar.isPlaying(),true);
 radar.play(false);assert.equal(radar.isPlaying(),false);
 radar.next();assert.ok(msg.some(x=>x.includes('RADAR')));
 radar.refresh();radar.show(false);assert.equal(active.size,0);radar.dispose();
});
test('desktop resources include documented polygons and extra tracking windows',()=>{
 const p=path.join(__dirname,'package.json'),pkg=require(p);
 for(const file of ['radar-loop.js','drawn-tracks.js','polygon-ui.js'])assert.ok(pkg.build.files.includes(file),file);
 const storm=fs.readFileSync(path.join(__dirname,'storm.html'),'utf8');
 for(const id of ['draw','undo','finish','clear','places','placesStatus','loop'])assert.match(storm,new RegExp('id="'+id+'"'));
 assert.match(storm,/polygon-ui.js/);assert.match(storm,/radar-loop.js/);
 const main=fs.readFileSync(path.join(__dirname,'main.cjs'),'utf8');
 for(const n of [1,2,3,4])assert.match(main,new RegExp("storm-"+n));
 assert.equal(pkg.version,'0.7.7');
 for(const file of ['polygon-ui.js','radar-loop.js','drawn-tracks.js','storm.js','app.js'])new Function(fs.readFileSync(path.join(__dirname,file),'utf8'));
});
