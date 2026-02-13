<?php
/**
 * get_quick_stats.php
 * Returns global app statistics:
 * total prompts, likes, favorites, copies, views
 */

header('Content-Type: application/json; charset=utf-8');
header("Access-Control-Allow-Origin: *");
header("Access-Control-Allow-Methods: GET");

require '../../config.php';

/* -------------------------
   API KEY VALIDATION
------------------------- */
$VALID_API_KEY = "7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c";
$providedKey = $_GET['api_key'] ?? $_POST['api_key'] ?? null;

if (!$providedKey || $providedKey !== $VALID_API_KEY) {
    http_response_code(403);
    echo json_encode([
        "success" => false,
        "message" => "Unauthorized"
    ]);
    exit();
}

/* -------------------------
   RESPONSE SKELETON
------------------------- */
$response = [
    "success" => false,
    "message" => "",
    "data"    => null
];

try {

    /* -------------------------
       SINGLE AGGREGATE QUERY
       (FAST & OPTIMIZED)
    ------------------------- */
    $sql = "
        SELECT 
            COUNT(*)                         AS total_prompts,
            SUM(COALESCE(likes, 0))          AS total_likes,
            SUM(COALESCE(favorites, 0))      AS total_favorites,
            SUM(COALESCE(copies, 0))         AS total_copies,
            SUM(COALESCE(views, 0))          AS total_views
        FROM ai_posts
        WHERE status = 'published'
    ";

    $result = $conn->query($sql);
    if (!$result) {
        throw new Exception("Query failed: " . $conn->error);
    }

    $row = $result->fetch_assoc();

    $response["success"] = true;
    $response["message"] = "OK";
    $response["data"] = [
        "total_prompts"   => (int)($row["total_prompts"]   ?? 0),
        "total_likes"     => (int)($row["total_likes"]     ?? 0),
        "total_favorites" => (int)($row["total_favorites"] ?? 0),
        "total_copies"    => (int)($row["total_copies"]    ?? 0),
        "total_views"     => (int)($row["total_views"]     ?? 0)
    ];

    echo json_encode($response, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);

} catch (Throwable $e) {

    http_response_code(500);
    $response["message"] = "Server error";
    $response["error"]   = $e->getMessage();

    echo json_encode($response, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);

} finally {

    if (isset($result) && $result instanceof mysqli_result) {
        $result->free();
    }
    $conn->close();
}
