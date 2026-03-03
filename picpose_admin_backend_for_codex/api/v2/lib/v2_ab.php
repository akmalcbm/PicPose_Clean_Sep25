<?php
require_once __DIR__ . '/v2_auth.php';

function v2_ab_parse_variants(?string $variantsJson): array
{
    $decoded = json_decode((string)$variantsJson, true);
    if (!is_array($decoded)) {
        return [];
    }

    $variants = [];

    $isList = array_keys($decoded) === range(0, count($decoded) - 1);
    if ($isList) {
        foreach ($decoded as $item) {
            if (!is_array($item)) {
                continue;
            }
            $name = trim((string)($item['variant'] ?? $item['name'] ?? $item['key'] ?? ''));
            $weight = (float)($item['weight'] ?? 0);
            if ($name === '' || $weight <= 0) {
                continue;
            }
            $variants[] = [
                'name' => $name,
                'weight' => $weight,
                'payload' => $item,
            ];
        }
        return $variants;
    }

    foreach ($decoded as $name => $value) {
        if (is_numeric($value)) {
            $weight = (float)$value;
            $payload = ['value' => null];
        } elseif (is_array($value)) {
            $weight = (float)($value['weight'] ?? 0);
            $payload = $value;
        } else {
            continue;
        }

        $name = trim((string)$name);
        if ($name === '' || $weight <= 0) {
            continue;
        }

        $variants[] = [
            'name' => $name,
            'weight' => $weight,
            'payload' => $payload,
        ];
    }

    return $variants;
}

function v2_ab_choose_variant(array $variants): ?string
{
    if (empty($variants)) {
        return null;
    }

    $total = 0.0;
    foreach ($variants as $variant) {
        $total += (float)$variant['weight'];
    }
    if ($total <= 0) {
        return null;
    }

    $threshold = random_int(1, 1000000) / 1000000 * $total;
    $running = 0.0;
    foreach ($variants as $variant) {
        $running += (float)$variant['weight'];
        if ($threshold <= $running) {
            return (string)$variant['name'];
        }
    }

    return (string)$variants[array_key_last($variants)]['name'];
}

function get_user_variant(mysqli $conn, int $userId, string $experimentKey): ?string
{
    if ($userId <= 0 || $experimentKey === '') {
        return null;
    }

    $assignStmt = $conn->prepare("
        SELECT variant
        FROM ab_user_assignments
        WHERE user_id = ? AND experiment_key = ?
        LIMIT 1
    ");
    if (!$assignStmt) {
        return null;
    }
    $assignStmt->bind_param('is', $userId, $experimentKey);
    $assignStmt->execute();
    $assignRes = $assignStmt->get_result();
    $assignment = $assignRes ? $assignRes->fetch_assoc() : null;
    $assignStmt->close();

    if ($assignment && !empty($assignment['variant'])) {
        return (string)$assignment['variant'];
    }

    $expStmt = $conn->prepare("
        SELECT variants_json
        FROM ab_experiments
        WHERE key_name = ? AND is_active = 1
        LIMIT 1
    ");
    if (!$expStmt) {
        return null;
    }
    $expStmt->bind_param('s', $experimentKey);
    $expStmt->execute();
    $expRes = $expStmt->get_result();
    $experiment = $expRes ? $expRes->fetch_assoc() : null;
    $expStmt->close();

    if (!$experiment) {
        return null;
    }

    $variants = v2_ab_parse_variants($experiment['variants_json'] ?? null);
    $chosen = v2_ab_choose_variant($variants);
    if ($chosen === null) {
        return null;
    }

    $insertStmt = $conn->prepare("
        INSERT INTO ab_user_assignments (user_id, experiment_key, variant)
        VALUES (?, ?, ?)
    ");
    if (!$insertStmt) {
        return $chosen;
    }
    $insertStmt->bind_param('iss', $userId, $experimentKey, $chosen);
    $ok = $insertStmt->execute();
    $errno = (int)$insertStmt->errno;
    $insertStmt->close();

    if ($ok) {
        return $chosen;
    }
    if ($errno === 1062) {
        return get_user_variant($conn, $userId, $experimentKey);
    }

    return $chosen;
}

function v2_ab_variant_payload(mysqli $conn, string $experimentKey, ?string $variantName): ?array
{
    if ($variantName === null || $experimentKey === '') {
        return null;
    }

    $stmt = $conn->prepare("
        SELECT variants_json
        FROM ab_experiments
        WHERE key_name = ? AND is_active = 1
        LIMIT 1
    ");
    if (!$stmt) {
        return null;
    }
    $stmt->bind_param('s', $experimentKey);
    $stmt->execute();
    $res = $stmt->get_result();
    $row = $res ? $res->fetch_assoc() : null;
    $stmt->close();

    if (!$row) {
        return null;
    }

    $variants = v2_ab_parse_variants($row['variants_json'] ?? null);
    foreach ($variants as $variant) {
        if ((string)$variant['name'] === $variantName) {
            return is_array($variant['payload']) ? $variant['payload'] : null;
        }
    }

    return null;
}

function v2_ab_variant_numeric(mysqli $conn, string $experimentKey, ?string $variantName, float $default): float
{
    $payload = v2_ab_variant_payload($conn, $experimentKey, $variantName);
    if (!$payload) {
        return $default;
    }

    foreach (['value', 'multiplier', 'points', 'cost'] as $key) {
        if (isset($payload[$key]) && is_numeric($payload[$key])) {
            return (float)$payload[$key];
        }
    }

    return $default;
}
