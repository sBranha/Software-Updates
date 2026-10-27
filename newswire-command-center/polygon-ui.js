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
const setStatus=(id,message)=>{const el=$(id);if(el)el.textContent=message};
function buttons(){
 $('draw').textContent=drawing?'Cancel Drawing':'✎ Draw Polygon';
 $('undo').disabled=!drawing||points.length===0;
 $('finish').disabled=!drawing||points.length<3;
 $('retry').disabled=drawing||working||!D.validPolygon(points);
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
function cancellation(){
 drawing=false;points=[];clearGeometry();setStatus('drawStatus','Drawing canceled. Previous polygon was not saved.');
 buttons();
}
function clear(){
 drawing=false;points=[];requestToken++;working=false;clearGeometry();
 try{localStorage.removeItem(STORE)}catch(_){}
 $('places').replaceChildren();
 setStatus('placesStatus','No communities loaded. Draw a polygon to look them up.');
 setStatus('communitySummary','NO DRAWN AREA');
 setStatus('drawStatus','Area cleared. No user polygon is displayed.');
 buttons();
}
async function lookup(){
 if(!D.validPolygon(points)){setStatus('placesStatus','Finish a valid polygon first.');return}
 if(!bridge?.lookupCommunities){
  setStatus('placesStatus','Desktop community lookup unavailable. Update to NewsWire v0.7.6.');
  setStatus('communitySummary','COMMUNITY LOOKUP UNAVAILABLE');return;
 }
 const token=++requestToken;
 working=true;buttons();$('places').replaceChildren();
 if(communityLayer){map.removeLayer(communityLayer);communityLayer=null}
 setStatus('placesStatus','Contacting community database… This may take up to 40 seconds if a service is down.');
 setStatus('communitySummary','COMMUNITY LOOKUP IN PROGRESS…');
 try{
  const result=await bridge.lookupCommunities(points.map(p=>({lat:p.lat,lng:p.lng})));
  if(token!==requestToken)return;
  if(!result?.ok)throw Error(result?.error||'Unknown lookup failure');
  const found=Array.isArray(result.places)?result.places:[];
  const list=$('places');list.replaceChildren();
  communityLayer=L.layerGroup().addTo(map);
  for(const place of found){
   const li=document.createElement('li'),name=document.createElement('strong'),kind=document.createElement('small');
   name.textContent=place.name;kind.textContent=' — '+place.type;
   li.append(name,kind);list.append(li);
   if(Number.isFinite(place.lat)&&Number.isFinite(place.lng)){
    const marker=L.circleMarker([place.lat,place.lng],{
     radius:5,color:'#d7fcff',weight:2,fillColor:'#0bb3db',fillOpacity:.94
    }).addTo(communityLayer);
    marker.bindTooltip(place.name+' · mapped '+place.type);
   }
  }
  setStatus('communitySummary',found.length+' MAPPED COMMUNIT'+(found.length===1?'Y':'IES')+' INSIDE YOUR AREA');
  setStatus('placesStatus',found.length?
   found.length+' OpenStreetMap place labels inside your polygon'+(result.complete===false?' (partial results — database limit reached)':'')+'. Blue map dots show their approximate centers.':
   'No named place centers returned inside your drawn polygon. This does NOT mean no one lives in the area.');
  if(result.complete===false)setStatus('drawStatus','Some communities may be missing because the source returned too many results. Draw a smaller area.');
 }catch(error){
  if(token!==requestToken)return;
  setStatus('communitySummary','COMMUNITY LOOKUP FAILED — RETRY AVAILABLE');
  setStatus('placesStatus','Lookup failed: '+String(error?.message||error).slice(0,650)+'. Click Retry Communities. Your polygon is still saved.');
 }finally{if(token===requestToken){working=false;buttons()}}
}
function restore(){
 try{
  const a=JSON.parse(localStorage.getItem(STORE)||'null');
  if(Array.isArray(a)&&D.validPolygon(a)){
   points=a;drawing=false;paint();buttons();
   setStatus('drawStatus','Restored your saved polygon from the last session.');
   setStatus('communitySummary','SAVED POLYGON — CHECKING COMMUNITIES');
   lookup();
  }
 }catch(_){}
}
$('draw').addEventListener('click',()=>{
 if(drawing){cancellation();return}
 drawing=true;points=[];requestToken++;working=false;clearGeometry();
 setStatus('communitySummary','DRAWING A NEW USER ESTIMATE');
 setStatus('drawStatus','Click 3–40 map corners, then Finish Shape. This is YOUR outline, not an official storm path.');
 buttons();
});
$('undo').addEventListener('click',()=>{if(!drawing||!points.length)return;points.pop();paint();buttons()});
$('finish').addEventListener('click',()=>{
 if(!drawing)return;
 if(!D.validPolygon(points)){setStatus('drawStatus','At least three distinct non-collinear corners are required.');return}
 try{D.safeQuery(points)}catch(error){setStatus('drawStatus',String(error.message));return}
 drawing=false;paint();buttons();
 try{localStorage.setItem(STORE,JSON.stringify(points))}catch(_){}
 setStatus('drawStatus','USER-DRAWN outline saved ('+points.length+' corners). Checking real community names…');
 lookup();
 $('communitySummary')?.scrollIntoView({block:'nearest',behavior:'smooth'});
});
$('retry').addEventListener('click',lookup);
$('clear').addEventListener('click',clear);
map.on('click',event=>{
 if(!drawing||!event?.latlng)return;
 const p={lat:event.latlng.lat,lng:event.latlng.lng};
 if(!D.validPoint(p)||points.length>=40)return;
 points.push(p);paint();buttons();
 setStatus('drawStatus',points.length+' corners selected. '+(points.length>=3?'Press Finish Shape to look up communities.':'Add more corners.'));
});
window.addEventListener('newswire:slot-changed',()=>{if(!drawing)clear()});
buttons();restore();
})();