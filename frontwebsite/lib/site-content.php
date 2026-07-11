<?php
require_once __DIR__ . '/site-db.php';

$shopLoginUrl = 'https://admin.retailzw.co.zw/auth/shop/login';
$registerUrl = 'https://admin.retailzw.co.zw/auth/signup';
$windowsExe = 'dist/RetailZW-POS-Setup-1.0.0-product-import.exe';
$windowsZip = 'dist/RetailZW-POS-Portable-1.0.0-product-import.zip';

function rz_url(string $path = ''): string {
    $path = trim($path, '/');
    return $path === '' ? './' : $path;
}

function rz_modules(): array {
    return [
        ['code' => 'POS', 'title' => 'Windows POS', 'desc' => 'Fast checkout, 80mm receipts, barcode search, shifts, and offline-ready selling.'],
        ['code' => 'MOB', 'title' => 'Mobile POS', 'desc' => 'Sell from a phone, check stock, build carts, and help customers from the shop floor.'],
        ['code' => 'STK', 'title' => 'Inventory', 'desc' => 'Import products from Excel, manage quantities, prices, categories, and branch stock.'],
        ['code' => 'WEB', 'title' => 'Cloud Admin', 'desc' => 'See sales, shops, users, reports, subscriptions, and support from one dashboard.'],
        ['code' => 'PAY', 'title' => 'Payments', 'desc' => 'Record cash, EcoCash, card, bank transfer, customer account, and split tenders.'],
        ['code' => 'COM', 'title' => 'Community', 'desc' => 'Ask questions, read guides, follow release news, and learn from other retailers.'],
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
        ['name' => 'Starter', 'price' => 'For one shop', 'items' => ['Windows POS', 'Product imports', 'Basic sales reports']],
        ['name' => 'Growth', 'price' => 'For busy retailers', 'items' => ['Multiple users', 'Mobile POS', 'Inventory and support']],
        ['name' => 'Platform', 'price' => 'For multi-branch groups', 'items' => ['Cloud admin', 'Subscriptions', 'Advanced reporting']],
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
  <link rel="stylesheet" href="css/retailzim-front.css?v=10">
</head>
<body>
  <header class="site-header">
    <div class="top-line">
      <span>Retail Zim</span>
      <a href="<?= htmlspecialchars(rz_url('downloads')) ?>">Windows POS, mobile selling, stock control, payments, reports, and support.</a>
    </div>
    <nav class="nav-wrap">
      <a class="brand" href="<?= htmlspecialchars(rz_url()) ?>" aria-label="Retail Zim home">
        <img src="img/retailzim-logo-clean.png" alt="Retail Zim">
        <span><strong>Retail Zim</strong><small>Retail operating system</small></span>
      </a>
      <div class="nav-links">
        <?php foreach ($nav as $key => $item): ?>
          <a class="<?= $active === $key ? 'active' : '' ?>" href="<?= htmlspecialchars($item[1]) ?>"><?= htmlspecialchars($item[0]) ?></a>
        <?php endforeach; ?>
      </div>
      <div class="nav-actions">
        <a class="btn btn-light" href="<?= htmlspecialchars($shopLoginUrl) ?>">Shop Login</a>
        <a class="btn btn-primary" href="<?= htmlspecialchars($registerUrl) ?>">Get Started</a>
      </div>
    </nav>
  </header>
<?php }

function rz_footer(): void {
    global $shopLoginUrl, $registerUrl;
    $year = date('Y');
?>
  <footer class="site-footer">
    <div>
      <a class="brand" href="<?= htmlspecialchars(rz_url()) ?>"><img src="img/retailzim-logo-clean.png" alt="Retail Zim"><span><strong>Retail Zim</strong><small>Retail operating system</small></span></a>
      <p>POS, mobile selling, inventory, payments, reports, guides, downloads, and support for modern shops.</p>
    </div>
    <div><h4>Platform</h4><a href="<?= htmlspecialchars(rz_url('platform')) ?>">Modules</a><a href="<?= htmlspecialchars(rz_url('how-it-works')) ?>">How it works</a><a href="<?= htmlspecialchars(rz_url('payments')) ?>">Payments</a></div>
    <div><h4>Customers</h4><a href="<?= htmlspecialchars(rz_url('community')) ?>">Community</a><a href="<?= htmlspecialchars(rz_url('resources')) ?>">Guides</a><a href="<?= htmlspecialchars(rz_url('downloads')) ?>">Downloads</a></div>
    <div><h4>Account</h4><a href="<?= htmlspecialchars($shopLoginUrl) ?>">Shop Login</a><a href="<?= htmlspecialchars($registerUrl) ?>">Register</a></div>
    <div class="footer-bottom">
      <span>All rights reserved @<?= htmlspecialchars($year) ?> Powered By <a href="https://cntechnologies.co.zw/" target="_blank" rel="noopener">CN</a></span>
    </div>
  </footer>
  <script src="js/retailzim-front.js?v=3"></script>
</body>
</html>
<?php }
?>
