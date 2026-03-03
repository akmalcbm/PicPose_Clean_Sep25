<?php
require_once __DIR__ . '/../lib/v2_pack_entitlements.php';
require_once __DIR__ . '/../lib/v2_personalization.php';

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'GET') {
    json_err('Method Not Allowed', 405);
}

function v2_feed_make_image_url(?string $path, string $baseUrl): ?string
{
    if (empty($path)) {
        return null;
    }
    if (preg_match('#^https?://#i', $path)) {
        return $path;
    }
    return $baseUrl . ltrim($path, '/');
}

function v2_feed_first_words(?string $text, int $words = 15): string
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

$user = require_user($conn);
$userId = (int)$user['id'];
$limit = isset($_GET['limit']) ? max(1, min(50, (int)$_GET['limit'])) : 20;
$offset = isset($_GET['offset']) ? max(0, (int)$_GET['offset']) : 0;

$tagScores = [];
$tagStmt = $conn->prepare("
    SELECT tag, score
    FROM user_tag_scores
    WHERE user_id = ?
    ORDER BY score DESC, updated_at DESC
    LIMIT 15
");
if (!$tagStmt) {
    json_err('Database query preparation failed', 500);
}
$tagStmt->bind_param('i', $userId);
$tagStmt->execute();
$tagRes = $tagStmt->get_result();
while ($row = ($tagRes ? $tagRes->fetch_assoc() : null)) {
    $tag = v2_personalization_normalize_tag($row['tag'] ?? null);
    if ($tag !== null) {
        $tagScores[$tag] = (int)$row['score'];
    }
}
$tagStmt->close();

$baseSql = "
    SELECT
        p.id,
        p.title,
        p.short_description,
        p.prompt_text,
        p.image_url1,
        p.image_url2,
        COALESCE(p.likes, 0) AS likes,
        COALESCE(p.favorites, 0) AS favorites,
        COALESCE(p.copies, 0) AS copies,
        COALESCE(p.views, 0) AS views,
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
        COALESCE(c.name, 'Uncategorized') AS category_name
    FROM ai_posts p
    LEFT JOIN categories c ON c.id = p.category_id
    WHERE p.status = 'published'
";

$candidateLimit = max(50, $limit * 5);
$params = [];
$types = '';

if (!empty($tagScores)) {
    $matchConditions = [];
    foreach (array_keys($tagScores) as $tag) {
        $matchConditions[] = "p.tags LIKE CONCAT('%', ?, '%')";
        $params[] = $tag;
        $types .= 's';
        $matchConditions[] = "LOWER(COALESCE(c.name, '')) = ?";
        $params[] = $tag;
        $types .= 's';
    }
    $baseSql .= ' AND (' . implode(' OR ', $matchConditions) . ')';
}

$baseSql .= ' ORDER BY p.is_featured DESC, p.is_popular DESC, p.priority DESC, p.created_at DESC LIMIT ? OFFSET ?';
$params[] = $candidateLimit;
$params[] = 0;
$types .= 'ii';

$stmt = $conn->prepare($baseSql);
if (!$stmt) {
    json_err('Database query preparation failed', 500);
}
$stmt->bind_param($types, ...$params);
$stmt->execute();
$res = $stmt->get_result();

$rows = [];
$postIds = [];
while ($row = ($res ? $res->fetch_assoc() : null)) {
    $rows[] = $row;
    $postIds[] = (int)$row['id'];
}
$stmt->close();

if (empty($rows) && empty($tagScores)) {
    $fallbackSql = "
        SELECT
            p.id,
            p.title,
            p.short_description,
            p.prompt_text,
            p.image_url1,
            p.image_url2,
            COALESCE(p.likes, 0) AS likes,
            COALESCE(p.favorites, 0) AS favorites,
            COALESCE(p.copies, 0) AS copies,
            COALESCE(p.views, 0) AS views,
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
            COALESCE(c.name, 'Uncategorized') AS category_name
        FROM ai_posts p
        LEFT JOIN categories c ON c.id = p.category_id
        WHERE p.status = 'published'
        ORDER BY p.is_featured DESC, p.is_popular DESC, (COALESCE(p.likes,0) + COALESCE(p.copies,0) + COALESCE(p.views,0)) DESC, p.created_at DESC
        LIMIT ?
    ";
    $fallbackStmt = $conn->prepare($fallbackSql);
    if (!$fallbackStmt) {
        json_err('Database query preparation failed', 500);
    }
    $fallbackStmt->bind_param('i', $candidateLimit);
    $fallbackStmt->execute();
    $fallbackRes = $fallbackStmt->get_result();
    while ($row = ($fallbackRes ? $fallbackRes->fetch_assoc() : null)) {
        $rows[] = $row;
        $postIds[] = (int)$row['id'];
    }
    $fallbackStmt->close();
}

$entitlementMap = v2_pack_prompt_entitlement_map($conn, $userId, $postIds);
$baseProto = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
$baseUrl = $baseProto . '://' . ($_SERVER['HTTP_HOST'] ?? 'localhost') . '/';

$scored = [];
foreach ($rows as $row) {
    $parsedTags = v2_personalization_parse_tags($row['tags'] ?? null);
    $categoryTag = v2_personalization_normalize_tag($row['category_name'] ?? null);
    $matchWeight = 0;
    foreach ($parsedTags as $tag) {
        $matchWeight += (int)($tagScores[$tag] ?? 0);
    }
    if ($categoryTag !== null) {
        $matchWeight += (int)($tagScores[$categoryTag] ?? 0);
    }

    $popularityScore = ((int)$row['likes'] * 2) + ((int)$row['copies'] * 3) + (int)floor(((int)$row['views']) / 10);
    $featuredBoost = !empty($row['is_featured']) ? 100 : 0;
    $popularBoost = !empty($row['is_popular']) ? 40 : 0;
    $rankScore = $matchWeight + $featuredBoost + $popularBoost + $popularityScore;

    $tier = strtoupper((string)($row['tier'] ?? 'FREE'));
    $isUnlocked = ($tier !== 'PREMIUM') || isset($entitlementMap[(int)$row['id']]);
    $isLocked = !$isUnlocked;
    $promptText = (string)($row['prompt_text'] ?? '');

    $scored[] = [
        'rank_score' => $rankScore,
        'data' => [
            'id' => (string)$row['id'],
            'title' => $row['title'],
            'shortPrompt' => $row['short_description'],
            'fullPrompt' => $isLocked ? null : $promptText,
            'imageUrl' => v2_feed_make_image_url($row['image_url1'], $baseUrl),
            'imageUrl2' => v2_feed_make_image_url($row['image_url2'], $baseUrl),
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
            'premiumUnlockCostPoints' => ((int)($row['premium_unlock_cost_points'] ?? 0) > 0) ? (int)$row['premium_unlock_cost_points'] : 200,
            'premiumPack' => $row['premium_pack'],
            'isLocked' => $isLocked,
            'teaserText' => $isLocked ? v2_feed_first_words($promptText, 15) : null,
        ],
    ];
}

usort($scored, static function (array $a, array $b): int {
    if ($a['rank_score'] === $b['rank_score']) {
        return 0;
    }
    return ($a['rank_score'] > $b['rank_score']) ? -1 : 1;
});

$total = count($scored);
$paged = array_slice($scored, $offset, $limit);
$data = array_map(static function (array $item): array {
    return $item['data'];
}, $paged);

json_ok([
    'success' => true,
    'message' => 'OK',
    'total' => $total,
    'limit' => $limit,
    'offset' => $offset,
    'hasMore' => ($offset + $limit) < $total,
    'data' => $data,
]);
