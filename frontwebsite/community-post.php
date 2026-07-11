<?php
require_once __DIR__ . '/lib/site-db.php';

$isJson = isset($_SERVER['HTTP_ACCEPT']) && strpos($_SERVER['HTTP_ACCEPT'], 'application/json') !== false;

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') {
    if ($isJson) {
        http_response_code(405);
        header('Content-Type: application/json');
        echo json_encode(['ok' => false, 'error' => 'Method not allowed']);
        exit;
    }
    header('Location: community');
    exit;
}

$ok = retailzim_create_post($_POST);
if ($isJson) {
    http_response_code($ok ? 200 : 422);
    header('Content-Type: application/json');
    echo json_encode(['ok' => $ok, 'posts' => retailzim_posts()]);
    exit;
}

header('Location: community#community-feed');
?>
