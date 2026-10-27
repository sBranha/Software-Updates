'use strict';
/*
 * NewsWire original experimental automatic reflectivity echo tracker.
 * Reads real IEM RIDGE Level III N0B archived PNG radar tiles at observed scan times.
 * Detects coherent colored reflectivity regions and associates their centroids across scans.
 *
 * This is NOT calibrated dBZ analysis, a NEXRAD Level II decoder, tornado detection,
 * mesocyclone detection, an official NWS storm track, or a reliable arrival forecast.
 * Blank/unavailable tiles MUST NEVER mean "all clear".
 */
const {PNG}=require('pngjs');
const Sites=require('../site-radar.js');
const Scans=require('./scan-service.cjs');
const Motion=require('../storm-motion.js');

const Z=8, TILE=256, STRIDE=4, TILES=3, GRID=TILES*TILE/STRIDE;
const MAX_BYTES=1024*1024, MAX_ECHOES=16, MIN_PIXELS=7;
const nowIso=t=>new Date(t).toISOString();
function validSite(site){if(!Sites.siteFor(site))throw Error('Unsupported NEXRAD site')}
function xyz(lat,lng,z=Z){
 if(!Number.isFinite(lat)||!Number.isFinite(lng)||Math.abs(lat)>85||Math.abs(lng)>180)throw Error('Invalid radar coordinates');
 const n=2**z, phi=lat*Math.PI/180;
 return {x:(lng+180)/360*n,y:(1-Math.asinh(Math.tan(phi))/Math.PI)/2*n};
}
function ll(x,y,z=Z){
 const n=2**z;
 return {lat:Math.atan(Math.sinh(Math.PI*(1-2*y/n)))*180/Math.PI,lng:x/n*360-180};
}
function tilePlan(site,z=Z){
 validSite(site);const radar=Sites.siteFor(site),c=xyz(radar.lat,radar.lng,z);
 const west=Math.floor(c.x)-1,north=Math.floor(c.y)-1,n=2**z;
 const tiles=[];
 for(let row=0;row<TILES;row++)for(let col=0;col<TILES;col++){
  const x=west+col,y=north+row;
  if(x>=0&&y>=0&&x<n&&y<n)tiles.push({x,y,row,col});
 }
 return {west,north,z,tiles};
}
function frameUrl(site,time,tile,z=Z){
 validSite(site);
 if(!/^\d{4}-\d\d-\d\dT\d\d:\d\dZ$/.test(time)||!Number.isFinite(Date.parse(time)))throw Error('Invalid observed UTC scan');
 if(!Number.isInteger(tile?.x)||!Number.isInteger(tile?.y)||tile.x<0||tile.y<0||tile.x>=2**z||tile.y>=2**z)throw Error('Invalid tile coordinates');
 const stamp=time.replace(/[-:TZ]/g,'');
 return 'https://mesonet.agron.iastate.edu/c/tile.py/1.0.0/ridge::'+site.slice(1)+'-N0B-'+stamp+'/'+z+'/'+tile.x+'/'+tile.y+'.png';
}
function coloredReflectivity(r,g,b,a){
 if(a<112)return false;const hi=Math.max(r,g,b),lo=Math.min(r,g,b);
 // Most radar echo pigments are distinctly colored. This intentionally misses weak gray echoes.
 return hi>=65&&hi-lo>=48&&((hi-lo)/hi)>=0.32;
}
function rasterize(tiles,plan){
 const mask=new Uint8Array(GRID*GRID);
 let validTiles=0;
 for(const item of tiles){
  if(!item?.rgba||item.width!==TILE||item.height!==TILE||item.rgba.length!==4*TILE*TILE)continue;
  validTiles++;
  const data=item.rgba;
  for(let yy=0;yy<TILE;yy+=STRIDE)for(let xx=0;xx<TILE;xx+=STRIDE){
   const src=(yy*TILE+xx)*4,gx=item.tile.col*TILE/STRIDE+xx/STRIDE,
     gy=item.tile.row*TILE/STRIDE+yy/STRIDE,i=gy*GRID+gx;
   if(coloredReflectivity(data[src],data[src+1],data[src+2],data[src+3]))mask[i]=1;
  }
 }
 return {mask,validTiles};
}
function components(raster,plan){
 const {mask}=raster;if(mask?.length!==GRID*GRID)throw Error('Invalid sampled reflectivity mask');
 const visited=new Uint8Array(mask.length),result=[],queue=new Int32Array(mask.length);
 for(let i=0;i<mask.length;i++){
  if(!mask[i]||visited[i])continue;
  let size=0,sumX=0,sumY=0,start=0,end=1;queue[0]=i;visited[i]=1;
  while(start<end){
   const j=queue[start++],x=j%GRID,y=Math.floor(j/GRID);
   size++;sumX+=x;sumY+=y;
   for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++){
    if(dx===0&&dy===0)continue;const xx=x+dx,yy=y+dy;
    if(xx<0||xx>=GRID||yy<0||yy>=GRID)continue;
    const k=yy*GRID+xx;
    if(mask[k]&&!visited[k]){visited[k]=1;queue[end++]=k}
   }
  }
  if(size<MIN_PIXELS)continue;
  const px=(sumX/size*STRIDE+STRIDE/2),py=(sumY/size*STRIDE+STRIDE/2);
  const center=ll(plan.west+px/TILE,plan.north+py/TILE,plan.z);
  const pixelKm=156.54303392*Math.cos(center.lat*Math.PI/180)/(2**plan.z)*STRIDE;
  const areaSqKm=size*pixelKm*pixelKm;
  result.push({lat:+center.lat.toFixed(5),lng:+center.lng.toFixed(5),
   sampleCount:size,approxAreaSqKm:+areaSqKm.toFixed(1)});
 }
 result.sort((a,b)=>b.sampleCount-a.sampleCount);
 return result.slice(0,MAX_ECHOES);
}
function matchFrames(frames,now=Date.now()){
 if(!Array.isArray(frames)||frames.length<2)return {tracks:[],unmatched:[]};
 const ordered=frames.slice().sort((a,b)=>Date.parse(a.time)-Date.parse(b.time));
 const current=ordered.at(-1),previous=ordered.at(-2);
 const dt=(Date.parse(current.time)-Date.parse(previous.time))/60000;
 if(!Number.isFinite(dt)||dt<1||dt>35)throw Error('Archived scans are not a usable pair (1–35 minutes apart)');
 const previousEchoes=previous.echoes||[],currentEchoes=current.echoes||[];
 const all=[];
 for(let i=0;i<currentEchoes.length;i++)for(let j=0;j<previousEchoes.length;j++){
  const e=currentEchoes[i],p=previousEchoes[j],mi=Motion.miles(e,p);
  const mph=mi/dt*60;
  if(mi>=0.3&&mph<=110)all.push({i,j,mi,mph});
 }
 all.sort((a,b)=>a.mi-b.mi);
 const assignedA=new Set(),assignedB=new Set(),tracks=[];
 for(const candidate of all){
  if(assignedA.has(candidate.i)||assignedB.has(candidate.j))continue;
  const p=previousEchoes[candidate.j],q=currentEchoes[candidate.i];
  try{
   const track=Motion.observedPair({...p,time:previous.time,site:current.site,product:'N0B'},
    {...q,time:current.time,site:current.site,product:'N0B'},now);
   assignedA.add(candidate.i);assignedB.add(candidate.j);
   tracks.push({id:current.site+'-'+candidate.i,lat:q.lat,lng:q.lng,
    approxAreaSqKm:q.approxAreaSqKm,first:{lat:p.lat,lng:p.lng,time:previous.time},
    last:{lat:q.lat,lng:q.lng,time:current.time},mph:track.mph,
    bearingDegrees:track.bearingDegrees,distanceMiles:track.distanceMiles,
    future:track.forecast,ageMinutes:track.staleMinutes,kind:'EXPERIMENTAL_RADAR_IMAGE_ECHO'});
  }catch(_){}
 }
 return {tracks:tracks.slice(0,12),unmatched:currentEchoes.filter((_,i)=>!assignedA.has(i)).slice(0,12)};
}
async function readTile(url,{fetchImpl=fetch,timeoutMs=10000}={}){
 const ac=new AbortController(),timer=setTimeout(()=>ac.abort(),timeoutMs);
 try{
  const resp=await fetchImpl(url,{method:'GET',redirect:'error',signal:ac.signal,
   headers:{'Accept':'image/png','User-Agent':'NewsWire-Command-Center/0.8.5 (radar observation)'}});
  if(!resp.ok)throw Error('RADAR tile HTTP '+resp.status);
  const length=Number(resp.headers?.get?.('content-length')||0);
  if(length>MAX_BYTES)throw Error('Oversized RADAR tile');
  const buffer=Buffer.from(await resp.arrayBuffer());
  if(buffer.length>MAX_BYTES||buffer.length<16||buffer.subarray(0,8).toString('hex')!=='89504e470d0a1a0a')
   throw Error('Radar source did not return a valid PNG tile');
  const decoded=PNG.sync.read(buffer,{checkCRC:true});
  if(decoded.width!==TILE||decoded.height!==TILE||decoded.data.length!==TILE*TILE*4)throw Error('Unsupported tile dimensions');
  return {width:decoded.width,height:decoded.height,rgba:decoded.data};
 }finally{clearTimeout(timer)}
}
async function mapLimit(items,limit,fn){
 let i=0;
 const results=new Array(items.length);
 await Promise.all(Array.from({length:Math.min(items.length,limit)},async()=>{
  while(i<items.length){const k=i++;try{results[k]=await fn(items[k])}catch(error){results[k]={error:String(error.message||error).slice(0,100)}}}
 }));
 return results;
}
async function frame(site,time,{fetchImpl=fetch}={}){
 const plan=tilePlan(site);
 const results=await mapLimit(plan.tiles,5,async tile=>{
  const png=await readTile(frameUrl(site,time,tile,plan.z),{fetchImpl});
  return {...png,tile};
 });
 const {mask,validTiles}=rasterize(results,plan);
 // Missing imagery is not dry air; incomplete scans must not be presented as authoritative.
 if(validTiles<plan.tiles.length)throw Error('Archived radar imagery incomplete ('+validTiles+'/'+plan.tiles.length+' tiles)');
 return {site,time,echoes:components({mask},plan),tiles:validTiles};
}
async function scan(site,{fetchImpl=fetch,now=Date.now(),scansOverride}={}){
 validSite(site);
 const meta=scansOverride||await Scans.fetchScans(site,'N0B',{fetchImpl,now});
 const selected=(meta.scans||[]).filter(t=>Number.isFinite(Date.parse(t))&&
  Date.parse(t)<=now+2*60000&&Date.parse(t)>=now-60*60000).slice(-2);
 if(selected.length<2)return {ok:false,error:'Two recent actual N0B scan times are required; no automatic track available'};
 const f=await Promise.all(selected.map(t=>frame(site,t,{fetchImpl})));
 const matched=matchFrames(f,now);
 const latest=f.at(-1),ageMinutes=Math.max(0,Math.floor((now-Date.parse(latest.time))/60000));
 return {ok:true,site,product:'N0B',source:'NOAA NEXRAD Level III N0B via Iowa Environmental Mesonet RIDGE',
  firstScan:f[0].time,latestScan:latest.time,ageMinutes,
  stale:ageMinutes>20,examinedTiles:f.reduce((n,q)=>n+q.tiles,0),
  echoesLatest:latest.echoes.length,trackedCount:matched.tracks.length,
  tracks:matched.tracks,unmatched:matched.unmatched,
  limitation:'EXPERIMENTAL colored-reflectivity echo tracking. Radar image pixels, NOT calibrated dBZ, NWS storm IDs, tornado detection, warning replacement, or reliable arrival forecasts.'};
}
module.exports={Z,TILE,GRID,xyz,ll,tilePlan,frameUrl,coloredReflectivity,rasterize,components,matchFrames,readTile,mapLimit,frame,scan};