<?php

if (!function_exists('potd_db_table_exists')) {
    function potd_db_table_exists(mysqli $conn, string $table): bool
    {
        static $cache = [];
        $key = strtolower(trim($table));
        if ($key === '') {
            return false;
        }
        if (array_key_exists($key, $cache)) {
            return $cache[$key];
        }

        $sql = "
            SELECT 1
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = ?
            LIMIT 1
        ";
        $stmt = $conn->prepare($sql);
        if (!$stmt) {
            $cache[$key] = false;
            return false;
        }
        $stmt->bind_param('s', $table);
        $stmt->execute();
        $res = $stmt->get_result();
        $exists = (bool)($res && $res->fetch_assoc());
        $stmt->close();

        $cache[$key] = $exists;
        return $exists;
    }
}

if (!function_exists('potd_normalize_mode')) {
    function potd_normalize_mode(?string $mode, string $default = 'NORMAL'): string
    {
        $normalized = strtoupper(trim((string)$mode));
        if (in_array($normalized, ['FREE', 'DISCOUNT', 'NORMAL'], true)) {
            return $normalized;
        }

        $fallback = strtoupper(trim($default));
        return in_array($fallback, ['FREE', 'DISCOUNT', 'NORMAL'], true) ? $fallback : 'NORMAL';
    }
}

if (!function_exists('potd_today_date')) {
    function potd_today_date(mysqli $conn): string
    {
        $res = $conn->query("SELECT DATE_FORMAT(CURDATE(), '%Y-%m-%d') AS day_date");
        $row = ($res instanceof mysqli_result) ? $res->fetch_assoc() : null;
        $day = trim((string)($row['day_date'] ?? ''));
        return $day !== '' ? $day : date('Y-m-d');
    }
}

if (!function_exists('potd_default_config')) {
    function potd_default_config(): array
    {
        return [
            'allow_featured_fallback' => true,
            'allow_premium_prompts' => true,
            'enable_legacy_daily_fallback' => true,
            'featured_fallback_mode' => 'NORMAL',
            'featured_fallback_discount_cost_points' => 0,
            'default_badge_text' => null,
        ];
    }
}

if (!function_exists('potd_load_config')) {
    function potd_load_config(mysqli $conn): array
    {
        $cfg = potd_default_config();

        if (!potd_db_table_exists($conn, 'prompt_of_day_config')) {
            return $cfg;
        }

        $res = $conn->query("SELECT * FROM prompt_of_day_config WHERE id = 1 LIMIT 1");
        if (!$res) {
            return $cfg;
        }

        $row = $res->fetch_assoc();
        if (!$row) {
            return $cfg;
        }

        $cfg['allow_featured_fallback'] = ((int)($row['allow_featured_fallback'] ?? 1)) === 1;
        $cfg['allow_premium_prompts'] = ((int)($row['allow_premium_prompts'] ?? 1)) === 1;
        $cfg['enable_legacy_daily_fallback'] = ((int)($row['enable_legacy_daily_fallback'] ?? 1)) === 1;
        $cfg['featured_fallback_mode'] = potd_normalize_mode((string)($row['featured_fallback_mode'] ?? 'NORMAL'));
        $cfg['featured_fallback_discount_cost_points'] = max(0, (int)($row['featured_fallback_discount_cost_points'] ?? 0));

        $badge = trim((string)($row['default_badge_text'] ?? ''));
        $cfg['default_badge_text'] = $badge !== '' ? mb_substr($badge, 0, 80) : null;

        return $cfg;
    }
}

