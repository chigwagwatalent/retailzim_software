<?php
require_once __DIR__ . '/lib/site-content.php';
retailzim_record_visit('downloads');
$windowsExeAvailable = is_file(__DIR__ . '/' . $windowsExe);
$windowsZipAvailable = is_file(__DIR__ . '/' . $windowsZip);
rz_header('Retail Zim Downloads | Windows POS and Mobile POS', 'Download Retail Zim Windows POS installer, portable ZIP, and mobile POS app links for shop devices.', 'downloads');
?>
<main>
  <section class="page-hero">
    <span class="eyebrow">Downloads</span>
    <h1>Install Retail Zim where your team works.</h1>
    <p>Use Windows POS at the counter and mobile POS for stock checks, quick selling, and manager visibility.</p>
  </section>
  <section class="section downloads">
    <div class="download-grid">
      <article><b>W</b><h3>Windows POS for tills</h3><p>Installer for shop computers, cashiers, receipt printing, shifts, and product imports.</p><div class="tags"><span>Windows</span><span>Thermal printers</span><span>Standalone POS</span></div><div class="card-actions"><?php if ($windowsExeAvailable): ?><a class="btn btn-primary" href="<?= htmlspecialchars($windowsExe) ?>" download>Download EXE</a><?php else: ?><span class="btn btn-primary disabled">EXE unavailable</span><?php endif; ?><?php if ($windowsZipAvailable): ?><a class="btn btn-light" href="<?= htmlspecialchars($windowsZip) ?>" download>Portable ZIP</a><?php else: ?><span class="btn btn-light disabled">ZIP unavailable</span><?php endif; ?></div></article>
      <article><b>M</b><h3>Mobile POS app</h3><p>Phone selling, stock checks, customer support, and daily activity on the move.</p><div class="tags"><span>Android</span><span>Mobile POS</span><span>Cloud sync</span></div><div class="card-actions"><a class="btn btn-primary" href="<?= htmlspecialchars(rz_url('community')) ?>">Request APK</a><a class="btn btn-light" href="<?= htmlspecialchars(rz_url('community')) ?>">Ask support</a></div></article>
    </div>
  </section>
</main>
<?php rz_footer(); ?>
