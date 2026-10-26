'use strict';
const fs = require('node:fs');
const path = require('node:path');
const TYPES = Object.freeze(['map','local','warnings']);
function validType(type) { return TYPES.includes(type); }
function sortedDisplays(displays) {
  return [...displays].sort((a,b)=>a.workArea.x-b.workArea.x || a.workArea.y-b.workArea.y);
}
function clampBounds(value,displays,fallback={width:1100,height:760}) {
  const all=sortedDisplays(displays);
  const display=all.find(d=> {
    const b=d.workArea;
    return value && Number.isFinite(value.x) && Number.isFinite(value.y) &&
      value.x>=b.x-100 && value.x<b.x+b.width-100 &&
      value.y>=b.y-100 && value.y<b.y+b.height-80;
  })||all[0];
  if(!display) return {width:fallback.width,height:fallback.height};
  const a=display.workArea;
  const width=Math.max(490,Math.min(Number.isFinite(value?.width)?value.width:fallback.width,a.width));
  const height=Math.max(400,Math.min(Number.isFinite(value?.height)?value.height:fallback.height,a.height));
  return {x:Math.round(Math.max(a.x,Math.min(Number.isFinite(value?.x)?value.x:a.x+30,a.x+a.width-width))),
          y:Math.round(Math.max(a.y,Math.min(Number.isFinite(value?.y)?value.y:a.y+30,a.y+a.height-height))),
          width:Math.min(Math.round(width),a.width),height:Math.min(Math.round(height),a.height)};
}
function readLayout(filename) {
  try {
    const s=fs.statSync(filename);if(s.size>8192)return {};
    const obj=JSON.parse(fs.readFileSync(filename,'utf8'));
    return obj && typeof obj==='object' && !Array.isArray(obj)?obj:{};
  } catch (_) { return {}; }
}
function saveLayout(filename,layout) {
  fs.mkdirSync(path.dirname(filename),{recursive:true});
  const tmp=filename+'.tmp';
  fs.writeFileSync(tmp,JSON.stringify(layout,null,2),{mode:0o600});
  fs.renameSync(tmp,filename);
}
module.exports={TYPES,validType,sortedDisplays,clampBounds,readLayout,saveLayout};
