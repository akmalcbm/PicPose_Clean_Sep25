<?php
/**
 * API Test Script
 * 
 * This script tests all the API endpoints to ensure they work correctly
 */

// Test configuration
define('API_BASE_URL', 'http://localhost/PicPose_Clean_Sep25/api/users.php');
define('TEST_API_KEY', '7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c');

// Colors for console output
define('COLOR_GREEN', "\033[32m");
define('COLOR_RED', "\033[31m");
define('COLOR_YELLOW', "\033[33m");
define('COLOR_BLUE', "\033[34m");
define('COLOR_RESET', "\033[0m");

$testResults = [];
$testEmail = 'test_' . time() . '@example.com';
$testPassword = 'testpass123';
$testName = 'Test User';
$registeredUserId = null;

echo COLOR_BLUE . "\n=== PicPose API Test Suite ===\n" . COLOR_RESET . "\n";

/**
 * Make API request
 */
function makeRequest($endpoint, $method = 'GET', $data = null) {
    $ch = curl_init($endpoint);
    
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_CUSTOMREQUEST, $method);
    
    if ($data !== null) {
        curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($data));
        curl_setopt($ch, CURLOPT_HTTPHEADER, [
            'Content-Type: application/json',
            'Content-Length: ' . strlen(json_encode($data))
        ]);
    }
    
    $response = curl_exec($ch);
    $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    $error = curl_error($ch);
    
    curl_close($ch);
    
    return [
        'code' => $httpCode,
        'body' => json_decode($response, true),
        'raw' => $response,
        'error' => $error
    ];
}

/**
 * Print test result
 */
function printTest($testName, $success, $details = null) {
    global $testResults;
    
    $testResults[] = $success;
    $status = $success ? COLOR_GREEN . "✓ PASS" : COLOR_RED . "✗ FAIL";
    echo "$status" . COLOR_RESET . " - $testName\n";
    
    if ($details && !$success) {
        echo COLOR_YELLOW . "   Details: $details\n" . COLOR_RESET;
    }
}

// Test 1: Missing API Key
echo "\n" . COLOR_BLUE . "Test 1: API Key Validation\n" . COLOR_RESET;
$response = makeRequest(API_BASE_URL . "?action=login", 'POST', [
    'email' => $testEmail,
    'password' => $testPassword
]);
printTest(
    "Should reject request without API key", 
    $response['code'] === 401 || $response['code'] === 403,
    "HTTP Code: " . $response['code']
);

// Test 2: Invalid API Key
$response = makeRequest(API_BASE_URL . "?action=login&api_key=invalid", 'POST', [
    'email' => $testEmail,
    'password' => $testPassword
]);
printTest(
    "Should reject request with invalid API key", 
    $response['code'] === 403,
    "HTTP Code: " . $response['code']
);

// Test 3: Registration with missing fields
echo "\n" . COLOR_BLUE . "Test 2: Registration Validation\n" . COLOR_RESET;
$response = makeRequest(API_BASE_URL . "?action=register&api_key=" . TEST_API_KEY, 'POST', [
    'email' => $testEmail
]);
printTest(
    "Should reject registration with missing fields", 
    $response['code'] === 400,
    "HTTP Code: " . $response['code'] . ", Message: " . ($response['body']['message'] ?? 'N/A')
);

// Test 4: Registration with invalid email
$response = makeRequest(API_BASE_URL . "?action=register&api_key=" . TEST_API_KEY, 'POST', [
    'email' => 'invalid-email',
    'password' => $testPassword,
    'name' => $testName
]);
printTest(
    "Should reject registration with invalid email", 
    $response['code'] === 400,
    "HTTP Code: " . $response['code']
);

// Test 5: Registration with weak password
$response = makeRequest(API_BASE_URL . "?action=register&api_key=" . TEST_API_KEY, 'POST', [
    'email' => $testEmail,
    'password' => '123',
    'name' => $testName
]);
printTest(
    "Should reject registration with weak password", 
    $response['code'] === 400,
    "HTTP Code: " . $response['code']
);

// Test 6: Successful registration
$response = makeRequest(API_BASE_URL . "?action=register&api_key=" . TEST_API_KEY, 'POST', [
    'email' => $testEmail,
    'password' => $testPassword,
    'name' => $testName
]);
$registrationSuccess = $response['code'] === 201 && 
                       isset($response['body']['success']) && 
                       $response['body']['success'] === true &&
                       isset($response['body']['user']) &&
                       isset($response['body']['token']);
printTest(
    "Should successfully register new user", 
    $registrationSuccess,
    "HTTP Code: " . $response['code']
);

