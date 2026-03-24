<?php
require_once __DIR__ . '/v2_auth.php';
require_once __DIR__ . '/v2_ab.php';
require_once __DIR__ . '/v2_pack_entitlements.php';

function v2_prompt_column_exists(mysqli $conn, string $columnName): bool
{
    static $cache = [];

    if (isset($cache[$columnName])) {
        return $cache[$columnName];
    }

    $sql = "
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'ai_posts'
          AND column_name = ?
        LIMIT 1
    ";
    $stmt = $conn->prepare($sql);
    if (!$stmt) {
        $cache[$columnName] = false;
        return false;
    }

    $stmt->bind_param('s', $columnName);
    $stmt->execute();
    $res = $stmt->get_result();
    $exists = (bool)($res && $res->fetch_assoc());
    $stmt->close();

    $cache[$columnName] = $exists;
    return $exists;
}

function v2_prompt_current_db_date(mysqli $conn): string
{
    $res = $conn->query("SELECT DATE_FORMAT(CURDATE(), '%Y-%m-%d') AS day_date");
    $row = ($res instanceof mysqli_result) ? $res->fetch_assoc() : null;
    $dayDate = (string)($row['day_date'] ?? '');
    return $dayDate !== '' ? $dayDate : date('Y-m-d');
}

function v2_prompt_load_today_offer_for_post(mysqli $conn, int $postId, ?string $dayDate = null): ?array
{
    if ($postId <= 0) {
        return null;
    }

    $resolvedDay = $dayDate ?: v2_prompt_current_db_date($conn);
    $stmt = $conn->prepare("
        SELECT day_date, post_id, mode, discount_cost_points
        FROM daily_featured_prompts
        WHERE day_date = ?
          AND post_id = ?
        LIMIT 1
    ");
    if (!$stmt) {
        return null;
    }

    $stmt->bind_param('si', $resolvedDay, $postId);
    $stmt->execute();
    $res = $stmt->get_result();
    $row = $res ? $res->fetch_assoc() : null;
    $stmt->close();

    return $row ?: null;
}

function v2_prompt_load_today_offers_for_posts(mysqli $conn, array $postIds, ?string $dayDate = null): array
{
    $normalizedIds = array_values(array_unique(array_filter(array_map('intval', $postIds), static fn (int $id): bool => $id > 0)));
    if (empty($normalizedIds)) {
        return [];
    }

    $resolvedDay = $dayDate ?: v2_prompt_current_db_date($conn);
    $placeholders = implode(',', array_fill(0, count($normalizedIds), '?'));
    $sql = "
        SELECT day_date, post_id, mode, discount_cost_points
        FROM daily_featured_prompts
        WHERE day_date = ?
          AND post_id IN ({$placeholders})
    ";
    $stmt = $conn->prepare($sql);
    if (!$stmt) {
        return [];
    }

    $types = 's' . str_repeat('i', count($normalizedIds));
    $params = array_merge([$resolvedDay], $normalizedIds);
    $stmt->bind_param($types, ...$params);
    $stmt->execute();
    $res = $stmt->get_result();

    $map = [];
    while ($row = ($res ? $res->fetch_assoc() : null)) {
        $id = (int)($row['post_id'] ?? 0);
        if ($id > 0) {
            $map[$id] = $row;
        }
    }
    $stmt->close();

    return $map;
}

function v2_prompt_apply_effective_credit_cost(
    mysqli $conn,
    int $postId,
    array &$flags,
    ?int $userId = null,
    ?array $potdOverride = null
): array {
    $isCreditUnlockable = (bool)($flags['is_credit_unlockable'] ?? false);
    $baseCost = (int)($flags['premium_unlock_cost_points'] ?? 0);
    if ($isCreditUnlockable && $baseCost <= 0) {
        $baseCost = 200;
    }
    if ($baseCost < 0) {
        $baseCost = 0;
    }

    $cost = $baseCost;
    if ($isCreditUnlockable && $userId !== null && $userId > 0) {
        $variant = get_user_variant($conn, $userId, 'premium_unlock_cost_multiplier');
        $multiplier = v2_ab_variant_numeric($conn, 'premium_unlock_cost_multiplier', $variant, 1.0);
        if ($multiplier <= 0) {
            $multiplier = 1.0;
        }
        $cost = max(0, (int)round($cost * $multiplier));
    }

    $potdMode = 'NORMAL';
    $potdDiscountCost = 0;
    $potdDayDate = null;
    $potdRow = $potdOverride ?: v2_prompt_load_today_offer_for_post($conn, $postId);
    if ($potdRow) {
        $potdMode = strtoupper((string)($potdRow['mode'] ?? 'NORMAL'));
        $potdDiscountCost = max(0, (int)($potdRow['discount_cost_points'] ?? 0));
        $potdDayDate = (string)($potdRow['day_date'] ?? '');
    }

    if ($isCreditUnlockable) {
        if ($potdMode === 'DISCOUNT') {
            $resolvedDiscount = $potdDiscountCost;
            if ($userId !== null && $userId > 0) {
                $potdVariant = get_user_variant($conn, $userId, 'potd_discount_cost');
                $resolvedDiscount = max(
                    0,
                    (int)round(v2_ab_variant_numeric($conn, 'potd_discount_cost', $potdVariant, (float)$potdDiscountCost))
                );
            }
            $cost = max(0, min($cost, $resolvedDiscount));
        } elseif ($potdMode === 'FREE') {
            $cost = 0;
        }
    }

    $flags['premium_unlock_cost_points'] = max(0, $cost);

    return [
        'cost' => (int)$flags['premium_unlock_cost_points'],
        'potd_mode' => $potdMode,
        'potd_discount_cost_points' => $potdDiscountCost,
        'potd_day_date' => $potdDayDate,
    ];
}

function v2_prompt_select_column_expr(mysqli $conn, string $postAlias, string $columnName): string
{
    if (v2_prompt_column_exists($conn, $columnName)) {
        return "{$postAlias}.{$columnName} AS {$columnName}";
    }

    return "NULL AS {$columnName}";
}

function v2_prompt_db_bool(mixed $value): ?bool
{
    if ($value === null) {
        return null;
    }
    if (is_bool($value)) {
        return $value;
    }
    if (is_int($value) || is_float($value)) {
        return ((int)$value) !== 0;
    }

    $raw = strtolower(trim((string)$value));
    if ($raw === '') {
        return null;
    }
    if (in_array($raw, ['1', 'true', 'yes', 'y', 'on'], true)) {
        return true;
    }
    if (in_array($raw, ['0', 'false', 'no', 'n', 'off'], true)) {
        return false;
    }

    return null;
}

function v2_prompt_bool_or_default(mixed $value, bool $default): bool
{
    $parsed = v2_prompt_db_bool($value);
    return $parsed ?? $default;
}

function v2_prompt_make_image_url(?string $path, string $baseUrl): ?string
{
    if (empty($path)) {
        return null;
    }
    if (preg_match('#^https?://#i', $path)) {
        return $path;
    }
    return $baseUrl . ltrim($path, '/');
}

function v2_prompt_parse_tags(?string $tagsField): array
{
    if (empty($tagsField)) {
        return [];
    }

    $decoded = json_decode((string)$tagsField, true);
    if (json_last_error() === JSON_ERROR_NONE && is_array($decoded)) {
        return array_values(array_unique(array_filter($decoded)));
    }

    return array_values(array_unique(array_filter(array_map('trim', explode(',', (string)$tagsField)))));
}

function v2_prompt_first_words(?string $text, int $words = 15): string
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

function v2_prompt_has_active_subscription(?string $accountType): bool
{
    $normalized = strtolower(trim((string)$accountType));
    return in_array($normalized, ['premium', 'pro', 'subscriber', 'subscribed'], true);
}

function v2_prompt_optional_user_profile(mysqli $conn): ?array
{
    $token = get_bearer_token();
    if ($token === null) {
        return null;
    }

    $stmt = $conn->prepare('SELECT id, account_type FROM users WHERE api_token = ? LIMIT 1');
    if (!$stmt) {
        return null;
    }

    $stmt->bind_param('s', $token);
    $stmt->execute();
    $res = $stmt->get_result();
    $row = $res ? $res->fetch_assoc() : null;
    $stmt->close();

    if (!$row) {
        return null;
    }

    $accountType = (string)($row['account_type'] ?? 'normal');

    return [
        'id' => (int)$row['id'],
        'account_type' => $accountType,
        'has_active_subscription' => v2_prompt_has_active_subscription($accountType),
    ];
}

function v2_prompt_is_visible_expression_sql(string $postAlias = 'p', ?mysqli $conn = null): string
{
    $legacyExpr = "CASE WHEN EXISTS(SELECT 1 FROM premium_pack_items ppi_vis WHERE ppi_vis.post_id = {$postAlias}.id) AND UPPER(COALESCE({$postAlias}.tier, 'FREE')) <> 'PREMIUM' THEN 0 ELSE 1 END";

    if ($conn !== null && v2_prompt_column_exists($conn, 'is_visible_in_general_feed')) {
        return "COALESCE({$postAlias}.is_visible_in_general_feed, {$legacyExpr})";
    }

    return $legacyExpr;
}

function v2_prompt_pack_links_for_posts(mysqli $conn, array $postIds, ?int $userId = null): array
{
    if (empty($postIds)) {
        return [];
    }

    $postIds = array_values(array_unique(array_map('intval', $postIds)));
    $placeholders = implode(',', array_fill(0, count($postIds), '?'));

    $thumbnailSubquery = "
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
    ";

    if ($userId !== null) {
        $sql = "
            SELECT
                ppi.post_id,
                pp.id AS pack_id,
                pp.name,
                pp.description,
                pp.price_points,
                pp.is_active,
                thumb.thumbnail_path,
                CASE WHEN upu.id IS NULL THEN 0 ELSE 1 END AS owns_pack
            FROM premium_pack_items ppi
            INNER JOIN premium_packs pp ON pp.id = ppi.pack_id
            LEFT JOIN ({$thumbnailSubquery}) thumb ON thumb.pack_id = pp.id
            LEFT JOIN user_pack_unlocks upu ON upu.pack_id = pp.id AND upu.user_id = ?
            WHERE ppi.post_id IN ({$placeholders})
            ORDER BY ppi.post_id ASC, pp.is_active DESC, pp.id ASC
        ";
        $types = 'i' . str_repeat('i', count($postIds));
        $params = array_merge([$userId], $postIds);
    } else {
        $sql = "
            SELECT
                ppi.post_id,
                pp.id AS pack_id,
                pp.name,
                pp.description,
                pp.price_points,
                pp.is_active,
                thumb.thumbnail_path,
                0 AS owns_pack
            FROM premium_pack_items ppi
            INNER JOIN premium_packs pp ON pp.id = ppi.pack_id
            LEFT JOIN ({$thumbnailSubquery}) thumb ON thumb.pack_id = pp.id
            WHERE ppi.post_id IN ({$placeholders})
            ORDER BY ppi.post_id ASC, pp.is_active DESC, pp.id ASC
        ";
        $types = str_repeat('i', count($postIds));
        $params = $postIds;
    }

    $stmt = $conn->prepare($sql);
    if (!$stmt) {
        return [];
    }

    $stmt->bind_param($types, ...$params);
    $stmt->execute();
    $res = $stmt->get_result();

    $map = [];
    while ($row = ($res ? $res->fetch_assoc() : null)) {
        $postId = (int)($row['post_id'] ?? 0);
        if ($postId <= 0) {
            continue;
        }

        if (!isset($map[$postId])) {
            $map[$postId] = [];
        }

        $map[$postId][] = [
            'id' => (int)($row['pack_id'] ?? 0),
            'name' => (string)($row['name'] ?? ''),
            'description' => $row['description'] ?? null,
            'price_points' => (int)($row['price_points'] ?? 0),
            'is_active' => (bool)($row['is_active'] ?? false),
            'thumbnail_path' => $row['thumbnail_path'] ?? null,
            'owns_pack' => ((int)($row['owns_pack'] ?? 0)) === 1,
        ];
    }

    $stmt->close();

    return $map;
}

function v2_prompt_primary_pack(array $packLinks): ?array
{
    if (empty($packLinks)) {
        return null;
    }

    foreach ($packLinks as $pack) {
        if (!empty($pack['is_active'])) {
            return $pack;
        }
    }

    return $packLinks[0] ?? null;
}

function v2_prompt_resolve_flags_from_row(array $row, bool $hasPack): array
{
    $tier = strtoupper((string)($row['tier'] ?? 'FREE'));
    if (!in_array($tier, ['FREE', 'PREMIUM'], true)) {
        $tier = 'FREE';
    }

    $legacyPackOnly = $hasPack && $tier !== 'PREMIUM';
    $isVisible = v2_prompt_bool_or_default($row['is_visible_in_general_feed'] ?? null, !$legacyPackOnly);

    $creditUnlockable = v2_prompt_bool_or_default($row['credit_unlock_enabled'] ?? null, $tier === 'PREMIUM');
    $rewardUnlockable = v2_prompt_bool_or_default($row['reward_unlock_enabled'] ?? null, $tier === 'PREMIUM');
    $tokenUnlockable = v2_prompt_bool_or_default($row['token_unlock_enabled'] ?? null, false);
    $subscriberUnlockable = v2_prompt_bool_or_default($row['subscriber_unlock_enabled'] ?? null, false);

    $hasDirectUnlockPath = $creditUnlockable || $rewardUnlockable || $tokenUnlockable || $subscriberUnlockable || ($tier === 'PREMIUM' && !$hasPack);
    $isPremium = ($tier === 'PREMIUM') || $hasPack || $creditUnlockable || $rewardUnlockable || $tokenUnlockable || $subscriberUnlockable;

    if (!$isPremium) {
        $creditUnlockable = false;
        $rewardUnlockable = false;
        $tokenUnlockable = false;
        $subscriberUnlockable = false;
        $premiumSourceType = 'NONE';
        $accessType = 'FREE';
        $isPackOnly = false;
    } elseif ($hasPack && !$hasDirectUnlockPath) {
        $premiumSourceType = 'PACK_ONLY';
        $accessType = 'PREMIUM_PACK_ONLY';
        $isPackOnly = true;
    } elseif ($hasPack) {
        $premiumSourceType = 'PACK_AND_DIRECT';
        $accessType = 'PREMIUM_PACK_OR_DIRECT';
        $isPackOnly = false;
    } else {
        $premiumSourceType = 'DIRECT';
        $accessType = 'PREMIUM_DIRECT';
        $isPackOnly = false;
    }

    $resolvedCost = (int)($row['premium_unlock_cost_points'] ?? 0);
    if ($creditUnlockable && $resolvedCost <= 0) {
        $resolvedCost = 200;
    }
    if (!$creditUnlockable && $resolvedCost < 0) {
        $resolvedCost = 0;
    }

    $unlockMethods = [];
    if ($isPremium && $hasPack) {
        $unlockMethods[] = 'PACK';
    }
    if ($creditUnlockable) {
        $unlockMethods[] = 'CREDITS';
    }
    if ($rewardUnlockable) {
        $unlockMethods[] = 'REWARDED_AD';
    }
    if ($tokenUnlockable) {
        $unlockMethods[] = 'TOKEN';
    }
    if ($subscriberUnlockable) {
        $unlockMethods[] = 'SUBSCRIPTION';
    }

    return [
        'tier' => $tier,
        'is_premium' => $isPremium,
        'premium_source_type' => $premiumSourceType,
        'access_type' => $accessType,
        'is_pack_only' => $isPackOnly,
        'is_visible_in_general_feed' => $isVisible,
        'is_credit_unlockable' => $creditUnlockable,
        'is_rewarded_unlockable' => $rewardUnlockable,
        'is_token_unlockable' => $tokenUnlockable,
        'is_subscriber_unlockable' => $subscriberUnlockable,
        'premium_unlock_cost_points' => $resolvedCost,
        'available_unlock_methods' => $unlockMethods,
    ];
}

function v2_prompt_is_unlocked(array $flags, bool $hasEntitlement, bool $hasActiveSubscription): bool
{
    if (!($flags['is_premium'] ?? false)) {
        return true;
    }

    if ($hasEntitlement) {
        return true;
    }

    if (($flags['is_subscriber_unlockable'] ?? false) && $hasActiveSubscription) {
        return true;
    }

    return false;
}

function v2_prompt_build_payload(
    array $row,
    array $flags,
    bool $isUnlocked,
    string $baseUrl,
    array $packLinks = []
): array {
    $promptText = (string)($row['prompt_text'] ?? '');
    $primaryPack = v2_prompt_primary_pack($packLinks);
    $packIds = array_values(array_unique(array_map(static function (array $pack): int {
        return (int)($pack['id'] ?? 0);
    }, $packLinks)));
    $packIds = array_values(array_filter($packIds, static fn (int $id): bool => $id > 0));

    return [
        'id' => (string)($row['id'] ?? ''),
        'title' => $row['title'] ?? '',
        'shortPrompt' => $row['short_description'] ?? null,
        'fullPrompt' => $isUnlocked ? $promptText : null,
        'imageUrl' => v2_prompt_make_image_url($row['image_url1'] ?? null, $baseUrl),
        'imageUrl2' => v2_prompt_make_image_url($row['image_url2'] ?? null, $baseUrl),
        'category' => $row['category_name'] ?? null,
        'tags' => v2_prompt_parse_tags($row['tags'] ?? null),
        'likes' => (int)($row['likes'] ?? 0),
        'favorites' => (int)($row['favorites'] ?? 0),
        'copies' => (int)($row['copies'] ?? 0),
        'views' => (int)($row['views'] ?? 0),
        'isPopular' => (bool)($row['is_popular'] ?? false),
        'isFeatured' => (bool)($row['is_featured'] ?? false),
        'status' => $row['status'] ?? null,
        'priority' => (int)($row['priority'] ?? 0),
        'createdAt' => $row['created_at'] ?? null,
        'updatedAt' => $row['updated_at'] ?? null,
        'tier' => (string)($flags['tier'] ?? 'FREE'),
        'premiumUnlockCostPoints' => (int)($flags['premium_unlock_cost_points'] ?? 0),
        'premiumPack' => $row['premium_pack'] ?? null,
        'isPremium' => (bool)($flags['is_premium'] ?? false),
        'premiumSourceType' => (string)($flags['premium_source_type'] ?? 'NONE'),
        'accessType' => (string)($flags['access_type'] ?? 'FREE'),
        'isVisibleInGeneralFeed' => (bool)($flags['is_visible_in_general_feed'] ?? true),
        'isPackOnly' => (bool)($flags['is_pack_only'] ?? false),
        'isCreditUnlockable' => (bool)($flags['is_credit_unlockable'] ?? false),
        'isRewardedUnlockable' => (bool)($flags['is_rewarded_unlockable'] ?? false),
        'isTokenUnlockable' => (bool)($flags['is_token_unlockable'] ?? false),
        'isSubscriberUnlockable' => (bool)($flags['is_subscriber_unlockable'] ?? false),
        'availableUnlockMethods' => $flags['available_unlock_methods'] ?? [],
        'premiumPackIds' => $packIds,
        'primaryPackId' => $primaryPack ? (int)($primaryPack['id'] ?? 0) : null,
        'primaryPackName' => $primaryPack['name'] ?? null,
        'primaryPackDescription' => $primaryPack['description'] ?? null,
        'primaryPackThumbnailUrl' => $primaryPack
            ? v2_prompt_make_image_url($primaryPack['thumbnail_path'] ?? null, $baseUrl)
            : null,
        'primaryPackPricePoints' => $primaryPack ? (int)($primaryPack['price_points'] ?? 0) : null,
        'primaryPackOwned' => $primaryPack ? (bool)($primaryPack['owns_pack'] ?? false) : false,
        'isLocked' => !$isUnlocked,
        'alreadyUnlocked' => $isUnlocked,
        'teaserText' => $isUnlocked ? null : v2_prompt_first_words($promptText, 15),
    ];
}
