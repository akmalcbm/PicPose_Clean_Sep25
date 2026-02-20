<?php
require_once __DIR__ . '/email_verification_lib.php';

auth_require_api_key();

if (!$conn || $conn->connect_errno) {
    auth_json_response(['status' => 'error', 'message' => 'Database unavailable'], 500);
}

$input = auth_parse_input();
$token = (string)($input['token'] ?? '');
$result = auth_consume_email_verification_token($conn, $token);

if ($result['ok'] === true) {
    auth_json_response([
        'status' => 'success',
        'success' => true,
        'message' => $result['message'],
    ]);
}

auth_json_response([
    'status' => 'error',
    'success' => false,
    'message' => $result['message'],
], 400);
