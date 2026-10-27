'use strict';
const {app,BrowserWindow,shell,session,dialog,ipcMain,screen,Menu,net}=require('electron');
const path=require('node:path');
const {createBridge}=require('./deck-bridge.cjs');
const W=require('./window-layout.cjs');
const T=require('./tracking-engine.js');
const {lookupCommunities}=require('./communities-service.cjs');
const SAFE_LINKS=new Set(['www.weather.gov','radar.weather.gov','api.weather.gov']);
const views=new Map();
const pageNames={map:'desktop-concept3.html',local:'local.html',warnings:'warnings.html','storm-1':'storm.html','storm-2':'storm.html','storm-3':'storm.html','storm-4':'storm.html'};
const tracked={1:null,2:null,3:null,4:null};
let bridge,layoutFile,layout={},latest=null;
let saveTimer=null;
function external(url){
 try{
  const u=new URL(url);
  if(u.protocol!=='https:')return;
  if(SAFE_LINKS.has(u.hostname)){shell.openExternal(u.toString());return}
  // Google Street View outbound links only; no arbitrary external Google URLs.
  if(u.hostname==='www.google.com'&&u.pathname==='/maps/@'&&u.searchParams.get('api')==='1'){
   const action=u.searchParams.get('map_action');
   const p=(action==='pano'?u.searchParams.get('viewpoint'):
     action==='map'?u.searchParams.get('center'):null)||'';
   const coords=p.split(',').map(Number);
   const ok=coords.length===2&&Number.isFinite(coords[0])&&Number.isFinite(coords[1])&&
     Math.abs(coords[0])<=90&&Math.abs(coords[1])<=180;
   if(!ok)return;
   if(action==='pano'){shell.openExternal(u.toString());return}
   if(action==='map'){
    const zoom=Number(u.searchParams.get('zoom'));
    const style=u.searchParams.get('basemap');
    if(Number.isInteger(zoom)&&zoom>=0&&zoom<=21&&
       ['roadmap','satellite','terrain'].includes(style))shell.openExternal(u.toString());
   }
  }
 }catch(_){}
}
function isTrusted(sender){return [...views.values()].some(win=>!win.isDestroyed()&&win.webContents===sender)}
function savePositions(){
 if(!layoutFile)return;
 try{W.saveLayout(layoutFile,layout)}catch(e){console.error('Failed to save window layout:',e.message)}
}
function remember(type,win){
 if(win.isDestroyed()||win.isMaximized()||win.isMinimized())return;
 layout[type]=win.getBounds();
 clearTimeout(saveTimer);saveTimer=setTimeout(savePositions,550);
}
function newWindow(type,options={}){
 if(!W.validType(type))return null;
 const existing=views.get(type);
 if(existing&&!existing.isDestroyed()){
  if(existing.isMinimized())existing.restore();
  existing.show();existing.focus();return existing;
 }
 const work=screen.getPrimaryDisplay().workArea;
 const defaults=type==='map'?{width:1480,height:900}:type==='local'?{width:780,height:740}:type==='warnings'?{width:920,height:800}:{width:1000,height:760};
 const initial=options.bounds||layout[type]||{x:work.x+55,y:work.y+50,...defaults};
 const bounds=W.clampBounds(initial,screen.getAllDisplays(),defaults);
 const win=new BrowserWindow({
  title:type==='map'?'NewsWire — National Command Center':type==='local'?'NewsWire — Local Dashboard':type==='warnings'?'NewsWire — Active Warnings':`NewsWire — Storm Tracker ${type.slice(-1)}`,
  ...bounds,minWidth:490,minHeight:400,show:false,autoHideMenuBar:true,
  backgroundColor:'#06111d',
  webPreferences:{nodeIntegration:false,contextIsolation:true,sandbox:true,webSecurity:true,
   backgroundThrottling:false,preload:path.join(__dirname,'preload.cjs')}
 });
 views.set(type,win);
 win.once('ready-to-show',()=>{if(!win.isDestroyed()){win.show();if(latest&&type!=='map')win.webContents.send('newswire:workspace-state',latest)}});
 win.on('move',()=>remember(type,win));
 win.on('resize',()=>remember(type,win));
 win.on('close',()=>{remember(type,win);savePositions()});
 win.on('closed',()=>views.delete(type));
 win.webContents.setWindowOpenHandler(({url})=>{external(url);return {action:'deny'}});
 win.webContents.on('will-navigate',(e,url)=>{
  if(url!==win.webContents.getURL()){e.preventDefault();external(url)}
 });
 if(type.startsWith('storm-')){
  const slot=Number(type.slice(-1));
  win.webContents.on('did-finish-load',()=>sendTracked(slot));
  win.loadFile(path.join(__dirname,pageNames[type]),{query:{slot:String(slot)}});
 }else win.loadFile(path.join(__dirname,pageNames[type]));
 return win;
}
function sendTracked(slot){
 const win=views.get('storm-'+slot);
 if(!win||win.isDestroyed()||win.webContents.isLoading())return;
 // Feed errors and disappeared warnings are not shown as live tracked storms.
 const found=tracked[slot]?.manual===true || (tracked[slot]&&latest?.warnings?.some(a=>a.id===tracked[slot].id));
 win.webContents.send('newswire:tracked-warning',slot,tracked[slot],Boolean(tracked[slot]?.manual||latest?.feedOk&&found));
}
function setDisplay(type,index){
 const win=newWindow(type);
 const list=W.sortedDisplays(screen.getAllDisplays());
 const d=list[index-1];
 if(!win||!d)return;
 const area=d.workArea;
 const w=Math.min(area.width,type==='map'?1480:850),h=Math.min(area.height,type==='map'?920:800);
 win.setBounds(W.clampBounds({x:area.x+Math.round((area.width-w)/2),y:area.y+Math.round((area.height-h)/2),width:w,height:h},[d]));
 win.show();win.focus();
}
function arrange(){
 const displays=W.sortedDisplays(screen.getAllDisplays());
 if(!displays.length)return;
 const order=['local','map','warnings'];
 order.forEach((type,i)=>{
  const d=displays[Math.min(i,displays.length-1)],a=d.workArea;
  const allSingle=displays.length===1;
  const w=allSingle?Math.round(a.width/3):(type==='map'?Math.min(a.width,1500):Math.min(a.width,860));
  const h=allSingle?a.height-85:Math.min(a.height-50,880);
  const x=allSingle?a.x+Math.round(a.width*i/3):a.x+Math.round((a.width-w)/2);
  const y=allSingle?a.y+45:a.y+30;
  const win=newWindow(type);
  if(win){win.setBounds(W.clampBounds({x,y,width:w,height:h},[d]));win.show()}
 });
 views.get('map')?.focus();
}
function deliver(action){
 if(/^display-[123]$/.test(action)){setDisplay('map',Number(action.at(-1)));return}
 if(action==='nws-radar'){external('https://radar.weather.gov/');return}
 if(action==='workspace-3'){arrange();return}
 if(['storm-1','storm-2','storm-3','storm-4'].includes(action)){newWindow(action);return}
 const win=views.get('map');
 if(!win||win.isDestroyed())return;
 win.webContents.send('newswire:deck-action',action);
}
function menu(){
 const template=[
  {label:'NewsWire',submenu:[{role:'about'},{type:'separator'},{role:'quit'}]},
  {label:'Windows',submenu:[
   {label:'National Command Center',accelerator:'CmdOrCtrl+1',click:()=>newWindow('map')},
   {label:'Local Dashboard',accelerator:'CmdOrCtrl+2',click:()=>newWindow('local')},
   {label:'Active Warnings',accelerator:'CmdOrCtrl+3',click:()=>newWindow('warnings')},
   {label:'Storm Tracker 1',accelerator:'CmdOrCtrl+4',click:()=>newWindow('storm-1')},
   {label:'Storm Tracker 2',accelerator:'CmdOrCtrl+5',click:()=>newWindow('storm-2')},
   {label:'Storm Tracker 3',accelerator:'CmdOrCtrl+6',click:()=>newWindow('storm-3')},
   {label:'Storm Tracker 4',accelerator:'CmdOrCtrl+7',click:()=>newWindow('storm-4')},
   {type:'separator'},{label:'Arrange on All Monitors',accelerator:'CmdOrCtrl+Shift+M',click:arrange}
  ]},
  {label:'View',submenu:[{role:'reload'},{role:'togglefullscreen'},{role:'toggleDevTools'}]}
 ];
 Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}