if (!function_exists('potd_row_to_offer')) {
    function potd_row_to_offer(array $row, string $source, string $dayDate, ?array $config = null): array
    {
        $cfg = $config ?: potd_default_config();
        $badge = trim((string)($row['badge_text'] ?? ''));
        if ($badge === '') {
            $badge = trim((string)($cfg['default_badge_text'] ?? ''));
        }

        return [
            'day_date' => $dayDate,
            'post_id' => (int)($row['prompt_id'] ?? $row['post_id'] ?? 0),
            'mode' => potd_normalize_mode((string)($row['mode'] ?? 'NORMAL')),
            'discount_cost_points' => max(0, (int)($row['discount_cost_points'] ?? 0)),
            'source' => $source,
            'entry_id' => isset($row['id']) ? (int)$row['id'] : null,
            'title_override' => isset($row['title_override']) ? trim((string)$row['title_override']) : null,
            'subtitle_override' => isset($row['subtitle_override']) ? trim((string)$row['subtitle_override']) : null,
            'badge_text' => $badge !== '' ? mb_substr($badge, 0, 80) : null,
            'effective_start_date' => $row['start_date'] ?? null,
            'effective_end_date' => $row['end_date'] ?? null,
        ];
    }
}

if (!function_exists('potd_find_scheduled_offer')) {
    function potd_find_scheduled_offer(mysqli $conn, string $dayDate, array $config): ?array
    {
        if (!potd_db_table_exists($conn, 'prompt_of_day_entries')) {
            return null;
        }

        $allowPremium = !empty($config['allow_premium_prompts']) ? 1 : 0;
        $sql = "
            SELECT
                e.id,
                e.prompt_id,
                e.title_override,
                e.subtitle_override,
                e.badge_text,
                e.start_date,
                e.end_date,
                e.mode,
                e.discount_cost_points
            FROM prompt_of_day_entries e
            INNER JOIN ai_posts p
                ON p.id = e.prompt_id
               AND p.status = 'published'
            WHERE e.is_active = 1
              AND e.is_default = 0
              AND e.start_date IS NOT NULL
              AND e.start_date <= ?
              AND (e.end_date IS NULL OR e.end_date >= ?)
              AND (? = 1 OR UPPER(COALESCE(p.tier, 'FREE')) <> 'PREMIUM')
            ORDER BY e.priority DESC, e.start_date DESC, e.id DESC
            LIMIT 1
        ";

        $stmt = $conn->prepare($sql);
        if (!$stmt) {
            return null;
        }
        $stmt->bind_param('ssi', $dayDate, $dayDate, $allowPremium);
        $stmt->execute();
        $res = $stmt->get_result();
        $row = $res ? $res->fetch_assoc() : null;
        $stmt->close();

        if (!$row) {
            return null;
        }

        return potd_row_to_offer($row, 'SCHEDULED', $dayDate, $config);
    }
}

if (!function_exists('potd_find_default_offer')) {
    function potd_find_default_offer(mysqli $conn, string $dayDate, array $config): ?array
    {
        if (!potd_db_table_exists($conn, 'prompt_of_day_entries')) {
            return null;
        }

        $allowPremium = !empty($config['allow_premium_prompts']) ? 1 : 0;
        $sql = "
            SELECT
                e.id,
                e.prompt_id,
                e.title_override,
                e.subtitle_override,
                e.badge_text,
                e.mode,
                e.discount_cost_points
            FROM prompt_of_day_entries e
            INNER JOIN ai_posts p
                ON p.id = e.prompt_id
               AND p.status = 'published'
            WHERE e.is_active = 1
              AND e.is_default = 1
              AND (? = 1 OR UPPER(COALESCE(p.tier, 'FREE')) <> 'PREMIUM')
            ORDER BY e.priority DESC, e.updated_at DESC, e.id DESC
            LIMIT 1
        ";

        $stmt = $conn->prepare($sql);
        if (!$stmt) {
            return null;
        }
        $stmt->bind_param('i', $allowPremium);
        $stmt->execute();
        $res = $stmt->get_result();
        $row = $res ? $res->fetch_assoc() : null;
        $stmt->close();

        if (!$row) {
            return null;
        }

        $row['start_date'] = null;
        $row['end_date'] = null;

        return potd_row_to_offer($row, 'DEFAULT', $dayDate, $config);
    }
}

