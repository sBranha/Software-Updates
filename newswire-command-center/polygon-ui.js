/* Storm-window drawing tools. User-drawn shapes are never official NWS warnings. */
(()=>{
'use strict';
const $=id=>document.getElementById(id),map=window.NewsWireStormMap,D=window.NewsWireDrawnTracks;
if(!map||!D)return;
const slot=Number(new URLSearchParams(location.search).get('slot'))||1;
const STORE='nwcc-drawn-track-'+slot;
let drawing=false,points=[],shape=null,draft=null,lastRequest=0,requestToken=0;
const setStatus=(id,t)=>{$(id).textContent=t};
function buttons(){
 $('draw').textContent=drawing?'Cancel Drawing':'✎ Draw Polygon';
 $('undo').disabled=!drawing||points.length===0;
 $('finish').disabled=!drawing||points.length<3;
}
function clearGeometry(){
 if(shape){map.removeLayer(shape);shape=null}
 if(draft){map.removeLayer(draft);draft=null}
}
function paint(){
 clearGeometry();
 if(points.length>=3&&!drawing){
  shape=L.polygon(points,{color:'#ffd552',weight:4,fillColor:'#f7bd2c',fillOpacity:.17,dashArray:'8 5'}).addTo(map);
  shape.bindTooltip('USER-DRAWN STORM AREA · NOT AN OFFICIAL NWS WARNING',{sticky:true});
  return;
 }
 if(points.length){
  draft=L.polyline(points,{color:'#ffd552',weight:4,dashArray:'7 4'}).addTo(map);
  points.forEach(p=>L.circleMarker(p,{radius:4,color:'#ffd552',weight:2,fillOpacity:1}).addTo(draft));
 }
}
function cancel(){
 drawing=false;points=[];clearGeometry();setStatus('drawStatus','Drawing canceled.');
 buttons();
}
function clear(){
 drawing=false;points=[];clearGeometry();
 try{localStorage.removeItem(STORE)}catch(_){}
 $('places').replaceChildren();
 setStatus('placesStatus','No communities loaded. Draw a polygon to look them up.');
 setStatus('drawStatus','Area cleared. No drawn storm area is currently displayed.');
 requestToken++;buttons();
}
function restore(){
 try{const a=JSON.parse(localStorage.getItem(STORE)||'null');
  if(Array.isArray(a)&&D.validPolygon(a)){
   points=a;drawing=false;paint();buttons();
   setStatus('drawStatus','Restored your saved user-drawn storm area.');
   lookup();
  }
 }catch(_){}
}
async function lookup(){
 if(!D.validPolygon(points))return;
 const token=++requestToken,now=Date.now();
 if(now-lastRequest<700)setStatus('placesStatus','Looking up communities…');
 lastRequest=now;
 $('places').replaceChildren();
 setStatus('placesStatus','Checking OpenStreetMap place names inside your drawn area…');
 try{
  const found=await D.fetchPlaces(points);
  if(token!==requestToken)return;
  const list=$('places');list.replaceChildren();
  for(const place of found){
   const li=document.createElement('li'),name=document.createElement('strong'),kind=document.createElement('small');
   name.textContent=place.name;kind.textContent=' — '+place.type;
   li.append(name,kind);list.append(li);
  }
  setStatus('placesStatus',found.length?
   found.length+' mapped communities whose representative points are inside your polygon (OpenStreetMap).':
   'No mapped communities found inside this area. That does not mean nobody lives there.');
 }catch(err){
  if(token!==requestToken)return;
  setStatus('placesStatus','Community lookup unavailable: '+String(err.message||err)+'. Shape stays visible; try again later.');
 }
}
$('draw').addEventListener('click',()=>{
 if(drawing){cancel();return}
 drawing=true;points=[];clearGeometry();
 setStatus('drawStatus','Drawing: click 3 or more map corners, then Finish Shape. Clicks move the outline, not the radar.');
 buttons();
});
$('undo').addEventListener('click',()=>{
 if(!drawing||!points.length)return;
 points.pop();paint();buttons();
});
$('finish').addEventListener('click',()=>{
 if(!drawing)return;
 if(!D.validPolygon(points)){setStatus('drawStatus','Area needs at least three distinct corners.');return}
 try{D.safeQuery(points)}catch(e){setStatus('drawStatus',e.message);return}
 drawing=false;paint();buttons();
 try{localStorage.setItem(STORE,JSON.stringify(points))}catch(_){}
 setStatus('drawStatus','USER-DRAWN polygon saved ('+points.length+' corners). Not an official NWS alert or storm forecast.');
 lookup();
});
$('clear').addEventListener('click',clear);
map.on('click',event=>{
 if(!drawing)return;
 // Dismissing a popup or map controls must not create polygon vertices.
 const p={lat:event.latlng.lat,lng:event.latlng.lng};
 if(!D.validPoint(p)||points.length>=40)return;
 points.push(p);paint();buttons();
});
window.addEventListener('newswire:slot-changed',()=>{if(!drawing)clear()});
buttons();restore();
})();