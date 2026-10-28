'use strict';
const {test}=require('node:test'),a=require('node:assert/strict'),fs=require('node:fs'),p=require('node:path'),W=require('./warning-operations.js');
test('countdown never presents expired or invalid warning as active',()=>{
 const now=Date.parse('2026-10-28T22:30:00Z');
 a.equal(W.countdown('2026-10-28T22:41:00Z',now),'11 min remaining');
 a.equal(W.countdown('2026-10-28T22:29:00Z',now),'EXPIRED — NOT ACTIVE');
 a.equal(W.countdown('garbage',now),'Expiration not provided');
});
test('bulletin extracts only literal NWS-provided hazard/source/impact/movement',()=>{
 const warning={id:'nws123',area:'Example County',headline:'Tornado Warning',sent:'2026-10-28T22:20:00Z',
 expires:'2026-10-28T22:50:00Z',description:'HAZARD...Tornado.\\nSOURCE...Radar indicated rotation.\\nIMPACT...Flying debris.\\nMOVING NORTHEAST AT 35 MPH',instruction:'Take shelter'};
 const n=W.summarize({...warning,description:warning.description.replaceAll('\\n','\n')});
 a.equal(n.hazard,'Tornado');a.equal(n.source,'Radar indicated rotation');
 a.equal(n.impact,'Flying debris');a.deepEqual(n.move,{direction:'NORTHEAST',mph:35,origin:'NWS bulletin statement'});
 a.equal(W.summarize({headline:'Unknown',expires:warning.expires}).move,null);
 a.equal(W.activeById([warning],'nws123',Date.parse('2026-10-28T22:30:00Z')).id,'nws123');
 a.equal(W.activeById([warning],'nws123',Date.parse('2026-10-28T22:55:00Z')),null);
});
test('radar bridge uses sandbox-compatible Electron IPC, not package require',()=>{
 const main=fs.readFileSync(p.join(__dirname,'main.cjs'),'utf8'),
  preload=fs.readFileSync(p.join(__dirname,'preload.cjs'),'utf8'),
  html=fs.readFileSync(p.join(__dirname,'desktop-concept3.html'),'utf8'),
  pkg=require('./package.json');
 a.equal(pkg.version,'0.8.7');
 a.ok(!preload.includes("require('./package.json')"));
 a.match(main,/newswire:app-version/);
 a.match(preload,/sendSync\('newswire:app-version'\)/);
 a.match(html,/warningLockToggle/);
 a.match(html,/warningExpiresCountdown/);
 a.ok(pkg.build.files.includes('warning-operations.js'));
});