app.whenReady().then(()=>{
 layoutFile=path.join(app.getPath('userData'),'newswire-window-layout.json');
 layout=W.readLayout(layoutFile);
 ipcMain.on('newswire:deck-state',(event,s)=>{
  if(!isTrusted(event.sender))return;
  try{bridge?.publish(s)}catch(error){console.error('Stream Deck status:',error.message)}
 });
 ipcMain.on('newswire:open-panel',(event,type)=>{
  if(isTrusted(event.sender)&&W.validType(type))newWindow(type);
 });
 ipcMain.on('newswire:arrange-panels',event=>{
  if(isTrusted(event.sender))arrange();
 });
 ipcMain.on('newswire:toggle-window-fullscreen',event=>{
  if(!isTrusted(event.sender))return;
  const owner=BrowserWindow.fromWebContents(event.sender);
  if(!owner||owner.isDestroyed()||![1,2,3,4].some(slot=>views.get('storm-'+slot)===owner))return;
  owner.setFullScreen(!owner.isFullScreen());
 });
 ipcMain.on('newswire:workspace-state',(event,s)=>{
  if(!isTrusted(event.sender)||views.get('map')?.webContents!==event.sender||!s||typeof s!=='object')return;
  // Send bounded display data only; never broadcast exact GPS coordinates.
  latest={
   feedOk:s.feedOk===true,
   warningCount:Number.isSafeInteger(s.warningCount)?Math.max(0,s.warningCount):0,
   localStatus:String(s.localStatus||'Local warning status unknown').slice(0,1200),
   location:String(s.location||'Select a ZIP or GPS').slice(0,120),
   checked:String(s.checked||'No successful check').slice(0,180),
   warnings:Array.isArray(s.warnings)?s.warnings.slice(0,300).map(a=>({
    id:String(a.id||'').slice(0,350),area:String(a.area||'').slice(0,320),
    expires:String(a.expires||'').slice(0,80),headline:String(a.headline||'').slice(0,450),
    instruction:String(a.instruction||'').slice(0,1000),url:String(a.url||'').slice(0,350)
   })):[]
  };
  for(const [type,win] of views)if(type!=='map'&&!win.isDestroyed())
   win.webContents.send('newswire:workspace-state',latest);
  for(const slot of [1,2,3,4])sendTracked(slot);
 });
 ipcMain.on('newswire:request-track',(event,slot,id)=>{
  if(!isTrusted(event.sender)||![1,2,3,4].includes(slot)||typeof id!=='string'||id.length>350)return;
  if(!latest?.feedOk||!latest.warnings.some(a=>a.id===id))return;
  const map=newWindow('map');
  if(!map)return;
  const send=()=>map.webContents.send('newswire:workspace-track-request',slot,id);
  if(map.webContents.isLoading())map.webContents.once('did-finish-load',send);else send();
 });
 ipcMain.handle('newswire:communities-lookup',async(event,points,expand=false)=>{
  const owner=BrowserWindow.fromWebContents(event.sender);
  if(!owner||owner.isDestroyed()||!isTrusted(event.sender)||!owner.webContents.getURL().includes('/storm.html'))
   return {ok:false,error:'Community lookup permitted only from an open storm tracking window.'};
  try{
   const result=await lookupCommunities(points,{expand:expand===true,fetchImpl:(url,opts)=>net.fetch(url,opts)});
   return {ok:true,...result};
  }catch(error){
   console.error('NewsWire community lookup:',String(error.message||error).slice(0,450));
   return {ok:false,error:String(error?.message||'Community lookup failed').slice(0,600)};
  }
 });
 ipcMain.on('newswire:track-warning',(event,slot,raw)=>{
  if(event.sender!==views.get('map')?.webContents||![1,2,3,4].includes(slot))return;
  const item=T.safeWarning(raw);
  if(!item||(!item.manual&&(!latest?.feedOk||!latest.warnings.some(x=>x.id===item.id)||T.hasExpired(item))))return;
  tracked[slot]=item;
  newWindow('storm-'+slot);
  sendTracked(slot);
 });
 ipcMain.on('newswire:track-updates',(event,items)=>{
  if(event.sender!==views.get('map')?.webContents||!Array.isArray(items))return;
  for(const x of items.slice(0,4)){
   if(!x||![1,2,3,4].includes(x.slot)||!tracked[x.slot])continue;
   const item=T.safeWarning(x.item);
   if(item&&item.id===tracked[x.slot].id)tracked[x.slot]=item;
  }
  for(const slot of [1,2,3,4])sendTracked(slot);
 });
 ipcMain.on('newswire:focus-warning',(event,id)=>{
  if(!isTrusted(event.sender)||typeof id!=='string'||id.length>350)return;
  const win=newWindow('map');
  if(!win)return;
  const send=()=>win.webContents.send('newswire:workspace-focus',id);
  if(win.webContents.isLoading())win.webContents.once('did-finish-load',send);else send();
 });
 ipcMain.on('newswire:set-zip',(event,zip)=>{
  if(!isTrusted(event.sender)||!/^\d{5}$/.test(String(zip)))return;
  const map=newWindow('map');
  if(!map)return;
  const send=()=>map.webContents.send('newswire:workspace-zip',zip);
  if(map.webContents.isLoading())map.webContents.once('did-finish-load',send);else send();
 });
 bridge=createBridge(deliver);
 session.defaultSession.setPermissionRequestHandler((webContents,permission,callback)=>{
  if(permission!=='geolocation'||!isTrusted(webContents)||!webContents.getURL().startsWith('file://')){callback(false);return}
  const owner=BrowserWindow.fromWebContents(webContents);
  dialog.showMessageBox(owner,{type:'question',title:'NewsWire location permission',
   message:"Use this computer's location to check nearby NWS warnings?",
   detail:'Location is requested only when you press Use my GPS. You can use a ZIP instead.',
   buttons:['Deny','Allow once'],defaultId:0,cancelId:0,noLink:true
  }).then(r=>callback(r.response===1)).catch(()=>callback(false));
 });
 menu();newWindow('map');
 app.on('activate',()=>{if(BrowserWindow.getAllWindows().length===0)newWindow('map')});
});
app.on('window-all-closed',()=>{if(process.platform!=='darwin')app.quit()});
app.on('before-quit',()=>{clearTimeout(saveTimer);savePositions();bridge?.close()});
