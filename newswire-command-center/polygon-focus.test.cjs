'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
const polygon=[{lat:34.31,lng:-86.45},{lat:34.31,lng:-86.30},{lat:34.41,lng:-86.30},{lat:34.41,lng:-86.45}];
const D=require('./drawn-tracks.js');
function launch(saved=true){
 const ids=new Map(),callbacks={},fits=[],timeOuts=[];
 function el(id){
  if(!ids.has(id))ids.set(id,{
   textContent:'',innerHTML:'',disabled:false,hidden:false,
   addEventListener(name,fn){callbacks[id+':'+name]=fn},
   replaceChildren(){this.items=[]},append(e){(this.items||(this.items=[])).push(e)},
   scrollIntoView(){},
  });
  return ids.get(id);
 }
 const map={invalidateSize(){this.invalidated=true},fitBounds(b,options){fits.push({b,options})},
  removeLayer(){},on(name,cb){callbacks['map:'+name]=cb}};
 const layer=()=>({addTo(){return this},bindTooltip(){return this}});
 const L={polygon:layer,polyline:layer,layerGroup:layer,circleMarker:layer,
  latLngBounds(vertices){return {vertices,isValid(){return true},pad(n){this.padded=n;return this}}}};
 const bridge={lookupCommunities:async()=>({ok:true,places:[],nearby:[]})};
 const context={window:{NewsWireStormMap:map,NewsWireDrawnTracks:D,NewsWireDesktopBridge:bridge,addEventListener(){}},
  document:{getElementById:el,createElement:()=>({textContent:'',append(){}})},
  localStorage:{getItem(){return saved?JSON.stringify(polygon):null},setItem(){},removeItem(){}},
  location:{search:'?slot=1'},URLSearchParams,console,
  setTimeout(cb,time){timeOuts.push(time);cb();return 1},L};
 vm.runInNewContext(fs.readFileSync(path.join(__dirname,'polygon-ui.js'),'utf8'),context);
 return {map,fits,callbacks,ids,timeOuts};
}
test('restored saved polygon automatically centers the storm radar map',()=>{
 const a=launch();
 assert.ok(a.fits.length>=1);
 assert.equal(a.map.invalidated,true);
 assert.equal(a.fits[0].options.maxZoom,11);
 assert.equal(a.fits[0].b.vertices.length,4);
});
test('focus polygon button explicitly re-centers map',()=>{
 const a=launch();const count=a.fits.length;
 a.callbacks['focusArea:click']();
 assert.equal(a.fits.length,count+1);
 assert.equal(a.fits.at(-1).options.animate,true);
});
test('no polygon disables Focus My Polygon',()=>{
 const a=launch(false);
 assert.equal(a.fits.length,0);
 assert.equal(a.ids.get('focusArea').disabled,true);
});
test('offline results remain while optional online expansion is pending',()=>{
 const js=fs.readFileSync(path.join(__dirname,'polygon-ui.js'),'utf8');
 assert.match(js,/if\(!expand\)\$\('places'\)\.replaceChildren\(\)/);
 assert.match(js,/if\(!expand&&communityLayer\)/);
 assert.doesNotMatch(js,/setStatus\('drawStatus','Offline data loaded\. Optional online expansion failed:/);
 assert.match(js,/Extra online neighborhoods are unavailable right now/);
});
test('Windows-only release includes new focus control and v0.8.3 artifact',()=>{
 const html=fs.readFileSync(path.join(__dirname,'storm.html'),'utf8');
 assert.match(html,/id="focusArea"/);
 const pkg=require('./package.json');
 assert.equal(pkg.version,'0.8.3');
 assert.match(pkg.build.win.artifactName,/0\.8\.3/);
});
