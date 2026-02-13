<?php
require_once __DIR__ . '/common_api.php';
require_once __DIR__ . '/../../config.php';

$method = $_SERVER['REQUEST_METHOD'];
$id = isset($_GET['id']) ? intval($_GET['id']) : 0;
$VALID_API_KEY = "7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c";

function bind_params_ref($stmt, $types, $params) {
    if ($types === '' || empty($params)) return true;
    $bind = [];
    $bind[] = $types;
    for ($i = 0; $i < count($params); $i++) $bind[] = &$params[$i];
    return call_user_func_array([$stmt, 'bind_param'], $bind);
}

function normalize_json_list($raw) {
    if (empty($raw)) return [];
    $decoded = @json_decode($raw, true);
    if (json_last_error() === JSON_ERROR_NONE && is_array($decoded)) {
        return array_values(array_filter(array_map(function($v){ return trim((string)$v); }, $decoded)));
    }
    return array_values(array_filter(array_map('trim', preg_split('/[,;|]+/', (string)$raw))));
}

function parse_tags_field($raw) {
    if (empty($raw)) return [];
    $decoded = @json_decode($raw, true);
    if (json_last_error() === JSON_ERROR_NONE && is_array($decoded)) {
        return array_values(array_filter(array_map(function($v){ return trim(ltrim((string)$v, '#')); }, $decoded)));
    }
    return array_values(array_filter(array_map(function($v){ return trim(ltrim((string)$v, '#')); }, preg_split('/[,;|]+/', (string)$raw))));
}

function make_absolute_media_url($path) {
    if ($path === null) return null;
    $p = trim((string)$path);
    if ($p === '') return null;
    if (preg_match('#^https?://#i', $p)) return $p;
    $proto = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
    $host = $_SERVER['HTTP_HOST'] ?? '';
    return $proto . '://' . $host . '/' . ltrim($p, '/');
}

function to_iso_datetime($value) {
    $v = trim((string)$value);
    if ($v === '') return null;
    $ts = strtotime($v);
    if ($ts === false) return null;
    return gmdate('c', $ts);
}

function is_valid_api_key($queryApiKey, $fallbackKey) {
    $headerKey = $_SERVER['HTTP_X_API_KEY'] ?? '';
    $provided = trim((string)($queryApiKey ?: $headerKey));
    if ($provided === '') return false;
    return hash_equals($fallbackKey, $provided);
}

function estimate_read_time_minutes($textOrHtml) {
    $plain = trim(strip_tags((string)$textOrHtml));
    if ($plain === '') return 1;
    $count = str_word_count($plain);
    return max(1, (int)ceil($count / 200));
}

function detect_device_likes($conn, array $guideIds, $deviceId): array {
    $deviceId = trim((string)$deviceId);
    if ($deviceId === '' || empty($guideIds)) return [];

    $cleanIds = array_values(array_filter(array_map('intval', $guideIds), function($v) { return $v > 0; }));
    if (empty($cleanIds)) return [];

    $placeholders = implode(',', array_fill(0, count($cleanIds), '?'));
    $types = str_repeat('i', count($cleanIds)) . 's';
    $params = array_merge($cleanIds, [$deviceId]);

    $sql = "SELECT guide_id FROM guide_likes WHERE guide_id IN ($placeholders) AND device_id = ?";
    $stmt = $conn->prepare($sql);
    if (!$stmt) return [];
    if (!bind_params_ref($stmt, $types, $params)) {
        $stmt->close();
        return [];
    }
    if (!$stmt->execute()) {
        $stmt->close();
        return [];
    }
    $res = $stmt->get_result();
    $likedMap = [];
    while ($r = $res->fetch_assoc()) {
        $likedMap[(int)$r['guide_id']] = true;
    }
    $stmt->close();
    return $likedMap;
}

