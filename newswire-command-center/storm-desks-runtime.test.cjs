'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
const V=require('./map-views.js'),D=require('./drawn-tracks.js');
const source=name=>fs.readFileSync(path.join(__dirname,name),'utf8');
function createDesk(slot,sharedStorage){
 const ids=new Map(),listeners={},mapListeners={};
 function el(id){
  if(!ids.has(id))ids.set(id,{
   textContent:'',className:'',style:{},hidden:false,disabled:false,
   attrs:{},setAttribute(k,v){this.attrs[k]=String(v)},
   addEventListener(k,fn){listeners[id+':'+k]=fn},
   replaceChildren(){this.children=[]},
   append(...entries){(this.children||(this.children=[])).push(...entries)},
   scrollIntoView(){},
  });
  return ids.get(id);
 }
 let center={lat:39,lng:-97},zoom=5,shown=false,playing=false,fullscreens=0;
 const map={
  on(evt,fn){(mapListeners[evt]||(mapListeners[evt]=[])).push(fn);return this},
  emit(evt,event){for(const fn of mapListeners[evt]||[])fn(event)},
  setView(point,n){center={lat:point[0],lng:point[1]};if(Number.isFinite(n))zoom=n;return this},
  flyTo(point,n){return this.setView(point,n)},
  getCenter(){return center},getZoom(){return zoom},
  createPane(){return this},getPane(){return {style:{}}},
  getContainer(){return {style:{}}},
  removeLayer(){},hasLayer(){return false},invalidateSize(){},
  fitBounds(){},
 };
 const layer=()=>({addTo(){return this},bringToBack(){return this},bindTooltip(){return this},
   getBounds(){return {isValid(){return true},pad(){return this}}}});
 const L={
  map:()=>map,tileLayer:layer,circleMarker:layer,polygon:layer,polyline:layer,layerGroup:layer,geoJSON:layer,
  latLngBounds:vertices=>({vertices,isValid(){return true},pad(){return this}})
 };
 const bridge={
  lookupCommunities:async()=>({ok:true,places:[],nearby:[]}),
  onTrackedWarning(){},toggleFullscreen(){fullscreens++},
  openPanel(){},
 };
 const window={
  NewsWireMapViews:V,NewsWireDrawnTracks:D,NewsWireDesktopBridge:bridge,
  NewsWireRadarLoop:{createRadar(){return {
   show(v){shown=Boolean(v);return shown},isShown(){return shown},
   play(v){playing=Boolean(v);return playing},isPlaying(){return playing},
   refresh(){},
  }}},
 };
 const document={getElementById:el,body:{classList:{toggle(){}}},hidden:false,
   createElement(){return {textContent:'',append(){}}}};
 const localStorage={
  getItem:k=>sharedStorage.has(k)?sharedStorage.get(k):null,
  setItem:(k,v)=>sharedStorage.set(k,String(v)),
  removeItem:k=>sharedStorage.delete(k),
 };
 const context={window,document,localStorage,L,
  URLSearchParams,URL,location:{search:'?slot='+slot},console,
  setTimeout(cb){cb();return 1},setInterval(){return 1},
 };
 vm.runInNewContext(source('storm.js'),context,{filename:'storm.js'});
 vm.runInNewContext(source('polygon-ui.js'),context,{filename:'polygon-ui.js'});
 return {slot,map,el,click:id=>{assert.ok(listeners[id+':click'],'Missing '+id);listeners[id+':click']()},
  drawPolygon(points){
   listeners['draw:click']();
   for(const p of points)map.emit('click',{latlng:p});
   listeners['finish:click']();
  },
  get fullscreenCount(){return fullscreens}};
}
test('two simultaneous radar windows retain separate basemaps, loop and warning controls',()=>{
 const storage=new Map(),a=createDesk(1,storage),b=createDesk(2,storage);
 a.click('streets');
 assert.equal(storage.get('nwcc-storm-map-mode-1'),'streets');
 assert.equal(storage.get('nwcc-storm-map-mode-2'),'satellite');
 b.click('radar');
 assert.equal(storage.get('nwcc-storm-2-radar-on'),'false');
 assert.match(a.el('radar').textContent,/ON/);
 assert.match(b.el('radar').textContent,/OFF/);
 a.click('warningOverlay');
 assert.equal(storage.get('nwcc-storm-1-warning-outline'),'false');
 assert.equal(storage.has('nwcc-storm-2-warning-outline'),false);
 b.click('autoFocus');
 assert.equal(storage.get('nwcc-storm-2-auto-focus'),'false');
 assert.equal(storage.has('nwcc-storm-1-auto-focus'),false);
 a.click('fullScreen');
 assert.equal(a.fullscreenCount,1);
 assert.equal(b.fullscreenCount,0);
});
test('map position and independent polygon persist under each storm slot key',()=>{
 const storage=new Map(),a=createDesk(1,storage),b=createDesk(2,storage);
 a.map.setView([34.33,-86.34],10);a.map.emit('moveend');
 b.map.setView([35.1,-87.2],8);b.map.emit('moveend');
 assert.deepEqual(JSON.parse(storage.get('nwcc-storm-1-viewport')),{lat:34.33,lng:-86.34,zoom:10});
 assert.deepEqual(JSON.parse(storage.get('nwcc-storm-2-viewport')),{lat:35.1,lng:-87.2,zoom:8});
 a.drawPolygon([{lat:34.25,lng:-86.4},{lat:34.25,lng:-86.2},{lat:34.4,lng:-86.2},{lat:34.4,lng:-86.4}]);
 b.drawPolygon([{lat:35.05,lng:-87.2},{lat:35.05,lng:-87},{lat:35.22,lng:-87},{lat:35.22,lng:-87.2}]);
 const ap=JSON.parse(storage.get('nwcc-drawn-track-1')),bp=JSON.parse(storage.get('nwcc-drawn-track-2'));
 assert.equal(ap.length,4);assert.equal(bp.length,4);
 assert.notEqual(ap[0].lat,bp[0].lat);
 a.click('clear');
 assert.equal(storage.get('nwcc-drawn-track-1'),undefined);
 assert.equal(storage.has('nwcc-drawn-track-2'),true);
});
test('main process applies fresh warning updates to all 4 assigned independent slots',()=>{
 const main=source('main.cjs');
 assert.match(main,/for\(const x of items\.slice\(0,4\)\)/);
 assert.match(main,/!\[1,2,3,4\]\.includes\(x\.slot\)/);
 assert.match(main,/for\(const slot of \[1,2,3,4\]\)sendTracked\(slot\)/);
});
