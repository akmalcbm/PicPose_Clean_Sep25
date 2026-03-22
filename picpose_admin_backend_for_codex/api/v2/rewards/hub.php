<?php
require_once __DIR__ . '/../lib/v2_ab.php';
require_once __DIR__ . '/../lib/v2_pack_entitlements.php';
require_once __DIR__ . '/../lib/v2_progress.php';

const V2_AD_DAILY_REWARD_CAP = 1;

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'GET') {
    json_err('Method Not Allowed', 405);
}

function v2_hub_default_daily_rewards(): array
{
    return [10, 20, 30, 40, 50, 60, 100];
}

function v2_hub_sanitize_daily_rewards(array $arr): array
{
    $out = [];
    for ($i = 0; $i < 7; $i++) {
        $v = isset($arr[$i]) ? (int)$arr[$i] : 0;
        if ($v < 0) $v = 0;
        if ($v > 1000) $v = 1000;
        $out[] = $v;
    }
    return $out;
}

function v2_hub_detect_config_table(mysqli $conn): ?string
{
    $sql = "SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ? LIMIT 1";
    $stmt = $conn->prepare($sql);
    if (!$stmt) return null;

    $table = 'pricing_config';
    $stmt->bind_param('s', $table);
    $stmt->execute();
    $exists = (bool)$stmt->get_result()->fetch_assoc();
    if ($exists) {
        $stmt->close();
        return 'pricing_config';
    }

    $table = 'app_config';
    $stmt->bind_param('s', $table);
    $stmt->execute();
    $exists = (bool)$stmt->get_result()->fetch_assoc();
    $stmt->close();
    if ($exists) {
        return 'app_config';
    }

    return null;
}

function v2_hub_load_daily_rewards(mysqli $conn): array
{
    $defaults = v2_hub_default_daily_rewards();
    $table = v2_hub_detect_config_table($conn);
    if ($table === null) return $defaults;

    $sql = "SELECT value_json FROM {$table} WHERE key_name = 'daily_login_rewards' LIMIT 1";
    $res = $conn->query($sql);
    if (!$res) return $defaults;
    $row = $res->fetch_assoc();
    if (!$row || !isset($row['value_json'])) return $defaults;

    $decoded = json_decode((string)$row['value_json'], true);
    if (!is_array($decoded)) return $defaults;
    $rewards = $decoded['rewards'] ?? null;
    if (!is_array($rewards)) return $defaults;
    return v2_hub_sanitize_daily_rewards($rewards);
}

function v2_hub_reward_for_streak(array $rewards, int $streakCount): int
{
    if (empty($rewards)) return 0;
    $index = min(max($streakCount, 1), count($rewards)) - 1;
    return (int)($rewards[$index] ?? 0);
}

function v2_hub_generate_referral_code(): string
{
    return strtoupper(substr(bin2hex(random_bytes(8)), 0, 10));
}

function v2_hub_get_or_create_referral_code(mysqli $conn, int $userId): ?string
{
    $stmt = $conn->prepare('SELECT code FROM referral_codes WHERE user_id = ? LIMIT 1');
    if (!$stmt) return null;
    $stmt->bind_param('i', $userId);
    $stmt->execute();
    $res = $stmt->get_result();
    $row = $res ? $res->fetch_assoc() : null;
    $stmt->close();
    if ($row && !empty($row['code'])) {
        return (string)$row['code'];
    }

    for ($i = 0; $i < 10; $i++) {
        $code = v2_hub_generate_referral_code();
        $insert = $conn->prepare('INSERT INTO referral_codes (user_id, code) VALUES (?, ?)');
        if (!$insert) return null;
        $insert->bind_param('is', $userId, $code);
        $ok = $insert->execute();
        $errno = (int)$insert->errno;
        $insert->close();
        if ($ok) {
            return $code;
        }
        if ($errno === 1062) {
            $check = $conn->prepare('SELECT code FROM referral_codes WHERE user_id = ? LIMIT 1');
            if ($check) {
                $check->bind_param('i', $userId);
                $check->execute();
                $checkRes = $check->get_result();
                $existing = $checkRes ? $checkRes->fetch_assoc() : null;
                $check->close();
                if ($existing && !empty($existing['code'])) {
                    return (string)$existing['code'];
                }
            }
        }
    }

    return null;
}

