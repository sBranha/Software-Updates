'use strict';
const {app,BrowserWindow,shell,session}=require('electron');
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
  callback(false);
 });
 launch();app.on('activate',()=>{if(BrowserWindow.getAllWindows().length===0)launch()});
});
app.on('window-all-closed',()=>{if(process.platform!=='darwin')app.quit()});
