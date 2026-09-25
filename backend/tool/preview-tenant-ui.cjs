// Isolated visual fixtures from source templates. No application server or database writes.
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');
const root = path.resolve(__dirname, '..');
const read = p => fs.readFileSync(path.join(root, 'src/main/resources', p), 'utf8');
(async () => {
  const browser = await chromium.launch({channel:'msedge', headless:true});
  const page = await browser.newPage();
  const layout = read('templates/common/layout.html');
  const admin = process.env.PREVIEW_ADMIN === '1';
  const sideStart = admin ? layout.lastIndexOf('<aside',layout.indexOf('th:fragment="platformSidebar(')) : layout.indexOf('<aside class="sidebar tenant-shell-sidebar"');
  const sidebar = layout.slice(sideStart, layout.indexOf('</aside>',sideStart) + 8);
  const topStart = layout.indexOf(admin ? '<nav class="app-topbar platform-topbar"' : '<nav class="app-topbar tenant-shell-topbar"');
  const topbar = layout.slice(topStart, layout.indexOf('</nav>', topStart) + 6);
  const footerStart = layout.indexOf(admin ? '<footer class="platform-footer"' : '<footer class="tenant-footer"');
  const footer = layout.slice(footerStart, layout.indexOf('</footer>', footerStart) + 9);
  const styles = ['retailzw.css','billing.css','tenant-modern.css','admin-modern.css','live-support.css'].map(f => `<style>${read('static/css/'+f)}</style>`).join('') + '<link rel="stylesheet" href="http://preview.local/webjars/font-awesome/6.4.2/css/all.min.css">';
  await page.route('**/*', async route => {
    const url = new URL(route.request().url());
    if (url.hostname !== 'preview.local') return route.abort();
    const local = url.pathname.startsWith('/webjars/') ? path.join(root,'target/preview-fonts/META-INF/resources',url.pathname) : path.join(root, 'src/main/resources/static', url.pathname);
    if (fs.existsSync(local) && fs.statSync(local).isFile()) return route.fulfill({path:local});
    return route.fulfill({body:'',status:404});
  });
  const out = path.join(root,admin ? 'target/admin-ui-preview' : 'target/tenant-ui-preview'); fs.mkdirSync(out,{recursive:true});
  const results = [];
  for (const name of admin ? ['dashboard','tenants','tenant-detail','subscriptions','support','accounting','accounting-payments','accounting-expenses','releases','website-community'] : ['dashboard','products','sales','cash','purchasing','reports','users','inventory','branches','company','gas-tanks','billing']) {
    for (const width of [1440,390]) {
      let html = read(`templates/${admin ? 'admin' : 'shop'}/${name}.html`)
        .replace(/<head[\s\S]*?<\/head>/,()=>`<head><base href="http://preview.local/">${styles}</head>`)
        .replace(/<aside th:replace[^>]*><\/aside>/,()=>sidebar)
        .replace(/<nav th:replace[^>]*><\/nav>/,()=>topbar)
        .replace(/<footer th:replace[^>]*><\/footer>/,()=>footer);
      await page.setViewportSize({width,height:1000});
      await page.setContent(html);
      await page.evaluate(() => {
        // Choose a retail administrator fixture. These conditions are NOT production logic.
        document.querySelectorAll('[th\\:if]').forEach(el => {
          const expr = el.getAttribute('th:if');
          if (expr.includes('supervisorUser == true') || expr.includes('gasModuleEnabled == true') || expr.includes('tenantBillingOnly == true')) el.remove();
        });
        document.querySelectorAll('img[th\\:src]').forEach(el => { el.src = el.getAttribute('th:src').replace(/^@\{/, '').replace(/\}$/, ''); });
        document.querySelectorAll('[th\\:class]').forEach(el => {
          if (el.tagName === 'A' && !el.className) el.className = el.closest('.sidebar') ? 'nav-link' : el.closest('.topbar-actions') ? 'icon-button' : '';
        });
        document.querySelectorAll('[th\\:each]').forEach(el => el.removeAttribute('th:each'));
        document.querySelectorAll('.tenant-sidebar-nav .nav-link').forEach((el,i) => el.classList.toggle('active',i===0));
        document.querySelectorAll('.tenant-shell-sidebar th\\:block').forEach(el => el.style.display='contents');
        document.querySelectorAll('.tenant-branch-pill span').forEach(el => el.textContent='Senga Shop');
        const switcher = document.querySelector('.shop-branch-switcher'); if(switcher) switcher.remove();
        document.querySelectorAll('script').forEach(el => el.remove());
      });
      await page.addScriptTag({content:read('static/js/'+(admin ? 'admin-modern.js' : 'tenant-modern.js'))});
      await page.evaluate(() => document.dispatchEvent(new Event('DOMContentLoaded')));
      const metrics = await page.evaluate(() => ({
        overflow: document.documentElement.scrollWidth > innerWidth + 2,
        overflowingElements: [...document.querySelectorAll('body *')].filter(el=>!el.closest('.sidebar,.admin-table-scroll') && el.getBoundingClientRect().right>innerWidth+2 && el.getBoundingClientRect().width>0).slice(0,6).map(el=>[el.className,Math.round(el.getBoundingClientRect().right)]),
        footerPosition: getComputedStyle(document.querySelector('.tenant-footer,.platform-footer')).position,
        tableCount: document.querySelectorAll('main table').length,
        focusableTables: document.querySelectorAll('.shop-table-scroll[tabindex="0"],.admin-table-scroll[tabindex="0"]').length
      }));
      results.push({page:name,width,...metrics});
      if (['dashboard','products','sales','tenants','accounting','releases','support'].includes(name)) await page.screenshot({path:path.join(out,`${name}-${width}.png`),fullPage:true});
    }
  }
  console.log(JSON.stringify(results,null,2));
  console.log('Overflow checks:', JSON.stringify(results.filter(row=>row.overflow).map(row=>({page:row.page,width:row.width}))));
  await browser.close();
})().catch(error => {console.error(error);process.exit(1);});
