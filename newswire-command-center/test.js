'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const E=require('./engine.js');
const time=Date.parse('2026-10-26T19:00:00Z');
const rect=(left,bottom,right,top)=>[[[left,bottom],[right,bottom],[right,top],[left,top],[left,bottom]]];
const poly=(left,bottom,right,top)=>({type:'Polygon',coordinates:rect(left,bottom,right,top)});
function alert(id,geometry=poly(-87,34,-86,35),overrides={}){
 return {type:'Feature',id,geometry,properties:{event:'Tornado Warning',id,areaDesc:'Example County',
  status:'Actual',sent:'2026-10-26T18:50:00Z',expires:'2026-10-26T19:30:00Z',
  headline:'National Weather Service tornado warning',...overrides}};
}
test('strictly accept current official tornado warning only',()=>{
 assert.equal(E.normalize(alert('a'),time).area,'Example County');
 assert.equal(E.normalize(alert('a',null,{event:'Severe Thunderstorm Warning'}),time),null);
 assert.equal(E.normalize(alert('a',null,{status:'Test'}),time),null);
 assert.equal(E.normalize(alert('a',null,{messageType:'Cancel'}),time),null);
 assert.equal(E.normalize(alert('a',null,{expires:'2026-10-26T18:00:00Z'}),time),null);
 assert.equal(E.normalize(alert('a',null,{effective:'2026-10-26T22:00:00Z'}),time),null);
});
test('valid real geometry checks include polygons, exclude holes, multipart',()=>{
 const g=poly(-87,34,-86,35);assert.ok(E.insideGeometry(g,34.5,-86.5));
 assert.equal(E.insideGeometry(g,34.5,-90),false);
 const withHole={type:'Polygon',coordinates:[...rect(-87,34,-86,35),...rect(-86.6,34.4,-86.4,34.6)]};
 assert.equal(E.insideGeometry(withHole,34.5,-86.5),false);
 const multi={type:'MultiPolygon',coordinates:[rect(-99,37,-98,38),rect(-87,34,-86,35)]};
 assert.ok(E.insideGeometry(multi,34.5,-86.5));
 assert.deepEqual(E.geometryCenter(g),[34.5,-86.5]);
 assert.equal(E.geometryCenter(null),null);
});
test('keep text-only warnings in list but never pretend mapped coordinates',()=>{
 const text=E.normalize(alert('missing',null),time);
 assert.ok(text);assert.equal(text.center,null);
 assert.equal(E.isLocal(text,34.5,-86.5,new Set()),false);
});
test('dedupe, newest first and nearby point warning priority',()=>{
 const list=E.alertsFromResponse({features:[
  alert('old',null,{sent:'2026-10-26T18:30:00Z'}),
  alert('new',poly(-95,38,-94,39),{sent:'2026-10-26T18:59:00Z'}),
  alert('new',poly(-95,38,-94,39),{sent:'2026-10-26T18:59:00Z'})]},time);
 assert.deepEqual(list.map(a=>a.id),['new','old']);
 assert.deepEqual(E.newAlerts(list,new Set(['new'])).map(a=>a.id),['old']);
 assert.deepEqual(E.prioritize(list,34.5,-86.5,new Set(['old'])).map(a=>a.id),['old','new']);
 assert.ok(E.isLocal(list[1],NaN,NaN,new Set(['old'])));
});
test('reject invalid/unsafe official links and network payloads',()=>{
 assert.equal(E.officialUrl({url:'javascript:alert(1)'}),'https://www.weather.gov/alerts');
 assert.equal(E.officialUrl({url:'https://api.weather.gov/alerts/123'}),'https://api.weather.gov/alerts/123');
 assert.throws(()=>E.alertsFromResponse({count:0},time));
});
test('desktop and Android share the exact command center resources',()=>{
 const html=fs.readFileSync(path.join(__dirname,'index.html'),'utf8');
 const script=fs.readFileSync(path.join(__dirname,'app.js'),'utf8');
 const build=fs.readFileSync(path.join(__dirname,'../newswave-android/app/build.gradle'),'utf8');
 const activity=fs.readFileSync(path.join(__dirname,'../newswave-android/app/src/main/java/com/newswave/live/NationwideCommandCenterActivity.java'),'utf8');
 const live=fs.readFileSync(path.join(__dirname,'../newswave-android/app/src/main/java/com/newswave/live/LiveCenterActivity.java'),'utf8');
 assert.match(build,/versionCode 35; versionName '0\.7\.0'/);
 assert.match(build,/newswire-command-center/);
 assert.match(html,/engine\.js/);assert.match(html,/app\.js/);
 assert.match(script,/api\.weather\.gov\/alerts\/active/);
 assert.match(script,/async function nationwideFetch/);
 assert.match(script,/Tornado%20Warning/);
 assert.match(script,/limit=500/);
 assert.match(script,/LOCAL ALERTS TAKE PRIORITY/);
 assert.match(script,/window\.updateNewsWirePosition/);
 assert.match(activity,/newswire:\/\/gps/);assert.match(activity,/android_asset\/index\.html/);
 assert.match(live,/NationwideCommandCenterActivity/);
 new Function(script);
});

test('separate user-draggable Windows/Mac panels are packaged',()=>{
 const pkg=require('./package.json');
 for(const item of ['local.html','warnings.html','panels.js','panels.css','desktop.css','workspace-ui.js','window-layout.cjs'])
  assert.ok(pkg.build.files.includes(item),item);
 const local=fs.readFileSync(path.join(__dirname,'local.html'),'utf8');
 const warnings=fs.readFileSync(path.join(__dirname,'warnings.html'),'utf8');
 const main=fs.readFileSync(path.join(__dirname,'main.cjs'),'utf8');
 assert.match(local,/data-view="local"/);assert.match(warnings,/data-view="warnings"/);
 assert.match(main,/new BrowserWindow/);assert.match(main,/newswire-window-layout.json/);
 assert.match(main,/newswire:workspace-state/);assert.match(main,/function arrange\(/);
});

test('image-three desktop dashboard is the active Electron window and preserves required controls',()=>{
 const main=fs.readFileSync(path.join(__dirname,'main.cjs'),'utf8');
 const html=fs.readFileSync(path.join(__dirname,'desktop-concept3.html'),'utf8');
 const css=fs.readFileSync(path.join(__dirname,'concept3.css'),'utf8');
 const ui=fs.readFileSync(path.join(__dirname,'concept3-ui.js'),'utf8');
 const pkg=require('./package.json');
 assert.match(main,/map:'desktop-concept3.html'/);
 for(const id of ['zip','setZip','myGps','auto','voice','style','refresh','status','map','mapHint',
  'home','country','resume','localStatus','count','checked','list','detail','official',
  'warningBanner','mapFooterState','arrange-workspace'])
  assert.match(html,new RegExp('id="'+id+'"'),id);
 for(const token of ['class="primaryNav"','class="leftPanel"','class="centerPanel"',
  'class="rightPanel"','class="dashboardStrip"','data-open-panel="warnings"',
  'data-open-panel="local"'])
  assert.ok(html.includes(token),token);
 for(const source of ['concept3.css','concept3-ui.js','workspace-ui.js','app.js'])
  assert.ok(html.includes(source),source);
 for(const source of ['desktop-concept3.html','concept3.css','concept3-ui.js'])
  assert.ok(pkg.build.files.includes(source),source);
 assert.match(css,/grid-template-columns:210px/);
 assert.match(html,/VELOCITY NOT INCLUDED/);
 new Function(ui);
});
