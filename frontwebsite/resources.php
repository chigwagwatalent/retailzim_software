<?php
require_once __DIR__ . '/lib/site-content.php';
retailzim_record_visit('resources');
rz_header('Retail Zim Resources | Guides, Training and Releases', 'Read Retail Zim guides for product imports, cashier shifts, downloads, payments, and retail workflows.', 'community');
?>
<main>
  <section class="page-hero">
    <span class="eyebrow">Resources</span>
    <h1>Guides for owners, managers, and cashiers.</h1>
    <p>Use these resources to understand the platform, train staff, and keep your shop running smoothly.</p>
  </section>
  <section class="section resources-section">
    <div class="resource-grid">
      <?php foreach (rz_resources() as $resource): ?>
        <article><b><?= htmlspecialchars($resource['tag']) ?></b><h3><?= htmlspecialchars($resource['title']) ?></h3><p><?= htmlspecialchars($resource['desc']) ?></p></article>
      <?php endforeach; ?>
    </div>
  </section>
</main>
<?php rz_footer(); ?>
