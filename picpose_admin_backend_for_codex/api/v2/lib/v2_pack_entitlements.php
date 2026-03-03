<?php
require_once __DIR__ . '/v2_auth.php';

function v2_pack_optional_user_id(mysqli $conn): ?int
{
    $token = get_bearer_token();
    if ($token === null) {
        return null;
    }

    $stmt = $conn->prepare('SELECT id FROM users WHERE api_token = ? LIMIT 1');
    if (!$stmt) {
        return null;
    }
    $stmt->bind_param('s', $token);
    $stmt->execute();
    $res = $stmt->get_result();
    $row = $res ? $res->fetch_assoc() : null;
    $stmt->close();

    return $row ? (int)$row['id'] : null;
}

function v2_pack_user_owns_pack(mysqli $conn, int $userId, int $packId): bool
{
    $stmt = $conn->prepare('SELECT 1 FROM user_pack_unlocks WHERE user_id = ? AND pack_id = ? LIMIT 1');
    if (!$stmt) {
        return false;
    }
    $stmt->bind_param('ii', $userId, $packId);
    $stmt->execute();
    $res = $stmt->get_result();
    $row = $res ? $res->fetch_assoc() : null;
    $stmt->close();

    return (bool)$row;
}

function v2_pack_owned_pack_map(mysqli $conn, int $userId, array $packIds): array
{
    if (empty($packIds)) {
        return [];
    }

    $packIds = array_values(array_unique(array_map('intval', $packIds)));
    $placeholders = implode(',', array_fill(0, count($packIds), '?'));
    $types = 'i' . str_repeat('i', count($packIds));
    $sql = "SELECT pack_id FROM user_pack_unlocks WHERE user_id = ? AND pack_id IN ($placeholders)";
    $stmt = $conn->prepare($sql);
    if (!$stmt) {
        return [];
    }

    $params = array_merge([$userId], $packIds);
    $stmt->bind_param($types, ...$params);
    $stmt->execute();
    $res = $stmt->get_result();

    $map = [];
    if ($res) {
        while ($row = $res->fetch_assoc()) {
            $map[(int)$row['pack_id']] = true;
        }
    }
    $stmt->close();

    return $map;
}

function v2_pack_prompt_entitlement_map(mysqli $conn, int $userId, array $postIds): array
{
    if (empty($postIds)) {
        return [];
    }

    $postIds = array_values(array_unique(array_map('intval', $postIds)));
    $placeholders = implode(',', array_fill(0, count($postIds), '?'));
    $types = 'i' . str_repeat('i', count($postIds));

    $unlockSql = "SELECT post_id FROM user_prompt_unlocks WHERE user_id = ? AND post_id IN ($placeholders)";
    $unlockStmt = $conn->prepare($unlockSql);
    $map = [];
    if ($unlockStmt) {
        $params = array_merge([$userId], $postIds);
        $unlockStmt->bind_param($types, ...$params);
        $unlockStmt->execute();
        $unlockRes = $unlockStmt->get_result();
        if ($unlockRes) {
            while ($row = $unlockRes->fetch_assoc()) {
                $map[(int)$row['post_id']] = true;
            }
        }
        $unlockStmt->close();
    }

    $packSql = "
        SELECT DISTINCT ppi.post_id
        FROM user_pack_unlocks upu
        INNER JOIN premium_pack_items ppi ON ppi.pack_id = upu.pack_id
        WHERE upu.user_id = ?
          AND ppi.post_id IN ($placeholders)
    ";
    $packStmt = $conn->prepare($packSql);
    if ($packStmt) {
        $params = array_merge([$userId], $postIds);
        $packStmt->bind_param($types, ...$params);
        $packStmt->execute();
        $packRes = $packStmt->get_result();
        if ($packRes) {
            while ($row = $packRes->fetch_assoc()) {
                $map[(int)$row['post_id']] = true;
            }
        }
        $packStmt->close();
    }

    return $map;
}