function v2_hub_make_image_url(?string $path, string $baseUrl): ?string
{
    if (empty($path)) return null;
    if (preg_match('#^https?://#i', $path)) return $path;
    return $baseUrl . ltrim($path, '/');
}

function v2_hub_parse_tags(?string $tagsField): array
{
    if (empty($tagsField)) return [];
    $decoded = json_decode((string)$tagsField, true);
    if (json_last_error() === JSON_ERROR_NONE && is_array($decoded)) {
        return array_values(array_unique(array_filter($decoded)));
    }
    return array_values(array_unique(array_filter(array_map('trim', explode(',', (string)$tagsField)))));
}

function v2_hub_first_words(?string $text, int $words = 15): string
{
    $clean = trim((string)$text);
    if ($clean === '') return '';
    $tokens = preg_split('/\s+/', $clean);
    if (!is_array($tokens)) return '';
    return implode(' ', array_slice($tokens, 0, max(1, $words)));
}

function v2_hub_load_today_potd(mysqli $conn, string $today): ?array
{
    $stmt = $conn->prepare("
        SELECT day_date, post_id, mode, discount_cost_points
        FROM daily_featured_prompts
        WHERE day_date = ?
        LIMIT 1
    ");
    if (!$stmt) return null;
    $stmt->bind_param('s', $today);
    $stmt->execute();
    $res = $stmt->get_result();
    $row = $res ? $res->fetch_assoc() : null;
    $stmt->close();
    return $row ?: null;
}

function v2_hub_pick_potd_post_id(mysqli $conn): ?int
{
    $sql = "
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
    $res = $conn->query($sql);
    if ($res && ($row = $res->fetch_assoc())) {
        return (int)$row['id'];
    }

    $fallback = $conn->query("
        SELECT p.id
        FROM ai_posts p
        WHERE p.status = 'published'
          AND p.tier = 'PREMIUM'
        ORDER BY (COALESCE(p.likes,0) + COALESCE(p.copies,0) + COALESCE(p.views,0)) DESC, p.created_at DESC
        LIMIT 1
    ");
    if ($fallback && ($row = $fallback->fetch_assoc())) {
        return (int)$row['id'];
    }
    return null;
}

function v2_hub_ensure_today_potd(mysqli $conn, string $today): ?array
{
    $existing = v2_hub_load_today_potd($conn, $today);
    if ($existing) return $existing;

    $postId = v2_hub_pick_potd_post_id($conn);
    if (!$postId) return null;

    $conn->begin_transaction();
    try {
        $check = v2_hub_load_today_potd($conn, $today);
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
            throw new RuntimeException('potd prepare failed');
        }
        $stmt->bind_param('sisi', $today, $postId, $mode, $discount);
        if (!$stmt->execute()) {
            $errno = (int)$stmt->errno;
            $stmt->close();
            if ($errno === 1062) {
                $conn->rollback();
                return v2_hub_load_today_potd($conn, $today);
            }
            throw new RuntimeException('potd insert failed');
        }
        $stmt->close();
        $conn->commit();
    } catch (Throwable $e) {
        $conn->rollback();
        return null;
    }

    return v2_hub_load_today_potd($conn, $today);
}

$user = require_user($conn);
$userId = (int)$user['id'];
$today = date('Y-m-d');

$pointsBalance = 0;
$walletStmt = $conn->prepare('SELECT points_balance FROM user_wallet WHERE user_id = ? LIMIT 1');
if ($walletStmt) {
    $walletStmt->bind_param('i', $userId);
    $walletStmt->execute();
    $walletRes = $walletStmt->get_result();
    $walletRow = $walletRes ? $walletRes->fetch_assoc() : null;
    $walletStmt->close();
    $pointsBalance = (int)($walletRow['points_balance'] ?? 0);
}

$tokenBalances = [
    'PROMPT_UNLOCK' => 0,
    'IMAGE_GEN_PRIORITY' => 0,
];
$tokenStmt = $conn->prepare('SELECT token_type, balance FROM user_tokens WHERE user_id = ?');
if ($tokenStmt) {
    $tokenStmt->bind_param('i', $userId);
    $tokenStmt->execute();
    $tokenRes = $tokenStmt->get_result();
    while ($row = ($tokenRes ? $tokenRes->fetch_assoc() : null)) {
        $tokenType = (string)($row['token_type'] ?? '');
        if ($tokenType !== '') {
            $tokenBalances[$tokenType] = (int)($row['balance'] ?? 0);
        }
    }
    $tokenStmt->close();
}

$rewardsSchedule = v2_hub_load_daily_rewards($conn);
$streakCount = 0;
$lastClaimDate = null;
$streakStmt = $conn->prepare('SELECT streak_count, last_claim_date FROM user_streaks WHERE user_id = ? LIMIT 1');
if ($streakStmt) {
    $streakStmt->bind_param('i', $userId);
    $streakStmt->execute();
    $streakRes = $streakStmt->get_result();
    $streakRow = $streakRes ? $streakRes->fetch_assoc() : null;
    $streakStmt->close();
    if ($streakRow) {
        $streakCount = (int)($streakRow['streak_count'] ?? 0);
        $lastClaimDate = $streakRow['last_claim_date'] ? (string)$streakRow['last_claim_date'] : null;
    }
}

$todayClaimed = false;
$claimStmt = $conn->prepare("
    SELECT 1
    FROM user_daily_claims
    WHERE user_id = ?
      AND claim_type = 'LOGIN'
      AND claim_date = CURDATE()
    LIMIT 1
");
if ($claimStmt) {
    $claimStmt->bind_param('i', $userId);
    $claimStmt->execute();
    $claimRes = $claimStmt->get_result();
    $todayClaimed = (bool)($claimRes && $claimRes->fetch_assoc());
    $claimStmt->close();
}

$adDailyCount = 0;
$adDailyStmt = $conn->prepare("
    SELECT COUNT(*) AS total_claims
    FROM user_daily_claims
    WHERE user_id = ?
      AND claim_type = 'AD_POINTS'
      AND claim_date = CURDATE()
");
if ($adDailyStmt) {
    $adDailyStmt->bind_param('i', $userId);
    $adDailyStmt->execute();
    $adDailyRes = $adDailyStmt->get_result();
    $adDailyRow = $adDailyRes ? $adDailyRes->fetch_assoc() : null;
    $adDailyStmt->close();
    $adDailyCount = (int)($adDailyRow['total_claims'] ?? 0);
}

$adVariant = get_user_variant($conn, $userId, 'ad_points_reward');
$adRewardPoints = (int)round(v2_ab_variant_numeric($conn, 'ad_points_reward', $adVariant, 10.0));
if ($adRewardPoints <= 0) {
    $adRewardPoints = 10;
}

$adRewardAvailable = $adDailyCount < V2_AD_DAILY_REWARD_CAP;

$referralCode = v2_hub_get_or_create_referral_code($conn, $userId);
$referralStats = [
    'pending' => 0,
    'qualified' => 0,
    'rewarded' => 0,
    'total' => 0,
];
$referralStatsStmt = $conn->prepare("
    SELECT
        SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) AS pending_count,
        SUM(CASE WHEN status = 'QUALIFIED' THEN 1 ELSE 0 END) AS qualified_count,
        SUM(CASE WHEN status = 'REWARDED' THEN 1 ELSE 0 END) AS rewarded_count,
        COUNT(*) AS total_count
    FROM referrals
    WHERE referrer_id = ?
");
if ($referralStatsStmt) {
    $referralStatsStmt->bind_param('i', $userId);
    $referralStatsStmt->execute();
    $referralStatsRes = $referralStatsStmt->get_result();
    $referralStatsRow = $referralStatsRes ? $referralStatsRes->fetch_assoc() : null;
    $referralStatsStmt->close();
    if ($referralStatsRow) {
        $referralStats = [
            'pending' => (int)($referralStatsRow['pending_count'] ?? 0),
            'qualified' => (int)($referralStatsRow['qualified_count'] ?? 0),
            'rewarded' => (int)($referralStatsRow['rewarded_count'] ?? 0),
            'total' => (int)($referralStatsRow['total_count'] ?? 0),
        ];
    }
}

$refereeReferralStatus = null;
$refereeStatusStmt = $conn->prepare("
    SELECT status
    FROM referrals
    WHERE referee_id = ?
    LIMIT 1
");
if ($refereeStatusStmt) {
    $refereeStatusStmt->bind_param('i', $userId);
    $refereeStatusStmt->execute();
    $refereeStatusRes = $refereeStatusStmt->get_result();
    $refereeStatusRow = $refereeStatusRes ? $refereeStatusRes->fetch_assoc() : null;
    $refereeStatusStmt->close();
    if ($refereeStatusRow && !empty($refereeStatusRow['status'])) {
        $refereeReferralStatus = (string)$refereeStatusRow['status'];
    }
}

$activePacks = [];
$packIds = [];
$packsRes = $conn->query("
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
");
if ($packsRes) {
    while ($row = $packsRes->fetch_assoc()) {
        $activePacks[] = $row;
        $packIds[] = (int)$row['id'];
    }
}
$ownedPackMap = v2_pack_owned_pack_map($conn, $userId, $packIds);
$packsOut = [];
foreach ($activePacks as $row) {
    $packId = (int)$row['id'];
    $packsOut[] = [
        'id' => $packId,
        'name' => $row['name'],
        'description' => $row['description'],
        'pricePoints' => (int)$row['price_points'],
        'itemCount' => (int)$row['item_count'],
        'isActive' => (bool)$row['is_active'],
        'createdAt' => $row['created_at'],
        'ownsPack' => isset($ownedPackMap[$packId]),
    ];
}

$progressXp = 0;
$progressLevel = 1;
$progressStmt = $conn->prepare('SELECT xp, level FROM user_progress WHERE user_id = ? LIMIT 1');
if ($progressStmt) {
    $progressStmt->bind_param('i', $userId);
    $progressStmt->execute();
    $progressRes = $progressStmt->get_result();
    $progressRow = $progressRes ? $progressRes->fetch_assoc() : null;
    $progressStmt->close();
    $progressXp = (int)($progressRow['xp'] ?? 0);
    $progressLevel = (int)($progressRow['level'] ?? 1);
}

$potdPayload = null;
$potd = v2_hub_ensure_today_potd($conn, $today);
if ($potd) {
    $postStmt = $conn->prepare("
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
    if ($postStmt) {
        $potdPostId = (int)$potd['post_id'];
        $postStmt->bind_param('i', $potdPostId);
        $postStmt->execute();
        $postRes = $postStmt->get_result();
        $potdPost = $postRes ? $postRes->fetch_assoc() : null;
        $postStmt->close();

        if ($potdPost) {
            $baseProto = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
            $baseUrl = $baseProto . '://' . ($_SERVER['HTTP_HOST'] ?? 'localhost') . '/';
            $mode = strtoupper((string)($potd['mode'] ?? 'NORMAL'));
            $discountCost = (int)($potd['discount_cost_points'] ?? 0);
            $baseCost = (int)($potdPost['premium_unlock_cost_points'] ?? 0);
            if ($baseCost <= 0) $baseCost = 200;
            $effectiveCost = $mode === 'DISCOUNT' ? max(0, $discountCost) : $baseCost;
            if ($mode === 'DISCOUNT') {
                $variant = get_user_variant($conn, $userId, 'potd_discount_cost');
                $effectiveCost = max(0, (int)round(v2_ab_variant_numeric($conn, 'potd_discount_cost', $variant, (float)$discountCost)));
            }

            $tier = strtoupper((string)($potdPost['tier'] ?? 'FREE'));
            $entitlements = v2_pack_prompt_entitlement_map($conn, $userId, [(int)$potdPost['id']]);
            $isUnlocked = ($tier !== 'PREMIUM') || ($mode === 'FREE') || isset($entitlements[(int)$potdPost['id']]);
            $isLocked = !$isUnlocked;
            $promptText = (string)($potdPost['prompt_text'] ?? '');

            $potdPayload = [
                'day_date' => (string)$potd['day_date'],
                'potd_mode' => $mode,
                'potd_unlock_cost_points' => $effectiveCost,
                'post' => [
                    'id' => (string)$potdPost['id'],
                    'title' => $potdPost['title'],
                    'shortPrompt' => $potdPost['short_description'],
                    'fullPrompt' => $isLocked ? null : $promptText,
                    'imageUrl' => v2_hub_make_image_url($potdPost['image_url1'], $baseUrl),
                    'imageUrl2' => v2_hub_make_image_url($potdPost['image_url2'], $baseUrl),
                    'category' => $potdPost['category_name'],
                    'tags' => v2_hub_parse_tags($potdPost['tags']),
                    'likes' => (int)$potdPost['likes'],
                    'favorites' => (int)$potdPost['favorites'],
                    'copies' => (int)$potdPost['copies'],
                    'views' => (int)$potdPost['views'],
                    'isPopular' => (bool)$potdPost['is_popular'],
                    'isFeatured' => (bool)$potdPost['is_featured'],
                    'status' => $potdPost['status'],
                    'priority' => (int)$potdPost['priority'],
                    'createdAt' => $potdPost['created_at'],
                    'updatedAt' => $potdPost['updated_at'],
                    'tier' => $tier,
                    'premiumUnlockCostPoints' => $effectiveCost,
                    'premiumPack' => $potdPost['premium_pack'],
                    'isLocked' => $isLocked,
                    'teaserText' => $isLocked ? v2_hub_first_words($promptText, 15) : null,
                ],
            ];
        }
    }
}

$experiments = [];
$experimentsRes = $conn->query("
    SELECT key_name
    FROM ab_experiments
    WHERE is_active = 1
    ORDER BY id ASC
");
if ($experimentsRes) {
    while ($row = $experimentsRes->fetch_assoc()) {
        $key = (string)$row['key_name'];
        $variant = get_user_variant($conn, $userId, $key);
        if ($variant !== null) {
            $experiments[] = [
                'key' => $key,
                'variant' => $variant,
                'payload' => v2_ab_variant_payload($conn, $key, $variant),
            ];
        }
    }
}

json_ok([
    'success' => true,
    'points_balance' => $pointsBalance,
    'streak_count' => $streakCount,
    'today_claimed' => $todayClaimed,
    'rewards_schedule' => $rewardsSchedule,
    'prompt_of_the_day' => $potdPayload,
    'referral' => [
        'my_code' => $referralCode,
        'status' => $refereeReferralStatus,
        'referred_count' => (int)($referralStats['total'] ?? 0),
        'rewarded_count' => (int)($referralStats['rewarded'] ?? 0),
        'pending_count' => (int)($referralStats['pending'] ?? 0),
        'qualified_count' => (int)($referralStats['qualified'] ?? 0),
        'code' => $referralCode,
        'stats' => $referralStats,
    ],
    'packs' => [
        'active' => $packsOut,
        'owned_count' => count($ownedPackMap),
    ],
    'progress' => [
        'xp' => $progressXp,
        'level' => $progressLevel,
        'next_level_xp' => v2_level_threshold_xp($progressLevel + 1),
        'points_reward_next' => 50,
    ],
    'token_balances' => $tokenBalances,
    'ad_daily_count' => $adDailyCount,
    'ad_daily_cap' => V2_AD_DAILY_REWARD_CAP,
    'ad_reward_points' => $adRewardPoints,
    'ad_reward_available' => $adRewardAvailable,
    'ab_flags' => $experiments,
]);
