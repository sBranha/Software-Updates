'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const D=require('./drawn-tracks.js');
const S=require('./communities-service.cjs');
const points=[{lat:34.1,lng:-86.6},{lat:34.1,lng:-86.1},{lat:34.6,lng:-86.1},{lat:34.6,lng:-86.6}];
const cities=[{name:'Inside Town',state:'AL',lat:34.25,lng:-86.35}];
function reply(elements,status=200){return {ok:status===200,status,text:async()=>JSON.stringify({elements})}}
test('community polygon lookup returns genuinely sourced names inside shape',async()=>{
 let url;
 const result=await S.lookupCommunities(points,{fetchImpl:async u=>{
  url=u;return reply([
   {lat:34.25,lon:-86.35,tags:{name:'Inside Town',place:'town'}},
   {lat:34.95,lon:-86.35,tags:{name:'Outside Town',place:'town'}},
   {center:{lat:34.4,lon:-86.28},tags:{name:'Neighborhood A',place:'neighbourhood'}}
  ])},
  providers:[S.PROVIDERS[0]],cities,expand:true
 });
 assert.match(url,/data=/);
 assert.ok(url.startsWith('https://overpass.private.coffee/api/interpreter?data='));
 assert.deepEqual(result.places.map(p=>p.name),['Inside Town','Neighborhood A']);
 assert.equal(result.complete,false);
 assert.match(result.source,/Census/);
});
test('uses fallback if first host fails or returns 429',async()=>{
 const hosts=[];const result=await S.lookupCommunities(points,{
  fetchImpl:async u=>{hosts.push(u);return hosts.length===1?reply([],429):reply([{lat:34.2,lon:-86.3,tags:{name:'Fallback Hamlet',place:'hamlet'}}])},
  providers:S.PROVIDERS.slice(0,2),cities,expand:true,
  timeoutMs:1000
 });
 assert.equal(hosts.length,2);
 assert.deepEqual(result.places.map(p=>p.name),['Fallback Hamlet','Inside Town']);
});
test('reports service failure instead of silently showing empty results',async()=>{
 const result=await S.lookupCommunities(points,{fetchImpl:async()=>{throw Error('socket disconnected')},providers:S.PROVIDERS.slice(0,2),cities,expand:true,timeoutMs:1000});
 assert.equal(result.places[0].name,'Inside Town');
 assert.match(result.warning,/socket disconnected/);
});
test('rejects forged endpoints, giant shapes and invalid coordinates',async()=>{
 await assert.rejects(S.lookupCommunities(points,{providers:['https://malicious.test/api'],cities,expand:true,fetchImpl:async()=>reply([])}),/Unapproved/);
 await assert.rejects(S.lookupCommunities([{lat:500,lng:-86},{lat:34,lng:-86},{lat:35,lng:-85}]),/Draw/);
});
test('large OSM result sets marked potentially incomplete, never definitive',async()=>{
 const elements=Array.from({length:2500},(_,i)=>({lat:34.2,lon:-86.4,tags:{name:'Town'+i,place:'town'}}));
 const r=await S.lookupCommunities(points,{providers:[S.PROVIDERS[0]],cities,expand:true,fetchImpl:async()=>reply(elements)});
 assert.equal(r.complete,false);
 assert.ok(r.places.length<=300);
});
test('desktop UI calls native bridge, maps places and provides retry and explicit feedback',()=>{
 const pkg=require('./package.json');
 assert.equal(pkg.version,'0.7.8');
 assert.ok(pkg.build.files.includes('communities-service.cjs'));
 assert.ok(pkg.build.files.includes('offline-cities.cjs'));
 const preload=fs.readFileSync(path.join(__dirname,'preload.cjs'),'utf8');
 const main=fs.readFileSync(path.join(__dirname,'main.cjs'),'utf8');
 const ui=fs.readFileSync(path.join(__dirname,'polygon-ui.js'),'utf8');
 const storm=fs.readFileSync(path.join(__dirname,'storm.html'),'utf8');
 assert.match(preload,/lookupCommunities:\(points,expand=false\)/);
 assert.match(main,/ipcMain\.handle\('newswire:communities-lookup'/);
 assert.match(main,/net\.fetch\(url,opts\)/);
 assert.match(ui,/bridge\.lookupCommunities/);
 assert.doesNotMatch(ui,/D\.fetchPlaces/);
 assert.match(ui,/L\.circleMarker/);
 assert.match(ui,/lookup\(\)/);
 assert.match(ui,/lookup\(true\)/);
 assert.match(storm,/id="retry"/);
 assert.match(storm,/id="communitySummary"/);
 for(const name of ['main.cjs','preload.cjs','polygon-ui.js','communities-service.cjs'])
  new Function(fs.readFileSync(path.join(__dirname,name),'utf8'));
});