if (!function_exists('potd_find_legacy_daily_offer')) {
    function potd_find_legacy_daily_offer(mysqli $conn, string $dayDate, array $config): ?array
    {
        if (empty($config['enable_legacy_daily_fallback'])) {
            return null;
        }
        if (!potd_db_table_exists($conn, 'daily_featured_prompts')) {
            return null;
        }

        $allowPremium = !empty($config['allow_premium_prompts']) ? 1 : 0;
        $sql = "
            SELECT d.day_date, d.post_id, d.mode, d.discount_cost_points
            FROM daily_featured_prompts d
            INNER JOIN ai_posts p
                ON p.id = d.post_id
               AND p.status = 'published'
            WHERE d.day_date = ?
              AND (? = 1 OR UPPER(COALESCE(p.tier, 'FREE')) <> 'PREMIUM')
            LIMIT 1
        ";

        $stmt = $conn->prepare($sql);
        if (!$stmt) {
            return null;
        }
        $stmt->bind_param('si', $dayDate, $allowPremium);
        $stmt->execute();
        $res = $stmt->get_result();
        $row = $res ? $res->fetch_assoc() : null;
        $stmt->close();

        if (!$row) {
            return null;
        }

        return potd_row_to_offer($row, 'LEGACY_DAILY', $dayDate, $config);
    }
}

if (!function_exists('potd_find_featured_fallback_offer')) {
    function potd_find_featured_fallback_offer(mysqli $conn, string $dayDate, array $config): ?array
    {
        if (empty($config['allow_featured_fallback'])) {
            return null;
        }

        $allowPremium = !empty($config['allow_premium_prompts']) ? 1 : 0;
        $sql = "
            SELECT p.id AS prompt_id
            FROM ai_posts p
            WHERE p.status = 'published'
              AND p.is_featured = 1
              AND (? = 1 OR UPPER(COALESCE(p.tier, 'FREE')) <> 'PREMIUM')
            ORDER BY p.priority DESC, p.created_at DESC
            LIMIT 1
        ";

        $stmt = $conn->prepare($sql);
        if (!$stmt) {
            return null;
        }
        $stmt->bind_param('i', $allowPremium);
        $stmt->execute();
        $res = $stmt->get_result();
        $row = $res ? $res->fetch_assoc() : null;
        $stmt->close();

        if (!$row) {
            return null;
        }

        $row['mode'] = potd_normalize_mode((string)($config['featured_fallback_mode'] ?? 'NORMAL'));
        $row['discount_cost_points'] = max(0, (int)($config['featured_fallback_discount_cost_points'] ?? 0));
        $row['badge_text'] = $config['default_badge_text'] ?? null;

        return potd_row_to_offer($row, 'FEATURED_FALLBACK', $dayDate, $config);
    }
}

if (!function_exists('potd_resolve_effective_prompt_offer')) {
    function potd_resolve_effective_prompt_offer(mysqli $conn, ?string $dayDate = null): ?array
    {
        $resolvedDay = $dayDate ?: potd_today_date($conn);
        $cfg = potd_load_config($conn);

        $scheduled = potd_find_scheduled_offer($conn, $resolvedDay, $cfg);
        if ($scheduled) {
            return $scheduled;
        }

        $default = potd_find_default_offer($conn, $resolvedDay, $cfg);
        if ($default) {
            return $default;
        }

        $legacy = potd_find_legacy_daily_offer($conn, $resolvedDay, $cfg);
        if ($legacy) {
            return $legacy;
        }

        $featuredFallback = potd_find_featured_fallback_offer($conn, $resolvedDay, $cfg);
        if ($featuredFallback) {
            return $featuredFallback;
        }

        return null;
    }
}

