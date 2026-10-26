'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'newswire-deck-test-'));
const original=os.homedir;os.homedir=()=>tmp;
const B=require('./deck-bridge.cjs');
os.homedir=original;
test.after(()=>fs.rmSync(tmp,{recursive:true,force:true}));
test('only approved actions are written and consumed once',()=>{
 assert.throws(()=>B.queueCommand('anything-else'),/Unknown/);
 B.queueCommand('home');B.queueCommand('voice');
 const actions=[];assert.equal(B.consume(a=>actions.push(a)),2);
 assert.deepEqual(actions.sort(),['home','voice']);
 assert.equal(B.consume(()=>{}),0);
});
test('discard old commands without invoking actions',()=>{
 fs.mkdirSync(B.DIR,{recursive:true});
 fs.writeFileSync(path.join(B.DIR,'cmd-aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.json'),
  JSON.stringify({action:'home',at:Date.now()-120000}));
 assert.equal(B.consume(()=>{throw Error('Expired command executed')}),0);
});
test('status excludes local coordinates and publishes feed freshness',()=>{
 const s=B.publishStatus({warningCount:4,localTornado:true,feedOk:true,lat:123,lon:45,
  lastChecked:Date.now(),voice:false});
 assert.equal(s.warningCount,4);assert.equal(s.localTornado,true);
 assert.equal(Object.hasOwn(s,'lat'),false);assert.equal(Object.hasOwn(s,'lon'),false);
 const disk=JSON.parse(fs.readFileSync(path.join(B.DIR,'state.json'),'utf8'));
 assert.equal(disk.warningCount,4);
});
test('plugin declares 32-key compatible keypad actions',()=>{
 const base=path.join(__dirname,'../newswire-streamdeck/com.newswire.commandcenter.sdPlugin');
 const manifest=JSON.parse(fs.readFileSync(path.join(base,'manifest.json'),'utf8'));
 assert.equal(manifest.Nodejs.Version,'24');
 assert.ok(manifest.Actions.length>=15);
 for(const a of manifest.Actions){assert.deepEqual(a.Controllers,['Keypad']);assert.match(a.UUID,/^com\.newswire\.commandcenter\./);}
 for(const name of ['plugin.png','plugin@2x.png','key.svg','key@2x.svg'])
  assert.ok(fs.existsSync(path.join(base,'imgs',name)),name);
});
