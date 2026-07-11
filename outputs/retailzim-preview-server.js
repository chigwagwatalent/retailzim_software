const http = require('http');
const fs = require('fs');
const path = require('path');
const { execFile } = require('child_process');
const { URLSearchParams } = require('url');

const repo = path.resolve(__dirname, '..');
const staticRoot = path.join(repo, 'outputs', 'frontwebsite-static');
const port = Number(process.env.PORT || 8099);

const pageRoutes = new Map([
  ['/', 'index.html'],
  ['/home', 'index.html'],
  ['/platform', 'platform.html'],
  ['/how-it-works', 'how-it-works.html'],
  ['/payments', 'payments.html'],
  ['/downloads', 'downloads.html'],
  ['/community', 'community.html'],
  ['/pricing', 'pricing.html'],
  ['/resources', 'resources.html']
]);

const phpRouteMap = new Map([
  ['/index.php', '/'],
  ['/platform.php', '/platform'],
  ['/how-it-works.php', '/how-it-works'],
  ['/payments.php', '/payments'],
  ['/downloads.php', '/downloads'],
  ['/community.php', '/community'],
  ['/pricing.php', '/pricing'],
  ['/resources.php', '/resources'],
  ['/analytics.php', '/analytics'],
  ['/community-post.php', '/community-post'],
  ['/community-engage.php', '/community-engage'],
  ['/track.php', '/track'],
  ['/api-community-posts.php', '/api/community/posts'],
  ['/api-community-answer.php', '/api/community/answer'],
  ['/api-visit-stats.php', '/api/visits/stats']
]);

const mime = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.exe': 'application/vnd.microsoft.portable-executable',
  '.zip': 'application/zip'
};

const mysqlExe = 'C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysql.exe';
const mysqlConfig = {
  host: process.env.RETAILZIM_DB_HOST || '127.0.0.1',
  user: process.env.RETAILZIM_DB_USER || 'connecte_retail_comunity',
  pass: process.env.RETAILZIM_DB_PASS || '@cHigwagwa1t@',
  name: process.env.RETAILZIM_DB_NAME || 'connecte_retail_comunity'
};

function sqlString(value, maxLength) {
  const clean = String(value || '').trim().slice(0, maxLength);
  return `'${clean.replace(/\\/g, '\\\\').replace(/'/g, "''")}'`;
}

function runMysql(sql) {
  return new Promise((resolve, reject) => {
    execFile(mysqlExe, [
      '-h', mysqlConfig.host,
      '-u', mysqlConfig.user,
      '--default-character-set=utf8mb4',
      '--batch',
      '--raw',
      '--skip-column-names',
      mysqlConfig.name,
      '-e', sql
    ], {
      env: { ...process.env, MYSQL_PWD: mysqlConfig.pass },
      windowsHide: true
    }, (error, stdout, stderr) => {
      if (error) {
        reject(new Error(stderr || error.message));
        return;
      }
      resolve(stdout);
    });
  });
}

async function mysqlJson(sql, fallback) {
  const stdout = await runMysql(sql);
  const text = stdout.trim();
  if (!text || text === 'NULL') {
    return fallback;
  }
  return JSON.parse(text);
}

async function ensureCommunityAnswerSchema() {
  await runMysql(`CREATE TABLE IF NOT EXISTS community_answers (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    post_id BIGINT UNSIGNED NOT NULL,
    responder VARCHAR(80) NOT NULL DEFAULT 'Retail Zim Support',
    answer TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_community_answers_post (post_id),
    CONSTRAINT fk_preview_community_answers_post
      FOREIGN KEY (post_id) REFERENCES community_posts(id)
      ON DELETE CASCADE
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`);
}