if (!function_exists('potd_prompt_preview_by_id')) {
    function potd_prompt_preview_by_id(mysqli $conn, int $promptId): ?array
    {
        if ($promptId <= 0) {
            return null;
        }

        $stmt = $conn->prepare(" 
            SELECT
                p.id,
                p.title,
                p.short_description,
                p.image_url1,
                p.image_url2,
                p.status,
                UPPER(COALESCE(p.tier, 'FREE')) AS tier,
                p.is_featured,
                c.name AS category_name
            FROM ai_posts p
            LEFT JOIN categories c ON c.id = p.category_id
            WHERE p.id = ?
            LIMIT 1
        ");
        if (!$stmt) {
            return null;
        }
        $stmt->bind_param('i', $promptId);
        $stmt->execute();
        $res = $stmt->get_result();
        $row = $res ? $res->fetch_assoc() : null;
        $stmt->close();

        return $row ?: null;
    }
}

if (!function_exists('potd_prompt_is_eligible')) {
    function potd_prompt_is_eligible(mysqli $conn, int $promptId, bool $allowPremium = true): bool
    {
        $row = potd_prompt_preview_by_id($conn, $promptId);
        if (!$row) {
            return false;
        }

        if (strcasecmp((string)($row['status'] ?? ''), 'published') !== 0) {
            return false;
        }

        $tier = strtoupper((string)($row['tier'] ?? 'FREE'));
        if (!$allowPremium && $tier === 'PREMIUM') {
            return false;
        }

        return true;
    }
}

if (!function_exists('potd_has_schedule_conflict')) {
    function potd_has_schedule_conflict(
        mysqli $conn,
        string $startDate,
        ?string $endDate,
        int $excludeId = 0
    ): bool {
        if (!potd_db_table_exists($conn, 'prompt_of_day_entries')) {
            return false;
        }

        $safeEnd = $endDate ?: '9999-12-31';

        if ($excludeId > 0) {
            $sql = "
                SELECT 1
                FROM prompt_of_day_entries e
                WHERE e.is_active = 1
                  AND e.is_default = 0
                  AND e.id <> ?
                  AND e.start_date IS NOT NULL
                  AND e.start_date <= ?
                  AND COALESCE(e.end_date, '9999-12-31') >= ?
                LIMIT 1
            ";
            $stmt = $conn->prepare($sql);
            if (!$stmt) {
                return true;
            }
            $stmt->bind_param('iss', $excludeId, $safeEnd, $startDate);
        } else {
            $sql = "
                SELECT 1
                FROM prompt_of_day_entries e
                WHERE e.is_active = 1
                  AND e.is_default = 0
                  AND e.start_date IS NOT NULL
                  AND e.start_date <= ?
                  AND COALESCE(e.end_date, '9999-12-31') >= ?
                LIMIT 1
            ";
            $stmt = $conn->prepare($sql);
            if (!$stmt) {
                return true;
            }
            $stmt->bind_param('ss', $safeEnd, $startDate);
        }

        $stmt->execute();
        $res = $stmt->get_result();
        $conflict = (bool)($res && $res->fetch_assoc());
        $stmt->close();

        return $conflict;
    }
}

if (!function_exists('potd_has_other_active_default')) {
    function potd_has_other_active_default(mysqli $conn, int $excludeId = 0): bool
    {
        if (!potd_db_table_exists($conn, 'prompt_of_day_entries')) {
            return false;
        }

        if ($excludeId > 0) {
            $stmt = $conn->prepare(" 
                SELECT 1
                FROM prompt_of_day_entries
                WHERE is_active = 1
                  AND is_default = 1
                  AND id <> ?
                LIMIT 1
            ");
            if (!$stmt) {
                return true;
            }
            $stmt->bind_param('i', $excludeId);
        } else {
            $stmt = $conn->prepare(" 
                SELECT 1
                FROM prompt_of_day_entries
                WHERE is_active = 1
                  AND is_default = 1
                LIMIT 1
            ");
            if (!$stmt) {
                return true;
            }
        }

        $stmt->execute();
        $res = $stmt->get_result();
        $exists = (bool)($res && $res->fetch_assoc());
        $stmt->close();

        return $exists;
    }
}
