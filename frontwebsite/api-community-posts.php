<?php
require_once __DIR__ . '/lib/site-db.php';

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'OPTIONS') {
    retailzim_json_response(['ok' => true]);
    exit;
}

$limit = (int)($_GET['limit'] ?? 50);
$status = isset($_GET['status']) ? retailzim_limit_string($_GET['status'], 24) : null;

retailzim_json_response([
    'ok' => true,
    'base_url' => 'https://retailzw.co.zw/',
    'posts' => retailzim_posts_with_answers($limit, $status),
]);
?>
