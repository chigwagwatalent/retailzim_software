<?php
require_once __DIR__ . '/lib/site-content.php';
retailzim_record_visit('home');
rz_header('Retail Zim | Connected POS and Retail Management', 'Retail Zim connects Windows POS, mobile selling, inventory, payments, shop reporting, and multi-branch controls for Zimbabwean retailers.', 'home');
?>
<main id="top">
  <section class="story-hero" data-track-section="home-hero">
    <div class="hero-photo hero-photo-left" aria-hidden="true"></div>
    <div class="hero-photo hero-photo-right" aria-hidden="true"></div>
    <div class="hero-panel">
      <span class="eyebrow">Retail software for real shops</span>
      <h1>Run your shop from <span>the till, the phone, and the cloud.</span></h1>
      <p>Retail Zim connects sales, stock, payments and reports in one powerful platform built for Zimbabwean businesses.</p>
      <div class="hero-actions">
        <a class="btn btn-primary" href="<?= htmlspecialchars($registerUrl) ?>">Register Your Shop <i class="fa-solid fa-arrow-right"></i></a>
        <a class="btn btn-ghost" href="<?= htmlspecialchars(rz_url('platform')) ?>">Explore Platform</a>
      </div>
    </div>
    <div class="hero-product-stage" aria-label="Retail Zim product preview">
      <figure class="device-frame device-laptop">
        <div class="device-bar"><i></i><i></i><i></i><span>Windows POS</span></div>
        <img src="img/showcase/windows-pos-checkout.png" alt="Retail Zim Windows point of sale checkout">
      </figure>
      <figure class="device-frame device-phone">
        <img src="img/showcase/mobile-pos-checkout.png" alt="Retail Zim mobile point of sale">
      </figure>
      <figure class="device-frame device-dashboard">
        <div class="device-bar"><i></i><i></i><i></i><span>Shop dashboard</span></div>
        <img src="img/showcase/shop-dashboard.png" alt="Retail Zim shop management dashboard showing sales and inventory">
      </figure>
    </div>
  </section>

  <section class="capability-band" aria-label="Retail Zim capabilities">
    <span><i class="fa-solid fa-desktop"></i>Windows POS</span>
    <span><i class="fa-solid fa-mobile-screen-button"></i>Mobile POS</span>
    <span><i class="fa-solid fa-boxes-stacked"></i>Inventory</span>
    <span><i class="fa-solid fa-wallet"></i>Payments</span>
    <span><i class="fa-solid fa-chart-line"></i>Reports</span>
  </section>

  <section class="section platform-overview" data-track-section="connected-platform">
    <div class="section-copy center">
      <span class="section-kicker">One connected platform</span>
      <h2>Everything your shop needs to sell, track and grow.</h2>
      <p>Give cashiers a fast checkout, managers accurate stock, and owners a clear view of performance.</p>
    </div>
    <div class="module-grid numbered-grid">
      <?php foreach (rz_modules() as $module): ?>
        <article class="interactive-card">
          <div class="module-top"><span><?= htmlspecialchars($module['code']) ?></span><i class="fa-solid <?= htmlspecialchars($module['icon']) ?>"></i></div>
          <h3><?= htmlspecialchars($module['title']) ?></h3>
          <p><?= htmlspecialchars($module['desc']) ?></p>
          <a href="<?= htmlspecialchars(rz_url('platform')) ?>">Explore feature <i class="fa-solid fa-arrow-right"></i></a>
        </article>
      <?php endforeach; ?>
    </div>
  </section>

  <section class="section product-suite" data-track-section="product-suite">
    <div class="section-copy center">
      <span class="section-kicker">Built for how you work</span>
      <h2>One platform. Three powerful ways to run your shop.</h2>
    </div>
    <div class="suite-grid">
      <article class="suite-card">
        <div class="suite-media"><img src="img/showcase/windows-pos-checkout.png" alt="Windows POS checkout interface"></div>
        <span>At the counter</span><h3>Windows POS</h3><p>Fast product search, barcode sales, shifts, payments and receipt printing for your main till.</p>
      </article>
      <article class="suite-card featured-suite">
        <div class="suite-media phone-media"><img src="img/showcase/mobile-pos-checkout.png" alt="Mobile POS checkout interface"></div>
        <span>On the shop floor</span><h3>Mobile POS</h3><p>Sell, look up products and check stock from an Android phone without leaving the customer.</p>
      </article>
      <article class="suite-card">
        <div class="suite-media"><img src="img/showcase/shop-dashboard.png" alt="Shop dashboard for sales and stock management"></div>
        <span>In the office</span><h3>Shop dashboard</h3><p>Monitor sales, inventory health, cashiers, recent activity and branches from one secure workspace.</p>
      </article>
    </div>
  </section>

  <section class="section setup-journey" data-track-section="setup-journey">
    <div class="section-copy center"><span class="section-kicker">From setup to success</span><h2>From registration to first sale.</h2><p>Follow a clear onboarding path your owner, managers and cashiers can understand.</p></div>
    <ol class="journey-line">
      <?php foreach (rz_steps() as $index => $step): ?>
        <li><b><?= str_pad((string)($index + 1), 2, '0', STR_PAD_LEFT) ?></b><i class="fa-solid <?= ['fa-user-plus','fa-box-open','fa-cloud-arrow-down','fa-cart-shopping','fa-chart-column'][$index] ?>"></i><strong><?= htmlspecialchars($step['title']) ?></strong><small><?= htmlspecialchars($step['desc']) ?></small></li>
      <?php endforeach; ?>
    </ol>
  </section>

  <section class="section payments-preview" data-track-section="payments">
    <div class="section-copy center"><span class="section-kicker">Flexible checkout</span><h2>Accept the payment methods your customers use.</h2></div>
    <div class="payment-pills">
      <?php foreach (rz_payment_types() as $index => $payment): ?>
        <a href="<?= htmlspecialchars(rz_url('payments')) ?>"><i class="fa-solid <?= ['fa-money-bill-wave','fa-mobile-screen','fa-credit-card','fa-building-columns','fa-user-clock','fa-arrows-left-right'][$index] ?>"></i><?= htmlspecialchars($payment['name']) ?></a>
      <?php endforeach; ?>
    </div>
  </section>

  <section class="final-cta" data-track-section="home-cta">
    <div><span class="section-kicker">Start strong</span><h2>Ready to run your shop smarter?</h2><p>Connect your sales, stock and team with Retail Zim.</p></div>
    <div><a class="btn btn-primary" href="<?= htmlspecialchars($registerUrl) ?>">Get Started Today <i class="fa-solid fa-arrow-right"></i></a><a class="text-link" href="<?= htmlspecialchars(rz_url('community')) ?>">Ask the community</a></div>
  </section>
</main>
<?php rz_footer(); ?>
