'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const siteApi=require('./site-radar.js'),D=require('./drawn-tracks.js'),V=require('./velocity-couplet.js');
const source=()=>fs.readFileSync(path.join(__dirname,'main-radar-desk.js'),'utf8');
function launch(){
 const elems=new Map(),markers=[],slots={},storage=new Map(),selected=[],activeModes=[],scans=[];
 const stubElement=id=>{
  if(!elems.has(id))elems.set(id,{
   id,textContent:'',value:'',disabled:false,hidden:false,attrs:{},children:[],
   addEventListener(k,fn){slots[id+':'+k]=fn},setAttribute(k,v){this.attrs[k]=v},
   replaceChildren(){this.children=[]},append(...c){this.children.push(...c)},focus(){}
  });
  return elems.get(id);
 };
 const layer=(coords)=>{
  const obj={coords,handlers:{},addTo(){return this},bindTooltip(){return this},
   on(name,fn){this.handlers[name]=fn;return this},setOpacity(){return this},bringToBack(){return this},
   getBounds(){return {isValid:()=>true,pad(){return this}}}};
  markers.push(obj);return obj;
 };
 const map={center:[39,-97],zoom:5,
  removeLayer(){},fitBounds(){},invalidateSize(){},flyTo(p){this.center=p},getZoom(){return this.zoom}
 };
 const L={layerGroup:()=>layer(),circleMarker:coords=>layer(coords),polygon:coords=>layer(coords),
  polyline:coords=>layer(coords),latLngBounds:()=>({isValid:()=>true,pad(){return this}})};
 let on=true,playing=true,warningChanges=0,requested=[];
 const composite={show(x){on=Boolean(x);return on},isShown:()=>on,
  isPlaying:()=>playing,play(x){playing=Boolean(x);return playing},refresh(){}};
 let siteRadarShown=false;
 const radar={site:'KHTX',product:'N0B',
  select(a,b){selected.push([a,b]);this.site=a;this.product=b},
  show(v){siteRadarShown=Boolean(v);return siteRadarShown},isShown(){return siteRadarShown},
  showScan(time){scans.push(time)},showLatest(){},dispose(){}};
 const site={...siteApi,createSiteRadar:()=>radar};
 const document={getElementById:stubElement,createElement:()=>({textContent:'',value:''})};
 const window={NewsWireSiteRadar:site,NewsWireDrawnTracks:D,NewsWireVelocityCouplet:V,
  NewsWireDesktopBridge:{
   requestRadarScans:async(a,b)=>{
    requested.push([a,b]);return {ok:true,scans:['2026-10-27T17:20Z','2026-10-27T17:25Z']};
   },
   lookupCommunities:async()=>({ok:true,places:[{name:'Verified Town',lat:34.34,lng:-86.3,state:'AL'}]})
  },addEventListener(){}
 };
 const localStorage={getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)};
 const context={window,document,localStorage,L,console,
  setInterval:()=>1,clearInterval(){},};
 vm.runInNewContext(source(),context,{filename:'main-radar-desk.js'});
 const desk=window.NewsWireMainRadarDesk.init({map,L,composite,setActive:r=>activeModes.push(r),
  onWarningToggle(){warningChanges++},onInteraction(){}});
 return {desk,elems,storage,slots,markers,selected,requested,scans,activeModes,radar,
  get warningChanges(){return warningChanges},
  click:id=>{assert.equal(typeof slots[id+':click'],'function',id);slots[id+':click']()},
  change:(id,v)=>{stubElement(id).value=v;slots[id+':change']()},
  el:stubElement
 };
}
test('main screen shares exact KHTX/Georgia NEXRAD product sources, independent of pop-out windows',async()=>{
 const t=launch();
 assert.equal(t.desk.getMode(),'mosaic');
 t.change('mainSite','KFFC');
 await new Promise(r=>setImmediate(r));
 assert.equal(t.desk.getMode(),'site');
 assert.equal(t.desk.getSite(),'KFFC');
 assert.deepEqual(t.selected.at(-1),['KFFC','N0B']);
 assert.equal(t.el('mainSite').value,'KFFC');
 t.change('mainProduct','N0S');
 await new Promise(r=>setImmediate(r));
 assert.equal(t.desk.getProduct(),'N0S');
 assert.deepEqual(t.requested.at(-1),['KFFC','N0S']);
 assert.equal(t.el('mainScanTime').textContent.includes('Observed'),true);
 assert.ok(t.scans.length>=1);
 assert.match(t.storage.get('nwcc-main-radar-config'),/"site":"KFFC"/);
 assert.equal(t.storage.has('nwcc-radar-config-1'),false);
});
test('clicking Georgia/NWS site pin enables radar velocity in the same map',async()=>{
 const t=launch();
 const siteMarker=t.markers.find(x=>Array.isArray(x.coords)&&Math.abs(x.coords[0]-33.36355)<0.01);
 assert.ok(siteMarker,'Georgia site marker missing');
 siteMarker.handlers.click();
 await new Promise(r=>setImmediate(r));
 assert.equal(t.desk.getSite(),'KFFC');
 t.change('mainProduct','N0U');
 await new Promise(r=>setImmediate(r));
 assert.equal(t.desk.getProduct(),'N0U');
 assert.deepEqual(t.selected.at(-1),['KFFC','N0U']);
});
test('main warning selection uses nearby site and never opens storm 1-4',async()=>{
 const t=launch();
 t.desk.analyzeWarning({center:[33.5,-84.5]});
 await new Promise(r=>setImmediate(r));
 assert.equal(t.desk.getSite(),'KFFC');
 t.click('mainWarningToggle');
 assert.equal(t.desk.isWarningsShown(),false);
 assert.equal(t.warningChanges,1);
 assert.equal(t.storage.get('nwcc-main-warnings'),'false');
});
test('draw a polygon and find real Census communities without opening a popout',async()=>{
 const t=launch();t.click('mainDraw');
 const corners=[{lat:34.2,lng:-86.5},{lat:34.2,lng:-86.2},{lat:34.5,lng:-86.2},{lat:34.5,lng:-86.5}];
 for(const p of corners)assert.equal(t.desk.consumeClick({latlng:p}),true);
 t.click('mainFinish');await new Promise(r=>setImmediate(r));
 assert.equal(JSON.parse(t.storage.get('nwcc-main-polygon')).length,4);
 assert.match(t.el('mainAreaSummary').textContent,/1 mapped town center/);
 assert.equal(t.el('mainPlaces').children[0].textContent,'Verified Town · AL');
 assert.equal(t.storage.has('nwcc-drawn-track-1'),false);
 t.click('mainClear');assert.equal(t.storage.has('nwcc-main-polygon'),false);
});
test('manual velocity worksheet operates within main map and never claims automated detection',()=>{
 const t=launch();t.click('mainVelocityPick');
 t.desk.consumeClick({latlng:{lat:34.92,lng:-86.08}});
 t.desk.consumeClick({latlng:{lat:34.93,lng:-86.08}});
 t.el('mainVelA').value='-40';t.el('mainVelB').value='60';t.click('mainVelocityCalculate');
 assert.match(t.el('mainVelocityResult').textContent,/ΔV 100 mph/);
 assert.match(t.el('mainVelocityResult').textContent,/NOT automatic rotation detection/);
 assert.equal(t.storage.has('nwcc-manual-velocity-1'),false);
});
test('main html, Electron IPC and package include every full-workstation control',()=>{
 const html=fs.readFileSync(path.join(__dirname,'desktop-concept3.html'),'utf8');
 for(const id of ['mainSource','mainSite','mainProduct','mainFocusSite','mainPrev','mainNext','mainLatest','mainLoop',
  'mainDraw','mainFinish','mainPlaces','mainVelocityPick','mainVelocityCalculate','mainWarningToggle','mainAnalysisToggle'])
  assert.match(html,new RegExp('id="'+id+'"'));
 assert.match(html,/src="main-radar-desk\.js"/);
 const app=fs.readFileSync(path.join(__dirname,'app.js'),'utf8');
 assert.match(app,/NewsWireMainRadarDesk\.init/);
 assert.match(app,/mainRadarDesk\?\.consumeClick/);
 const ipc=fs.readFileSync(path.join(__dirname,'main.cjs'),'utf8');
 assert.match(ipc,/owner===views\.get\('map'\)/);
 const pkg=require('./package.json');
 assert.equal(pkg.version,'0.8.6');
 assert.ok(pkg.build.files.includes('main-radar-desk.js'));
 assert.match(pkg.build.win.artifactName,/0\.8\.6/);
});
