<?php
require_once __DIR__ . '/site-db.php';

$shopLoginUrl = 'https://admin.retailzw.co.zw/auth/shop/login';
$registerUrl = 'https://admin.retailzw.co.zw/auth/signup';
$windowsExe = 'dist/RetailZW-POS-Setup-1.0.0-product-import.exe';
$windowsZip = 'dist/RetailZW-POS-Portable-1.0.0-product-import.zip';

function rz_release_api_url(): string {
    $configured = getenv('RETAILZIM_RELEASE_API_URL');
    if (is_string($configured) && trim($configured) !== '') {
        return rtrim(trim($configured), '/');
    }
    $host = $_SERVER['HTTP_HOST'] ?? '';
    if (str_starts_with($host, '127.0.0.1') || str_starts_with($host, 'localhost')) {
        return 'http://127.0.0.1:8080/api/public/releases';
    }
    return 'https://admin.retailzw.co.zw/api/public/releases';
}

function rz_release_catalog(): array {
    $context = stream_context_create([
        'http' => [
            'method' => 'GET',
            'header' => "Accept: application/json\r\nUser-Agent: RetailZim-Website/1.0\r\n",
            'timeout' => 3,
            'ignore_errors' => true,
        ],
        'ssl' => ['verify_peer' => true, 'verify_peer_name' => true],
    ]);
    $json = @file_get_contents(rz_release_api_url(), false, $context);
    if (is_string($json) && $json !== '') {
        $payload = json_decode($json, true);
        if (is_array($payload) && isset($payload['releases']) && is_array($payload['releases'])) {
            return array_values(array_filter($payload['releases'], 'is_array'));
        }
    }
    return rz_legacy_release_catalog();
}

function rz_legacy_release_catalog(): array {
    global $windowsExe, $windowsZip;
    $candidates = [
        [
            'path' => $windowsExe,
            'packageType' => 'INSTALLER',
            'title' => 'Retail Zim Windows POS',
            'description' => 'The recommended setup package for counter computers, including product imports, barcode sales, shifts and receipt printing.',
        ],
        [
            'path' => $windowsZip,
            'packageType' => 'PORTABLE_ZIP',
            'title' => 'Retail Zim Portable POS',
            'description' => 'A portable Windows package for assisted installations and computers where a standard installer is not suitable.',
        ],
    ];
    $releases = [];
    foreach ($candidates as $candidate) {
        $absolute = dirname(__DIR__) . '/' . $candidate['path'];
        if (!is_file($absolute)) continue;
        preg_match('/(\d+\.\d+\.\d+)/', basename($candidate['path']), $matches);
        $bytes = (int) filesize($absolute);
        $releases[] = [
            'id' => null,
            'platform' => 'WINDOWS',
            'packageType' => $candidate['packageType'],
            'version' => $matches[1] ?? '1.0.0',
            'title' => $candidate['title'],
            'description' => $candidate['description'],
            'releaseNotes' => "Product import support\nFast checkout and barcode search\nReceipt printing and cashier shifts",
            'minimumRequirements' => 'Windows 10 or 11, 4 GB RAM, 500 MB free storage',
            'fileName' => basename($candidate['path']),
            'fileSize' => $bytes,
            'formattedSize' => rz_file_size($bytes),
            'checksum' => null,
            'latest' => true,
            'releasedAt' => date(DATE_ATOM, (int) filemtime($absolute)),
            'downloadCount' => null,
            'downloadUrl' => $candidate['path'],
        ];
    }
    return $releases;
}

function rz_file_size(int $bytes): string {
    if ($bytes >= 1073741824) return number_format($bytes / 1073741824, 1) . ' GB';
    if ($bytes >= 1048576) return number_format($bytes / 1048576, 1) . ' MB';
    if ($bytes >= 1024) return number_format($bytes / 1024, 1) . ' KB';
    return $bytes . ' B';
}

function rz_url(string $path = ''): string {
    $path = trim($path, '/');
    return $path === '' ? './' : $path;
}

