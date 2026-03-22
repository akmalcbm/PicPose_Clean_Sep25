<?php
require_once __DIR__ . '/../lib/v2_common.php';
require_once __DIR__ . '/../lib/v2_auth.php';
require_once __DIR__ . '/../lib/v2_pack_entitlements.php';
require_once __DIR__ . '/../lib/v2_personalization.php';

function v2_make_image_url(?string $path, string $baseUrl): ?string
{
    if (empty($path)) {
        return null;
    }
    if (preg_match('#^https?://#i', $path)) {
        return $path;
    }
    return $baseUrl . ltrim($path, '/');
}

function v2_first_words(?string $text, int $words = 15): string
{
    $clean = trim((string)$text);
    if ($clean === '') {
        return '';
    }

    $tokens = preg_split('/\s+/', $clean);
    if (!is_array($tokens)) {
        return '';
    }

    return implode(' ', array_slice($tokens, 0, max(1, $words)));
}

function v2_is_post_part_of_pack(mysqli $conn, int $postId): bool
{
    $stmt = $conn->prepare('SELECT 1 FROM premium_pack_items WHERE post_id = ? LIMIT 1');
    if (!$stmt) {
        return false;
    }

    $stmt->bind_param('i', $postId);
    $stmt->execute();
    $result = $stmt->get_result();
    $found = $result && $result->fetch_assoc();
    $stmt->close();

    return (bool)$found;
}

if (!isset($_GET['id']) || !ctype_digit((string)$_GET['id'])) {
    json_err('Invalid Prompt ID', 400);
}

$promptId = intval($_GET['id']);
$baseProto = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
$BASE_URL = $baseProto . '://' . ($_SERVER['HTTP_HOST'] ?? 'localhost') . '/';

$sql = "SELECT
            p.id, p.title, p.short_description, p.prompt_text,
            p.image_url1, p.image_url2,
            COALESCE(p.likes,0) AS likes,
            COALESCE(p.favorites,0) AS favorites,
            COALESCE(p.copies,0) AS copies,
            COALESCE(p.views,0) AS views,
            p.is_popular, p.is_featured, p.status, p.priority,
            p.created_at, p.updated_at,
            p.tier, p.premium_unlock_cost_points, p.premium_pack,
            c.name AS category_name,
            p.tags
        FROM ai_posts p
        LEFT JOIN categories c ON c.id = p.category_id
        WHERE p.id = ? AND p.status = 'published'
        LIMIT 1";

$stmt = $conn->prepare($sql);
if (!$stmt) {
    json_err('DB Error: ' . $conn->error, 500);
}

$stmt->bind_param('i', $promptId);
$stmt->execute();
$result = $stmt->get_result();
$row = $result ? $result->fetch_assoc() : null;
$stmt->close();

if (!$row) {
    json_err('Prompt Not Found', 404);
}

$tier = strtoupper((string)($row['tier'] ?? 'FREE'));
$isPackItem = v2_is_post_part_of_pack($conn, $promptId);
$isPremium = ($tier === 'PREMIUM') || $isPackItem;
$authUserId = v2_pack_optional_user_id($conn);
$hasActiveSubscription = false; // TODO: wire real subscription source.
$entitlementMap = $authUserId ? v2_pack_prompt_entitlement_map($conn, $authUserId, [$promptId]) : [];

$isUnlocked = !$isPremium;
if ($isPremium && $authUserId) {
    $isUnlocked = isset($entitlementMap[$promptId]) || $hasActiveSubscription;
}

$isLocked = !$isUnlocked;
$promptText = (string)($row['prompt_text'] ?? '');
$parsedTags = v2_personalization_parse_tags($row['tags']);

$data = [
    'id' => (string)$row['id'],
    'title' => $row['title'],
    'shortPrompt' => $row['short_description'],
    'fullPrompt' => $isLocked ? null : $promptText,
    'imageUrl' => v2_make_image_url($row['image_url1'], $BASE_URL),
    'imageUrl2' => v2_make_image_url($row['image_url2'], $BASE_URL),
    'category' => $row['category_name'],
    'tags' => $parsedTags,
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
    'premiumUnlockCostPoints' => (int)($row['premium_unlock_cost_points'] ?? 0),
    'premiumPack' => $row['premium_pack'],
    'isLocked' => $isLocked,
    'teaserText' => $isLocked ? v2_first_words($promptText, 15) : null,
];

if ($authUserId) {
    try {
        $signalTags = $parsedTags;
        $categoryTag = v2_personalization_normalize_tag($row['category_name'] ?? null);
        if ($categoryTag !== null) {
            $signalTags[] = $categoryTag;
        }
        update_user_tag_scores($conn, $authUserId, $signalTags, 1);
    } catch (Throwable $e) {
        error_log('get_ai_post personalization update failed: ' . $e->getMessage());
    }
}

json_ok([
    'success' => true,
    'message' => 'OK',
    'data' => $data,
]);
