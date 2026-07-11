<?php
require_once __DIR__ . '/lib/site-db.php';
retailzim_record_visit($_POST['page'] ?? 'home', $_POST['section'] ?? null);
header('Content-Type: application/json');
echo json_encode(['ok' => true, 'metrics' => retailzim_metrics()]);
?>
