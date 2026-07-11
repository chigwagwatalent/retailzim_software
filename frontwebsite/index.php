<?php
require_once __DIR__ . '/lib/site-content.php';
retailzim_record_visit('home');
rz_header('Retail Zim | POS and Inventory Platform for Shops', 'Retail Zim helps shops sell faster with Windows POS, mobile POS, inventory, payments, cloud reports, downloads, guides, and community support.', 'home');
?>
<main id="top">
  <section class="story-hero" data-track-section="home-hero">
    <div class="hero-panel">
      <span class="eyebrow">Retail software for real shops</span>
      <h1>Run your shop from the till, the phone, and the cloud.</h1>
      <p>Retail Zim helps cashiers sell faster, managers control stock, and owners understand what is happening across the business.</p>
      <div class="hero-actions">
        <a class="btn btn-primary" href="<?= htmlspecialchars($registerUrl) ?>">Register your shop</a>
        <a class="btn btn-light" href="<?= htmlspecialchars(rz_url('platform')) ?>">Explore the platform</a>
      </div>
      <div class="trust-row">
        <span>Windows POS</span><span>Mobile POS</span><span>Inventory</span><span>Payments</span><span>Reports</span>
      </div>
    </div>
    <div class="hero-showcase">
      <img class="people-shot" src="img/banners/retail-counter-windows-pos.png" alt="Shop cashier using Retail Zim">
      <figure class="screen-card desktop-screen"><img src="img/showcase/windows-pos-checkout.png" alt="Retail Zim Windows POS checkout"></figure>
      <figure class="screen-card phone-screen"><img src="img/showcase/mobile-pos-checkout.png" alt="Retail Zim mobile POS checkout"></figure>
    </div>
  </section>

  <section class="metrics-band customer-band">
    <article><strong>Sell</strong><span>faster checkout at the counter</span></article>
    <article><strong>Track</strong><span>stock, shifts, and payments</span></article>
    <article><strong>Learn</strong><span>guides and community answers</span></article>
    <article><strong>Grow</strong><span>from one shop to many branches</span></article>
  </section>

  <section class="section product-story">
    <div class="section-copy">
      <span class="section-kicker">Why Retail Zim</span>
      <h2>Everything connects around the daily sale.</h2>
      <p>A good retail system should make the till simple, stock visible, payments clear, and reports useful.</p>
    </div>
    <div class="story-grid">
      <article><b>1</b><h3>Cashiers sell</h3><p>Search products, build carts, take payment, and print receipts.</p></article>
      <article><b>2</b><h3>Managers control stock</h3><p>Import products, update quantities, and monitor stock movement.</p></article>
      <article><b>3</b><h3>Owners see reports</h3><p>Use the cloud dashboard to review sales, shops, users, and activity.</p></article>
      <article><b>4</b><h3>Teams get support</h3><p>Read guides, ask questions, and follow product updates.</p></article>
    </div>
  </section>

  <section class="section product-proof">
    <div class="section-copy">
      <span class="section-kicker">Product preview</span>
      <h2>See the tools before you download.</h2>
      <p>Windows POS, mobile POS, and cloud admin work together for the same shop.</p>
    </div>
    <div class="proof-grid">
      <article class="wide"><img src="img/showcase/admin-dashboard.png" alt="Retail Zim dashboard"><h3>Cloud dashboard</h3><p>Review shops, reports, users, plans, and support activity.</p></article>
      <article><img src="img/showcase/windows-pos-checkout.png" alt="Windows POS checkout"><h3>Windows checkout</h3><p>Counter sales with products, cart totals, and payment.</p></article>
      <article><img src="img/showcase/mobile-pos-checkout.png" alt="Mobile POS checkout"><h3>Mobile POS</h3><p>Phone selling and product lookup for busy shop floors.</p></article>
    </div>
  </section>

  <section class="section customer-journey">
    <div class="section-copy center">
      <span class="section-kicker">Choose your path</span>
      <h2>Where do you want to go next?</h2>
    </div>
    <div class="page-grid">
      <a href="<?= htmlspecialchars(rz_url('platform')) ?>"><b>Platform</b><span>Explore every Retail Zim module.</span></a>
      <a href="<?= htmlspecialchars(rz_url('how-it-works')) ?>"><b>How it works</b><span>Learn the setup and selling flow.</span></a>
      <a href="<?= htmlspecialchars(rz_url('payments')) ?>"><b>Payments</b><span>See supported payment workflows.</span></a>
      <a href="<?= htmlspecialchars(rz_url('downloads')) ?>"><b>Downloads</b><span>Get the Windows and mobile apps.</span></a>
      <a href="<?= htmlspecialchars(rz_url('community')) ?>"><b>Community</b><span>Ask questions and read answers.</span></a>
      <a href="<?= htmlspecialchars(rz_url('pricing')) ?>"><b>Pricing</b><span>Pick the right package.</span></a>
    </div>
  </section>
</main>
<?php rz_footer(); ?>