function html(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

function initials(name, shop) {
  return `${String(name || 'R').charAt(0)}${String(shop || 'Z').charAt(0)}`.toUpperCase();
}

async function communityPostsHtml() {
  await ensureCommunityAnswerSchema();
  const posts = await mysqlJson(`SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT(
      'id', id,
      'name', name,
      'shop', shop,
      'category', category,
      'message', message,
      'status', status,
      'likes', likes,
      'replies', replies,
      'answers', answers
    )), JSON_ARRAY())
    FROM (
      SELECT p.id, p.name, p.shop, p.category, p.message, p.status, p.likes, p.replies,
        COALESCE((
          SELECT JSON_ARRAYAGG(JSON_OBJECT('id', a.id, 'post_id', a.post_id, 'responder', a.responder, 'answer', a.answer, 'created_at', a.created_at))
          FROM community_answers a
          WHERE a.post_id = p.id
          ORDER BY a.id ASC
        ), JSON_ARRAY()) answers
      FROM community_posts p
      ORDER BY p.id DESC
      LIMIT 8
    ) posts`, []);

  return posts.map(post => `
          <article class="community-post">
            <header>
              <div class="member"><div class="community-avatar">${html(initials(post.name, post.shop))}</div><span><b>${html(post.name)}</b><small>${post.shop ? html(post.shop) + ' - ' : ''}Community post</small></span></div>
              <em class="status">${html(post.status || 'open')}</em>
            </header>
            <h2>${html(post.category || 'Question')}</h2>
            <p>${html(post.message)}</p>
            ${(post.answers || []).map(answer => `<blockquote><b>${html(answer.responder || 'Retail Zim Support')}:</b> ${html(answer.answer || '')}</blockquote>`).join('')}
            <footer><span><button type="button" data-engage-post="${Number(post.id)}" data-action="like">${Number(post.likes || 0)} likes</button><button type="button" data-engage-post="${Number(post.id)}" data-action="reply">${Number(post.replies || 0)} replies</button><button type="button">Follow</button></span><strong>${html(post.category || 'General')}</strong></footer>
          </article>`).join('');
}

async function renderCommunity(res) {
  try {
    const php = fs.readFileSync(path.join(repo, 'frontwebsite', 'community.php'), 'utf8');
    const main = php.match(/<main>[\s\S]*?<\/main>/)[0]
      .replace(/<\?php foreach \(\$posts as \$post\): \?>[\s\S]*?<\?php endforeach; \?>/, await communityPostsHtml())
      .replace(/<\?= [\s\S]*? \?>/g, '')
      .replace(/<\?php[\s\S]*?\?>/g, '');
    const header = `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Retail Zim Community</title><link rel="stylesheet" href="../frontwebsite/css/retailzim-front.css?v=9"></head><body><header class="site-header"><div class="top-line"><span>Retail Zim</span><a href="downloads">Windows POS, mobile selling, stock control, payments, reports, and support.</a></div><nav class="nav-wrap"><a class="brand" href="./"><img src="../frontwebsite/img/retailzim-logo-clean.png" alt="Retail Zim"><span><strong>Retail Zim</strong><small>Retail operating system</small></span></a><div class="nav-links"><a href="platform">Platform</a><a href="how-it-works">How it works</a><a href="payments">Payments</a><a href="downloads">Downloads</a><a class="active" href="community">Community</a><a href="pricing">Pricing</a></div><div class="nav-actions"><a class="btn btn-light" href="https://admin.retailzw.co.zw/auth/shop/login">Shop Login</a><a class="btn btn-primary" href="https://admin.retailzw.co.zw/auth/signup">Get Started</a></div></nav></header>`;
    const footer = `<footer class="site-footer"><div><a class="brand" href="./"><img src="../frontwebsite/img/retailzim-logo-clean.png" alt="Retail Zim"><span><strong>Retail Zim</strong><small>Retail operating system</small></span></a><p>POS, mobile selling, inventory, payments, reports, guides, downloads, and support for modern shops.</p></div><div><h4>Platform</h4><a href="platform">Modules</a><a href="how-it-works">How it works</a><a href="payments">Payments</a></div><div><h4>Customers</h4><a href="community">Community</a><a href="resources">Guides</a><a href="downloads">Downloads</a></div><div><h4>Account</h4><a href="https://admin.retailzw.co.zw/auth/shop/login">Shop Login</a><a href="https://admin.retailzw.co.zw/auth/signup">Register</a></div><div class="footer-bottom"><span>All rights reserved @${new Date().getFullYear()} Powered By <a href="https://cntechnologies.co.zw/" target="_blank" rel="noopener">CN</a></span></div></footer><script src="../frontwebsite/js/retailzim-front.js?v=3"></script></body></html>`;
    res.writeHead(200, { 'content-type': 'text/html; charset=utf-8' });
    res.end(header + main + footer);
  } catch (error) {
    serve(res, path.join(staticRoot, 'community.html'));
  }
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.on('data', chunk => {
      body += chunk;
      if (body.length > 100000) {
        reject(new Error('Body too large'));
        req.destroy();
      }
    });
    req.on('end', () => resolve(body));
    req.on('error', reject);
  });
}

