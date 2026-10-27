'use strict';
/* Build-time only: fetch an immutable, checksum-verified Census-based city snapshot.
   Runtime never needs network access for these places.
   Source: https://github.com/nmajor/us-city-data (compiled from US Census 2025 Gazetteer).
   Dataset is CC0/public-domain equivalent (DATA_LICENSE.md).
*/
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const URL='https://raw.githubusercontent.com/nmajor/us-city-data/5b5d33f9bc6d7cfc491a83854ffada347f8f5a35/data/us-cities.json';
const EXPECTED='c0441d57b07015f2825b79ca6dd31aa41314075cd8c3657292e6237ab19dea1e';
const OUT=path.join(__dirname,'data','census-places-2025.json');
async function prepare({fetchImpl=fetch}={}){
 const response=await fetchImpl(URL,{signal:AbortSignal.timeout(60000)});
 if(!response.ok)throw Error('Cannot download trusted city source: '+response.status);
 const data=Buffer.from(await response.arrayBuffer());
 const hash=crypto.createHash('sha256').update(data).digest('hex');
 if(hash!==EXPECTED)throw Error('City source integrity check failed: '+hash);
 const array=JSON.parse(data.toString('utf8'));
 if(!Array.isArray(array)||array.length<18000)throw Error('Expected a complete national city source');
 const names=new Set(),cities=[];
 for(const city of array){
  const name=String(city.nameShort||city.name||'').trim().slice(0,110);
  const state=String(city.state||'').trim().slice(0,2);
  const lat=Number(city.lat),lon=Number(city.lon);
  if(!name||!/^[A-Z]{2}$/.test(state)||!Number.isFinite(lat)||!Number.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)continue;
  const key=[name,state,lat.toFixed(5),lon.toFixed(5)].join('/');
  if(names.has(key))continue;names.add(key);
  cities.push({name,state,lat:+lat.toFixed(6),lng:+lon.toFixed(6),type:'census_place'});
 }
 if(cities.length<17500)throw Error('Insufficient geocoded place records');
 fs.mkdirSync(path.dirname(OUT),{recursive:true});
 fs.writeFileSync(OUT,JSON.stringify(cities));
 console.log('Prepared '+cities.length+' verified Census-based place coordinates ('+fs.statSync(OUT).size+' bytes).');
 return cities;
}
if(require.main===module)prepare().catch(e=>{console.error(e);process.exitCode=1});
module.exports={prepare,URL,EXPECTED,OUT};
