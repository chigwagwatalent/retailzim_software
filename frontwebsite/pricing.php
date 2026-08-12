<?php
require_once __DIR__ . '/lib/site-content.php';
retailzim_record_visit('pricing');
rz_header('Retail Zim Pricing | Plans from USD 5 per Month', 'Choose Retail Zim Starter, Growth, Business, or Enterprise pricing for your shop or multi-branch retail operation.', 'pricing');
?>
<main>
  <section class="page-hero pricing-hero">
    <div class="page-hero-copy"><span class="eyebrow">Simple monthly pricing</span><h1>Start affordably and grow without changing systems.</h1><p>Choose the package that matches your operation today. Upgrade your users, branches and retail tools whenever the business is ready.</p><div class="hero-actions"><a class="btn btn-primary" href="#pricing-packages">Compare packages <i class="fa-solid fa-arrow-down"></i></a><a class="btn btn-ghost" href="mailto:sales@retailzw.co.zw">Talk to sales</a></div></div>
    <div class="pricing-orb"><i class="fa-solid fa-arrow-trend-up"></i><strong>Plans from USD 5</strong><span>One shop to many branches</span></div>
  </section>

  <section class="section plans" id="pricing-packages">
    <div class="section-copy center"><span class="section-kicker">Choose your fit</span><h2>A clear package for every stage of retail growth.</h2><p>All prices are billed monthly in United States dollars.</p></div>
    <div class="pricing-trust-row" aria-label="Package benefits"><span><i class="fa-solid fa-check"></i>No complicated setup</span><span><i class="fa-solid fa-check"></i>Upgrade at any time</span><span><i class="fa-solid fa-check"></i>Secure cloud access</span><span><i class="fa-solid fa-check"></i>Professional support</span></div>
    <div class="plan-grid">
      <?php foreach (rz_plans() as $plan): ?>
        <?php $popular = !empty($plan['popular']); ?>
        <article class="plan-card <?= $popular ? 'featured-plan' : '' ?>">
          <?php if ($popular): ?><span class="popular-ribbon">Most popular</span><?php endif; ?>
          <span class="plan-label"><?= htmlspecialchars($plan['label']) ?></span>
          <h3><?= htmlspecialchars($plan['name']) ?></h3>
          <p class="plan-summary"><?= htmlspecialchars($plan['summary']) ?></p>
          <div class="plan-price"><sup>$</sup><strong><?= htmlspecialchars($plan['amount']) ?></strong><span>/ month</span></div>
          <small class="billing-note">Billed monthly in United States dollars.</small>
          <div class="plan-limits">
            <span><i class="fa-solid fa-user"></i><?= htmlspecialchars($plan['admins']) ?></span>
            <span><i class="fa-solid fa-store"></i><?= htmlspecialchars($plan['branches']) ?></span>
            <span><i class="fa-solid fa-users"></i><?= htmlspecialchars($plan['cashiers']) ?></span>
          </div>
          <strong class="plan-features-title">Package includes:</strong>
          <ul><?php foreach ($plan['items'] as $item): ?><li><i class="fa-solid fa-check"></i><?= htmlspecialchars($item) ?></li><?php endforeach; ?></ul>
          <a class="btn <?= $popular ? 'btn-primary' : 'btn-ghost' ?>" href="<?= htmlspecialchars($registerUrl) ?>">Choose <?= htmlspecialchars($plan['name']) ?></a>
        </article>
      <?php endforeach; ?>
    </div>
    <p class="pricing-note">Need help selecting the right package? <a href="mailto:sales@retailzw.co.zw">Talk to our sales team.</a></p>
  </section>
  <section class="final-cta"><div><span class="section-kicker">Grow with confidence</span><h2>Your Retail Zim package grows with your business.</h2><p>Start from USD 5 per month and upgrade whenever your team needs more capacity.</p></div><div><a class="btn btn-primary" href="<?= htmlspecialchars($registerUrl) ?>">Start now <i class="fa-solid fa-arrow-right"></i></a></div></section>
</main>
<?php rz_footer(); ?>