function redirect(res, location) {
  res.writeHead(303, { location });
  res.end();
}

function serve(res, file) {
  fs.readFile(file, (err, data) => {
    if (err) {
      res.writeHead(404, { 'content-type': 'text/plain; charset=utf-8' });
      res.end('Not found');
      return;
    }

    res.writeHead(200, {
      'content-type': mime[path.extname(file).toLowerCase()] || 'application/octet-stream'
    });
    res.end(data);
  });
}

function cleanHtmlLinks(markup) {
  const year = new Date().getFullYear();
  const footerBottom = `<div class="footer-bottom"><span>All rights reserved @${year} Powered By <a href="https://cntechnologies.co.zw/" target="_blank" rel="noopener">CN</a></span></div>`;
  let clean = markup
    .replaceAll('../frontwebsite/img/retailzim-logo.png', '../frontwebsite/img/retailzim-logo-clean.png')
    .replaceAll('css/retailzim-front.css?v=7', 'css/retailzim-front.css?v=9')
    .replaceAll('../frontwebsite/css/retailzim-front.css?v=7', '../frontwebsite/css/retailzim-front.css?v=9')
    .replaceAll('../frontwebsite/css/retailzim-front.css?v=8', '../frontwebsite/css/retailzim-front.css?v=9')
    .replaceAll('href="index.html"', 'href="./"')
    .replaceAll('href="platform.html"', 'href="platform"')
    .replaceAll('href="how-it-works.html"', 'href="how-it-works"')
    .replaceAll('href="payments.html"', 'href="payments"')
    .replaceAll('href="downloads.html"', 'href="downloads"')
    .replaceAll('href="community.html"', 'href="community"')
    .replaceAll('href="pricing.html"', 'href="pricing"')
    .replaceAll('href="resources.html"', 'href="resources"')
    .replaceAll('action="community-post.php"', 'action="community-post"')
    .replaceAll('track.php', 'track')
    .replaceAll('community-engage.php', 'community-engage');
  if (!clean.includes('favicon.png')) {
    clean = clean.replace('</title>', '</title><link rel="icon" type="image/png" href="../frontwebsite/img/favicon.png"><link rel="shortcut icon" href="../frontwebsite/img/favicon.png">');
  }
  const existingFooter = clean.match(/<div class="footer-bottom">[\s\S]*?<\/div>(?=<\/footer>)/);
  if (existingFooter) {
    clean = clean.replace(existingFooter[0], footerBottom);
  } else {
    const footerClose = clean.lastIndexOf('</footer>');
    if (footerClose !== -1) {
      clean = clean.slice(0, footerClose) + footerBottom + clean.slice(footerClose);
    }
  }
  return clean;
}

function serveStaticPage(res, file) {
  fs.readFile(file, 'utf8', (err, data) => {
    if (err) {
      res.writeHead(404, { 'content-type': 'text/plain; charset=utf-8' });
      res.end('Not found');
      return;
    }

    res.writeHead(200, { 'content-type': 'text/html; charset=utf-8' });
    res.end(cleanHtmlLinks(data));
  });
}

