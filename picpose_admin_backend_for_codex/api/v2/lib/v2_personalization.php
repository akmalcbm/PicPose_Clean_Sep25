<?php
require_once __DIR__ . '/v2_pack_entitlements.php';

function v2_personalization_parse_tags(?string $tagsField): array
{
    if (empty($tagsField)) {
        return [];
    }

    $decoded = json_decode($tagsField, true);
    if (json_last_error() === JSON_ERROR_NONE && is_array($decoded)) {
        $tags = $decoded;
    } else {
        $tags = array_map('trim', explode(',', (string)$tagsField));
    }

    $out = [];
    $seen = [];
    foreach ($tags as $tag) {
        $norm = v2_personalization_normalize_tag($tag);
        if ($norm === null || isset($seen[$norm])) {
            continue;
        }
        $seen[$norm] = true;
        $out[] = $norm;
    }

    return $out;
}

function v2_personalization_normalize_tag(?string $tag): ?string
{
    $tag = trim((string)$tag);
    $tag = ltrim($tag, '#');
    if ($tag === '') {
        return null;
    }
    $tag = preg_replace('/\s+/u', ' ', $tag);
    $tag = mb_strtolower(trim((string)$tag));
    if ($tag === '') {
        return null;
    }
    if (mb_strlen($tag) > 64) {
        $tag = mb_substr($tag, 0, 64);
    }
    return $tag;
}

function v2_personalization_load_post_signals(mysqli $conn, int $postId): array
{
    $stmt = $conn->prepare("
        SELECT p.tags, c.name AS category_name
        FROM ai_posts p
        LEFT JOIN categories c ON c.id = p.category_id
        WHERE p.id = ?
        LIMIT 1
    ");
    if (!$stmt) {
        return [];
    }
    $stmt->bind_param('i', $postId);
    $stmt->execute();
    $res = $stmt->get_result();
    $row = $res ? $res->fetch_assoc() : null;
    $stmt->close();

    if (!$row) {
        return [];
    }

    $tags = v2_personalization_parse_tags($row['tags'] ?? null);
    $categoryTag = v2_personalization_normalize_tag($row['category_name'] ?? null);
    if ($categoryTag !== null) {
        $tags[] = $categoryTag;
    }

    return array_values(array_unique(array_filter($tags)));
}

function update_user_tag_scores(mysqli $conn, int $userId, array $tags, int $scoreDelta): void
{
    if ($userId <= 0 || $scoreDelta <= 0 || empty($tags)) {
        return;
    }

    $normalized = [];
    foreach ($tags as $tag) {
        $norm = v2_personalization_normalize_tag($tag);
        if ($norm !== null) {
            $normalized[$norm] = true;
        }
    }

    if (empty($normalized)) {
        return;
    }

    $stmt = $conn->prepare("
        INSERT INTO user_tag_scores (user_id, tag, score)
        VALUES (?, ?, ?)
        ON DUPLICATE KEY UPDATE
            score = score + VALUES(score),
            updated_at = CURRENT_TIMESTAMP
    ");
    if (!$stmt) {
        throw new RuntimeException('Failed to prepare user_tag_scores upsert');
    }

    foreach (array_keys($normalized) as $tag) {
        $stmt->bind_param('isi', $userId, $tag, $scoreDelta);
        if (!$stmt->execute()) {
            $stmt->close();
            throw new RuntimeException('Failed to update user tag scores');
        }
    }

    $stmt->close();
}
