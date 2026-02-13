<?php
// api/get-active-tokens.php (Admin only)
declare(strict_types=1);
session_start();
header('Content-Type: application/json');

require_once __DIR__ . '/../config.php';

if (!isset($_SESSION['admin'])) {
    http_response_code(401);
    echo json_encode(['error' => 'Unauthorized']);
    exit();
}

$page = max(1, intval($_GET['page'] ?? 1));
$perPage = min(100, intval($_GET['per_page'] ?? 50));
$offset = ($page - 1) * $perPage;

try {
    // Get total count
    $totalResult = $conn->query("
        SELECT COUNT(*) as total 
        FROM device_tokens 
        WHERE is_active = 1
    ");
    $total = $totalResult->fetch_assoc()['total'];
    
    // Get tokens
    $stmt = $conn->prepare("
        SELECT id, fcm_token, device_type, device_model, 
               os_version, app_version, last_active, created_at
        FROM device_tokens 
        WHERE is_active = 1
        ORDER BY last_active DESC
        LIMIT ? OFFSET ?
    ");
    
    $stmt->bind_param("ii", $perPage, $offset);
    $stmt->execute();
    $result = $stmt->get_result();
    
    $tokens = [];
    while ($row = $result->fetch_assoc()) {
        $tokens[] = $row;
    }
    
    $stmt->close();
    
    echo json_encode([
        'success' => true,
        'tokens' => $tokens,
        'pagination' => [
            'page' => $page,
            'per_page' => $perPage,
            'total' => $total,
            'total_pages' => ceil($total / $perPage)
        ]
    ]);
    
} catch (Exception $e) {
    http_response_code(500);
    echo json_encode(['error' => 'Server error: ' . $e->getMessage()]);
}

$conn->close();