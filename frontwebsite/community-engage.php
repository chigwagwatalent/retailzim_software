<?php
require_once __DIR__ . '/lib/site-db.php';
$ok = retailzim_engage((int)($_POST['post_id'] ?? 0), $_POST['action'] ?? 'like');
header('Content-Type: application/json');
echo json_encode(['ok' => $ok, 'metrics' => retailzim_metrics()]);
?>
