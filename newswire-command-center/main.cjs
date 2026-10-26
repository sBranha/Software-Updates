'use strict';
const {app,BrowserWindow,shell,session,dialog}=require('electron');
const path=require('node:path');
const SAFE_LINKS=new Set(['www.weather.gov','radar.weather.gov','api.weather.gov']);
function external(url){
 try{const u=new URL(url);if(u.protocol==='https:'&&SAFE_LINKS.has(u.hostname))shell.openExternal(u.toString());}catch(e){}
}
function launch(){
 const win=new BrowserWindow({
  title:'NewsWire Storm Command Center',width:1520,height:930,minWidth:850,minHeight:630,
  backgroundColor:'#06111d',show:false,autoHideMenuBar:true,
  webPreferences:{nodeIntegration:false,contextIsolation:true,sandbox:true,webSecurity:true}
 });
 win.once('ready-to-show',()=>win.show());
 win.webContents.setWindowOpenHandler(({url})=>{external(url);return {action:'deny'}});
 win.webContents.on('will-navigate',(e,url)=>{
  if(url!==win.webContents.getURL()){e.preventDefault();external(url)}
 });
 win.loadFile(path.join(__dirname,'index.html'));
}
app.whenReady().then(()=>{
 // Do not silently grant device location permissions; ZIP entry remains available.
 session.defaultSession.setPermissionRequestHandler((webContents,permission,callback)=>{
  if(permission!=='geolocation'||!webContents.getURL().startsWith('file://')){callback(false);return;}
  const owner=BrowserWindow.fromWebContents(webContents);
  dialog.showMessageBox(owner,{type:'question',title:'NewsWire location permission',
    message:'Use this computer\'s location to check nearby NWS warnings?',
    detail:'Location is requested only when you press Use my GPS. You can use a ZIP instead.',
    buttons:['Deny','Allow once'],defaultId:0,cancelId:0,noLink:true
  }).then(result=>callback(result.response===1)).catch(()=>callback(false));
 });
 launch();app.on('activate',()=>{if(BrowserWindow.getAllWindows().length===0)launch()});
});
app.on('window-all-closed',()=>{if(process.platform!=='darwin')app.quit()});
