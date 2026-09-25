const {chromium}=require('playwright');
const fs=require('fs');
const assert=require('assert/strict');
(async()=>{
 const browser=await chromium.launch({channel:'msedge',headless:true});
 try {
  for(const kind of ['platform','tenant']) for(const width of [1440,390]) for(const height of [100,2200]) {
   const page=await browser.newPage({viewport:{width,height:800}});
   const side=kind==='platform'?'platform-sidebar':'tenant-shell-sidebar';
   const foot=kind==='platform'?'platform-footer':'tenant-footer';
   await page.setContent(`<style>body{margin:0}.app-shell{display:grid;grid-template-columns:${width>600?'252px':'0px'} 1fr}main{min-width:0}.content{height:${height}px}.support-widget{position:fixed;right:16px;width:58px;height:58px}</style><div class="app-shell ${kind==='platform'?'platform-shell':''}"><aside class="${side}"></aside><main><div class="content">Content</div><footer class="${foot}"><span>© 2026 RetailZim. All rights reserved.</span><span>Powered by <a>CN Technologies</a></span><a>Help & support</a></footer></main></div><div class="support-widget"></div>`);
   await page.addStyleTag({content:fs.readFileSync('src/main/resources/static/css/workspace-footer.css','utf8')});
   await page.addScriptTag({content:fs.readFileSync('src/main/resources/static/js/workspace-footer.js','utf8')});
   await page.waitForFunction(()=>document.documentElement.style.getPropertyValue('--workspace-footer-height'));
   for(const scroll of [0,300,10000]) {
    await page.evaluate(y=>window.scrollTo(0,y),scroll);
    const result=await page.evaluate(()=>{
     const f=document.querySelector('footer').getBoundingClientRect(),m=document.querySelector('main'),chat=document.querySelector('.support-widget').getBoundingClientRect();
     return {bottom:f.bottom,left:f.left,mainLeft:m.getBoundingClientRect().left,padding:parseFloat(getComputedStyle(m).paddingBottom),height:f.height,chatBottom:chat.bottom,top:f.top};
    });
    assert.equal(result.bottom,800);assert.equal(result.left,result.mainLeft);assert.ok(result.padding>=result.height+20);assert.ok(result.chatBottom<result.top);
   }
   await page.close();
  }
  console.log('PASS: admin and tenant footers stay fixed on desktop/mobile, short/long pages; content and chat clearance preserved.');
 } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
