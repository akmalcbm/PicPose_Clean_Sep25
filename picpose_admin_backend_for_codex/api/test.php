<?php
// api/test.php - Simple test for API endpoints
header('Content-Type: application/json; charset=utf-8');

echo json_encode([
    "status" => "success",
    "message" => "API is working!",
    "timestamp" => date('Y-m-d H:i:s'),
    "endpoints" => [
        "register" => "POST /api/users.php?action=register&api_key=YOUR_KEY",
        "login" => "POST /api/users.php?action=login&api_key=YOUR_KEY",
        "profile" => "GET /api/users.php?id=USER_ID&api_key=YOUR_KEY"
    ]
], JSON_PRETTY_PRINT);
?>