if ($registrationSuccess) {
    $registeredUserId = $response['body']['user']['id'];
    echo COLOR_YELLOW . "   Registered User ID: $registeredUserId\n" . COLOR_RESET;
}

// Test 7: Duplicate registration
$response = makeRequest(API_BASE_URL . "?action=register&api_key=" . TEST_API_KEY, 'POST', [
    'email' => $testEmail,
    'password' => $testPassword,
    'name' => $testName
]);
printTest(
    "Should reject duplicate email registration", 
    $response['code'] === 409,
    "HTTP Code: " . $response['code']
);

// Test 8: Login with missing fields
echo "\n" . COLOR_BLUE . "Test 3: Login Validation\n" . COLOR_RESET;
$response = makeRequest(API_BASE_URL . "?action=login&api_key=" . TEST_API_KEY, 'POST', [
    'email' => $testEmail
]);
printTest(
    "Should reject login with missing password", 
    $response['code'] === 400,
    "HTTP Code: " . $response['code']
);

// Test 9: Login with wrong credentials
$response = makeRequest(API_BASE_URL . "?action=login&api_key=" . TEST_API_KEY, 'POST', [
    'email' => $testEmail,
    'password' => 'wrongpassword'
]);
printTest(
    "Should reject login with wrong password", 
    $response['code'] === 401,
    "HTTP Code: " . $response['code']
);

// Test 10: Successful login
$response = makeRequest(API_BASE_URL . "?action=login&api_key=" . TEST_API_KEY, 'POST', [
    'email' => $testEmail,
    'password' => $testPassword
]);
$loginSuccess = $response['code'] === 200 && 
                isset($response['body']['success']) && 
                $response['body']['success'] === true &&
                isset($response['body']['user']) &&
                isset($response['body']['token']);
printTest(
    "Should successfully login with correct credentials", 
    $loginSuccess,
    "HTTP Code: " . $response['code']
);

// Test 11: Get user profile
if ($registeredUserId) {
    echo "\n" . COLOR_BLUE . "Test 4: User Profile Operations\n" . COLOR_RESET;
    $response = makeRequest(API_BASE_URL . "?id=$registeredUserId&api_key=" . TEST_API_KEY, 'GET');
    $profileSuccess = $response['code'] === 200 && 
                      isset($response['body']['success']) && 
                      $response['body']['success'] === true &&
                      isset($response['body']['user']);
    printTest(
        "Should get user profile", 
        $profileSuccess,
        "HTTP Code: " . $response['code']
    );
    
    // Test 12: Get non-existent user profile
    $response = makeRequest(API_BASE_URL . "?id=999999&api_key=" . TEST_API_KEY, 'GET');
    printTest(
        "Should return 404 for non-existent user", 
        $response['code'] === 404,
        "HTTP Code: " . $response['code']
    );
    
    // Test 13: Update user profile
    $response = makeRequest(API_BASE_URL . "?id=$registeredUserId&api_key=" . TEST_API_KEY, 'PUT', [
        'name' => 'Updated Test User',
        'bio' => 'Test bio'
    ]);
    $updateSuccess = $response['code'] === 200 && 
                     isset($response['body']['success']) && 
                     $response['body']['success'] === true;
    printTest(
        "Should update user profile", 
        $updateSuccess,
        "HTTP Code: " . $response['code']
    );
}

// Test 14: Invalid action
echo "\n" . COLOR_BLUE . "Test 5: Error Handling\n" . COLOR_RESET;
$response = makeRequest(API_BASE_URL . "?action=invalid&api_key=" . TEST_API_KEY, 'POST');
printTest(
    "Should reject invalid action", 
    $response['code'] === 400,
    "HTTP Code: " . $response['code']
);

// Print summary
echo "\n" . COLOR_BLUE . "=== Test Summary ===\n" . COLOR_RESET;
$totalTests = count($testResults);
$passedTests = count(array_filter($testResults));
$failedTests = $totalTests - $passedTests;

echo "Total Tests: $totalTests\n";
echo COLOR_GREEN . "Passed: $passedTests\n" . COLOR_RESET;
if ($failedTests > 0) {
    echo COLOR_RED . "Failed: $failedTests\n" . COLOR_RESET;
}

$successRate = round(($passedTests / $totalTests) * 100, 2);
echo "\nSuccess Rate: $successRate%\n";

if ($successRate === 100.0) {
    echo "\n" . COLOR_GREEN . "🎉 All tests passed!\n" . COLOR_RESET;
} else {
    echo "\n" . COLOR_YELLOW . "⚠️  Some tests failed. Check the details above.\n" . COLOR_RESET;
}

echo "\n";
