<?php
require_once __DIR__ . '/lib/site-content.php';
retailzim_record_visit('pricing');
rz_header('Retail Zim Pricing | Shop Plans', 'Choose a Retail Zim plan for one shop, busy retail teams, or multi-branch groups.', 'pricing');
?>
<main>
  <section class="page-hero">
    <span class="eyebrow">Pricing</span>
    <h1>Start small and grow into a full retail platform.</h1>
    <p>Pick the plan that fits your shop today, then add more users, branches, and tools as you grow.</p>
  </section>
  <section class="section plans">
    <div class="plan-grid">
      <?php foreach (rz_plans() as $plan): ?>
        <article><h3><?= htmlspecialchars($plan['name']) ?></h3><strong><?= htmlspecialchars($plan['price']) ?></strong><ul><?php foreach ($plan['items'] as $item): ?><li><?= htmlspecialchars($item) ?></li><?php endforeach; ?></ul><a class="btn btn-light" href="<?= htmlspecialchars($registerUrl) ?>">Choose plan</a></article>
      <?php endforeach; ?>
    </div>
  </section>
</main>
<?php rz_footer(); ?>
