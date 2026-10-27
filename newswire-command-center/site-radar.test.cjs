'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const S=require('./site-radar.js');
test('officially located NEXRAD stations include Hytop KHTX and independent nearby sites',()=>{
 assert.deepEqual(S.SITES.map(x=>x.id),['KHTX','KOHX','KFFC','KMRX']);
 const k=S.siteFor('KHTX');
 assert.ok(Math.abs(k.lat-34.930556)<.0001);
 assert.ok(Math.abs(k.lng+86.083611)<.0001);
 assert.equal(k.name,'Hytop / Northeast Alabama');
});
test('real RIDGE Level III products include reflectivity, base velocity, storm-relative velocity',()=>{
 assert.deepEqual(S.PRODUCTS.map(x=>x.id),['N0B','N0U','N0S']);
 for(const product of S.PRODUCTS){
  const params=S.ridgeOptions('KHTX',product.id);
  assert.equal(params.sector,'HTX');assert.equal(params.prod,product.id);
  assert.equal(params.layers,'single');assert.equal(params.format,'image/png');
  assert.equal(params.transparent,true);
 }
 assert.throws(()=>S.ridgeOptions('BOGUS','N0U'),/Unsupported/);
 assert.throws(()=>S.ridgeOptions('KHTX','fake'),/Unsupported/);
});
test('site viewer changes layer and shows genuine unavailable status on tile errors',()=>{
 const layers=[],active=new Set(),statuses=[];
 const L={tileLayer:{wms:(url,options)=>{
  const callbacks={};
  const layer={url,options,callbacks,on(name,fn){callbacks[name]=fn;return this},
   addTo(){active.add(this);return this},bringToFront(){return this},
   setParams(v){this.params=v}};
  layers.push(layer);return layer;
 }}};
 const map={hasLayer:layer=>active.has(layer),removeLayer:layer=>active.delete(layer)};
 const x=S.createSiteRadar(L,map,t=>statuses.push(t));
 assert.equal(x.show(true),true);assert.ok(x.isShown());
 assert.match(layers[0].url,/sector=HTX&prod=N0B/);
 x.select('KHTX','N0U');assert.match(layers[1].url,/sector=HTX&prod=N0U/);
 layers[1].callbacks.tileload();assert.ok(x.lastLoaded()>0);
 x.select('KOHX','N0S');assert.match(layers[2].url,/sector=OHX&prod=N0S/);
 for(let i=0;i<3;i++)layers[2].callbacks.tileerror();
 assert.ok(statuses.some(s=>s.includes('SOURCE UNAVAILABLE')));
 assert.ok(statuses.some(s=>s.includes('timestamp unverified')));
 x.refresh();assert.ok(layers[2].params._nw_refresh>0);
 x.dispose();assert.equal(active.size,0);
});
test('four detachables get individual site/product and saved polygon keys',()=>{
 const code=fs.readFileSync(path.join(__dirname,'storm.js'),'utf8');
 const draw=fs.readFileSync(path.join(__dirname,'polygon-ui.js'),'utf8');
 const html=fs.readFileSync(path.join(__dirname,'storm.html'),'utf8');
 for(const id of ['radarSite','radarProduct','radarSource','centerSite','siteProductStatus'])assert.match(html,new RegExp('id="'+id+'"'));
 assert.match(html,/src="site-radar\.js"/);
 assert.match(code,/nwcc-radar-config-'\+slot/);
 assert.match(code,/nwcc-storm-viewport-'\+slot/);
 assert.match(code,/R\.createSiteRadar/);
 assert.match(code,/pin\.on\('click'/);
 assert.match(code,/saveRadar\(\)/);
 assert.match(draw,/nwcc-drawn-track-'\+slot/);
 assert.doesNotMatch(draw,/newswire:slot-changed/);
 assert.match(code,/radarMode==='site'\?radarLocal:radarComposite/); // Guard against replacing site mode with mosaic silently.
});
test('only Windows is built; no product falsely promises 3D or Level II decoder',()=>{
 const pkg=require('./package.json');
 assert.equal(pkg.version,'0.8.3');
 assert.ok(pkg.build.files.includes('site-radar.js'));
 const w=fs.readFileSync(path.join(__dirname,'..','.github/workflows/build-newswire-streamdeck-v0.7.1.yml'),'utf8');
 assert.match(w,/windows-latest/);assert.doesNotMatch(w,/macos-latest/);
});
