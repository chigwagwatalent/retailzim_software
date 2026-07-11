<?php
require_once __DIR__ . '/lib/site-db.php';

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'OPTIONS') {
    retailzim_json_response(['ok' => true]);
    exit;
}

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') {
    retailzim_json_response(['ok' => false, 'message' => 'POST is required.'], 405);
    exit;
}

if (!retailzim_authorized_admin_api()) {
    retailzim_json_response(['ok' => false, 'message' => 'Unauthorized.'], 401);
    exit;
}

$data = retailzim_request_data();
$postId = (int)($data['post_id'] ?? 0);
$answer = (string)($data['answer'] ?? '');
$responder = (string)($data['responder'] ?? 'Retail Zim Support');
$status = (string)($data['status'] ?? 'answered');

$ok = retailzim_answer_post($postId, $answer, $responder, $status);
retailzim_json_response([
    'ok' => $ok,
    'message' => $ok ? 'Answer saved.' : 'Answer could not be saved.',
], $ok ? 200 : 422);
?>
