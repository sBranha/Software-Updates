/* Image-3-style desktop dashboard visual state; no fabricated weather data. */
(()=>{
 'use strict';
 const $=id=>document.getElementById(id);
 const bridge=window.NewsWireDesktopBridge;
 function click(id){const button=$(id);if(button)button.click()}
 function openPanel(view){if(bridge&&typeof bridge.openPanel==='function')bridge.openPanel(view);}
 $('nav-map')?.addEventListener('click',()=>click('country'));
 $('navWarnings')?.addEventListener('click',()=>openPanel('warnings'));
 $('nav-home')?.addEventListener('click',()=>click('home'));
 $('nav-country')?.addEventListener('click',()=>click('country'));
 $('warningBanner')?.addEventListener('click',()=>openPanel('warnings'));
 $('settings')?.addEventListener('click',()=>{
  const bar=$('controls');bar?.classList.add('emphasis');
  bar?.scrollIntoView?.({block:'nearest',behavior:'smooth'});
  $('auto')?.focus();
  setTimeout(()=>bar?.classList.remove('emphasis'),1400);
 });
 function update(){
  const feed=$('status')?.textContent||'';
  const checked=$('checked')?.textContent||'';
  const local=$('localStatus')?.textContent||'Local status not checked';
  const count=$('count')?.textContent?.trim()||'—';
  const stale=/unavailable|unknown|waiting|checking|loading/i.test(feed) ||
    !/\bactive US tornado warnings\b/i.test(feed) ||
    /currently stale|unknown/i.test(checked);
  const urgent=/tornado warning affecting|tornado warning.*local|local tornado warning|tornado warning\s*[—-]/i.test(local);
  const banner=$('warningBanner');
  if(banner)banner.className='warningBanner '+(stale?'unknownStatus':urgent||Number(count)>0?'activeStatus':'clearStatus');
  const label=stale?'NWS STATUS UNKNOWN':urgent?'LOCAL TORNADO WARNING':
   Number(count)>0?'ACTIVE US TORNADO WARNINGS':'NO ACTIVE US TORNADO WARNINGS';
  const sub=stale?'Official nationwide feed unavailable / checking':
   urgent?'Official local warning — follow NWS instructions':
   Number(count)>0?count+' active nationwide':'No active tornado warnings returned at last check';
  if($('bannerTitle'))$('bannerTitle').textContent=label;
  if($('bannerSub'))$('bannerSub').textContent=sub;
  if($('mapFooterState'))$('mapFooterState').textContent=stale?'NWS STATUS UNKNOWN':'NWS FEED LAST CHECKED';
  if($('mapFooterCount'))$('mapFooterCount').textContent=stale?'—':count;
  if($('quickCount'))$('quickCount').textContent=stale?'?':count;
  if($('leftLocalStatus'))$('leftLocalStatus').textContent=local;
  try{
   const home=JSON.parse(localStorage.getItem('nwcc-home')||'null');
   if(home&&typeof home.label==='string'&&home.label.length<120){
    if($('heroLocation'))$('heroLocation').textContent=home.label;
    if($('headerLocation'))$('headerLocation').textContent=home.label;
   }
  }catch(_){}
 }
 if(typeof MutationObserver!=='undefined'){
  const o=new MutationObserver(update);
  for(const id of ['status','checked','count','localStatus'])if($(id))o.observe($(id),{childList:true,characterData:true,subtree:true});
 }
 $('setZip')?.addEventListener('click',()=>setTimeout(update,500),{capture:false});
 update();
})();
