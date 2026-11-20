'use strict';
const {chromium}=require('playwright'),http=require('http'),fs=require('fs'),path=require('path'),assert=require('assert/strict');
(async()=>{
 const root=path.resolve('newswave-android/app/src/main/assets');
 const server=http.createServer((req,res)=>{const f=path.join(root,new URL(req.url,'http://localhost').pathname);try{res.setHeader('Content-Type',f.endsWith('.js')?'text/javascript':f.endsWith('.css')?'text/css':'text/html');res.end(fs.readFileSync(f))}catch(e){res.statusCode=404;res.end()}});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const browser=await chromium.launch({executablePath:process.env.CHROME_PATH,headless:true,args:['--no-sandbox','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
 try{
 const page=await browser.newPage({viewport:{width:412,height:850}}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 await page.route('https://**/*',route=>{
  if(route.request().url().includes('/styles/'))return route.fulfill({contentType:'application/json',body:JSON.stringify({version:8,sources:{},layers:[{id:'background',type:'background',paint:{'background-color':'#112233'}}]})});
  return route.fulfill({contentType:'image/png',body:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jG2kAAAAASUVORK5CYII=','base64')});
 });
 const base='http://127.0.0.1:'+server.address().port;
 await page.goto(base+'/drive_map.html?lat=1&lon=1');
 await page.waitForSelector('canvas');
 assert.equal(await page.locator('#styleBtn').innerText(),'Satellite');
 assert.match(await page.locator('#status').innerText(),/Saved ZIP/);
 for(const name of ['Terrain','Roads','Satellite']){await page.locator('#styleBtn').click();assert.equal(await page.locator('#styleBtn').innerText(),name);}
 await page.locator('#followBtn').click();assert.equal(await page.locator('#followBtn').getAttribute('aria-pressed'),'false');
 await page.reload();assert.equal(await page.locator('#followBtn').getAttribute('aria-pressed'),'false');
 await page.goto(base+'/live_center_map.html?lat=1&lon=1');
 assert.equal(await page.evaluate(()=>mapStyle),'satellite');
 assert.equal(await page.evaluate(()=>referenceLayers.length),2);
 await page.locator('#terrainType').click();assert.equal(await page.evaluate(()=>mapStyle),'terrain');assert.equal(await page.evaluate(()=>referenceLayers.length),0);
 await page.locator('#satelliteType').click();assert.equal(await page.evaluate(()=>referenceLayers.length),2);
 const before=await page.evaluate(()=>map.getZoom());await page.getByRole('button',{name:'Zoom in',exact:true}).click();
 await page.waitForFunction(z=>map.getZoom()>z,before);
 await page.evaluate(()=>{setPosition(2,2,true);recenter()});assert.ok(Math.abs(await page.evaluate(()=>map.getCenter().lat)-2)<.002,"recenter within one projected screen pixel");
 assert.deepEqual(errors,[]);
 console.log('Browser PASS: satellite default, style cycling, saved ZIP, follow persistence, labels, zoom and recenter (network tiles mocked).');
 }finally{await browser.close();await new Promise(r=>server.close(r));}
})().catch(e=>{console.error(e);process.exit(1)});
