'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const {PNG}=require('pngjs'),A=require('./auto-echo.cjs');
const center=A.xyz(34.930556,-86.083611);
const plan=A.tilePlan('KHTX');
function syntheticBlob(tile,lat,lng){
 const p=A.xyz(lat,lng);
 const x=Math.round((p.x-tile.x)*256),y=Math.round((p.y-tile.y)*256);
 const data=Buffer.alloc(256*256*4);
 for(let yy=0;yy<256;yy++)for(let xx=0;xx<256;xx++){
  const i=(yy*256+xx)*4;
  if(Math.hypot(xx-x,yy-y)<33){data[i]=15;data[i+1]=210;data[i+2]=40;data[i+3]=240}
 }
 const pimg=new PNG({width:256,height:256});data.copy(pimg.data);
 return PNG.sync.write(pimg);
}
test('tile projection honors KHTX, maps to real longitude and rejects arbitrary site/tiles',()=>{
 const origin=A.ll(center.x,center.y);
 assert.ok(Math.abs(origin.lat-34.930556)<0.00001&&Math.abs(origin.lng+86.083611)<0.00001);
 assert.equal(plan.tiles.length,9);
 assert.match(A.frameUrl('KHTX','2026-10-27T17:25Z',plan.tiles[0]),/ridge::HTX-N0B-202610271725\/8\/\d+\/\d+\.png/);
 assert.throws(()=>A.tilePlan('KZZZ'),/Unsupported/);
 assert.throws(()=>A.frameUrl('KHTX','fake',plan.tiles[0]),/UTC/);
});
test('alpha and color checks ignore transparent and grayscale backgrounds',()=>{
 assert.equal(A.coloredReflectivity(20,180,20,230),true);
 assert.equal(A.coloredReflectivity(20,180,20,0),false);
 assert.equal(A.coloredReflectivity(95,95,95,255),false);
});
test('real decoded PNG tiles yield geographical echo centers and fail closed on missing scans',async()=>{
 const lat=34.93,lng=-86.10;
 const pngMap=new Map();
 for(const tile of plan.tiles){
  const buff=syntheticBlob(tile,lat,lng);
  pngMap.set(tile.x+'/'+tile.y,buff);
 }
 const fetchImpl=async url=>{
  const m=String(url).match(/\/8\/(\d+)\/(\d+)\.png$/);
  const buf=pngMap.get(m[1]+'/'+m[2]);
  return {ok:true,status:200,headers:{get:()=>String(buf.length)},arrayBuffer:async()=>buf};
 };
 const f=await A.frame('KHTX','2026-10-27T17:25Z',{fetchImpl});
 assert.equal(f.tiles,9);assert.ok(f.echoes.length>=1);
 assert.ok(Math.abs(f.echoes[0].lat-lat)<0.08);
 assert.ok(Math.abs(f.echoes[0].lng-lng)<0.08);
 await assert.rejects(()=>A.frame('KHTX','2026-10-27T17:25Z',{fetchImpl:async()=>({ok:false,status:404})}),/incomplete/);
});
test('association tracks real displacement and excludes implausibly fast echo matches',()=>{
 const prev={site:'KHTX',time:'2026-10-27T17:20Z',echoes:[{lat:34.9,lng:-86.20,approxAreaSqKm:25}]};
 const next={site:'KHTX',time:'2026-10-27T17:30Z',echoes:[{lat:34.92,lng:-86.13,approxAreaSqKm:30}]};
 const {tracks,unmatched}=A.matchFrames([prev,next],Date.parse('2026-10-27T17:31Z'));
 assert.equal(tracks.length,1);
 assert.ok(tracks[0].mph>10&&tracks[0].mph<40);
 assert.equal(tracks[0].future.length,3);
 assert.equal(unmatched.length,0);
 const far=A.matchFrames([prev,{...next,echoes:[{lat:36.8,lng:-82.0}]}]);
 assert.equal(far.tracks.length,0);
});
test('scan uses two real timestamps and does not manufacture motion if no echoes',async()=>{
 const blank=PNG.sync.write(new PNG({width:256,height:256}));
 const fetchImpl=async url=>({ok:true,status:200,headers:{get:()=>String(blank.length)},arrayBuffer:async()=>blank});
 const t=Date.parse('2026-10-27T17:31Z');
 const x=await A.scan('KHTX',{fetchImpl,now:t,scansOverride:{scans:['2026-10-27T17:20Z','2026-10-27T17:30Z']}});
 assert.equal(x.ok,true);assert.equal(x.tracks.length,0);assert.equal(x.echoesLatest,0);
 assert.equal(x.examinedTiles,18);assert.equal(x.stale,false);
 assert.match(x.limitation,/EXPERIMENTAL/);
 const missing=await A.scan('KHTX',{fetchImpl,now:t,scansOverride:{scans:[]}});
 assert.equal(missing.ok,false);
});
test('valid application includes operational native echo analysis bridge and packaged dependency',()=>{
 const fs=require('node:fs'),p=require('node:path');
 const root=p.join(__dirname,'..'),pkg=require('../package.json');
 assert.equal(pkg.version,'0.8.6');
 assert.equal(pkg.dependencies.pngjs,'7.0.0');
 for(const path of ['radar-data/auto-echo.cjs','auto-echo-ui.js'])assert.ok(pkg.build.files.includes(path),path);
 const main=fs.readFileSync(p.join(root,'main.cjs'),'utf8'),
   preload=fs.readFileSync(p.join(root,'preload.cjs'),'utf8'),
   html=fs.readFileSync(p.join(root,'desktop-concept3.html'),'utf8');
 assert.match(main,/newswire:auto-echo/);
 assert.match(preload,/requestAutoEcho/);
 assert.match(html,/mainAutoEchoToggle/);
 assert.match(html,/auto-echo-ui\.js/);
});