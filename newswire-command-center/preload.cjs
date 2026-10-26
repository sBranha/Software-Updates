'use strict';
const {contextBridge,ipcRenderer}=require('electron');
contextBridge.exposeInMainWorld('NewsWireDesktopBridge',{
 reportState:s=>{if(s&&typeof s==='object')ipcRenderer.send('newswire:deck-state',s);},
 onAction:listener=>{if(typeof listener==='function')ipcRenderer.on('newswire:deck-action',(_e,action)=>{
  if(typeof action==='string')listener(action);
 });}
});
