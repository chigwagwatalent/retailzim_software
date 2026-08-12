<?php
require_once __DIR__ . '/lib/site-content.php';
retailzim_record_visit('resources');
rz_header('Retail Zim Resources | Setup Guides and Training', 'Use Retail Zim guides for product imports, cashier shifts, downloads, payments, receipts, and daily retail workflows.', 'community');
?>
<main>
  <section class="page-hero resources-hero"><div class="page-hero-copy"><span class="eyebrow">Guides and training</span><h1>Practical help for owners, managers and cashiers.</h1><p>Prepare the system, train your team and solve everyday POS, stock, payment and receipt questions.</p><div class="hero-actions"><a class="btn btn-primary" href="#guides">Browse guides</a><a class="btn btn-ghost" href="<?= htmlspecialchars(rz_url('community')) ?>">Ask the community</a></div></div><div class="resource-stack"><i class="fa-solid fa-book-open"></i><span>Setup guides</span><span>Cashier training</span><span>Release notes</span></div></section>
  <section class="section resources-section" id="guides">
    <div class="section-copy center"><span class="section-kicker">Start learning</span><h2>Help for the moments that matter in your shop.</h2></div>
    <div class="resource-grid">
      <?php foreach (rz_resources() as $index => $resource): ?>
        <article class="resource-card"><div><b><?= htmlspecialchars($resource['tag']) ?></b><span><?= str_pad((string)($index + 1), 2, '0', STR_PAD_LEFT) ?></span></div><i class="fa-solid <?= ['fa-file-excel','fa-cash-register','fa-download'][$index] ?>"></i><h3><?= htmlspecialchars($resource['title']) ?></h3><p><?= htmlspecialchars($resource['desc']) ?></p><a href="<?= htmlspecialchars($index === 2 ? rz_url('downloads') : rz_url('how-it-works')) ?>">Open resource <i class="fa-solid fa-arrow-right"></i></a></article>
      <?php endforeach; ?>
    </div>
  </section>
  <section class="section support-banner"><div><span class="section-kicker">Need a human answer?</span><h2>Join the Retail Zim community.</h2><p>Post a question, comment on discussions and get guidance from support and other retailers.</p></div><a class="btn btn-primary" href="<?= htmlspecialchars(rz_url('community')) ?>">Visit community <i class="fa-solid fa-comments"></i></a></section>
</main>
<?php rz_footer(); ?>
