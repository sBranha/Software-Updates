'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const read=name=>fs.readFileSync(path.join(__dirname,name),'utf8');
test('four storm desks expose separate radar, focus, warning, full screen and layout controls',()=>{
 const html=read('storm.html'),css=read('storm.css');
 for(const id of ['radar','loop','focus','autoFocus','warningOverlay','radarOnly','fullScreen','draw','focusArea'])
  assert.match(html,new RegExp('id="'+id+'"'));
 assert.match(css,/body\.radarOnly \.layout/);
 assert.match(css,/body\.radarOnly aside/);
});
test('storm settings and viewport are keyed per monitor window, not shared globally',()=>{
 const js=read('storm.js');
 assert.match(js,/nwcc-storm-'\+slot\+'-'/);
 for(const setting of ['viewport','auto-focus','warning-outline','radar-only','radar-on','loop-on'])
  assert.ok(js.includes("'"+setting+"'"),'Missing saved setting '+setting);
 assert.match(js,/const slot=Number\(new URLSearchParams\(location\.search\)/);
 assert.match(js,/map\.on\('moveend'/);
 assert.match(js,/if\(changed&&autoFocus\)/);
 assert.match(js,/if\(showWarnings\)/);
});
test('warning reassignment never clears the independent drawn polygon',()=>{
 const js=read('polygon-ui.js');
 assert.match(js,/const STORE='nwcc-drawn-track-'\+slot/);
 assert.doesNotMatch(js,/addEventListener\('newswire:slot-changed'/);
 assert.match(js,/Drawing canceled\. Your previous saved polygon has been restored/);
 assert.match(js,/points=D\.validPolygon\(saved\)\?saved:\[\]/);
});
test('fullscreen IPC is restricted to trusted storm tracking windows',()=>{
 const main=read('main.cjs'),preload=read('preload.cjs');
 assert.match(preload,/toggleFullscreen:\(\)=>ipcRenderer\.send\('newswire:toggle-window-fullscreen'\)/);
 assert.match(main,/ipcMain\.on\('newswire:toggle-window-fullscreen'/);
 assert.match(main,/isTrusted\(event\.sender\)/);
 assert.match(main,/views\.get\('storm-'\+slot\)===owner/);
 assert.match(main,/owner\.setFullScreen\(!owner\.isFullScreen\(\)\)/);
});
test('all changed client and Electron sources parse',()=>{
 for(const name of ['storm.js','polygon-ui.js','main.cjs','preload.cjs'])
  assert.doesNotThrow(()=>new Function(read(name)),name);
});
