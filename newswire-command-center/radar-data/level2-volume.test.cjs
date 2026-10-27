'use strict';
const test=require('node:test');const assert=require('node:assert/strict');
const {inspectVolume}=require('./level2-volume.cjs');
test('rejects unrelated and incomplete data',()=>{
 assert.throws(()=>inspectVolume(Buffer.from('junk')));
 assert.throws(()=>inspectVolume(Buffer.alloc(40)));
});
test('inspects header without falsely claiming analysis',()=>{
 const b=Buffer.alloc(40);b.write('AR2V0001',0,'ascii');
 b.writeUInt32BE(20000,12);b.writeUInt32BE(3600000,16);b.write('KHTX',20);
 const result=inspectVolume(b);
 assert.equal(result.station,'KHTX');assert.equal(result.decoded,false);
 assert.match(result.warning,/NOT been analyzed/);
});
