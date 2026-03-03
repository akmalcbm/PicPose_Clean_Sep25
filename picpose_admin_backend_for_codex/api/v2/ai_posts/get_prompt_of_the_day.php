<?php
require_once __DIR__ . '/../lib/v2_common.php';
require_once __DIR__ . '/../lib/v2_auth.php';
require_once __DIR__ . '/../lib/v2_ab.php';

function v2_potd_make_image_url(?string $path, string $baseUrl): ?string
{
    if (empty($path)) return null;
    if (preg_match('#^https?://#i', $path)) return $path;
    return $baseUrl . ltrim($path, '/');
}

function v2_potd_parse_tags(?string $tagsField): array
{
    if (empty($tagsField)) return [];
    $decoded = json_decode($tagsField, true);
    if (json_last_error() === JSON_ERROR_NONE && is_array($decoded)) {
        return array_values(array_unique(array_filter($decoded)));
    }
    return array_values(array_unique(array_filter(array_map('trim', explode(',', $tagsField)))));
}

function v2_potd_first_words(?string $text, int $words = 15): string
{
    $clean = trim((string)$text);
    if ($clean === '') return '';
    $tokens = preg_split('/\s+/', $clean);
    if (!is_array($tokens)) return '';
    return implode(' ', array_slice($tokens, 0, max(1, $words)));
}

function v2_potd_optional_user_id(mysqli $conn): ?int
{
    $token = get_bearer_token();
    if ($token === null) return null;

    $stmt = $conn->prepare('SELECT id FROM users WHERE api_token = ? LIMIT 1');
    if (!$stmt) return null;
    $stmt->bind_param('s', $token);
    $stmt->execute();
    $res = $stmt->get_result();
    $row = $res ? $res->fetch_assoc() : null;
    $stmt->close();

    return $row ? (int)$row['id'] : null;
}

function v2_potd_is_unlocked(mysqli $conn, int $userId, int $postId): bool
{
    $stmt = $conn->prepare('SELECT 1 FROM user_prompt_unlocks WHERE user_id = ? AND post_id = ? LIMIT 1');
    if (!$stmt) return false;
    $stmt->bind_param('ii', $userId, $postId);
    $stmt->execute();
    $res = $stmt->get_result();
    $row = $res ? $res->fetch_assoc() : null;
    $stmt->close();
    return (bool)$row;
}

