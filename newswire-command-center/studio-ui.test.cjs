'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const read=name=>fs.readFileSync(path.join(__dirname,name),'utf8');
function launch(saved=null){
 const classes=new Set(),attrs=new Map(),els=new Map(),listeners={},actions={fullscreen:0,exit:0,resizes:0},store=new Map();
 if(saved!==null)store.set('nwcc-radar-first-studio',saved);
 const element=id=>{
  if(!els.has(id))els.set(id,{id,hidden:id==='mainAnalysisDrawer',textContent:'',
   setAttribute(name,value){attrs.set(id+':'+name,String(value))},
   addEventListener(name,fn){listeners[id+':'+name]=fn},
   focus(){}});
  return els.get(id);
 };
 const window={NewsWireDesktopBridge:{toggleFullscreen(){actions.fullscreen++},exitFullscreen(){actions.exit++}},
  dispatchEvent(){actions.resizes++}};
 const document={body:{classList:{toggle(n,yes){if(yes)classes.add(n);else classes.delete(n)},
   contains:n=>classes.has(n)}},
   getElementById:element,addEventListener(name,fn){listeners['document:'+name]=fn}};
 const storage={getItem:k=>store.get(k)??null,setItem:(k,v)=>store.set(k,v)};
 vm.runInNewContext(read('studio-ui.js'),{window,document,localStorage:storage,Event:class Event{},setTimeout:cb=>{cb();return 1}});
 return {classes,actions,store,element,click:id=>{const cb=listeners[id+':click'];assert.equal(typeof cb,'function');cb()},
  key:(key,tagName)=>{const cb=listeners['document:keydown'];assert.equal(typeof cb,'function');cb({key,target:{tagName},preventDefault(){}})}};
}
test('main radar opens in large studio by default with controls to return to original layout',()=>{
 const v=launch();assert.ok(v.classes.has('studioMode'));
 assert.match(v.element('studioToggle').textContent,/Standard Layout/);
 v.click('studioToggle');assert.equal(v.classes.has('studioMode'),false);
 assert.equal(v.store.get('nwcc-radar-first-studio'),'false');
 v.click('studioToggle');assert.equal(v.classes.has('studioMode'),true);
});
test('warnings remain accessible over main map; no second window is opened',()=>{
 const v=launch();v.click('studioAlerts');
 assert.ok(v.classes.has('studioAlertsVisible'));
 assert.match(v.element('studioAlerts').textContent,/Hide Alerts/);
 v.key('Escape','BUTTON');
 assert.equal(v.classes.has('studioAlertsVisible'),false);
 assert.ok(v.classes.has('studioMode'));
});
test('F11 and on-screen fullscreen work on primary Windows view and Escape gets back out',()=>{
 const v=launch();v.key('F11','DIV');v.click('studioFullscreen');
 assert.equal(v.actions.fullscreen,2);
 v.key('Escape','DIV');
 assert.equal(v.actions.exit,1);
 assert.equal(v.classes.has('studioMode'),false);
});
test('Escape closes analysis overlay before exiting Studio',()=>{
 const v=launch();v.element('mainAnalysisDrawer').hidden=false;
 v.key('Escape','BUTTON');
 assert.equal(v.element('mainAnalysisDrawer').hidden,true);
 assert.ok(v.classes.has('studioMode'));
});
test('site selectors, NWS alerts and drawing panels are not removed in radar-first mode',()=>{
 const html=read('desktop-concept3.html'),css=read('concept3.css'),main=read('main.cjs'),preload=read('preload.cjs');
 for(const id of ['mainSite','mainProduct','mainSource','mainLoop','mainPrev','mainNext','mainLatest',
  'mainAnalysisToggle','mainWarningToggle','studioFullscreen','studioToggle','studioAlerts',
  'mainDraw','mainFinish','mainPlaces','mainVelocityPick','mainVelocityCalculate'])
  assert.match(html,new RegExp('id="'+id+'"'));
 assert.match(html,/src="studio-ui\.js"/);
 assert.match(css,/body\.studioMode \.shell/);
 assert.match(css,/body\.studioMode\.studioAlertsVisible \.rightPanel/);
 assert.match(css,/body\.studioMode \.mainRadarStudio/);
 assert.match(main,/if\(type==='map'\)win\.maximize\(\)/);
 assert.match(main,/views\.get\('map'\)===owner/);
 assert.match(main,/newswire:exit-window-fullscreen/);
 assert.match(preload,/exitFullscreen/);
 const pkg=require('./package.json');assert.equal(pkg.version,'0.8.5');
 assert.ok(pkg.build.files.includes('studio-ui.js'));
 assert.match(pkg.build.win.artifactName,/0\.8\.5/);
});
