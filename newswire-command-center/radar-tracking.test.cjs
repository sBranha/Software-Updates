'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const R=require('./radar.js'),T=require('./tracking-engine.js');
test('real live weather overlay uses documented current NEXRAD base reflectivity WMS',()=>{
 assert.equal(R.WMS,'https://mesonet.agron.iastate.edu/cgi-bin/wms/nexrad/n0q.cgi');
 assert.equal(R.LAYER,'nexrad-n0q-900913');
 assert.match(R.ATTRIBUTION,/NEXRAD/);
});
test('radar layer is transparent and user-toggleable, with loading error state',()=>{
 let added=0,removed=0,loaded=null,errored=null;
 const layer={on(name,fn){if(name==='tileload')loaded=fn;if(name==='tileerror')errored=fn;return this},addTo(){added++;return this},bringToFront(){return this},setParams(){return this}};
 const L={tileLayer:{wms(url,opts){assert.equal(url,R.WMS);assert.equal(opts.layers,R.LAYER);assert.equal(opts.transparent,true);assert.equal(opts.format,'image/png');return layer}}};
 const map={hasLayer(){return added>removed},removeLayer(){removed++}};
 const status=[];const radar=R.createRadar(L,map,s=>status.push(s));
 assert.equal(radar.show(true),true);assert.equal(added,1);
 loaded();assert.ok(status.some(s=>s.includes('tiles loaded')));
 for(let i=0;i<3;i++)errored();
 assert.ok(status.some(s=>s.includes('source unavailable')));
 radar.refresh();assert.equal(radar.show(false),false);assert.equal(removed,1);
});
test('only report motion explicitly in NWS warning bulletin',()=>{
 assert.deepEqual(T.parseMotion('MOVING NORTHEAST AT 35 MPH'),{direction:'NORTHEAST',mph:35,source:'NWS bulletin text'});
 assert.equal(T.parseMotion('A possible storm may move northeast.'),null);
 assert.equal(T.parseMotion('MOVING NORTH AT 800 MPH'),null);
});
test('reject missing, giant and unrelated map geometries',()=>{
 assert.equal(T.validGeometry({type:'Point',coordinates:[0,0]}),false);
 assert.equal(T.validGeometry(null),false);
 const g={type:'Polygon',coordinates:[[[-86,34],[-85,34],[-85,35],[-86,34]]]};
 assert.equal(T.validGeometry(g),true);
 assert.equal(T.safeWarning({id:'nws1',area:'Example County',description:'MOVING EAST AT 20 MPH',geometry:g}).motion.mph,20);
 assert.equal(T.safeWarning({description:'missing ID'}),null);
});
test('four independent warning windows, separate position persistence and all IPC paths',()=>{
 const files=['main.cjs','preload.cjs','app.js','desktop-concept3.html','storm.html','storm.js','window-layout.cjs'];
 const source=Object.fromEntries(files.map(x=>[x,fs.readFileSync(path.join(__dirname,x),'utf8')]));
 assert.match(source['main.cjs'],/storm-1/);
 assert.match(source['main.cjs'],/storm-2/);
 assert.match(source['main.cjs'],/storm-3/);
 assert.match(source['main.cjs'],/storm-4/);
 assert.match(source['main.cjs'],/newswire:track-warning/);
 assert.match(source['main.cjs'],/newswire:track-updates/);
 assert.match(source['main.cjs'],/sendTracked/);
 assert.match(source['main.cjs'],/newswire-window-layout.json/);
 assert.match(source['preload.cjs'],/onTrackedWarning/);
 assert.match(source['app.js'],/Track '\+slot/);
 assert.match(source['app.js'],/NewsWireRadarLoop\|\|NewsWireRadar/);
 assert.match(source['storm.js'],/NewsWireRadarLoop\|\|NewsWireRadar/);
 assert.match(source['storm.js'],/NewsWireTracking\.parseMotion/);
 assert.match(source['desktop-concept3.html'],/id="radarToggle"/);
 assert.match(source['desktop-concept3.html'],/data-open-panel="storm-1"/);
 assert.match(source['desktop-concept3.html'],/data-open-panel="storm-2"/);
 const pkg=require('./package.json');
 assert.equal(pkg.version,'0.8.4');
 for(const f of ['radar.js','radar-loop.js','drawn-tracks.js','polygon-ui.js','tracking-engine.js','storm.html','storm.css','storm.js'])assert.ok(pkg.build.files.includes(f),f);
 for(const f of ['radar.js','tracking-engine.js','storm.js','main.cjs','app.js','preload.cjs'])new Function(source[f]||fs.readFileSync(path.join(__dirname,f),'utf8'));
});

test('manual radar-watch pins are valid but do not claim NWS storm motion or automatic cell following',()=>{
 const a=T.safeWarning({id:'manual-1',source:'manual',point:{lat:34.245,lon:-86.312}});
 assert.ok(a?.manual);
 assert.equal(a.geometry,null);
 assert.equal(a.motion,null);
 assert.equal(a.point.lat,34.245);
 assert.match(a.instruction,/fixed viewing area/i);
 assert.equal(T.safeWarning({id:'bad',source:'manual',point:{lat:200,lon:0}}),null);
 const main=fs.readFileSync(path.join(__dirname,'main.cjs'),'utf8');
 const app=fs.readFileSync(path.join(__dirname,'app.js'),'utf8');
 const html=fs.readFileSync(path.join(__dirname,'desktop-concept3.html'),'utf8');
 const storm=fs.readFileSync(path.join(__dirname,'storm.js'),'utf8');
 assert.match(main,/item\.manual/);
 assert.match(app,/trackManualSlot/);
 assert.match(html,/id="pinStorm1"/);
 assert.match(html,/id="pinStorm2"/);
 assert.match(storm,/Manually selected radar viewing point/i);
});
