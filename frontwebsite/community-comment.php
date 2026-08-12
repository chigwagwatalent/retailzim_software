<?php
require_once __DIR__ . '/lib/site-db.php';

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') {
    retailzim_json_response(['ok' => false, 'message' => 'POST is required.'], 405);
    exit;
}

$data = retailzim_request_data();
$result = retailzim_add_comment(
    (int)($data['post_id'] ?? 0),
    (string)($data['name'] ?? ''),
    (string)($data['comment'] ?? '')
);
retailzim_json_response($result, $result['ok'] ? 200 : 422);
?>
