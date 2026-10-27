'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const S=require('./scan-service.cjs'),R=require('../site-radar.js');
const base=Date.parse('2026-10-27T17:30:00Z');
const mk=(scans,status=200)=>({ok:status===200,status,text:async()=>JSON.stringify(scans)});
test('KHTX real IEM scan list request includes bounded UTC time range',()=>{
 const url=new URL(S.urlFor('KHTX','N0U',base));
 assert.equal(url.hostname,'mesonet.agron.iastate.edu');
 assert.equal(url.searchParams.get('operation'),'list');
 assert.equal(url.searchParams.get('radar'),'HTX');
 assert.equal(url.searchParams.get('product'),'N0U');
 assert.match(url.searchParams.get('start'),/^2026-10-27T15:55Z$/);
 assert.throws(()=>S.urlFor('EVIL','N0B',base),/Unsupported/);
});
test('archive times are real, validated, deduplicated, bounded and sorted',()=>{
 const a=S.parseScans({scans:[
  {valid:'2026-10-27T17:25Z'},
  '202610271720','202610271725',{'time':'2026-10-27T17:15:00Z'},
  '2026-10-25T00:00:00Z','invalid'
 ]},base);
 assert.deepEqual(a,['2026-10-27T17:15Z','2026-10-27T17:20Z','2026-10-27T17:25Z']);
 assert.equal(S.parseScans({scans:[]},base).length,0);
});
test('fetch returns chronological published scan list with accurate UTC age',async()=>{
 const result=await S.fetchScans('KHTX','N0B',{now:base,fetchImpl:async url=>{
  assert.match(url,/product=N0B/);
  return mk({scans:[{time:'2026-10-27T17:15:00Z'},{time:'2026-10-27T17:25:00Z'}]});
 }});
 assert.equal(result.latest,'2026-10-27T17:25Z');
 assert.equal(result.ageMinutes,5);
 assert.equal(result.scans.length,2);
});
test('KHTX archive frame layers use exact observed UTC time and safe TMS',()=>{
 const created=[],active=new Set();
 const tileLayer=(url,options)=>{
  const o={url,options,handlers:{},on(name,cb){this.handlers[name]=cb;return this},
   addTo(){active.add(this);return this},bringToFront(){return this},
   setParams(){return this}};
  created.push(o);return o;
 };
 tileLayer.wms=tileLayer;
 const map={hasLayer:x=>active.has(x),removeLayer:x=>active.delete(x)};
 const radar=R.createSiteRadar({tileLayer},map);
 radar.show(true);
 radar.showScan('2026-10-27T17:25Z');
 assert.match(created[1].url,/ridge::HTX-N0B-202610271725\/\{z\}\/\{x\}\/\{y\}\.png/);
 assert.equal(radar.scanTime(),'2026-10-27T17:25Z');
 radar.showLatest();
 assert.equal(radar.scanTime(),null);
 radar.dispose();
 assert.equal(active.size,0);
 assert.throws(()=>radar.showScan('some random time'),/Invalid observation/);
});
test('Windows package includes both renderer radar and native scan service',()=>{
 const pkg=require('../package.json');
 assert.equal(pkg.version,'0.8.3');
 assert.ok(pkg.build.files.includes('site-radar.js'));
 assert.ok(pkg.build.files.includes('radar-data/scan-service.cjs'));
 const html=fs.readFileSync(path.join(__dirname,'..','storm.html'),'utf8');
 for(const id of ['radarSite','radarProduct','sitePrev','siteNext','siteLatest','siteScanTime','warningOverlay','fullScreen'])
  assert.match(html,new RegExp('id="'+id+'"'));
 const js=fs.readFileSync(path.join(__dirname,'..','storm.js'),'utf8');
 for(const fn of ['requestRadarScans','loadSiteScans','showScan','stepSite'])
  assert.match(js,new RegExp(fn));
 const main=fs.readFileSync(path.join(__dirname,'..','main.cjs'),'utf8');
 assert.match(main,/newswire:radar-scans/);
 assert.match(main,/\[1,2,3,4\]\.includes\(x\.slot\)/);
});
