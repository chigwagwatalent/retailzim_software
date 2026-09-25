const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');
// Render actual Thymeleaf output from the opt-in local smoke test, without making any mutations.
(async () => {
  const root = path.resolve(__dirname, '..');
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  await page.route('http://workspace.test/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname.startsWith('/preview/')) {
      const file = path.join(root, 'target/workspace-smoke', path.basename(url.pathname) + '.html');
      return route.fulfill({ contentType: 'text/html', body: fs.readFileSync(file) });
    }
    const file = path.join(root, 'src/main/resources/static', url.pathname);
    if (fs.existsSync(file) && fs.statSync(file).isFile()) {
      const types = { '.css': 'text/css', '.js': 'application/javascript', '.png': 'image/png', '.svg': 'image/svg+xml' };
      return route.fulfill({ contentType: types[path.extname(file)] || 'application/octet-stream', body: fs.readFileSync(file) });
    }
    if (url.pathname.startsWith('/webjars/')) {
      const asset = path.join(root, 'target/workspace-smoke/assets/META-INF/resources', url.pathname);
      return route.fulfill({ contentType: url.pathname.endsWith('.css') ? 'text/css' : 'font/woff2', body: fs.readFileSync(asset) });
    }
    return route.fulfill({ status: 204, body: '' });
  });
  for (const name of ['categories', 'products', 'dashboard', 'admin-settings']) {
    await page.goto('http://workspace.test/preview/' + name);
    await page.screenshot({ path: path.join(root, 'target/workspace-smoke', name + '.png'), fullPage: true });
    const info = await page.evaluate(() => ({
      tables: document.querySelectorAll('table').length,
      categoryCards: document.querySelectorAll('.category-card').length,
      overflow: document.documentElement.scrollWidth > window.innerWidth + 1,
      body: document.body.innerText.slice(0, 90)
    }));
    if (info.overflow) throw new Error(name + ': viewport overflow');
    if (!info.tables) throw new Error(name + ': missing table');
    if (name === 'categories' && info.categoryCards) throw new Error('Category cards still present');
    console.log(name, JSON.stringify(info));
    if (name === 'admin-settings') {
      const triggers = await page.locator('[data-settings-open]').count();
      for (let index = 0; index < triggers; index++) {
        const trigger = page.locator('[data-settings-open]').nth(index);
        const id = await trigger.getAttribute('data-settings-open');
        await trigger.click();
        if (!await page.locator(`#${id}`).evaluate(dialog => dialog.open)) throw new Error('Dialog did not open: ' + id);
        await page.keyboard.press('Escape');
        if (await page.locator(`#${id}`).evaluate(dialog => dialog.open)) throw new Error('Escape did not close: ' + id);
      }
      await page.locator('[data-settings-open="create-admin"]').click();
      await page.screenshot({ path: path.join(root, 'target/workspace-smoke/admin-create-modal.png') });
      await page.locator('#create-admin [data-settings-close]').first().click();
      await page.setViewportSize({width:390,height:844});
      await page.locator('[data-settings-open="create-admin"]').click();
      const bounds=await page.locator('#create-admin').boundingBox();
      if (bounds.x < 0 || bounds.x + bounds.width > 390) throw new Error('Mobile dialog overflow');
      await page.screenshot({path:path.join(root,'target/workspace-smoke/admin-create-mobile.png')});
      await page.keyboard.press('Escape');
      console.log('All administrator action dialogs opened and closed; mobile dialog fits');
      await page.setViewportSize({width:1440,height:1000});
    }
    if (name === 'categories') {
      const select = page.locator('#workspace-branch');
      const choice = await select.locator('option').nth(1).getAttribute('value');
      const sent = page.waitForRequest(request => request.url().endsWith('/shop/branch/select') && request.method() === 'POST');
      await select.selectOption(choice);
      const request = await sent;
      if (!new URLSearchParams(request.postData()).get('branchId')?.includes(choice)) throw new Error('Wrong branch submitted');
      console.log('Branch selection automatically submitted successfully');
    }
  }
  await browser.close();
})().catch(error => { console.error(error); process.exit(1); });