function rz_modules(): array {
    return [
        ['code' => '01', 'icon' => 'fa-desktop', 'title' => 'Windows POS', 'desc' => 'Fast checkout, barcode search, cashier shifts, 80mm receipts, returns, and offline-ready selling.'],
        ['code' => '02', 'icon' => 'fa-mobile-screen-button', 'title' => 'Mobile POS', 'desc' => 'Sell from an Android phone, check stock, build carts, and serve customers anywhere in the shop.'],
        ['code' => '03', 'icon' => 'fa-boxes-stacked', 'title' => 'Inventory control', 'desc' => 'Import products, monitor quantities, investigate variances, transfer stock, and receive purchases.'],
        ['code' => '04', 'icon' => 'fa-chart-line', 'title' => 'Shop dashboard', 'desc' => 'Track today\'s sales, stock health, cashier activity, recent sales, and branch performance.'],
        ['code' => '05', 'icon' => 'fa-wallet', 'title' => 'Flexible payments', 'desc' => 'Record cash, EcoCash, card, bank transfer, customer-account, and split-tender payments.'],
        ['code' => '06', 'icon' => 'fa-store', 'title' => 'Multi-branch tools', 'desc' => 'Manage branches, users, suppliers, purchasing, wholesale prices, customers, and reports.'],
    ];
}

function rz_steps(): array {
    return [
        ['title' => 'Register your shop', 'desc' => 'Create your Retail Zim account and set up users for owners, managers, and cashiers.'],
        ['title' => 'Add your products', 'desc' => 'Import products from Excel or add them manually with prices, quantities, and categories.'],
        ['title' => 'Install your apps', 'desc' => 'Use Windows POS at the till and mobile POS for sales or stock checks from a phone.'],
        ['title' => 'Open a shift and sell', 'desc' => 'Cashiers search or scan products, take payment, print receipts, and keep the queue moving.'],
        ['title' => 'Close and review', 'desc' => 'Close the shift, check totals, review reports, and understand what happened in your shop.'],
    ];
}

function rz_payment_types(): array {
    return [
        ['name' => 'Cash', 'desc' => 'Handle USD cash, tendered amounts, and change due at checkout.'],
        ['name' => 'EcoCash / Mobile Money', 'desc' => 'Record mobile money sales and payment references.'],
        ['name' => 'Card / Swipe', 'desc' => 'Keep card terminal sales visible in your cash-up workflow.'],
        ['name' => 'Bank Transfer', 'desc' => 'Capture transfer notes for account or high-value sales.'],
        ['name' => 'Customer Account', 'desc' => 'Support customers who buy on account or keep balances.'],
        ['name' => 'Split Tender', 'desc' => 'Accept more than one payment type on the same sale.'],
    ];
}

function rz_plans(): array {
    return [
        [
            'name' => 'Starter',
            'label' => 'Affordable',
            'summary' => 'Perfect for small shops getting started.',
            'amount' => '5',
            'admins' => '1 Admin user',
            'branches' => '1 Branch',
            'cashiers' => '1 Cashier',
            'items' => ['Windows POS access', 'Basic product management', 'Sales and receipt processing', 'Daily sales reports', 'Stock level tracking'],
        ],
        [
            'name' => 'Growth',
            'label' => 'Growing shops',
            'summary' => 'Built for shops with a growing team.',
            'amount' => '11',
            'admins' => '2 Admin users',
            'branches' => '2 Branches',
            'cashiers' => '2 Cashiers',
            'items' => ['Everything in Starter', 'Multi-branch management', 'Branch sales comparison', 'Cashier performance reports', 'Product transfer between branches'],
        ],
        [
            'name' => 'Business',
            'label' => 'Most popular',
            'summary' => 'Advanced retail tools for busy businesses.',
            'amount' => '35',
            'admins' => '10 Admin users',
            'branches' => '5 Branches',
            'cashiers' => '12 Cashiers',
            'popular' => true,
            'items' => ['Everything in Growth', 'Advanced stock management', 'Purchase order management', 'Detailed profit reports', 'Low-stock notifications', 'Manager dashboards'],
        ],
        [
            'name' => 'Enterprise',
            'label' => 'Large retailers',
            'summary' => 'Powerful control for large retail operations.',
            'amount' => '150',
            'admins' => '25 Admin users',
            'branches' => '10 Branches',
            'cashiers' => '50 Cashiers',
            'items' => ['Everything in Business', 'Centralised branch control', 'Enterprise reporting dashboard', 'Advanced audit logs', 'Bulk product import'],
        ],
    ];
}

function rz_resources(): array {
    return [
        ['tag' => 'Guide', 'title' => 'Import products from Excel', 'desc' => 'Prepare names, prices, quantities, and categories before your first sale.'],
        ['tag' => 'Training', 'title' => 'Run a cashier shift', 'desc' => 'Open shift, sell, take payment, print receipts, and close cleanly.'],
        ['tag' => 'Release', 'title' => 'Windows POS installer', 'desc' => 'Download the latest installer and portable ZIP for your till computers.'],
    ];
}

