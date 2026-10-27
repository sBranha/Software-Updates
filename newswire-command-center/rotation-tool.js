/* On-screen manual velocity-couplet worksheet; each Electron storm slot isolated. */
(()=>{
'use strict';
const $=id=>document.getElementById(id),map=window.NewsWireStormMap,V=window.NewsWireVelocityCouplet;
if(!map||!V)return;
const slot=Number(new URLSearchParams(location.search).get('slot'))||1;
let picking=false,positions=[],markers=[];
const saveKey='nwcc-manual-velocity-'+slot;
function msg(text){$('rotationResult').textContent=text}
function clearMarkers(){for(const marker of markers)map.removeLayer(marker);markers=[]}
function redraw(){
 clearMarkers();
 positions.forEach((p,i)=>{
  const marker=L.circleMarker([p.lat,p.lng],{
   radius:8,color:i?'#ff6588':'#63e0ff',weight:3,fillOpacity:.8
  }).addTo(map);
  marker.bindTooltip((i?'Point B':'Point A')+' — manually selected velocity gate');
  markers.push(marker);
 });
 $('rotationPick').textContent=picking?'Cancel Selection':'Select Two Radar Points';
 $('rotationPicked').textContent=positions.length+' of 2 points selected in Storm Desk '+slot;
}
window.NWVelocityCancel=()=>{picking=false;redraw()};
function persist(){
 try{localStorage.setItem(saveKey,JSON.stringify({positions,a:$('rotationA').value,b:$('rotationB').value}))}catch(_){}
}
try{
 const old=JSON.parse(localStorage.getItem(saveKey)||'null');
 if(old&&Array.isArray(old.positions)&&old.positions.length===2&&
    old.positions.every(p=>Number.isFinite(p.lat)&&Number.isFinite(p.lng))){
  positions=old.positions;
  $('rotationA').value=String(old.a||'');$('rotationB').value=String(old.b||'');
 }
}catch(_){}
redraw();
$('rotationPick').addEventListener('click',()=>{
 if(window.NWPolygonIsDrawing){msg('Finish or cancel your polygon before selecting velocity gates.');return}
 picking=!picking;
 if(picking){positions=[];clearMarkers();msg('Click two separate points on the velocity map. Enter radar velocity values yourself; colors alone are not decoded.')}
 redraw();
});
map.on('click',event=>{
 if(!picking||window.NWPolygonIsDrawing||!event?.latlng)return;
 const p={lat:event.latlng.lat,lng:event.latlng.lng};
 if(!Number.isFinite(p.lat)||!Number.isFinite(p.lng))return;
 if(positions.length<2)positions.push(p);
 if(positions.length===2){picking=false;msg('Two gates selected. Enter their signed observed radial velocities, then calculate.')}
 redraw();persist();
});
$('rotationCalc').addEventListener('click',()=>{
 if(positions.length!==2){msg('Select two velocity-gate locations on the radar first.');return}
 try{
  const r=V.analyze(positions[0],positions[1],$('rotationA').value,$('rotationB').value);
  msg('USER ESTIMATE: gate spacing '+r.distanceKm+' km · ΔV '+r.velocityDifferenceMph+
   ' mph · half ΔV '+r.inferredHalfDifferenceMph+' mph · shear '+r.radialShearPerSecond+
   ' s⁻¹. Not an automatic rotation detection or tornado diagnosis.');
  persist();
 }catch(error){msg('Cannot calculate: '+String(error.message||error))}
});
$('rotationClear').addEventListener('click',()=>{
 picking=false;positions=[];clearMarkers();
 $('rotationA').value='';$('rotationB').value='';
 try{localStorage.removeItem(saveKey)}catch(_){}
 redraw();msg('Cleared only this storm desk’s manual velocity measurement.');
});
window.addEventListener('beforeunload',()=>clearMarkers());
})();