try {
    if ($method === 'GET') {
        if (!is_valid_api_key($_GET['api_key'] ?? '', $VALID_API_KEY)) {
            send_json(['success' => false, 'message' => 'Unauthorized Access', 'data' => []], 403);
        }
        $deviceId = trim((string)($_GET['device_id'] ?? $_POST['device_id'] ?? ''));

        if ($id > 0) {
            $sql = "SELECT g.*, c.name AS category_name
                    FROM guide_posts g
                    LEFT JOIN categories c ON c.id = g.category_id
                    WHERE g.id = ?
                    LIMIT 1";
            $stmt = $conn->prepare($sql);
            if (!$stmt) error_json('DB prepare failed: ' . $conn->error, 500);
            if (!bind_params_ref($stmt, 'i', [$id])) error_json('Bind failed', 500);
            if (!$stmt->execute()) error_json('DB execute failed: ' . $stmt->error, 500);
            $res = $stmt->get_result();
            $row = $res ? $res->fetch_assoc() : null;
            $stmt->close();
            if (!$row) error_json('Not found', 404);

            $row['tags'] = parse_tags_field($row['tags'] ?? '');
            $row['image_url1'] = make_absolute_media_url($row['image_url1'] ?? '');
            $images = normalize_json_list($row['images'] ?? '');
            $videos = normalize_json_list($row['videos'] ?? '');
            $row['images'] = array_values(array_filter(array_map('make_absolute_media_url', $images)));
            $row['videos'] = array_values(array_filter(array_map('make_absolute_media_url', $videos)));
            $row['id'] = (string)($row['id'] ?? '');
            $row['description'] = (string)($row['short_description'] ?? '');
            $row['image'] = $row['image_url1'] ?? null;
            $row['category'] = (string)($row['category_name'] ?? '');
            $row['isFeatured'] = !empty($row['is_featured']);
            $row['isPopular'] = !empty($row['is_popular']);
            $row['likes'] = intval($row['likes'] ?? 0);
            $row['likes_total'] = $row['likes'];
            $row['views'] = intval($row['views'] ?? 0);
            $row['views_total'] = $row['views'];
            $row['is_liked_by_user'] = false;
            if ($deviceId !== '') {
                $likeStmt = $conn->prepare("SELECT 1 FROM guide_likes WHERE guide_id = ? AND device_id = ? LIMIT 1");
                if ($likeStmt) {
                    $gid = intval($row['id'] ?? 0);
                    $likeStmt->bind_param('is', $gid, $deviceId);
                    if ($likeStmt->execute()) {
                        $likeRes = $likeStmt->get_result();
                        $row['is_liked_by_user'] = $likeRes && $likeRes->num_rows > 0;
                    }
                    $likeStmt->close();
                }
            }
            $row['favorites'] = intval($row['favorites'] ?? 0);
            $row['createdAt'] = to_iso_datetime($row['created_at'] ?? null);
            $row['updatedAt'] = to_iso_datetime($row['updated_at'] ?? null);
            $row['readTimeMinutes'] = estimate_read_time_minutes($row['content'] ?? '');

            send_json(['success' => true, 'data' => $row]);
        }

        list($page, $limit, $offset) = parse_pagination();
        $q = trim($_GET['q'] ?? '');
        $featured = isset($_GET['featured']) ? ($_GET['featured'] === 'true' || $_GET['featured'] === '1') : null;
        $popular = isset($_GET['popular']) ? ($_GET['popular'] === 'true' || $_GET['popular'] === '1') : null;
        $status = isset($_GET['status']) ? trim($_GET['status']) : null;
        $category = isset($_GET['category']) ? trim($_GET['category']) : null;

        $where = ' WHERE 1=1 ';
        $types = '';
        $params = [];

        if ($q !== '') {
            $where .= ' AND (g.title LIKE CONCAT("%", ?, "%") OR g.short_description LIKE CONCAT("%", ?, "%") OR g.tags LIKE CONCAT("%", ?, "%")) ';
            $types .= 'sss';
            $params[] = $q;
            $params[] = $q;
            $params[] = $q;
        }
        if ($featured !== null) {
            $where .= ' AND g.is_featured = ? ';
            $types .= 'i';
            $params[] = $featured ? 1 : 0;
        }
        if ($popular !== null) {
            $where .= ' AND g.is_popular = ? ';
            $types .= 'i';
            $params[] = $popular ? 1 : 0;
        }
        if ($status !== null && $status !== '') {
            $where .= ' AND g.status = ? ';
            $types .= 's';
            $params[] = $status;
        }
        if ($category !== null && $category !== '') {
            if (is_numeric($category)) {
                $where .= ' AND g.category_id = ? ';
                $types .= 'i';
                $params[] = intval($category);
            } else {
                $where .= ' AND c.name = ? ';
                $types .= 's';
                $params[] = $category;
            }
        }

        $countSql = "SELECT COUNT(1) AS cnt
                     FROM guide_posts g
                     LEFT JOIN categories c ON c.id = g.category_id
                     {$where}";
        $stmt = $conn->prepare($countSql);
        if (!$stmt) error_json('DB prepare failed (count): ' . $conn->error, 500);
        if ($types !== '' && !bind_params_ref($stmt, $types, $params)) error_json('Bind failed (count)', 500);
        if (!$stmt->execute()) error_json('DB execute failed (count): ' . $stmt->error, 500);
        $cres = $stmt->get_result();
        $total = intval(($cres ? $cres->fetch_assoc()['cnt'] : 0) ?? 0);
        $stmt->close();

        $listSql = "SELECT g.id, g.title, g.short_description, g.image_url1, g.tags, g.likes, g.views, g.favorites, g.is_featured, g.is_popular, g.status, g.content, g.created_at, g.updated_at, c.name AS category_name
                    FROM guide_posts g
                    LEFT JOIN categories c ON c.id = g.category_id
                    {$where}
                    ORDER BY g.priority DESC, g.created_at DESC
                    LIMIT ? OFFSET ?";
        $stmt = $conn->prepare($listSql);
        if (!$stmt) error_json('DB prepare failed (list): ' . $conn->error, 500);
        $listTypes = $types . 'ii';
        $listParams = array_merge($params, [$limit, $offset]);
        if (!bind_params_ref($stmt, $listTypes, $listParams)) error_json('Bind failed (list)', 500);
        if (!$stmt->execute()) error_json('DB execute failed (list): ' . $stmt->error, 500);
        $res = $stmt->get_result();

        $rawRows = [];
        $guideIds = [];
        while ($r = $res->fetch_assoc()) {
            $rawRows[] = $r;
            $guideIds[] = intval($r['id'] ?? 0);
        }
        $likedMap = detect_device_likes($conn, $guideIds, $deviceId);

        $rows = [];
        foreach ($rawRows as $r) {
            $description = (string)($r['short_description'] ?? '');
            $image = make_absolute_media_url($r['image_url1'] ?? '');
            $categoryName = (string)($r['category_name'] ?? '');
            $likes = intval($r['likes'] ?? 0);
            $views = intval($r['views'] ?? 0);
            $favorites = intval($r['favorites'] ?? 0);
            $isFeatured = !empty($r['is_featured']);
            $isPopular = !empty($r['is_popular']);
            $statusValue = (string)($r['status'] ?? 'published');
            $createdAt = to_iso_datetime($r['created_at'] ?? null);
            $updatedAt = to_iso_datetime($r['updated_at'] ?? null);
            $tags = parse_tags_field($r['tags'] ?? '');
            $readTimeMinutes = estimate_read_time_minutes($r['content'] ?? '');

            $rows[] = [
                'id' => (string)$r['id'],
                'title' => $r['title'] ?? '',
                'description' => $description,
                'image' => $image,
                'category' => $categoryName,
                'tags' => $tags,
                'likes' => $likes,
                'likes_total' => $likes,
                'views' => $views,
                'views_total' => $views,
                'is_liked_by_user' => !empty($likedMap[intval($r['id'] ?? 0)]),
                'favorites' => $favorites,
                'isFeatured' => $isFeatured,
                'isPopular' => $isPopular,
                'status' => $statusValue,
                'createdAt' => $createdAt,
                'updatedAt' => $updatedAt,
                'readTimeMinutes' => $readTimeMinutes,
                // Backward-compatible aliases
                'short_description' => $description,
                'image_url1' => $image,
                'category_name' => $categoryName,
                'is_featured' => $isFeatured,
                'is_popular' => $isPopular
            ];
        }
        $stmt->close();

        send_json(['success' => true, 'data' => $rows, 'page' => $page, 'limit' => $limit, 'total' => $total]);
    }

    if ($method === 'POST') {
        require_admin();
        $raw = file_get_contents('php://input');
        $data = json_decode($raw, true) ?? $_POST;

        $title = trim($data['title'] ?? '');
        $category_id = intval($data['category_id'] ?? 0);
        $short_description = isset($data['short_description']) ? trim((string)$data['short_description']) : null;
        $content = $data['content'] ?? null;
        $tags = $data['tags'] ?? null;
        $status = in_array($data['status'] ?? 'published', ['published','draft','archived'], true) ? $data['status'] : 'published';
        $priority = intval($data['priority'] ?? 0);
        $is_popular = !empty($data['is_popular']) ? 1 : 0;
        $is_featured = !empty($data['is_featured']) ? 1 : 0;
        $external_id = $data['external_id'] ?? null;
        $image_url1 = $data['image_url1'] ?? null;
        $images = $data['images'] ?? null;
        $videos = $data['videos'] ?? null;
        $content_blocks = $data['content_blocks'] ?? null;
        $likes = intval($data['likes'] ?? 0);
        $views = intval($data['views'] ?? 0);
        $favorites = intval($data['favorites'] ?? 0);

        if ($title === '' || $category_id <= 0) error_json('Title and category_id required', 422);

        $tags_json = null;
        if (!empty($tags)) {
            if (is_array($tags)) $tags_json = json_encode(array_values(array_filter($tags)), JSON_UNESCAPED_UNICODE);
            else $tags_json = json_encode(array_values(array_filter(array_map('trim', explode(',', (string)$tags)))), JSON_UNESCAPED_UNICODE);
        }

        $images_json = null;
        if (!empty($images)) {
            if (is_array($images)) $images_json = json_encode(array_values(array_filter($images)), JSON_UNESCAPED_UNICODE);
            else {
                $try = @json_decode($images, true);
                if (is_array($try)) $images_json = json_encode(array_values(array_filter($try)), JSON_UNESCAPED_UNICODE);
                else $images_json = json_encode(array_values(array_filter(array_map('trim', explode(',', (string)$images)))), JSON_UNESCAPED_UNICODE);
            }
        }

        $videos_json = null;
        if (!empty($videos)) {
            if (is_array($videos)) $videos_json = json_encode(array_values(array_filter($videos)), JSON_UNESCAPED_UNICODE);
            else {
                $try = @json_decode($videos, true);
                if (is_array($try)) $videos_json = json_encode(array_values(array_filter($try)), JSON_UNESCAPED_UNICODE);
                else $videos_json = json_encode(array_values(array_filter(array_map('trim', explode(',', (string)$videos)))), JSON_UNESCAPED_UNICODE);
            }
        }

        $content_blocks_json = null;
        if (!empty($content_blocks)) {
            if (is_array($content_blocks)) $content_blocks_json = json_encode($content_blocks, JSON_UNESCAPED_UNICODE);
            else {
                $try = @json_decode((string)$content_blocks, true);
                if (is_array($try)) $content_blocks_json = json_encode($try, JSON_UNESCAPED_UNICODE);
            }
        }

        $stmt = $conn->prepare("INSERT INTO guide_posts (title, category_id, short_description, content, tags, image_url1, images, videos, content_blocks, external_id, status, priority, is_popular, is_featured, likes, views, favorites, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())");
        if (!$stmt) error_json('DB prepare failed (insert): ' . $conn->error, 500);

        if (!$stmt->bind_param(
            'sisssssssssiiiiii',
            $title,
            $category_id,
            $short_description,
            $content,
            $tags_json,
            $image_url1,
            $images_json,
            $videos_json,
            $content_blocks_json,
            $external_id,
            $status,
            $priority,
            $is_popular,
            $is_featured,
            $likes,
            $views,
            $favorites
        )) {
            error_json('DB bind failed (insert): ' . $stmt->error, 500);
        }

        if (!$stmt->execute()) error_json('DB insert failed: ' . $stmt->error, 500);
        $newId = intval($conn->insert_id);
        $stmt->close();
        send_json(['success' => true, 'data' => ['id' => $newId]], 201);
    }

    if (in_array($method, ['PUT', 'PATCH'], true)) {
        require_admin();
        $body = json_decode(file_get_contents('php://input'), true) ?? $_POST;
        if ($id <= 0) $id = intval($body['id'] ?? 0);
        if ($id <= 0) error_json('ID required', 422);

        $check = $conn->prepare('SELECT id FROM guide_posts WHERE id = ? LIMIT 1');
        if (!$check) error_json('DB prepare failed: ' . $conn->error, 500);
        if (!bind_params_ref($check, 'i', [$id])) error_json('Bind failed', 500);
        if (!$check->execute()) error_json('DB execute failed: ' . $check->error, 500);
        $checkRes = $check->get_result();
        if (!$checkRes || $checkRes->num_rows === 0) {
            $check->close();
            error_json('Post not found', 404);
        }
        $check->close();

        $fields = [];
        $params = [];
        $types = '';

        $map = [
            'title' => 's',
            'category_id' => 'i',
            'short_description' => 's',
            'content' => 's',
            'tags' => 's',
            'image_url1' => 's',
            'images' => 's',
            'videos' => 's',
            'content_blocks' => 's',
            'external_id' => 's',
            'status' => 's',
            'priority' => 'i',
            'is_popular' => 'i',
            'is_featured' => 'i',
            'likes' => 'i',
            'views' => 'i',
            'favorites' => 'i'
        ];

        foreach ($map as $k => $t) {
            if (!array_key_exists($k, $body)) continue;
            $val = $body[$k];
            if (in_array($k, ['tags', 'images', 'videos', 'content_blocks'], true)) {
                if (is_array($val)) $val = json_encode($val, JSON_UNESCAPED_UNICODE);
                elseif ($val === '' || $val === null) $val = null;
                else {
                    $try = @json_decode((string)$val, true);
                    if (is_array($try)) $val = json_encode($try, JSON_UNESCAPED_UNICODE);
                    elseif ($k === 'tags') $val = json_encode(array_values(array_filter(array_map('trim', explode(',', (string)$val)))), JSON_UNESCAPED_UNICODE);
                }
            }
            if (in_array($k, ['category_id','priority','is_popular','is_featured','likes','views','favorites'], true)) {
                $val = intval($val);
            }
            $fields[] = "$k = ?";
            $params[] = $val;
            $types .= $t;
        }

        if (empty($fields)) error_json('No fields to update', 422);

        $sql = 'UPDATE guide_posts SET ' . implode(', ', $fields) . ', updated_at = NOW() WHERE id = ?';
        $types .= 'i';
        $params[] = $id;

        $stmt = $conn->prepare($sql);
        if (!$stmt) error_json('DB prepare failed (update): ' . $conn->error, 500);
        if (!bind_params_ref($stmt, $types, $params)) error_json('DB bind failed (update)', 500);
        if (!$stmt->execute()) error_json('DB update failed: ' . $stmt->error, 500);
        $stmt->close();

        send_json(['success' => true, 'data' => ['id' => $id]]);
    }

    if ($method === 'DELETE') {
        require_admin();
        if ($id <= 0) {
            $body = json_decode(file_get_contents('php://input'), true) ?? $_POST;
            $id = intval($body['id'] ?? 0);
        }
        if ($id <= 0) error_json('ID required', 422);

        $stmt = $conn->prepare('DELETE FROM guide_posts WHERE id = ? LIMIT 1');
        if (!$stmt) error_json('DB prepare failed (delete): ' . $conn->error, 500);
        if (!bind_params_ref($stmt, 'i', [$id])) error_json('Bind failed (delete)', 500);
        if (!$stmt->execute()) error_json('DB delete failed: ' . $stmt->error, 500);
        $stmt->close();

        send_json(['success' => true, 'data' => ['id' => $id]]);
    }

    error_json('Method not allowed', 405);
} catch (Throwable $t) {
    error_log('[get_guide_posts.php] Exception: ' . $t->getMessage());
    error_json('Internal server error', 500);
}
