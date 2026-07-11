<?php
require_once __DIR__ . '/lib/site-db.php';

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'OPTIONS') {
    retailzim_json_response(['ok' => true]);
    exit;
}

$days = (int)($_GET['days'] ?? 30);

retailzim_json_response([
    'ok' => true,
    'base_url' => 'https://retailzw.co.zw/',
    'stats' => retailzim_visit_stats($days),
]);
?>