function rz_header(string $title, string $description, string $active = 'home'): void {
    global $shopLoginUrl, $registerUrl;
    $nav = [
        'platform' => ['Platform', rz_url('platform')],
        'how' => ['How it works', rz_url('how-it-works')],
        'payments' => ['Payments', rz_url('payments')],
        'downloads' => ['Downloads', rz_url('downloads')],
        'community' => ['Community', rz_url('community')],
        'pricing' => ['Pricing', rz_url('pricing')],
    ];
?>
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title><?= htmlspecialchars($title) ?></title>
  <meta name="description" content="<?= htmlspecialchars($description) ?>">
  <link rel="icon" type="image/png" href="img/favicon.png">
  <link rel="shortcut icon" href="img/favicon.png">
  <meta name="theme-color" content="#061225">
  <link rel="stylesheet" href="css/all.min.css">
  <link rel="stylesheet" href="css/retailzim-front.css?v=21">
</head>
<body data-page="<?= htmlspecialchars($active) ?>">
  <header class="site-header">
    <nav class="nav-wrap">
      <a class="brand" href="<?= htmlspecialchars(rz_url()) ?>" aria-label="Retail Zim home">
        <img src="img/retailzim-logo-clean.png" alt="Retail Zim">
      </a>
      <button class="nav-toggle" type="button" aria-expanded="false" aria-controls="site-navigation" aria-label="Open navigation">
        <i class="fa-solid fa-bars"></i><span>Menu</span>
      </button>
      <div class="nav-menu" id="site-navigation">
        <div class="nav-links">
        <?php foreach ($nav as $key => $item): ?>
          <a class="<?= $active === $key ? 'active' : '' ?>" href="<?= htmlspecialchars($item[1]) ?>"><?= htmlspecialchars($item[0]) ?></a>
        <?php endforeach; ?>
        </div>
        <div class="nav-actions">
          <a class="btn btn-ghost" href="<?= htmlspecialchars($shopLoginUrl) ?>">Shop Login</a>
          <a class="btn btn-primary" href="<?= htmlspecialchars($registerUrl) ?>">Get Started <i class="fa-solid fa-arrow-right"></i></a>
        </div>
      </div>
    </nav>
  </header>
<?php }

function rz_footer(): void {
    global $shopLoginUrl, $registerUrl;
    $year = date('Y');
?>
  <footer class="site-footer">
    <div class="footer-brand">
      <a class="brand" href="<?= htmlspecialchars(rz_url()) ?>"><img src="img/retailzim-logo-clean.png" alt="Retail Zim"></a>
      <p>Connected point of sale, stock control, payments, reporting, and practical support for Zimbabwean retailers.</p>
      <div class="footer-social"><a href="<?= htmlspecialchars(rz_url('community')) ?>" aria-label="Retail Zim community"><i class="fa-solid fa-comments"></i></a><a href="mailto:sales@retailzw.co.zw" aria-label="Email Retail Zim"><i class="fa-solid fa-envelope"></i></a><a href="tel:+263717170895" aria-label="Call Retail Zim"><i class="fa-solid fa-phone"></i></a></div>
    </div>
    <div><h4>Product</h4><a href="<?= htmlspecialchars(rz_url('platform')) ?>">Platform</a><a href="<?= htmlspecialchars(rz_url('how-it-works')) ?>">How it works</a><a href="<?= htmlspecialchars(rz_url('payments')) ?>">Payments</a><a href="<?= htmlspecialchars(rz_url('pricing')) ?>">Pricing</a></div>
    <div><h4>Resources</h4><a href="<?= htmlspecialchars(rz_url('community')) ?>">Community</a><a href="<?= htmlspecialchars(rz_url('resources')) ?>">Guides</a><a href="<?= htmlspecialchars(rz_url('downloads')) ?>">Downloads</a><a href="mailto:support@retailzw.co.zw">Support</a></div>
    <div><h4>Get started</h4><a href="<?= htmlspecialchars($shopLoginUrl) ?>">Shop Login</a><a href="<?= htmlspecialchars($registerUrl) ?>">Register your shop</a><a href="mailto:sales@retailzw.co.zw">Talk to sales</a><a href="tel:+263717170895">+263 71 717 0895</a></div>
    <div class="footer-bottom">
      <span>&copy; <?= htmlspecialchars($year) ?> Retail Zim. All rights reserved.</span>
      <span>Built for Zimbabwean businesses.</span>
      <span>Powered by <a href="https://cntechnologies.co.zw/" target="_blank" rel="noopener">CN Technologies</a></span>
    </div>
  </footer>
  <script src="js/retailzim-front.js?v=11"></script>
</body>
</html>
<?php }
?>
