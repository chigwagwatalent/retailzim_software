<?php
require_once __DIR__ . '/lib/site-content.php';
retailzim_record_visit('platform');
rz_header('Retail Zim Platform | POS, Stock and Shop Management', 'Explore Retail Zim Windows POS, mobile selling, inventory, payments, shop reporting, purchasing, wholesale pricing, and multi-branch tools.', 'platform');
?>
<main>
  <section class="page-hero platform-hero">
    <div class="page-hero-copy"><span class="eyebrow">Platform modules</span><h1>One retail system for the counter, stockroom, and office.</h1><p>Every Retail Zim tool connects to the same products, stock, customers and branches—so your team can work from one reliable view.</p><div class="hero-actions"><a class="btn btn-primary" href="<?= htmlspecialchars($registerUrl) ?>">Start with Retail Zim</a><a class="btn btn-ghost" href="#modules">Explore modules</a></div></div>
    <div class="hero-visual-card"><img src="img/showcase/shop-dashboard.png" alt="Retail Zim shop operations dashboard"><span><i class="fa-solid fa-lock"></i> Secure shop workspace</span></div>
  </section>

  <section class="section modules-section" id="modules">
    <div class="section-copy center"><span class="section-kicker">Connected by design</span><h2>Tools that follow the daily retail workflow.</h2><p>Sell, receive, transfer, reconcile and report without maintaining separate systems.</p></div>
    <div class="module-grid numbered-grid">
      <?php foreach (rz_modules() as $module): ?>
        <article class="interactive-card"><div class="module-top"><span><?= htmlspecialchars($module['code']) ?></span><i class="fa-solid <?= htmlspecialchars($module['icon']) ?>"></i></div><h3><?= htmlspecialchars($module['title']) ?></h3><p><?= htmlspecialchars($module['desc']) ?></p></article>
      <?php endforeach; ?>
    </div>
  </section>

  <section class="section feature-split">
    <div class="split-visual"><img src="img/showcase/windows-pos-checkout.png" alt="Retail Zim Windows POS"></div>
    <div class="split-copy"><span class="section-kicker">Fast at the till</span><h2>Checkout that keeps the queue moving.</h2><p>Search or scan products, use retail or wholesale prices, capture flexible payments and print clear receipts from a cashier-friendly screen.</p><ul class="check-list"><li>Barcode and product search</li><li>Cashier shifts and cash-up</li><li>Returns and held change</li><li>Offline-ready sales sync</li></ul></div>
  </section>

  <section class="section feature-split reverse">
    <div class="split-visual dashboard-visual"><img src="img/showcase/shop-dashboard.png" alt="Retail Zim shop dashboard"></div>
    <div class="split-copy"><span class="section-kicker">Clear for managers</span><h2>See what is happening inside your shop.</h2><p>The customer-facing platform shows the shop dashboard only. It gives authorised shop owners and managers visibility into sales, products, alerts, cashiers, branches and reports.</p><ul class="check-list"><li>Sales overview and recent activity</li><li>Stock health and reorder alerts</li><li>Branch and cashier performance</li><li>Role-based shop access</li></ul></div>
  </section>

  <section class="final-cta"><div><span class="section-kicker">Your retail workspace</span><h2>Bring every branch into one operating rhythm.</h2><p>Start with the tools you need and expand as your business grows.</p></div><div><a class="btn btn-primary" href="<?= htmlspecialchars($registerUrl) ?>">Register your shop <i class="fa-solid fa-arrow-right"></i></a></div></section>
</main>
<?php rz_footer(); ?>
