<?php
require_once __DIR__ . '/lib/site-content.php';
retailzim_record_visit('how-it-works');
rz_header('How Retail Zim Works | Set Up, Sell and Review', 'Learn how to register a shop, import products, install Retail Zim, sell, take payments, and review shift and business reports.', 'how');
?>
<main>
  <section class="page-hero how-hero">
    <div class="page-hero-copy"><span class="eyebrow">How it works</span><h1>From setup to first sale in a clear shop workflow.</h1><p>Retail Zim is designed so owners understand the setup, managers stay in control, and cashiers always know the next step.</p><a class="btn btn-primary" href="#steps">See the five steps <i class="fa-solid fa-arrow-down"></i></a></div>
    <div class="process-orbit" aria-label="Retail Zim workflow"><i class="fa-solid fa-store"></i><span class="orbit-one">Products</span><span class="orbit-two">Sales</span><span class="orbit-three">Reports</span></div>
  </section>

  <section class="section workflow-section" id="steps">
    <div class="section-copy center"><span class="section-kicker">Your launch path</span><h2>Five steps. One connected result.</h2><p>Use the process as your onboarding checklist and a simple training guide for the team.</p></div>
    <div class="workflow-list">
      <?php foreach (rz_steps() as $index => $step): ?>
        <article class="workflow-step"><b><?= str_pad((string)($index + 1), 2, '0', STR_PAD_LEFT) ?></b><div><span>Step <?= $index + 1 ?></span><h3><?= htmlspecialchars($step['title']) ?></h3><p><?= htmlspecialchars($step['desc']) ?></p></div><i class="fa-solid <?= ['fa-building-circle-check','fa-file-excel','fa-download','fa-cart-plus','fa-chart-line'][$index] ?>"></i></article>
      <?php endforeach; ?>
    </div>
  </section>

  <section class="section role-section">
    <div class="section-copy center"><span class="section-kicker">Everyone has a clear role</span><h2>Simple for cashiers. Useful for managers. Clear for owners.</h2></div>
    <div class="role-grid">
      <article><i class="fa-solid fa-cash-register"></i><h3>Cashiers</h3><p>Open a shift, search or scan products, accept payment, print a receipt and close accurately.</p></article>
      <article><i class="fa-solid fa-boxes-packing"></i><h3>Managers</h3><p>Receive stock, monitor alerts, approve adjustments and keep daily operations moving.</p></article>
      <article><i class="fa-solid fa-chart-pie"></i><h3>Owners</h3><p>Review shop and branch performance, user activity, stock health and business reports.</p></article>
    </div>
  </section>

  <section class="final-cta"><div><span class="section-kicker">Begin your setup</span><h2>Your first Retail Zim sale starts here.</h2><p>Create your shop, prepare your products and bring the team onboard.</p></div><div><a class="btn btn-primary" href="<?= htmlspecialchars($registerUrl) ?>">Create your account <i class="fa-solid fa-arrow-right"></i></a><a class="text-link" href="<?= htmlspecialchars(rz_url('community')) ?>">Ask a setup question</a></div></section>
</main>
<?php rz_footer(); ?>
