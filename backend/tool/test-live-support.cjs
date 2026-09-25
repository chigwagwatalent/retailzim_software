const { chromium } = require('playwright');
const fs = require('fs');
const assert = require('assert/strict');
(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  try {
    const page = await browser.newPage();
    await page.route('https://support.test/**', route => route.fulfill({ contentType:'text/html', body:`
      <meta name="csrf-token" content="test"><meta name="csrf-header" content="X-CSRF-TOKEN">
      <a title="Notifications"></a><div class="support-widget chat-panel" data-feed-url="/shop/support/chat/feed">
      <button class="support-fab" data-chat-toggle>Chat</button><div class="chat-feed"></div>
      <form action="/shop/support/chat" data-async-chat><input name="message"><button type="submit">Send</button></form></div>` }));
    await page.goto('https://support.test/');
    await page.evaluate(() => {
      window.calls = []; window.failSend = true; window.tones = 0;
      window.AudioContext = class {
        state = 'running'; currentTime = 0; destination = {};
        resume() { return Promise.resolve(); }
        createOscillator() { return {connect(){}, disconnect(){}, frequency:{setValueAtTime(){}},start(){window.tones++},stop(){}}; }
        createGain() {return {connect(){}, disconnect(){},gain:{setValueAtTime(){},exponentialRampToValueAtTime(){}}};}
      };
      window.EventSource = class {
        constructor() { window.liveSource = this; }
        addEventListener(type, fn) { this.receive = fn; }
        close() {}
      };
      window.fetch = async (url, options = {}) => {
        const body = options.body ? Object.fromEntries(options.body.entries()) : {};
        window.calls.push({ url, body, headers: options.headers });
        if (url.endsWith('/messages')) {
          if (window.failSend) throw new Error('Disconnected after sending');
          return { ok: true, redirected: false, json: async () => ({id:10}) };
        }
        return { ok: true, redirected:false, json: async () => [{id:9,senderType:'PLATFORM',senderName:'Support',message:'<img src=x onerror=alert(1)>',createdAt:'2026-09-15T12:00:00'}] };
      };
    });
    await page.addScriptTag({content:fs.readFileSync('src/main/resources/static/js/live-support.js','utf8')});
    await page.evaluate(() => window.liveSource.receive({ data: JSON.stringify({latest:9,unread:2,notifications:3}) }));
    assert.equal(await page.locator('.support-fab [data-live-count]').textContent(), '2');
    assert.equal(await page.evaluate(() => window.calls.length), 0, 'closed widget must not fetch or acknowledge');
    await page.evaluate(() => document.querySelector('.support-widget').classList.add('open'));
    await page.locator('[data-chat-toggle]').click();
    await page.waitForFunction(() => window.calls.some(c => c.url.endsWith('/read')));
    assert.equal(await page.locator('.chat-feed img').count(),0,'messages must be text, never executable HTML');
    assert.equal(await page.locator('.chat-feed p').textContent(),'<img src=x onerror=alert(1)>');
    await page.locator('input[name=message]').fill('Please help');
    await page.locator('[type=submit]').click();
    await page.waitForFunction(() => document.querySelector('.chat-connection').textContent.startsWith('Delivery'));
    assert.equal(await page.locator('input').inputValue(),'Please help');
    await page.evaluate(() => window.failSend = false);
    await page.locator('[type=submit]').click();
    await page.waitForFunction(() => document.querySelector('input').value === '');
    const sends = await page.evaluate(() => window.calls.filter(c => c.url.endsWith('/messages')));
    assert.equal(sends.length,2); assert.equal(sends[0].body.requestId,sends[1].body.requestId,'retry must be idempotent');
    assert.equal(sends[0].headers['X-CSRF-TOKEN'],'test');
    // Tenant fixture has no header; mount a header before initializing in production.
    const adminPage = await browser.newPage();
    await adminPage.setContent('<div class="platform-shell"><div class="topbar-actions"></div><aside class="platform-sidebar"><a class="nav-link" href="/admin/support"><i></i><span>Live Support</span><small class="nav-pill">1</small></a></aside></div>');
    await adminPage.evaluate(() => {
      window.tones=0;
      window.EventSource=class {constructor(){window.source=this} addEventListener(t,fn){this.receive=fn} close(){}};
      window.AudioContext=class {state='running';currentTime=0;destination={};resume(){return Promise.resolve()}createOscillator(){return {connect(){},disconnect(){},frequency:{setValueAtTime(){}},start(){window.tones++},stop(){}}}createGain(){return {connect(){},disconnect(){},gain:{setValueAtTime(){},exponentialRampToValueAtTime(){}}}}};
    });
    await adminPage.addScriptTag({content:fs.readFileSync('src/main/resources/static/js/live-support.js','utf8')});
    await adminPage.evaluate(()=>window.source.receive({data:JSON.stringify({latest:1,unread:1,notifications:0})}));
    assert.equal(await adminPage.locator('.nav-link > span').textContent(),'Live Support');
    assert.equal(await adminPage.locator('.nav-link').evaluate(el=>el.classList.contains('has-badge')),false);
    assert.equal(await adminPage.locator('.support-sound-toggle').count(),0);
    assert.equal(await adminPage.evaluate(()=>window.tones),0,'initial unread snapshot must not make a sound');
    await adminPage.evaluate(()=>window.source.receive({data:JSON.stringify({latest:2,unread:2,notifications:0})}));
    assert.equal(await adminPage.evaluate(()=>window.tones),1);
    await adminPage.evaluate(()=>window.source.receive({data:JSON.stringify({latest:2,unread:2,notifications:0})}));
    assert.equal(await adminPage.evaluate(()=>window.tones),1);
    await adminPage.evaluate(()=>window.source.receive({data:JSON.stringify({latest:3,unread:3,notifications:0})}));
    assert.equal(await adminPage.evaluate(()=>window.tones),1);
    await adminPage.close();
    console.log('PASS: chat safety, retry, CSRF, sidebar labels, always-enabled sound without toggle, no initial/repeated tone.');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode=1; });