function v2_potd_load_today_record(mysqli $conn, string $today): ?array
{
    $stmt = $conn->prepare("
        SELECT day_date, post_id, mode, discount_cost_points
        FROM daily_featured_prompts
        WHERE day_date = ?
        LIMIT 1
    ");
    if (!$stmt) {
        json_err('Database query preparation failed', 500);
    }
    $stmt->bind_param('s', $today);
    $stmt->execute();
    $res = $stmt->get_result();
    $row = $res ? $res->fetch_assoc() : null;
    $stmt->close();
    return $row ?: null;
}

function v2_potd_pick_post_id(mysqli $conn): ?int
{
    $sqlPreferred = "
        SELECT p.id
        FROM ai_posts p
        WHERE p.status = 'published'
          AND p.tier = 'PREMIUM'
          AND p.id NOT IN (
              SELECT d.post_id
              FROM daily_featured_prompts d
              WHERE d.day_date >= DATE_SUB(CURDATE(), INTERVAL 14 DAY)
          )
        ORDER BY (COALESCE(p.likes,0) + COALESCE(p.copies,0) + COALESCE(p.views,0)) DESC, p.created_at DESC
        LIMIT 1
    ";
    $res = $conn->query($sqlPreferred);
    if ($res && ($row = $res->fetch_assoc())) {
        return (int)$row['id'];
    }

    $sqlFallback = "
        SELECT p.id
        FROM ai_posts p
        WHERE p.status = 'published'
          AND p.tier = 'PREMIUM'
        ORDER BY (COALESCE(p.likes,0) + COALESCE(p.copies,0) + COALESCE(p.views,0)) DESC, p.created_at DESC
        LIMIT 1
    ";
    $res = $conn->query($sqlFallback);
    if ($res && ($row = $res->fetch_assoc())) {
        return (int)$row['id'];
    }

    return null;
}

function v2_potd_ensure_today_record(mysqli $conn, string $today): array
{
    $existing = v2_potd_load_today_record($conn, $today);
    if ($existing) return $existing;

    $postId = v2_potd_pick_post_id($conn);
    if (!$postId) {
        json_err('No eligible prompt available for today', 404);
    }

    $conn->begin_transaction();
    try {
        $check = v2_potd_load_today_record($conn, $today);
        if ($check) {
            $conn->commit();
            return $check;
        }

        $mode = 'DISCOUNT';
        $discount = 50;
        $stmt = $conn->prepare("
            INSERT INTO daily_featured_prompts (day_date, post_id, mode, discount_cost_points)
            VALUES (?, ?, ?, ?)
        ");
        if (!$stmt) {
            throw new RuntimeException('Failed to prepare POTD insert');
        }
        $stmt->bind_param('sisi', $today, $postId, $mode, $discount);
        if (!$stmt->execute()) {
            $errno = (int)$stmt->errno;
            $stmt->close();
            if ($errno === 1062) {
                $conn->rollback();
                $existing = v2_potd_load_today_record($conn, $today);
                if ($existing) return $existing;
            }
            throw new RuntimeException('Failed to insert POTD row');
        }
        $stmt->close();
        $conn->commit();
    } catch (Throwable $e) {
        $conn->rollback();
        json_err('Failed to resolve prompt of the day', 500);
    }

    $inserted = v2_potd_load_today_record($conn, $today);
    if (!$inserted) {
        json_err('Failed to load prompt of the day', 500);
    }
    return $inserted;
}

$today = date('Y-m-d');
$potd = v2_potd_ensure_today_record($conn, $today);

$stmt = $conn->prepare("
    SELECT
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
    LIMIT 1
");
if (!$stmt) {
    json_err('Database query preparation failed', 500);
}
$postId = (int)$potd['post_id'];
$stmt->bind_param('i', $postId);
$stmt->execute();
$res = $stmt->get_result();
$row = $res ? $res->fetch_assoc() : null;
$stmt->close();

if (!$row) {
    json_err('Prompt of the day not found', 404);
}

$mode = strtoupper((string)($potd['mode'] ?? 'NORMAL'));
$discountCost = (int)($potd['discount_cost_points'] ?? 0);
$baseCost = (int)($row['premium_unlock_cost_points'] ?? 0);
if ($baseCost <= 0) $baseCost = 200;
$effectiveCost = $mode === 'DISCOUNT' ? max(0, $discountCost) : $baseCost;

$baseProto = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
$baseUrl = $baseProto . '://' . ($_SERVER['HTTP_HOST'] ?? 'localhost') . '/';

$tier = strtoupper((string)($row['tier'] ?? 'FREE'));
$isPremium = ($tier === 'PREMIUM');
$userId = v2_potd_optional_user_id($conn);
$hasActiveSubscription = false;

if ($mode === 'DISCOUNT' && $userId) {
    $potdVariant = get_user_variant($conn, $userId, 'potd_discount_cost');
    $variantCost = (int)round(v2_ab_variant_numeric($conn, 'potd_discount_cost', $potdVariant, (float)$discountCost));
    $effectiveCost = max(0, $variantCost);
}

$isUnlocked = !$isPremium;
if ($isPremium && $mode === 'FREE') {
    $isUnlocked = true;
} elseif ($isPremium && $userId) {
    $isUnlocked = v2_potd_is_unlocked($conn, $userId, (int)$row['id']) || $hasActiveSubscription;
}

$isLocked = !$isUnlocked;
$promptText = (string)($row['prompt_text'] ?? '');

$post = [
    'id' => (string)$row['id'],
    'title' => $row['title'],
    'shortPrompt' => $row['short_description'],
    'fullPrompt' => $isLocked ? null : $promptText,
    'imageUrl' => v2_potd_make_image_url($row['image_url1'], $baseUrl),
    'imageUrl2' => v2_potd_make_image_url($row['image_url2'], $baseUrl),
    'category' => $row['category_name'],
    'tags' => v2_potd_parse_tags($row['tags']),
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
    'premiumUnlockCostPoints' => $effectiveCost,
    'premiumPack' => $row['premium_pack'],
    'isLocked' => $isLocked,
    'teaserText' => $isLocked ? v2_potd_first_words($promptText, 15) : null,
];

json_ok([
    'success' => true,
    'day_date' => (string)$potd['day_date'],
    'post' => $post,
    'potd_mode' => $mode,
    'potd_unlock_cost_points' => $effectiveCost,
]);
