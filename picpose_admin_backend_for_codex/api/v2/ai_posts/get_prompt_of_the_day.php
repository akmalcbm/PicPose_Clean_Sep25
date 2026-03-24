<?php
require_once __DIR__ . '/../lib/v2_common.php';
require_once __DIR__ . '/../lib/v2_ab.php';
require_once __DIR__ . '/../lib/v2_prompt_access.php';

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
    if ($existing) {
        return $existing;
    }

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
                if ($existing) {
                    return $existing;
                }
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

$isVisibleSelect = v2_prompt_select_column_expr($conn, 'p', 'is_visible_in_general_feed');
$creditEnabledSelect = v2_prompt_select_column_expr($conn, 'p', 'credit_unlock_enabled');
$rewardEnabledSelect = v2_prompt_select_column_expr($conn, 'p', 'reward_unlock_enabled');
$tokenEnabledSelect = v2_prompt_select_column_expr($conn, 'p', 'token_unlock_enabled');
$subscriberEnabledSelect = v2_prompt_select_column_expr($conn, 'p', 'subscriber_unlock_enabled');

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
        {$isVisibleSelect},
        {$creditEnabledSelect},
        {$rewardEnabledSelect},
        {$tokenEnabledSelect},
        {$subscriberEnabledSelect},
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

$userProfile = v2_prompt_optional_user_profile($conn);
$userId = $userProfile['id'] ?? null;
$hasActiveSubscription = (bool)($userProfile['has_active_subscription'] ?? false);

if ($mode === 'DISCOUNT' && $userId !== null) {
    $potdVariant = get_user_variant($conn, (int)$userId, 'potd_discount_cost');
    $discountCost = max(0, (int)round(v2_ab_variant_numeric($conn, 'potd_discount_cost', $potdVariant, (float)$discountCost)));
}

$packLinksMap = v2_prompt_pack_links_for_posts($conn, [$postId], $userId !== null ? (int)$userId : null);
$packLinks = $packLinksMap[$postId] ?? [];
$flags = v2_prompt_resolve_flags_from_row($row, !empty($packLinks));
if (($flags['is_credit_unlockable'] ?? false) && $mode === 'DISCOUNT') {
    $flags['premium_unlock_cost_points'] = $discountCost;
} elseif (($flags['is_credit_unlockable'] ?? false) && $mode === 'FREE') {
    $flags['premium_unlock_cost_points'] = 0;
}

$unlockMap = [];
if ($userId !== null) {
    $unlockMap = v2_pack_prompt_entitlement_map($conn, (int)$userId, [$postId]);
}

$isUnlocked = v2_prompt_is_unlocked(
    $flags,
    isset($unlockMap[$postId]),
    $hasActiveSubscription
);
if ($mode === 'FREE') {
    $isUnlocked = true;
}

$baseProto = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
$baseUrl = $baseProto . '://' . ($_SERVER['HTTP_HOST'] ?? 'localhost') . '/';
$post = v2_prompt_build_payload($row, $flags, $isUnlocked, $baseUrl, $packLinks);

json_ok([
    'success' => true,
    'day_date' => (string)$potd['day_date'],
    'post' => $post,
    'potd_mode' => $mode,
    'potd_unlock_cost_points' => (int)($post['premiumUnlockCostPoints'] ?? 0),
]);
