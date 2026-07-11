<?php
require_once __DIR__ . '/lib/site-content.php';
retailzim_record_visit('payments');
rz_header('Retail Zim Payments | Cash, Card, EcoCash and Split Tender', 'Retail Zim helps shops record cash, mobile money, card, bank transfer, customer account, and split tender payments.', 'payments');
?>
<main>
  <section class="page-hero">
    <span class="eyebrow">Payments</span>
    <h1>Take payment the way your customers pay.</h1>
    <p>Keep checkout flexible and cash-up clearer with payment types built around real shop workflows.</p>
  </section>
  <section class="section payments-section">
    <div class="payment-grid">
      <?php foreach (rz_payment_types() as $payment): ?>
        <article><h3><?= htmlspecialchars($payment['name']) ?></h3><p><?= htmlspecialchars($payment['desc']) ?></p></article>
      <?php endforeach; ?>
    </div>
  </section>
</main>
<?php rz_footer(); ?>
