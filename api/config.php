<?php
/**
 * Database Configuration and API Key Validation
 * 
 * This file provides database connection and API security
 */

// Enable error reporting for debugging (disable in production)
error_reporting(E_ALL);
ini_set('display_errors', 1);
ini_set('log_errors', 1);
ini_set('error_log', __DIR__ . '/error.log');

// Set content type to JSON
header('Content-Type: application/json');

// CORS Headers for mobile app compatibility
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With, api_key');
header('Access-Control-Max-Age: 86400'); // 24 hours

// Handle preflight OPTIONS request
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit();
}

// API Configuration
define('API_KEY', '7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c');
define('DEBUG_MODE', true); // Set to false in production

// Database Configuration
define('DB_HOST', 'localhost');
define('DB_NAME', 'picpose_db');
define('DB_USER', 'root');
define('DB_PASS', '');

// Global database connection variable
$conn = null;

/**
 * Validate API Key
 * 
 * @return bool True if valid, false otherwise
 */
function validateApiKey() {
    $apiKey = isset($_GET['api_key']) ? $_GET['api_key'] : 
              (isset($_POST['api_key']) ? $_POST['api_key'] : 
              (isset($_SERVER['HTTP_API_KEY']) ? $_SERVER['HTTP_API_KEY'] : null));
    
    if (DEBUG_MODE) {
        error_log("API Key validation - Received: " . ($apiKey ? $apiKey : 'NULL'));
    }
    
    if (empty($apiKey)) {
        sendErrorResponse('API key is required', 401);
        return false;
    }
    
    if ($apiKey !== API_KEY) {
        sendErrorResponse('Invalid API key', 403);
        return false;
    }
    
    return true;
}

/**
 * Get Database Connection
 * 
 * @return mysqli|null Database connection or null on failure
 */
function getDbConnection() {
    global $conn;
    
    if ($conn !== null && $conn->ping()) {
        return $conn;
    }
    
    try {
        $conn = new mysqli(DB_HOST, DB_USER, DB_PASS, DB_NAME);
        
        if ($conn->connect_error) {
            error_log("Database connection failed: " . $conn->connect_error);
            if (DEBUG_MODE) {
                sendErrorResponse('Database connection failed: ' . $conn->connect_error, 500);
            } else {
                sendErrorResponse('Database connection failed', 500);
            }
            return null;
        }
        
        $conn->set_charset('utf8mb4');
        
        if (DEBUG_MODE) {
            error_log("Database connection successful");
        }
        
        return $conn;
    } catch (Exception $e) {
        error_log("Database connection exception: " . $e->getMessage());
        if (DEBUG_MODE) {
            sendErrorResponse('Database error: ' . $e->getMessage(), 500);
        } else {
            sendErrorResponse('Database error', 500);
        }
        return null;
    }
}

/**
 * Send JSON Success Response
 * 
 * @param string $message Success message
 * @param mixed $data Additional data
 * @param int $code HTTP status code
 */
function sendSuccessResponse($message, $data = null, $code = 200) {
    http_response_code($code);
    $response = [
        'success' => true,
        'message' => $message
    ];
    
    if ($data !== null) {
        $response = array_merge($response, $data);
    }
    
    if (DEBUG_MODE) {
        error_log("Success response: " . json_encode($response));
    }
    
    echo json_encode($response);
    exit();
}

/**
 * Send JSON Error Response
 * 
 * @param string $message Error message
 * @param int $code HTTP status code
 * @param array $details Additional error details
 */
function sendErrorResponse($message, $code = 400, $details = null) {
    http_response_code($code);
    $response = [
        'success' => false,
        'message' => $message
    ];
    
    if ($details !== null && DEBUG_MODE) {
        $response['details'] = $details;
    }
    
    error_log("Error response [$code]: $message" . ($details ? ' - Details: ' . json_encode($details) : ''));
    
    echo json_encode($response);
    exit();
}

/**
 * Sanitize input string
 * 
 * @param string $data Input data
 * @return string Sanitized data
 */
function sanitizeInput($data) {
    $data = trim($data);
    $data = stripslashes($data);
    $data = htmlspecialchars($data, ENT_QUOTES, 'UTF-8');
    return $data;
}

/**
 * Validate email address
 * 
 * @param string $email Email to validate
 * @return bool True if valid, false otherwise
 */
function validateEmail($email) {
    return filter_var($email, FILTER_VALIDATE_EMAIL) !== false;
}

/**
 * Validate password strength
 * 
 * @param string $password Password to validate
 * @return bool True if valid, false otherwise
 */
function validatePassword($password) {
    // Minimum 6 characters
    return strlen($password) >= 6;
}

/**
 * Get request body as JSON
 * 
 * @return array|null Decoded JSON or null
 */
function getRequestBody() {
    $body = file_get_contents('php://input');
    
    if (DEBUG_MODE) {
        error_log("Request body: " . $body);
    }
    
    if (empty($body)) {
        return null;
    }
    
    $data = json_decode($body, true);
    
    if (json_last_error() !== JSON_ERROR_NONE) {
        error_log("JSON decode error: " . json_last_error_msg());
        sendErrorResponse('Invalid JSON in request body', 400, ['json_error' => json_last_error_msg()]);
        return null;
    }
    
    return $data;
}

/**
 * Initialize database tables if they don't exist
 */
function initializeDatabaseTables() {
    global $conn;
    
    if ($conn === null) {
        return;
    }
    
    $createUsersTable = "
    CREATE TABLE IF NOT EXISTS users (
        id INT AUTO_INCREMENT PRIMARY KEY,
        email VARCHAR(255) UNIQUE NOT NULL,
        password VARCHAR(255) NOT NULL,
        name VARCHAR(255) NOT NULL,
        profile_picture TEXT,
        bio TEXT,
        followers_count INT DEFAULT 0,
        following_count INT DEFAULT 0,
        posts_count INT DEFAULT 0,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        INDEX idx_email (email)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    ";
    
    if (!$conn->query($createUsersTable)) {
        error_log("Failed to create users table: " . $conn->error);
    } else {
        if (DEBUG_MODE) {
            error_log("Users table checked/created successfully");
        }
    }
}

// Initialize database connection and tables
$conn = getDbConnection();
if ($conn !== null) {
    initializeDatabaseTables();
}
