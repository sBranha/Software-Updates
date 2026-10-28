'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const Offline=require('./offline-cities.cjs'),Srv=require('./communities-service.cjs');
const polygon=[{lat:34.15,lng:-86.55},{lat:34.15,lng:-86.08},{lat:34.6,lng:-86.08},{lat:34.6,lng:-86.55}];
const fixture=[
 {name:'Insideville',state:'AL',lat:34.36,lng:-86.31},
 {name:'Outsideville',state:'AL',lat:34.62,lng:-86.31},
 {name:'Farville',state:'AL',lat:40,lng:-77}
];
function response(elements,status=200){return {ok:status===200,status,text:async()=>JSON.stringify({elements})}}
test('offline community centers are filtered to polygon, nearby remain explicitly OUTSIDE',()=>{
 const res=Offline.query(polygon,{cities:fixture});
 assert.deepEqual(res.places.map(x=>x.name),['Insideville']);
 assert.equal(res.places[0].source,'2025 U.S. Census-based places');
 assert.equal(res.nearby[0].name,'Outsideville');
 assert.equal(res.nearby[0].distanceKm>0,true);
});
test('offline lookup NEVER calls blocked online services',async()=>{
 let called=0;
 const v=await Srv.lookupCommunities(polygon,{cities:fixture,fetchImpl:()=>{called++;throw Error('Online failed')}});
 assert.equal(called,0);assert.deepEqual(v.places.map(x=>x.name),['Insideville']);
 assert.equal(v.offline,true);
});
test('expanded online failure preserves offline communities and reports errors',async()=>{
 const v=await Srv.lookupCommunities(polygon,{
  cities:fixture,expand:true,timeoutMs:500,providers:Srv.PROVIDERS.slice(0,2),
  fetchImpl:async()=>response([],406)
 });
 assert.equal(v.places.length,1);
 assert.equal(v.places[0].name,'Insideville');
 assert.match(v.warning,/HTTP 406/);
});
test('expanded online success merges extra communities without losing offline towns',async()=>{
 const v=await Srv.lookupCommunities(polygon,{
  cities:fixture,expand:true,providers:Srv.PROVIDERS.slice(0,1),
  fetchImpl:async()=>response([{lat:34.3,lon:-86.25,tags:{place:'hamlet',name:'Small Hamlet'}}])
 });
 assert.deepEqual(v.places.map(x=>x.name),['Insideville','Small Hamlet']);
 assert.equal(v.online,true);
});
test('bundled verified nationwide Census dataset exists and includes Alabama cities',()=>{
 const dataset=Offline.load();
 assert.ok(dataset.length>17500,'Need a complete nationwide offline city dataset');
 assert.ok(dataset.some(x=>x.name==='Guntersville'&&x.state==='AL'),'Missing Guntersville, Alabama');
 assert.ok(dataset.some(x=>x.name==='Albertville'&&x.state==='AL'),'Missing Albertville, Alabama');
});
test('package includes offline database and source generation script; Windows remains only release platform',()=>{
 const pkg=require('./package.json');
 assert.equal(pkg.version,'0.8.6');
 assert.ok(pkg.build.files.includes('data/census-places-2025.json'));
 assert.ok(pkg.build.files.includes('offline-cities.cjs'));
 const workflow=fs.readFileSync(path.join(__dirname,'..','.github','workflows','build-newswire-streamdeck-v0.7.1.yml'),'utf8');
 assert.match(workflow,/node newswire-command-center\/prepare-cities\.cjs/);
 assert.match(workflow,/windows-latest/);
 assert.doesNotMatch(workflow,/macos-latest/);
 const ui=fs.readFileSync(path.join(__dirname,'polygon-ui.js'),'utf8');
 assert.match(ui,/lookup\(expand=false\)/);
 assert.match(ui,/nearbyHeading/);
 assert.match(ui,/lookup\(true\)/);
});
