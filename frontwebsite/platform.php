<?php
require_once __DIR__ . '/lib/site-content.php';
retailzim_record_visit('platform');
rz_header('Retail Zim Platform | POS, Stock, Reports and Support', 'Explore the Retail Zim platform modules for Windows POS, mobile selling, inventory, payments, cloud reporting, and community support.', 'platform');
?>
<main>
  <section class="page-hero">
    <span class="eyebrow">Platform modules</span>
    <h1>One retail system for the counter, stockroom, and office.</h1>
    <p>Retail Zim gives every part of your shop a clear job: sell, track, report, support, and grow.</p>
  </section>
  <section class="section modules-section">
    <div class="module-grid">
      <?php foreach (rz_modules() as $module): ?>
        <article><b><?= htmlspecialchars($module['code']) ?></b><h3><?= htmlspecialchars($module['title']) ?></h3><p><?= htmlspecialchars($module['desc']) ?></p></article>
      <?php endforeach; ?>
    </div>
  </section>
  <section class="section product-proof compact-proof">
    <div class="section-copy"><span class="section-kicker">Screens</span><h2>Your shop tools, side by side.</h2></div>
    <div class="proof-grid">
      <article class="wide"><img src="img/showcase/admin-dashboard.png" alt="Retail Zim dashboard"><h3>Cloud dashboard</h3></article>
      <article><img src="img/showcase/windows-pos-checkout.png" alt="Windows POS"><h3>Windows POS</h3></article>
      <article><img src="img/showcase/mobile-pos-checkout.png" alt="Mobile POS"><h3>Mobile POS</h3></article>
    </div>
  </section>
</main>
<?php rz_footer(); ?>
