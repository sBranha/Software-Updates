'use strict';
// Native SDK WebSocket protocol; runs locally under Elgato's Node.js 24 runtime.
const fs=require('node:fs'),path=require('node:path');
const crypto=require('node:crypto');
const os=require('node:os');
const DIR=path.join(os.homedir(),'.newswire-command-center','streamdeck');
const UUID='com.newswire.commandcenter.';
const VALID=new Set(["warning-status","warning-latest","warning-next","home","country","auto","voice","satellite","refresh","resume","zoom-in","zoom-out","display-1","display-2","display-3","nws-radar"]);
const COMMANDS=new Set(["warning-latest","warning-next","home","country","auto","voice","satellite","refresh","resume","zoom-in","zoom-out","display-1","display-2","display-3","nws-radar"]);
const contexts=new Map();
const opt=(k)=>{const i=process.argv.indexOf(k);return i>=0?process.argv[i+1]:undefined};
const port=Number(opt('-port')),registerEvent=opt('-registerEvent'),pluginUUID=opt('-pluginUUID');
if(!Number.isSafeInteger(port)||port<1||port>65535||!registerEvent||!pluginUUID)process.exit(1);
const ws=new WebSocket('ws://127.0.0.1:'+port);
function send(event,context,payload){if(ws.readyState===WebSocket.OPEN)ws.send(JSON.stringify({event,context,payload}));}
function sendIcon(context,urgent){
 const fill=urgent?(Math.floor(Date.now()/1000)%2===0?'#a00020':'#421320'):'#10334a';
 const svg='<svg xmlns="http://www.w3.org/2000/svg" width="144" height="144" viewBox="0 0 144 144"><rect width="144" height="144" rx="20" fill="'+fill+'"/><path d="M16 96L48 41L68 80L88 37L128 96" fill="none" stroke="#aefaff" stroke-width="9" stroke-linejoin="round"/></svg>';
 send('setImage',context,{image:'data:image/svg+xml;base64,'+Buffer.from(svg).toString('base64'),target:0});
}
function status(){
 try{const s=JSON.parse(fs.readFileSync(path.join(DIR,'state.json'),'utf8'));
  if(!Number.isSafeInteger(s.updatedAt)||Math.abs(Date.now()-s.updatedAt)>15000)return null;
  return s;
 }catch(_){return null;}
}
function writeCommand(action){
 if(!COMMANDS.has(action))return false;
 fs.mkdirSync(DIR,{recursive:true,mode:0o700});
 const name='cmd-'+crypto.randomUUID()+'.json',dest=path.join(DIR,name);
 const tmp=path.join(DIR,'.'+crypto.randomUUID()+'.tmp');
 try{fs.writeFileSync(tmp,JSON.stringify({action,at:Date.now()}),{encoding:'utf8',flag:'wx',mode:0o600});
  fs.renameSync(tmp,dest);return true;}
 finally{try{fs.unlinkSync(tmp)}catch(_){}}
}
function repaint(){
 const s=status();
 for(const [context,action] of contexts){
  let title=action.replaceAll('-',' ').toUpperCase();
  if(action==='warning-status'){
   title=!s||!s.feedOk?'NWS\nUNKNOWN':('TORNADO\n'+String(s.warningCount||0));
   if(s&&s.localTornado)title='LOCAL\nTORNADO';
  }else if(action==='auto'||action==='voice'||action==='satellite'){
   title=(action==='auto'?'AUTO FLY':action==='voice'?'VOICE':'SATELLITE')+'\n'+(!s?'?':s[action]?'ON':'OFF');
  }else if(action==='warning-latest')title='LATEST\nWARNING';
  else if(action==='warning-next')title='NEXT\nWARNING';
  else if(action==='country')title='US\nOVERVIEW';
  else if(action==='home')title='MY\nLOCATION';
  else if(action==='refresh')title='REFRESH\nNWS';
  else if(action==='resume')title='RESUME\nAUTO';
  else if(action.startsWith('display-'))title='MONITOR\n'+action.slice(-1);
  else if(action==='nws-radar')title='NWS\nRADAR';
  else if(action==='zoom-in')title='ZOOM\nIN';
  else if(action==='zoom-out')title='ZOOM\nOUT';
  send('setTitle',context,{title,target:0});
  sendIcon(context,!!(s&&s.feedOk&&s.localTornado&&action==='warning-status'));
 }
}
ws.addEventListener('open',()=>{
 ws.send(JSON.stringify({event:registerEvent,uuid:pluginUUID}));
 setInterval(repaint,1500);
});
ws.addEventListener('message',e=>{
 let m;try{m=JSON.parse(String(e.data))}catch(_){return}
 if(m.event==='willAppear'&&typeof m.context==='string'){
  const action=String(m.action||'').replace(UUID,'');
  if(VALID.has(action)){contexts.set(m.context,action);repaint();}
 }else if(m.event==='willDisappear')contexts.delete(m.context);
 else if(m.event==='keyDown'){
  const action=contexts.get(m.context)||String(m.action||'').replace(UUID,'');
  if(action==='warning-status')return;
  try{if(writeCommand(action))send('showOk',m.context,{});}catch(_){send('showAlert',m.context,{});}
 }
});
ws.addEventListener('close',()=>process.exit(0));
ws.addEventListener('error',()=>{});
