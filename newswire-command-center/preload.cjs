'use strict';
const {contextBridge,ipcRenderer}=require('electron');
contextBridge.exposeInMainWorld('NewsWireDesktopBridge',{
 reportState:s=>{if(s&&typeof s==='object')ipcRenderer.send('newswire:deck-state',s)},
 onAction:listener=>{if(typeof listener==='function')ipcRenderer.on('newswire:deck-action',(_e,action)=>{
  if(typeof action==='string')listener(action)
 })},
 openPanel:type=>{if(['map','local','warnings'].includes(type))ipcRenderer.send('newswire:open-panel',type)},
 arrangePanels:()=>ipcRenderer.send('newswire:arrange-panels'),
 publishWorkspace:data=>{if(data&&typeof data==='object')ipcRenderer.send('newswire:workspace-state',data)},
 onWorkspaceState:listener=>{if(typeof listener==='function')ipcRenderer.on('newswire:workspace-state',(_e,state)=>listener(state))},
 focusWarning:id=>{if(typeof id==='string')ipcRenderer.send('newswire:focus-warning',id)},
 onFocusWarning:listener=>{if(typeof listener==='function')ipcRenderer.on('newswire:workspace-focus',(_e,id)=>listener(id))},
 setZip:zip=>{if(/^\d{5}$/.test(zip))ipcRenderer.send('newswire:set-zip',zip)},
 onSetZip:listener=>{if(typeof listener==='function')ipcRenderer.on('newswire:workspace-zip',(_e,zip)=>listener(zip))}
});
