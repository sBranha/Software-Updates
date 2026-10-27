'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const V=require('./velocity-couplet.js');
test('rotation operator worksheet computes accurate signed velocity difference, shear and distance',()=>{
 const a={lat:34.93,lng:-86.08},b={lat:34.94,lng:-86.08};
 const r=V.analyze(a,b,-40,60);
 assert.equal(r.velocityDifferenceMph,100);
 assert.equal(r.inferredHalfDifferenceMph,50);
 assert.ok(r.distanceKm>1.10&&r.distanceKm<1.12);
 assert.ok(r.radialShearPerSecond>0.039&&r.radialShearPerSecond<0.041);
 assert.match(r.warning,/NOT automatic rotation detection/);
});
test('reject missing or invented gate values and invalid geometry',()=>{
 const a={lat:34.9,lng:-86},b={lat:34.91,lng:-86};
 assert.throws(()=>V.analyze(a,b,'',50),/Enter two/);
 assert.throws(()=>V.analyze(a,b,-500,50),/Enter two/);
 assert.throws(()=>V.analyze(a,a,-10,40),/0.1/);
 assert.throws(()=>V.analyze({lat:200,lng:4},b,-10,20),/valid/);
});
test('each independent storm window contains worksheet and clearly identifies manual inputs',()=>{
 const html=fs.readFileSync(path.join(__dirname,'storm.html'),'utf8');
 for(const id of ['rotationPick','rotationCalc','rotationClear','rotationA','rotationB','rotationResult'])
  assert.match(html,new RegExp('id="'+id+'"'));
 assert.match(html,/velocity-couplet\.js/);
 assert.match(html,/rotation-tool\.js/);
 const controller=fs.readFileSync(path.join(__dirname,'rotation-tool.js'),'utf8');
 new Function(controller);
 assert.match(controller,/nwcc-manual-velocity-'\\+slot/);
 assert.match(controller,/V\.analyze/);
 assert.match(controller,/NOT automatic rotation|not an automatic rotation/i);
 assert.match(fs.readFileSync(path.join(__dirname,'polygon-ui.js'),'utf8'),/NWVelocityCancel/);
 const pkg=require('./package.json');
 assert.ok(pkg.build.files.includes('rotation-tool.js'));
 assert.ok(pkg.build.files.includes('velocity-couplet.js'));
});
