'use strict';
const {contextBridge,ipcRenderer}=require('electron');
contextBridge.exposeInMainWorld('NewsWireDesktopBridge',{
 reportState:s=>{if(s&&typeof s==='object')ipcRenderer.send('newswire:deck-state',s)},
 lookupCommunities:(points,expand=false)=>{
  if(!Array.isArray(points)||points.length<3||points.length>40)return Promise.resolve({ok:false,error:'Draw 3 to 40 corners.'});
  return ipcRenderer.invoke('newswire:communities-lookup',points,expand===true);
 },
 onAction:listener=>{if(typeof listener==='function')ipcRenderer.on('newswire:deck-action',(_e,action)=>{
  if(typeof action==='string')listener(action)
 })},
 openPanel:type=>{if(['map','local','warnings','storm-1','storm-2','storm-3','storm-4'].includes(type))ipcRenderer.send('newswire:open-panel',type)},
 arrangePanels:()=>ipcRenderer.send('newswire:arrange-panels'),
 requestRadarScans:(site,product)=>{
  if(!/^K[A-Z0-9]{3}$/.test(String(site))||!/^(N0B|N0U|N0S|NET)$/.test(String(product)))
   return Promise.resolve({ok:false,error:'Unsupported radar selection'});
  return ipcRenderer.invoke('newswire:radar-scans',site,product);
 },
 toggleFullscreen:()=>ipcRenderer.send('newswire:toggle-window-fullscreen'),
 exitFullscreen:()=>ipcRenderer.send('newswire:exit-window-fullscreen'),
 requestTrack:(slot,id)=>{if([1,2,3,4].includes(slot)&&typeof id==='string')ipcRenderer.send('newswire:request-track',slot,id)},
 onRequestTrack:listener=>{if(typeof listener==='function')ipcRenderer.on('newswire:workspace-track-request',(_e,slot,id)=>listener(slot,id))},
 trackWarning:(slot,data)=>{if([1,2,3,4].includes(slot)&&data&&typeof data==='object')ipcRenderer.send('newswire:track-warning',slot,data)},
 onTrackedWarning:listener=>{if(typeof listener==='function')ipcRenderer.on('newswire:tracked-warning',(_e,slot,item,feedOk)=>listener(slot,item,feedOk))},
 publishTrackUpdates:items=>{if(Array.isArray(items))ipcRenderer.send('newswire:track-updates',items)},
 publishWorkspace:data=>{if(data&&typeof data==='object')ipcRenderer.send('newswire:workspace-state',data)},
 onWorkspaceState:listener=>{if(typeof listener==='function')ipcRenderer.on('newswire:workspace-state',(_e,state)=>listener(state))},
 focusWarning:id=>{if(typeof id==='string')ipcRenderer.send('newswire:focus-warning',id)},
 onFocusWarning:listener=>{if(typeof listener==='function')ipcRenderer.on('newswire:workspace-focus',(_e,id)=>listener(id))},
 setZip:zip=>{if(/^\d{5}$/.test(zip))ipcRenderer.send('newswire:set-zip',zip)},
 onSetZip:listener=>{if(typeof listener==='function')ipcRenderer.on('newswire:workspace-zip',(_e,zip)=>listener(zip))}
});
