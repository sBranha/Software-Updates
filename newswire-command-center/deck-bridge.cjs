'use strict';
// Offline local-file bridge. No network port, no elevated access and no cloud service.
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),crypto=require('node:crypto');
const DIR=path.join(os.homedir(),'.newswire-command-center','streamdeck');
const COMMANDS=new Set(['warning-latest','warning-next','home','country','resume','auto','voice','satellite','refresh','zoom-in','zoom-out','display-1','display-2','display-3','nws-radar','workspace-3']);
function ensure(){fs.mkdirSync(DIR,{recursive:true,mode:0o700});}
function writeAtomic(dest,obj){
 ensure();
 const tmp=path.join(DIR,'.'+crypto.randomUUID()+'.tmp');
 try{fs.writeFileSync(tmp,JSON.stringify(obj),{encoding:'utf8',mode:0o600,flag:'wx'});fs.renameSync(tmp,dest);}
 finally{try{fs.unlinkSync(tmp)}catch(_){}}
}
function publishStatus(input){
 const s=(input&&typeof input==='object')?input:{};
 const keys=['warningCount','localTornado','localVerified','feedOk','lastChecked','auto','voice','satellite'];
 const out={updatedAt:Date.now()};
 for(const key of keys)if(typeof s[key]==='boolean'||(key==='warningCount'&&Number.isSafeInteger(s[key]))||
   (key==='lastChecked'&&(s[key]===null||Number.isSafeInteger(s[key]))))out[key]=s[key];
 writeAtomic(path.join(DIR,'state.json'),out);return out;
}
function queueCommand(action,at=Date.now()){
 if(!COMMANDS.has(action))throw Error('Unknown Stream Deck command');
 writeAtomic(path.join(DIR,'cmd-'+crypto.randomUUID()+'.json'),{action,at});
}
function consume(onAction,clock=Date.now()){
 ensure();let n=0;
 for(const name of fs.readdirSync(DIR).filter(n=>/^cmd-[a-f0-9-]{36}\.json$/.test(n)).slice(0,64)){
  const filename=path.join(DIR,name);
  try{
   const st=fs.statSync(filename);if(st.size>4096)continue;
   const {action,at}=JSON.parse(fs.readFileSync(filename,'utf8'));
   if(COMMANDS.has(action)&&Number.isSafeInteger(at)&&at<=clock+5000&&clock-at<30000){
    onAction(action);n++;
   }
  }catch(_){}finally{try{fs.unlinkSync(filename)}catch(_){}}
 }
 return n;
}
function createBridge(onAction){
 ensure();
 const timer=setInterval(()=>consume(onAction),300);timer.unref?.();
 return {publish:publishStatus,close:()=>clearInterval(timer)};
}
module.exports={DIR,COMMANDS,publishStatus,queueCommand,consume,createBridge};
