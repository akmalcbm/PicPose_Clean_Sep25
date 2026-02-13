<?php
// api/ai_posts/get_daily_tips.php
header('Content-Type: application/json; charset=utf-8');
// Optional CORS for mobile dev (remove/restrict in production)
header("Access-Control-Allow-Origin: *");
require '../../config.php'; // CHANGED: was '../config.php', now needs to go up 2 levels

try {
    $sql = "SELECT id, tip_text, is_active, display_order, created_at, updated_at
            FROM daily_tips
            WHERE is_active = 1
            ORDER BY display_order ASC, created_at DESC";
    $res = $conn->query($sql);
    if (!$res) throw new Exception("DB error: " . $conn->error);

    $tips = [];
    while ($row = $res->fetch_assoc()) {
        $tips[] = [
            "id" => (string)$row['id'],
            "tip" => (string)$row['tip_text'],
            "isActive" => (bool)$row['is_active'],
            "order" => (int)$row['display_order'],
            "createdAt" => $row['created_at'],
            "updatedAt" => $row['updated_at']
        ];
    }

    echo json_encode([
        "success" => true,
        "message" => null,
        "data" => $tips
    ], JSON_UNESCAPED_UNICODE);

} catch (Exception $ex) {
    http_response_code(500);
    echo json_encode([
        "success" => false,
        "message" => $ex->getMessage(),
        "data" => []
    ], JSON_UNESCAPED_UNICODE);
}
?>