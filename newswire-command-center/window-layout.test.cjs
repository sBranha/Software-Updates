'use strict';
const assert=require('node:assert/strict'),test=require('node:test');
const fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const W=require('./window-layout.cjs');
const monitors=[
 {id:2,workArea:{x:1920,y:0,width:1920,height:1080}},
 {id:1,workArea:{x:0,y:0,width:1920,height:1080}},
 {id:3,workArea:{x:3840,y:-50,width:1920,height:1080}}
];
test('recognizes only permitted detachable views',()=>{
 assert.equal(W.validType('warnings'),true);
 assert.equal(W.validType('../../x'),false);
});
test('reorders real monitors from left to right',()=>{
 assert.deepEqual(W.sortedDisplays(monitors).map(x=>x.id),[1,2,3]);
});
test('restores window on appropriate monitor and keeps onscreen',()=>{
 const result=W.clampBounds({x:2100,y:20,width:1200,height:800},monitors);
 assert.equal(result.x,2100);
 assert.equal(result.y,20);
 assert.equal(result.width,1200);
});
test('clamps unplugged display to available screen',()=>{
 const b=W.clampBounds({x:8100,y:99,width:1400,height:900},[monitors[1]]);
 assert.ok(b.x>=0&&b.x+b.width<=1920);
 assert.ok(b.y>=0&&b.y+b.height<=1080);
});
test('limits oversized restored bounds',()=>{
 const b=W.clampBounds({x:0,y:0,width:8000,height:8000},[monitors[1]]);
 assert.equal(b.width,1920);assert.equal(b.height,1080);
});
test('persists per-window placement',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'nw-layout-'));
 try {const file=path.join(dir,'state.json');W.saveLayout(file,{map:{x:99,width:1250}});
 assert.equal(W.readLayout(file).map.width,1250);
 fs.writeFileSync(file,'not json');assert.deepEqual(W.readLayout(file),{});
 }finally{fs.rmSync(dir,{recursive:true,force:true})}
});
