/* Polygon drawing and community labels — browser never contacts Overpass directly.
   Source: OSM point/representative centers. These are not official storm impact claims. */
(()=>{
'use strict';
const $=id=>document.getElementById(id),map=window.NewsWireStormMap,D=window.NewsWireDrawnTracks;
const bridge=window.NewsWireDesktopBridge;
if(!map||!D)return;
const slot=Number(new URLSearchParams(location.search).get('slot'))||1;
const STORE='nwcc-drawn-track-'+slot;
let drawing=false,points=[],shape=null,draft=null,vertices=null,communityLayer=null,requestToken=0,working=false;
window.NWPolygonIsDrawing=false;
const setStatus=(id,message)=>{const el=$(id);if(el)el.textContent=message};
function buttons(){
 $('draw').textContent=drawing?'Cancel Drawing':'✎ Draw Polygon';
 $('undo').disabled=!drawing||points.length===0;
 $('finish').disabled=!drawing||points.length<3;
 $('retry').disabled=drawing||working||!D.validPolygon(points);
 $('focusArea').disabled=drawing||!D.validPolygon(points);
}
function clearGeometry(){
 if(shape){map.removeLayer(shape);shape=null}
 if(draft){map.removeLayer(draft);draft=null}
 if(vertices){map.removeLayer(vertices);vertices=null}
 if(communityLayer){map.removeLayer(communityLayer);communityLayer=null}
}
function paint(){
 clearGeometry();
 if(points.length>=3&&!drawing){
  shape=L.polygon(points,{color:'#ffd552',weight:4,fillColor:'#f7bd2c',fillOpacity:.17,dashArray:'8 5'}).addTo(map);
  shape.bindTooltip('USER-DRAWN STORM AREA · NOT AN OFFICIAL NWS WARNING',{sticky:true});
 }else if(points.length){
  draft=L.polyline(points,{color:'#ffd552',weight:4,dashArray:'7 4'}).addTo(map);
  vertices=L.layerGroup().addTo(map);
  points.forEach(p=>L.circleMarker(p,{radius:4,color:'#ffd552',weight:2,fillOpacity:1}).addTo(vertices));
 }
}
function focusPolygon(animate=false){
 if(!D.validPolygon(points))return false;
 const bounds=L.latLngBounds(points.map(p=>[p.lat,p.lng]));
 if(!bounds.isValid())return false;
 map.invalidateSize();
 map.fitBounds(bounds.pad(.3),{maxZoom:11,padding:[28,28],animate});
 return true;
}
function cancellation(){
 drawing=false;window.NWPolygonIsDrawing=false;points=[];clearGeometry();setStatus('drawStatus','Drawing canceled. Previous polygon was not saved.');
 buttons();
}
function clear(){
 drawing=false;window.NWPolygonIsDrawing=false;points=[];requestToken++;working=false;clearGeometry();
 try{localStorage.removeItem(STORE)}catch(_){}
 $('places').replaceChildren();$('nearby').replaceChildren();$('nearbyHeading').hidden=true;
 setStatus('placesStatus','No communities loaded. Draw a polygon to look them up.');
 setStatus('communitySummary','NO DRAWN AREA');
 setStatus('drawStatus','Area cleared. No user polygon is displayed.');
 buttons();
}
async function lookup(expand=false){
 if(!D.validPolygon(points)){setStatus('placesStatus','Finish a valid polygon first.');return}
 if(!bridge?.lookupCommunities){
  setStatus('placesStatus','Desktop community lookup unavailable. Update to NewsWire v0.7.6.');
  setStatus('communitySummary','COMMUNITY LOOKUP UNAVAILABLE');return;
 }
 const token=++requestToken;
 working=true;buttons();
 if(!expand)$('places').replaceChildren();
 if(!expand&&communityLayer){map.removeLayer(communityLayer);communityLayer=null}
 setStatus('placesStatus',expand?'Checking optional online neighborhoods; built-in towns remain available…':'Finding Census-based town centers inside your polygon (works offline)…');
 if(!expand)setStatus('communitySummary','COMMUNITY LOOKUP IN PROGRESS…');
 try{
  const result=await bridge.lookupCommunities(points.map(p=>({lat:p.lat,lng:p.lng})),expand);
  if(token!==requestToken)return;
  if(!result?.ok)throw Error(result?.error||'Unknown lookup failure');
  const found=Array.isArray(result.places)?result.places:[];
  const list=$('places');list.replaceChildren();$('nearby').replaceChildren();
  if(communityLayer){map.removeLayer(communityLayer);communityLayer=null}
  communityLayer=L.layerGroup().addTo(map);
  for(const place of found){
   const li=document.createElement('li'),name=document.createElement('strong'),kind=document.createElement('small');
   name.textContent=place.name;kind.textContent=' — '+(place.state?place.state+' · ':'')+(place.type==='census_place'?'Census town center':'OpenStreetMap '+place.type);
   li.append(name,kind);list.append(li);
   if(Number.isFinite(place.lat)&&Number.isFinite(place.lng)){
    const marker=L.circleMarker([place.lat,place.lng],{
     radius:5,color:'#d7fcff',weight:2,fillColor:'#0bb3db',fillOpacity:.94
    }).addTo(communityLayer);
    marker.bindTooltip(place.name+' · mapped '+place.type);
   }
  }
  const nearby=Array.isArray(result.nearby)?result.nearby:[];
  for(const n of nearby){
   const li=document.createElement('li');li.textContent=n.name+(n.state?', '+n.state:'')+' — nearby, OUTSIDE polygon ('+n.distanceKm+' km from center)';
   $('nearby').append(li);
  }
  $('nearbyHeading').hidden=nearby.length===0;
  setStatus('communitySummary',found.length+' MAPPED TOWN'+(found.length===1?'':'S')+' INSIDE YOUR OUTLINE');
  setStatus('placesStatus',
   found.length?
    found.length+' documented place center'+(found.length===1?'':'s')+' inside your polygon. Census towns work without internet; blue markers indicate approximate centers.':
    'No built-in town center falls inside this outline. This does NOT mean the storm area is unpopulated.');
  if(result.warning){
   console.info('NewsWire optional communities provider:',result.warning);
   setStatus('drawStatus','Offline towns loaded. Extra online neighborhoods are unavailable right now; your local results are still shown.');
  }
  else setStatus('drawStatus','Census-based offline places loaded. '+(expand&&result.online?'Extra OpenStreetMap labels added. ': '')+'Smaller neighborhoods may be missing.');
  if(nearby.length&&found.length===0)setStatus('placesStatus',$('placesStatus').textContent+' See NEARBY towns below — they are outside your polygon.');

 }catch(error){
  if(token!==requestToken)return;
  setStatus('communitySummary','COMMUNITY LOOKUP FAILED — RETRY AVAILABLE');
  setStatus('placesStatus','Offline place database error: '+String(error?.message||error).slice(0,650)+'. The Windows installer may be incomplete.');
 }finally{if(token===requestToken){working=false;buttons()}}
}
function restore(){
 try{
  const a=JSON.parse(localStorage.getItem(STORE)||'null');
  if(Array.isArray(a)&&D.validPolygon(a)){
   points=a;drawing=false;paint();buttons();
   setTimeout(()=>{if(!drawing&&D.validPolygon(points))focusPolygon(false)},500);
   setStatus('drawStatus','Restored your saved polygon from the last session.');
   setStatus('communitySummary','SAVED POLYGON — CHECKING COMMUNITIES');
   lookup();
  }
 }catch(_){}
}
$('draw').addEventListener('click',()=>{
 if(drawing){cancellation();return}
 window.NWStreetViewCancel?.();window.NWVelocityCancel?.();
 drawing=true;window.NWPolygonIsDrawing=true;points=[];requestToken++;working=false;clearGeometry();
 setStatus('communitySummary','DRAWING A NEW USER ESTIMATE');
 setStatus('drawStatus','Click 3–40 map corners, then Finish Shape. This is YOUR outline, not an official storm path.');
 buttons();
});
$('undo').addEventListener('click',()=>{if(!drawing||!points.length)return;points.pop();paint();buttons()});
$('finish').addEventListener('click',()=>{
 if(!drawing)return;
 if(!D.validPolygon(points)){setStatus('drawStatus','At least three distinct non-collinear corners are required.');return}
 try{D.safeQuery(points)}catch(error){setStatus('drawStatus',String(error.message));return}
 drawing=false;window.NWPolygonIsDrawing=false;paint();buttons();focusPolygon(true);
 try{localStorage.setItem(STORE,JSON.stringify(points))}catch(_){}
 setStatus('drawStatus','USER-DRAWN outline saved ('+points.length+' corners). Checking real community names…');
 lookup();
 $('communitySummary')?.scrollIntoView({block:'nearest',behavior:'smooth'});
});
$('retry').addEventListener('click',()=>lookup(true));
$('focusArea').addEventListener('click',()=>focusPolygon(true));
$('clear').addEventListener('click',clear);
map.on('click',event=>{
 if(!drawing||!event?.latlng)return;
 const p={lat:event.latlng.lat,lng:event.latlng.lng};
 if(!D.validPoint(p)||points.length>=40)return;
 points.push(p);paint();buttons();
 setStatus('drawStatus',points.length+' corners selected. '+(points.length>=3?'Press Finish Shape to look up communities.':'Add more corners.'));
});
// Keep a user's independently drawn polygon when the assigned NWS warning changes.
// Clear only from the explicit Clear action, never when another monitor changes.
buttons();restore();
})();