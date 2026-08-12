<?php
require_once __DIR__ . '/lib/site-content.php';
retailzim_record_visit('downloads');
$releases = rz_release_catalog();
$platforms = ['WINDOWS' => [], 'ANDROID' => []];
foreach ($releases as $release) {
    $platform = strtoupper((string) ($release['platform'] ?? ''));
    if (isset($platforms[$platform])) $platforms[$platform][] = $release;
}
$releaseCount = count($releases);
rz_header('Retail Zim App Downloads | Windows and Android', 'Download verified Retail Zim Windows POS and Android Mobile POS versions with release notes, requirements and file integrity details.', 'downloads');
?>
<main class="release-page">
  <section class="release-hero">
    <div class="release-hero-copy">
      <span class="eyebrow"><i class="fa-solid fa-shield-halved"></i> Official Retail Zim downloads</span>
      <h1>Your shop apps.<br><span>Ready when you are.</span></h1>
      <p>Choose a verified Windows or Android build, review exactly what changed, then download it directly to your shop device.</p>
      <div class="release-hero-actions"><a class="btn btn-primary" href="#app-downloads">View latest versions <i class="fa-solid fa-arrow-down"></i></a><a class="btn btn-ghost" href="mailto:support@retailzw.co.zw">Installation support</a></div>
      <div class="release-trust-row"><span><i class="fa-solid fa-check"></i>Validated packages</span><span><i class="fa-solid fa-fingerprint"></i>SHA-256 integrity</span><span><i class="fa-solid fa-rotate"></i>Version history</span></div>
    </div>
    <div class="release-hero-visual" aria-label="Retail Zim apps for Windows and Android">
      <div class="release-glow"></div>
      <div class="release-device release-desktop"><div class="release-device-bar"><i></i><i></i><i></i></div><img src="img/showcase/windows-pos-checkout.png" alt="Retail Zim Windows POS checkout"><span><i class="fa-brands fa-windows"></i>Windows POS <b>Counter ready</b></span></div>
      <div class="release-device release-mobile"><img src="img/showcase/mobile-pos-checkout.png" alt="Retail Zim Android mobile POS"><span><i class="fa-brands fa-android"></i>Android</span></div>
      <div class="release-version-float"><i class="fa-solid fa-cloud-arrow-down"></i><span><small>Available builds</small><strong><?= htmlspecialchars((string) $releaseCount) ?> verified version<?= $releaseCount === 1 ? '' : 's' ?></strong></span></div>
    </div>
  </section>

  <section id="app-downloads" class="section release-catalogue">
    <div class="section-copy center"><span class="section-kicker">Choose your platform</span><h2>One connected shop. The right app for every device.</h2><p>Version details, requirements and download packages are managed centrally by Retail Zim administrators.</p></div>
    <div class="release-platform-tabs" role="tablist" aria-label="Download platforms">
      <button class="active" type="button" role="tab" aria-selected="true" data-release-tab="WINDOWS"><i class="fa-brands fa-windows"></i><span><b>Windows POS</b><small><?= count($platforms['WINDOWS']) ?> package<?= count($platforms['WINDOWS']) === 1 ? '' : 's' ?></small></span></button>
      <button type="button" role="tab" aria-selected="false" data-release-tab="ANDROID"><i class="fa-brands fa-android"></i><span><b>Android Mobile POS</b><small><?= count($platforms['ANDROID']) ?> package<?= count($platforms['ANDROID']) === 1 ? '' : 's' ?></small></span></button>
    </div>

    <?php foreach ($platforms as $platform => $items): ?>
    <div class="release-platform-panel<?= $platform === 'WINDOWS' ? ' active' : '' ?>" data-release-panel="<?= htmlspecialchars($platform) ?>" <?= $platform === 'WINDOWS' ? '' : 'hidden' ?>>
      <div class="release-panel-heading"><div><span class="release-platform-icon <?= strtolower($platform) ?>"><i class="fa-brands fa-<?= $platform === 'WINDOWS' ? 'windows' : 'android' ?>"></i></span><span><small><?= $platform === 'WINDOWS' ? 'FOR SHOP COUNTERS' : 'FOR PHONES AND TABLETS' ?></small><h3><?= $platform === 'WINDOWS' ? 'Retail Zim Windows POS' : 'Retail Zim Mobile POS' ?></h3></span></div><p><?= $platform === 'WINDOWS' ? 'Fast counter sales, barcode scanning, cashier shifts, receipt printing and product imports.' : 'Mobile selling, product search and live stock visibility from Android shop-floor devices.' ?></p></div>
      <?php if (empty($items)): ?>
        <div class="release-coming-soon"><span><i class="fa-brands fa-<?= $platform === 'WINDOWS' ? 'windows' : 'android' ?>"></i></span><h3>The next <?= strtolower($platform) ?> build is being prepared.</h3><p>No public package has been published for this platform yet. Contact support if your team needs early access.</p><a class="btn btn-primary" href="mailto:support@retailzw.co.zw?subject=Retail%20Zim%20<?= rawurlencode($platform) ?>%20release">Notify me when it is ready</a></div>
      <?php else: ?>
        <div class="release-card-grid">
        <?php foreach ($items as $index => $release):
          $latest = !empty($release['latest']);
          $packageType = strtoupper((string) ($release['packageType'] ?? 'PACKAGE'));
          $packageLabel = match ($packageType) { 'PORTABLE_ZIP' => 'Portable ZIP', 'APK' => 'Android APK', default => 'Windows installer' };
          $releaseDate = !empty($release['releasedAt']) ? date('d M Y', strtotime((string) $release['releasedAt'])) : 'Current release';
          $notes = preg_split('/\r\n|\r|\n/', trim((string) ($release['releaseNotes'] ?? '')));
          $notes = array_values(array_filter(array_map('trim', $notes)));
        ?>
          <article class="release-product-card<?= $latest ? ' latest' : '' ?>">
            <header><div class="release-product-badges"><?php if ($latest): ?><span class="release-latest"><i class="fa-solid fa-star"></i>Recommended</span><?php endif; ?><span><?= htmlspecialchars($packageLabel) ?></span></div><span class="release-file-size"><i class="fa-regular fa-file"></i><?= htmlspecialchars((string) ($release['formattedSize'] ?? rz_file_size((int) ($release['fileSize'] ?? 0)))) ?></span></header>
            <div class="release-product-version"><span>VERSION</span><strong>v<?= htmlspecialchars((string) ($release['version'] ?? '1.0.0')) ?></strong><small>Released <?= htmlspecialchars($releaseDate) ?></small></div>
            <h3><?= htmlspecialchars((string) ($release['title'] ?? 'Retail Zim POS')) ?></h3>
            <p><?= htmlspecialchars((string) ($release['description'] ?? 'Retail Zim application package.')) ?></p>
            <div class="release-requirements"><i class="fa-solid fa-microchip"></i><span><small>Minimum requirements</small><b><?= htmlspecialchars((string) ($release['minimumRequirements'] ?? ($platform === 'WINDOWS' ? 'Windows 10/11' : 'Android 8.0 or newer'))) ?></b></span></div>
            <?php if (!empty($notes)): ?><details class="release-notes" <?= $index === 0 ? 'open' : '' ?>><summary><span><i class="fa-solid fa-wand-magic-sparkles"></i>What is included</span><i class="fa-solid fa-chevron-down"></i></summary><ul><?php foreach (array_slice($notes, 0, 8) as $note): ?><li><?= htmlspecialchars($note) ?></li><?php endforeach; ?></ul></details><?php endif; ?>
            <a class="btn btn-primary release-download-button" href="<?= htmlspecialchars((string) ($release['downloadUrl'] ?? '#')) ?>" download><i class="fa-solid fa-download"></i>Download <?= htmlspecialchars($packageLabel) ?><span><?= htmlspecialchars((string) ($release['formattedSize'] ?? '')) ?></span></a>
            <footer><span title="<?= htmlspecialchars((string) ($release['fileName'] ?? '')) ?>"><i class="fa-solid fa-file-shield"></i><?= htmlspecialchars((string) ($release['fileName'] ?? 'Verified package')) ?></span><?php if (!empty($release['downloadCount'])): ?><span><i class="fa-solid fa-arrow-down"></i><?= number_format((int) $release['downloadCount']) ?> downloads</span><?php endif; ?></footer>
            <?php if (!empty($release['checksum'])): ?><div class="release-checksum"><span><small>SHA-256 checksum</small><code><?= htmlspecialchars((string) $release['checksum']) ?></code></span><button type="button" aria-label="Copy checksum" data-copy-checksum="<?= htmlspecialchars((string) $release['checksum']) ?>"><i class="fa-regular fa-copy"></i></button></div><?php endif; ?>
          </article>
        <?php endforeach; ?>
        </div>
      <?php endif; ?>
    </div>
    <?php endforeach; ?>
  </section>

  <section class="release-install-strip">
    <div><span class="section-kicker">Need a hand?</span><h2>Go from download to first sale smoothly.</h2><p>Our team can help with installation, account sign-in, product imports, receipt printers and first-shift checks.</p></div>
    <div><a class="btn btn-primary" href="resources">Read setup guides <i class="fa-solid fa-arrow-right"></i></a><a class="btn btn-ghost" href="mailto:support@retailzw.co.zw">Contact support</a></div>
  </section>
</main>
<?php rz_footer(); ?>
