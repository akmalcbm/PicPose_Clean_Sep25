<?php
require_once __DIR__ . '/../lib/v2_pack_entitlements.php';

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'GET') {
    json_err('Method Not Allowed', 405);
}

$userId = v2_pack_optional_user_id($conn);

$sql = "
    SELECT
        pp.id,
        pp.name,
        pp.description,
        pp.price_points,
        pp.is_active,
        pp.created_at,
        COUNT(ppi.post_id) AS item_count
    FROM premium_packs pp
    LEFT JOIN premium_pack_items ppi ON ppi.pack_id = pp.id
    WHERE pp.is_active = 1
    GROUP BY pp.id, pp.name, pp.description, pp.price_points, pp.is_active, pp.created_at
    ORDER BY pp.created_at DESC, pp.id DESC
";
$res = $conn->query($sql);
if (!$res) {
    json_err('Failed to load packs', 500);
}

$rows = [];
$packIds = [];
while ($row = $res->fetch_assoc()) {
    $rows[] = $row;
    $packIds[] = (int)$row['id'];
}

$ownedMap = $userId ? v2_pack_owned_pack_map($conn, $userId, $packIds) : [];

$data = [];
foreach ($rows as $row) {
    $packId = (int)$row['id'];
    $data[] = [
        'id' => $packId,
        'name' => $row['name'],
        'description' => $row['description'],
        'pricePoints' => (int)$row['price_points'],
        'itemCount' => (int)$row['item_count'],
        'isActive' => (bool)$row['is_active'],
        'createdAt' => $row['created_at'],
        'ownsPack' => isset($ownedMap[$packId]),
    ];
}

json_ok([
    'success' => true,
    'data' => $data,
]);
