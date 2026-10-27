'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const V=require('./map-views.js');
test('satellite is always default when unset or invalid, with real streets and terrain',()=>{
 assert.equal(V.normalize(), 'satellite');
 assert.equal(V.normalize('not-google'),'satellite');
 assert.deepEqual(Object.keys(V.STYLES),['satellite','streets','terrain']);
 assert.match(V.STYLES.streets.url,/World_Street_Map/);
 assert.match(V.STYLES.satellite.url,/USGSImageryTopo/);
 assert.match(V.STYLES.terrain.url,/USGSTopo/);
});
test('switching background tiles uses documented imagery sources, radar layers remain independent',()=>{
 let actual;
 const L={tileLayer:(url,options)=>{actual={url,options};return actual}};
 const layer=V.createLayer(L,'streets');
 assert.equal(layer,actual);
 assert.match(actual.url,/World_Street_Map/);
 assert.ok(actual.options.attribution.includes('Esri'));
 assert.equal(V.createLayer(L,'bad').url,V.STYLES.satellite.url);
});
test('official no-key Google Maps URLs reflect active viewport and selected mode',()=>{
 const url=new URL(V.googleMapUrl(34.36,-86.32,12,'streets'));
 assert.equal(url.hostname,'www.google.com');
 assert.equal(url.pathname,'/maps/@');
 assert.equal(url.searchParams.get('api'),'1');
 assert.equal(url.searchParams.get('map_action'),'map');
 assert.equal(url.searchParams.get('basemap'),'roadmap');
 assert.equal(url.searchParams.get('zoom'),'12');
 assert.equal(url.searchParams.get('center'),'34.360000,-86.320000');
 assert.equal(new URL(V.googleMapUrl(34.36,-86.32,8,'satellite')).searchParams.get('basemap'),'satellite');
 assert.equal(new URL(V.googleMapUrl(34.36,-86.32,8,'terrain')).searchParams.get('basemap'),'terrain');
});
test('Google Street View panorama URL is not mistaken for real-time radar',()=>{
 const url=new URL(V.streetViewUrl(34.4,-86.3));
 assert.equal(url.searchParams.get('map_action'),'pano');
 assert.equal(url.searchParams.get('viewpoint'),'34.400000,-86.300000');
 assert.throws(()=>V.streetViewUrl(NaN,-86),/valid/);
 assert.throws(()=>V.googleMapUrl(100,-86),/valid/);
 assert.throws(()=>V.googleMapUrl(35,180.2),/valid/);
});
test('shared controls exist in both active main dashboard and separate tracking windows',()=>{
 const main=fs.readFileSync(path.join(__dirname,'desktop-concept3.html'),'utf8');
 const storm=fs.readFileSync(path.join(__dirname,'storm.html'),'utf8');
 for(const file of [main,storm]){
  for(const id of ['satellite','streetView','googleMaps']){
   const match=id==='satellite'&&file===main?'id="style"':'id="'+id+'"';
   assert.match(file,new RegExp(match));
  }
  assert.match(file,/map-views\.js/);
 }
 for(const id of ['streetMap','terrainMap'])assert.match(main,new RegExp('id="'+id+'"'));
 for(const id of ['streets','terrain'])assert.match(storm,new RegExp('id="'+id+'"'));
 for(const p of ['storm.js','app.js','main.cjs','map-views.js']){
  const file=fs.readFileSync(path.join(__dirname,p),'utf8');
  new Function(file);
  if(p==='storm.js'||p==='app.js'){
   assert.match(file,/googleMapUrl/);
   assert.match(file,/streetViewUrl/);
  }
 }
});
test('packaging and Windows-only workflow reference no Mac build',()=>{
 const pkg=require('./package.json');assert.equal(pkg.version,'0.8.4');
 assert.ok(pkg.build.files.includes('map-views.js'));
 assert.match(pkg.build.win.artifactName,/0\.8\.3/);
 const ci=fs.readFileSync(path.join(__dirname,'..','.github/workflows/build-newswire-streamdeck-v0.7.1.yml'),'utf8');
 assert.doesNotMatch(ci,/macos-latest/);
 assert.match(ci,/windows-latest/);
});
