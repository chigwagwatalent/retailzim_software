<?php
require_once __DIR__ . '/lib/site-content.php';
retailzim_record_visit('payments');
rz_header('Retail Zim Payments | Cash, EcoCash, Card and Split Tender', 'Retail Zim helps shops record cash, EcoCash, mobile money, card, bank transfer, customer-account, and split-tender payments.', 'payments');
?>
<main>
  <section class="page-hero payments-hero">
    <div class="page-hero-copy"><span class="eyebrow">Flexible payments</span><h1>Take payment the way your customers pay.</h1><p>Keep checkout flexible and cash-up clearer with payment workflows designed around real Zimbabwean shops.</p><div class="hero-actions"><a class="btn btn-primary" href="<?= htmlspecialchars($registerUrl) ?>">Start accepting sales</a><a class="btn btn-ghost" href="#payment-methods">View methods</a></div></div>
    <div class="checkout-demo" data-checkout-demo>
      <div class="checkout-head"><span>Current sale</span><b>3 items</b></div>
      <ul><li><span>Bread</span><strong>$1.00</strong></li><li><span>Milk 1L</span><strong>$1.35</strong></li><li><span>Cooking Oil</span><strong>$3.50</strong></li></ul>
      <div class="checkout-total"><span>Total due</span><strong>$5.85</strong></div>
      <div class="checkout-methods"><button type="button" class="active">Cash</button><button type="button">EcoCash</button><button type="button">Card</button></div>
      <button class="checkout-pay" type="button">Complete payment</button>
      <small class="checkout-status" aria-live="polite">Choose a payment method</small>
    </div>
  </section>

  <section class="section payments-section" id="payment-methods">
    <div class="section-copy center"><span class="section-kicker">One checkout, many choices</span><h2>Keep every tender visible in your daily reports.</h2></div>
    <div class="payment-grid">
      <?php foreach (rz_payment_types() as $index => $payment): ?>
        <article class="interactive-card"><i class="fa-solid <?= ['fa-money-bill-wave','fa-mobile-screen','fa-credit-card','fa-building-columns','fa-user-clock','fa-arrows-left-right'][$index] ?>"></i><span><?= str_pad((string)($index + 1), 2, '0', STR_PAD_LEFT) ?></span><h3><?= htmlspecialchars($payment['name']) ?></h3><p><?= htmlspecialchars($payment['desc']) ?></p></article>
      <?php endforeach; ?>
    </div>
  </section>

  <section class="section cashup-section">
    <div class="cashup-card"><span class="section-kicker">Clear cash-up</span><h2>Know how every sale was paid.</h2><p>Retail Zim keeps payment methods attached to each sale so managers can compare receipts, shifts and drawer totals without guessing.</p><ul class="check-list"><li>Payment reference capture</li><li>Split-tender breakdown</li><li>Shift and drawer totals</li><li>Customer account balances</li></ul></div>
    <div class="cashup-visual"><div><span>Cash</span><strong>42%</strong></div><div><span>Mobile money</span><strong>31%</strong></div><div><span>Card</span><strong>19%</strong></div><div><span>Other</span><strong>8%</strong></div></div>
  </section>

  <section class="final-cta"><div><span class="section-kicker">Flexible at checkout</span><h2>Give customers choice without losing control.</h2><p>Record every payment clearly and keep the shift easier to reconcile.</p></div><div><a class="btn btn-primary" href="<?= htmlspecialchars($registerUrl) ?>">Get Retail Zim <i class="fa-solid fa-arrow-right"></i></a></div></section>
</main>
<?php rz_footer(); ?>