http.createServer(async (req, res) => {
  const urlPath = decodeURIComponent((req.url || '/').split('?')[0]);
  let requestPath = urlPath || '/';
  const frontwebsitePhp = requestPath.match(/^\/frontwebsite(\/[^/]+\.php)$/);
  if (frontwebsitePhp && phpRouteMap.has(frontwebsitePhp[1])) {
    requestPath = frontwebsitePhp[1];
  }

  if (phpRouteMap.has(requestPath)) {
    const cleanRoute = phpRouteMap.get(requestPath);
    if (req.method === 'GET' || req.method === 'HEAD') {
      redirect(res, cleanRoute);
      return;
    }
    requestPath = cleanRoute;
  }

  if (requestPath.endsWith('.html')) {
    const cleanRoute = requestPath === '/index.html' ? '/' : requestPath.replace(/\.html$/, '');
    if (pageRoutes.has(cleanRoute)) {
      redirect(res, cleanRoute);
      return;
    }
  }

  if (requestPath === '/community-post') {
    if (req.method !== 'POST') {
      redirect(res, '/community');
      return;
    }

    try {
      const params = new URLSearchParams(await readBody(req));
      const name = String(params.get('name') || '').trim();
      const message = String(params.get('message') || '').trim();
      if (!name || !message) {
        redirect(res, '/community#community-feed');
        return;
      }
      const category = params.get('category') || 'General';
      const shop = params.get('shop') || '';
      await runMysql(`INSERT INTO community_posts (name, shop, category, message, status, created_at)
        VALUES (${sqlString(name, 80)}, ${sqlString(shop, 80)}, ${sqlString(category, 40)}, ${sqlString(message, 1200)}, 'open', UTC_TIMESTAMP())`);
      redirect(res, '/community#community-feed');
    } catch (error) {
      res.writeHead(500, { 'content-type': 'text/plain; charset=utf-8' });
      res.end('Community post failed');
    }
    return;
  }

  if (requestPath === '/community-engage') {
    if (req.method !== 'POST') {
      res.writeHead(405, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ ok: false }));
      return;
    }
    try {
      const params = new URLSearchParams(await readBody(req));
      const postId = Number(params.get('post_id') || 0);
      const action = params.get('action') === 'reply' ? 'reply' : 'like';
      const column = action === 'reply' ? 'replies' : 'likes';
      if (postId > 0) {
        await runMysql(`INSERT INTO community_engagements (post_id, action, visitor_hash, created_at)
          VALUES (${postId}, '${action}', 'local-preview', UTC_TIMESTAMP());
          UPDATE community_posts SET ${column} = ${column} + 1 WHERE id = ${postId};`);
      }
      res.writeHead(200, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ ok: postId > 0 }));
    } catch (error) {
      res.writeHead(500, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ ok: false }));
    }
    return;
  }

  if (requestPath === '/track') {
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end(JSON.stringify({ ok: true }));
    return;
  }

  if (requestPath === '/api/community/posts') {
    try {
      await ensureCommunityAnswerSchema();
      const posts = await mysqlJson(`SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT(
          'id', id,
          'name', name,
          'shop', shop,
          'category', category,
          'message', message,
          'status', status,
          'likes', likes,
          'replies', replies,
          'created_at', created_at,
          'updated_at', updated_at,
          'answers', answers
        )), JSON_ARRAY())
        FROM (
          SELECT p.id, p.name, p.shop, p.category, p.message, p.status, p.likes, p.replies, p.created_at, p.updated_at,
            COALESCE((
              SELECT JSON_ARRAYAGG(JSON_OBJECT('id', a.id, 'post_id', a.post_id, 'responder', a.responder, 'answer', a.answer, 'created_at', a.created_at, 'updated_at', a.updated_at))
              FROM community_answers a
              WHERE a.post_id = p.id
              ORDER BY a.id ASC
            ), JSON_ARRAY()) answers
          FROM community_posts p
          ORDER BY p.id DESC
          LIMIT 100
        ) posts`, []);
      res.writeHead(200, { 'content-type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({ ok: true, base_url: 'https://retailzw.co.zw/', posts }));
    } catch (error) {
      res.writeHead(500, { 'content-type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({ ok: false, posts: [] }));
    }
    return;
  }

  if (requestPath === '/api/community/answer') {
    if (req.method !== 'POST') {
      res.writeHead(405, { 'content-type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({ ok: false, message: 'POST is required.' }));
      return;
    }
    try {
      await ensureCommunityAnswerSchema();
      const rawBody = await readBody(req);
      const data = (req.headers['content-type'] || '').includes('application/json')
        ? JSON.parse(rawBody || '{}')
        : Object.fromEntries(new URLSearchParams(rawBody));
      const postId = Number(data.post_id || 0);
      const answer = String(data.answer || '').trim();
      const responder = String(data.responder || 'Retail Zim Support').trim();
      const status = String(data.status || 'answered').trim();
      if (postId <= 0 || !answer) {
        res.writeHead(422, { 'content-type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify({ ok: false, message: 'Missing post_id or answer.' }));
        return;
      }
      await runMysql(`INSERT INTO community_answers (post_id, responder, answer, created_at)
        VALUES (${postId}, ${sqlString(responder || 'Retail Zim Support', 80)}, ${sqlString(answer, 4000)}, UTC_TIMESTAMP());
        UPDATE community_posts SET status = ${sqlString(status || 'answered', 24)}, replies = replies + 1, updated_at = UTC_TIMESTAMP() WHERE id = ${postId};`);
      res.writeHead(200, { 'content-type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({ ok: true, message: 'Answer saved.' }));
    } catch (error) {
      res.writeHead(500, { 'content-type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({ ok: false, message: 'Answer failed.' }));
    }
    return;
  }

  if (requestPath === '/api/visits/stats') {
    try {
      const metrics = await mysqlJson(`SELECT JSON_OBJECT(
        'visits', (SELECT COUNT(*) FROM site_visits),
        'unique_visitors', (SELECT COUNT(DISTINCT visitor_hash) FROM site_visits),
        'posts', (SELECT COUNT(*) FROM community_posts),
        'engagements', (SELECT COUNT(*) FROM community_engagements)
      )`, { visits: 0, unique_visitors: 0, posts: 0, engagements: 0 });
      const byPage = await mysqlJson(`SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT('page', page, 'visits', visits, 'unique_visitors', unique_visitors)), JSON_ARRAY())
        FROM (SELECT page, COUNT(*) visits, COUNT(DISTINCT visitor_hash) unique_visitors FROM site_visits GROUP BY page ORDER BY visits DESC) pages`, []);
      const recent = await mysqlJson(`SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT('page', page, 'section', section, 'referrer', referrer, 'user_agent', user_agent, 'created_at', created_at)), JSON_ARRAY())
        FROM (SELECT page, section, referrer, user_agent, created_at FROM site_visits ORDER BY id DESC LIMIT 25) visits`, []);
      res.writeHead(200, { 'content-type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({ ok: true, base_url: 'https://retailzw.co.zw/', stats: { metrics, by_page: byPage, daily: [], recent } }));
    } catch (error) {
      res.writeHead(500, { 'content-type': 'application/json; charset=utf-8' });
      res.end(JSON.stringify({ ok: false, stats: { metrics: {}, by_page: [], daily: [], recent: [] } }));
    }
    return;
  }

  if (requestPath === '/community') {
    await renderCommunity(res);
    return;
  }

  if (pageRoutes.has(requestPath)) {
    serveStaticPage(res, path.join(staticRoot, pageRoutes.get(requestPath)));
    return;
  }

  const safeRel = requestPath.replace(/^\/+/, '').replace(/\.\.+/g, '');
  const file = path.join(repo, safeRel);
  if (!file.startsWith(repo)) {
    res.writeHead(403, { 'content-type': 'text/plain; charset=utf-8' });
    res.end('Forbidden');
    return;
  }

  serve(res, file);
}).listen(port, '127.0.0.1', () => {
  console.log(`Retail Zim preview running at http://127.0.0.1:${port}/`);
});
