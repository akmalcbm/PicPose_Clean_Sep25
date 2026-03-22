<?php
require_once __DIR__ . '/../lib/v2_pack_entitlements.php';

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'GET') {
    json_err('Method Not Allowed', 405);
}

function v2_pack_make_image_url(?string $path, string $baseUrl): ?string
{
    if (empty($path)) {
        return null;
    }
    if (preg_match('#^https?://#i', $path)) {
        return $path;
    }
    return $baseUrl . ltrim($path, '/');
}

$userId = v2_pack_optional_user_id($conn);
$baseProto = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
$baseUrl = $baseProto . '://' . ($_SERVER['HTTP_HOST'] ?? 'localhost') . '/';

$sql = "
    SELECT
        pp.id,
        pp.name,
        pp.description,
        thumb.thumbnail_path,
        pp.price_points,
        pp.is_active,
        pp.created_at,
        COUNT(ppi.post_id) AS item_count
    FROM premium_packs pp
    LEFT JOIN premium_pack_items ppi ON ppi.pack_id = pp.id
    LEFT JOIN (
        SELECT
            ppi2.pack_id,
            SUBSTRING_INDEX(
                GROUP_CONCAT(
                    COALESCE(NULLIF(p.image_url1, ''), NULLIF(p.image_url2, ''))
                    ORDER BY p.priority DESC, p.created_at DESC
                    SEPARATOR ','
                ),
                ',',
                1
            ) AS thumbnail_path
        FROM premium_pack_items ppi2
        INNER JOIN ai_posts p ON p.id = ppi2.post_id AND p.status = 'published'
        GROUP BY ppi2.pack_id
    ) thumb ON thumb.pack_id = pp.id
    WHERE pp.is_active = 1
    GROUP BY pp.id, pp.name, pp.description, thumb.thumbnail_path, pp.price_points, pp.is_active, pp.created_at
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
        'thumbnailUrl' => v2_pack_make_image_url($row['thumbnail_path'] ?? null, $baseUrl),
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
