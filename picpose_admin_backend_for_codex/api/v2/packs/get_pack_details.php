<?php
require_once __DIR__ . '/../lib/v2_pack_entitlements.php';

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'GET') {
    json_err('Method Not Allowed', 405);
}

function v2_pack_details_make_image_url(?string $path, string $baseUrl): ?string
{
    if (empty($path)) return null;
    if (preg_match('#^https?://#i', $path)) return $path;
    return $baseUrl . ltrim($path, '/');
}

function v2_pack_details_parse_tags(?string $tagsField): array
{
    if (empty($tagsField)) return [];
    $decoded = json_decode($tagsField, true);
    if (json_last_error() === JSON_ERROR_NONE && is_array($decoded)) {
        return array_values(array_unique(array_filter($decoded)));
    }
    return array_values(array_unique(array_filter(array_map('trim', explode(',', $tagsField)))));
}

function v2_pack_details_first_words(?string $text, int $words = 15): string
{
    $clean = trim((string)$text);
    if ($clean === '') return '';
    $tokens = preg_split('/\s+/', $clean);
    if (!is_array($tokens)) return '';
    return implode(' ', array_slice($tokens, 0, max(1, $words)));
}

$packId = (int)($_GET['id'] ?? 0);
if ($packId <= 0) {
    json_err('Invalid pack id', 400);
}

$userId = v2_pack_optional_user_id($conn);
$packStmt = $conn->prepare("
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
    WHERE pp.id = ?
    GROUP BY pp.id, pp.name, pp.description, pp.price_points, pp.is_active, pp.created_at
    LIMIT 1
");
if (!$packStmt) {
    json_err('Database query preparation failed', 500);
}
$packStmt->bind_param('i', $packId);
$packStmt->execute();
$packRes = $packStmt->get_result();
$pack = $packRes ? $packRes->fetch_assoc() : null;
$packStmt->close();

if (!$pack) {
    json_err('Pack not found', 404);
}

$ownsPack = $userId ? v2_pack_user_owns_pack($conn, $userId, $packId) : false;
$baseProto = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
$baseUrl = $baseProto . '://' . ($_SERVER['HTTP_HOST'] ?? 'localhost') . '/';

$sql = "
    SELECT
        p.id,
        p.title,
        p.short_description,
        p.prompt_text,
        p.image_url1,
        p.image_url2,
        COALESCE(p.likes,0) AS likes,
        COALESCE(p.favorites,0) AS favorites,
        COALESCE(p.copies,0) AS copies,
        COALESCE(p.views,0) AS views,
        p.is_popular,
        p.is_featured,
        p.status,
        p.priority,
        p.created_at,
        p.updated_at,
        p.tier,
        p.premium_unlock_cost_points,
        p.premium_pack,
        p.tags,
        c.name AS category_name
    FROM premium_pack_items ppi
    INNER JOIN ai_posts p ON p.id = ppi.post_id
    LEFT JOIN categories c ON c.id = p.category_id
    WHERE ppi.pack_id = ?
      AND p.status = 'published'
    ORDER BY p.priority DESC, p.created_at DESC
";
$stmt = $conn->prepare($sql);
if (!$stmt) {
    json_err('Database query preparation failed', 500);
}
$stmt->bind_param('i', $packId);
$stmt->execute();
$res = $stmt->get_result();

$rows = [];
$postIds = [];
while ($row = $res->fetch_assoc()) {
    $rows[] = $row;
    $postIds[] = (int)$row['id'];
}
$stmt->close();

$entitlementMap = ($userId && !$ownsPack) ? v2_pack_prompt_entitlement_map($conn, $userId, $postIds) : [];

$items = [];
foreach ($rows as $row) {
    $tier = strtoupper((string)($row['tier'] ?? 'FREE'));
    $promptText = (string)($row['prompt_text'] ?? '');
    $isUnlocked = ($tier !== 'PREMIUM') || $ownsPack || ($userId && isset($entitlementMap[(int)$row['id']]));
    $isLocked = !$isUnlocked;

    $items[] = [
        'id' => (string)$row['id'],
        'title' => $row['title'],
        'shortPrompt' => $row['short_description'],
        'fullPrompt' => $isLocked ? null : $promptText,
        'imageUrl' => v2_pack_details_make_image_url($row['image_url1'], $baseUrl),
        'imageUrl2' => v2_pack_details_make_image_url($row['image_url2'], $baseUrl),
        'category' => $row['category_name'],
        'tags' => v2_pack_details_parse_tags($row['tags'] ?? null),
        'likes' => (int)$row['likes'],
        'favorites' => (int)$row['favorites'],
        'copies' => (int)$row['copies'],
        'views' => (int)$row['views'],
        'isPopular' => (bool)$row['is_popular'],
        'isFeatured' => (bool)$row['is_featured'],
        'status' => $row['status'],
        'priority' => (int)$row['priority'],
        'createdAt' => $row['created_at'],
        'updatedAt' => $row['updated_at'],
        'tier' => $tier,
        'premiumUnlockCostPoints' => ((int)($row['premium_unlock_cost_points'] ?? 0) > 0) ? (int)$row['premium_unlock_cost_points'] : 200,
        'premiumPack' => $row['premium_pack'],
        'isLocked' => $isLocked,
        'teaserText' => $isLocked ? v2_pack_details_first_words($promptText, 15) : null,
    ];
}

json_ok([
    'success' => true,
    'pack' => [
        'id' => (int)$pack['id'],
        'name' => $pack['name'],
        'description' => $pack['description'],
        'pricePoints' => (int)$pack['price_points'],
        'itemCount' => (int)$pack['item_count'],
        'isActive' => (bool)$pack['is_active'],
        'createdAt' => $pack['created_at'],
        'ownsPack' => $ownsPack,
    ],
    'items' => $items,
]);
