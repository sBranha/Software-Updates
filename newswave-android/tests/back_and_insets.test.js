'use strict';
// Guards the Android 16 (targetSdk 36) regressions: onBackPressed() is no longer
// called, and edge-to-edge is enforced. These are structural checks, not a
// substitute for testing Back and the bars on a real Android 16 phone.
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const root='newswave-android/app/src/main/';
const dir=root+'java/com/newswave/live/';
const manifest=fs.readFileSync(root+'AndroidManifest.xml','utf8');
assert.ok(manifest.includes('android:enableOnBackInvokedCallback="true"'),'manifest must opt in to back callbacks');
const activities=[...manifest.matchAll(/<activity android:name="\.([A-Za-z]+)"/g)].map(m=>m[1]);
assert.ok(activities.length>=14,'expected to find every activity');
for(const name of activities){
  const src=fs.readFileSync(dir+name+'.java','utf8');
  assert.ok(src.includes('SystemUi.install(this'),name+' must pad for edge-to-edge system bars');
  if(src.includes('onBackPressed'))
    assert.ok(src.includes('SystemUi.onBack(this'),name+' overrides onBackPressed but is not registered for Android 16 back');
}
const radio=fs.readFileSync(dir+'NewsRadioActivity.java','utf8');
const main=fs.readFileSync(dir+'MainActivity.java','utf8');
assert.ok(main.includes('PLAY MY NEWS')&&main.includes('putExtra("autoplay",true)'),'home must expose a one-tap spoken-news entry');
assert.ok(/tab\.equals\("News"\)\|\|tab\.equals\("National"\)/.test(main)&&main.includes('PLAY MY NEWS ALOUD'),'news screens need a play button');
assert.ok(radio.includes('getBooleanExtra("autoplay"')&&radio.includes('UtteranceProgressListener')&&radio.includes('requestAudioFocus'),'audio screen must autoplay, report completion and take audio focus');
console.log('Back handling, edge-to-edge insets and spoken-news entry checks PASS');
