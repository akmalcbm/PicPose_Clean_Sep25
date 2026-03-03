<?php
require_once __DIR__ . '/../lib/v2_ab.php';

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'GET') {
    json_err('Method Not Allowed', 405);
}

$user = require_user($conn);
$userId = (int)$user['id'];

$res = $conn->query("
    SELECT key_name, variants_json
    FROM ab_experiments
    WHERE is_active = 1
    ORDER BY id ASC
");
if (!$res) {
    json_err('Failed to load experiments', 500);
}

$assignments = [];
while ($row = $res->fetch_assoc()) {
    $key = (string)$row['key_name'];
    $variant = get_user_variant($conn, $userId, $key);
    if ($variant === null) {
        continue;
    }
    $payload = v2_ab_variant_payload($conn, $key, $variant);
    $assignments[] = [
        'key' => $key,
        'variant' => $variant,
        'payload' => $payload,
    ];
}

json_ok([
    'success' => true,
    'experiments' => $assignments,
]);
