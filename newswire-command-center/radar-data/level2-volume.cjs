'use strict';
/** Local NEXRAD Level II volume inspection. Does not claim meteorological detection. */
const fs=require('node:fs');
const zlib=require('node:zlib');
const MAGIC=Buffer.from('AR2V');
function inspectVolume(buffer){
 if(!Buffer.isBuffer(buffer)||buffer.length<24) throw Error('Invalid or incomplete NEXRAD volume');
 const magic=buffer.subarray(0,4).toString('ascii');
 if(magic!=='AR2V'&&magic!=='ARCH') throw Error('Not a recognized NEXRAD Level II archive');
 const station=buffer.subarray(20,24).toString('ascii').trim();
 const julian=buffer.readUInt32BE(12);
 const milliseconds=buffer.readUInt32BE(16);
 if(julian<1||julian>100000||milliseconds>=86400000) throw Error('Invalid volume timestamp');
 const scanTime=new Date((julian-1)*86400000+milliseconds);
 return {format:'NEXRAD Level II',station,scanTime:scanTime.toISOString(),bytes:buffer.length,decoded:false,warning:'Header only: radar moments and rotation have NOT been analyzed'};
}
function inspectFile(path){return inspectVolume(fs.readFileSync(path))}
module.exports={inspectVolume,inspectFile};
