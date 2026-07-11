<?php
require_once __DIR__ . '/lib/site-content.php';
retailzim_record_visit('how-it-works');
rz_header('How Retail Zim Works | Setup, Sell and Close Shifts', 'Learn how to register a shop, import products, install POS apps, sell, take payments, and close shifts in Retail Zim.', 'how');
?>
<main>
  <section class="page-hero">
    <span class="eyebrow">How it works</span>
    <h1>From setup to first sale in a simple shop workflow.</h1>
    <p>Retail Zim is designed so owners understand the system before they register and cashiers understand what to do at the till.</p>
  </section>
  <section class="section learn-section">
    <div><span class="section-kicker">Steps</span><h2>Follow the flow.</h2><p>Use this page as a simple training guide for shop owners, managers, and cashiers.</p></div>
    <div class="step-list">
      <?php foreach (rz_steps() as $index => $step): ?>
        <article><b><?= $index + 1 ?></b><span><strong><?= htmlspecialchars($step['title']) ?></strong><small><?= htmlspecialchars($step['desc']) ?></small></span></article>
      <?php endforeach; ?>
    </div>
  </section>
</main>
<?php rz_footer(); ?>
