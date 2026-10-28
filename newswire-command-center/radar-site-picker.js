/* NEXRAD site search, independent in the main screen and each Storm Desk.
   Filter by station ID, name, state or aliases; never guesses station codes.
 */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;root.NewsWireRadarSitePicker=api})(typeof window!=='undefined'?window:globalThis,function(){
'use strict';
const Data=typeof module==='object'&&module.exports?require('./radar-sites.js'):null;
function search(sites,query,max=80){
 const terms=String(query||'').toLowerCase().trim().split(/\s+/).filter(Boolean);
 return sites.filter(site=>{
  const words=[site.id,site.name,site.state,site.aliases||''].join(' ').toLowerCase();
  return terms.every(part=>words.includes(part));
 }).slice(0,max);
}
function attach({input,select,sites,getSelected,onChoose}){
 if(!input||!select||!Array.isArray(sites))return null;
 let matches=sites;
 function update(){
  const q=String(input.value||'').trim();
  matches=search(sites,q,q?100:sites.length);
  if(!q){ // Restore the complete directory whenever search is cleared.
   matches=sites;
  }
  const selected=getSelected();
  const shown=matches.some(item=>item.id===selected)?matches:[...matches,...sites.filter(item=>item.id===selected)];
  const opts=shown.map(item=>{
   const option=document.createElement('option');
   option.value=item.id;
   option.textContent=item.id+' · '+item.name+(item.state?' ('+item.state+')':'');
   return option;
  });
  select.replaceChildren(...opts);
  select.value=selected;
  input.setAttribute('aria-label','Search '+sites.length+' radar sites by code, city or state');
  input.title=matches.length+' matching radar sites';
  return matches;
 }
 input.addEventListener('input',update);
 input.addEventListener('keydown',e=>{
  if(e.key==='Escape'){input.value='';update();input.blur?.();return}
  if(e.key!=='Enter'||!matches.length)return;
  e.preventDefault?.();
  const id=matches[0].id;
  if(typeof onChoose==='function')onChoose(id);
  input.value='';
  update();
 });
 return {update,search:value=>search(sites,value,100),getMatches:()=>matches};
}
return {search,attach};
});
