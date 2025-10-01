<?php
/**
 * PicPose API Index
 * 
 * Provides API information and status
 */

header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');

$response = [
    'name' => 'PicPose API',
    'version' => '1.0.0',
    'status' => 'active',
    'description' => 'User authentication and profile management API',
    'endpoints' => [
        'login' => [
            'method' => 'POST',
            'url' => '/api/users.php?action=login&api_key={api_key}',
            'description' => 'User login'
        ],
        'register' => [
            'method' => 'POST',
            'url' => '/api/users.php?action=register&api_key={api_key}',
            'description' => 'User registration'
        ],
        'profile' => [
            'method' => 'GET',
            'url' => '/api/users.php?id={userId}&api_key={api_key}',
            'description' => 'Get user profile'
        ],
        'update_profile' => [
            'method' => 'PUT',
            'url' => '/api/users.php?id={userId}&api_key={api_key}',
            'description' => 'Update user profile'
        ]
    ],
    'documentation' => '/api/README.md',
    'timestamp' => date('Y-m-d H:i:s')
];

echo json_encode($response, JSON_PRETTY_PRINT);
