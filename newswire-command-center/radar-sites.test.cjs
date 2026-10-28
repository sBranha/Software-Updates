'use strict';
const {test}=require('node:test'),a=require('node:assert/strict'),fs=require('node:fs');
const Registry=require('./radar-sites.js'),Picker=require('./radar-site-picker.js'),Site=require('./site-radar.js');
test('NewsWire includes every station from the 161-site NEXRAD directory',()=>{
 a.equal(Registry.SITES.length,161);
 a.equal(new Set(Registry.SITES.map(s=>s.id)).size,161);
 a.equal(Registry.SITES[0].id,'KHTX');
 for(const id of ['KHTX','KOHX','KFFC','KMRX','KBMX','KTLH','KJAX','KAMX','KATX','KBOX','PABC','PHKI','PGUA','TJUA','LPLA','RODN']){
  a.ok(Registry.siteFor(id),id);
  a.equal(Site.siteFor(id).id,id);
 }
 for(const s of Registry.SITES){
  a.match(s.id,/^[A-Z]{4}$/);a.ok(s.lat>=-90&&s.lat<=90);
  a.ok(s.lng>=-180&&s.lng<=180);
  a.ok(s.name);
 }
 a.equal(Registry.siteFor('KZZZ'),null);
});
test('search finds station IDs, state, place aliases, and uncommon four-letter prefixes',()=>{
 a.equal(Picker.search(Registry.SITES,'Huntsville')[0].id,'KHTX');
 a.ok(Picker.search(Registry.SITES,'AL').some(s=>s.id==='KBMX'));
 a.equal(Picker.search(Registry.SITES,'PABC')[0].id,'PABC');
 a.ok(Picker.search(Registry.SITES,'Miami').some(s=>s.id==='KAMX'));
 a.equal(Picker.search(Registry.SITES,'zzzz').length,0);
});
test('enter picks site while preserving selected station when filtering',()=>{
 const old=global.document,created=[];
 global.document={createElement:()=>({value:'',textContent:''})};
 try{
  let current='KHTX',chosen=null;
  const handlers={},input={value:'',title:'',setAttribute(){},addEventListener(e,h){handlers[e]=h},blur(){}};
  const select={options:[],value:'KHTX',replaceChildren(...opts){this.options=opts}};
  const api=Picker.attach({input,select,sites:Registry.SITES,getSelected:()=>current,onChoose:id=>{chosen=id;current=id}});
  input.value='Florida';handlers.input();
  a.ok(select.options.some(x=>x.value==='KHTX'));
  input.value='KBMX';handlers.input();handlers.keydown({key:'Enter',preventDefault(){}});
  a.equal(chosen,'KBMX');a.equal(select.value,'KBMX');
  a.equal(api.getMatches().length,Registry.SITES.length);
 }finally{global.document=old}
});
test('native radar requests accept ICAO station codes while rejecting arbitrary IDs',()=>{
 const pkg=require('./package.json');a.equal(pkg.version,'0.8.6');
 const preload=fs.readFileSync(__dirname+'/preload.cjs','utf8');
 const main=fs.readFileSync(__dirname+'/main.cjs','utf8');
 a.match(preload,/requestRadarScans/);
 a.match(main,/siteFor\(site\)/);
 a.ok(pkg.build.files.includes('radar-sites.js'));
 a.ok(pkg.build.files.includes('radar-site-picker.js'));
});
test('location marker may be hidden without losing NWS monitoring or centering',()=>{
 const app=fs.readFileSync(__dirname+'/app.js','utf8'),html=fs.readFileSync(__dirname+'/desktop-concept3.html','utf8');
 a.match(html,/id="locationPinToggle"/);
 a.match(app,/showLocationPin/);
 a.match(app,/location-pin/);
 a.match(app,/if\(!map\|\|!home\|\|!showLocationPin\)/);
 a.match(app,/setHome\(lat,lon,label,type\)/);
 a.match(app,/markHome\(\)/);
});
