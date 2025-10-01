<?php
/**
 * User API Endpoints
 * 
 * Handles user authentication and profile management
 * 
 * Endpoints:
 * - POST /api/users.php?action=login - User login
 * - POST /api/users.php?action=register - User registration
 * - GET /api/users.php?id={userId} - Get user profile
 * - PUT /api/users.php?id={userId} - Update user profile
 */

require_once __DIR__ . '/config.php';

// Log the request for debugging
if (DEBUG_MODE) {
    error_log("=== New Request ===");
    error_log("Method: " . $_SERVER['REQUEST_METHOD']);
    error_log("URI: " . $_SERVER['REQUEST_URI']);
    error_log("Query: " . json_encode($_GET));
    error_log("Headers: " . json_encode(getallheaders()));
}

// Validate API key for all requests
if (!validateApiKey()) {
    exit();
}

// Check database connection
if ($conn === null) {
    sendErrorResponse('Database connection not available', 500);
}

// Get request method and action
$method = $_SERVER['REQUEST_METHOD'];
$action = isset($_GET['action']) ? sanitizeInput($_GET['action']) : null;
$userId = isset($_GET['id']) ? sanitizeInput($_GET['id']) : null;

// Route to appropriate handler
switch ($method) {
    case 'POST':
        handlePost($action);
        break;
    
    case 'GET':
        handleGet($userId);
        break;
    
    case 'PUT':
        handlePut($userId);
        break;
    
    default:
        sendErrorResponse('Method not allowed', 405);
}

/**
 * Handle POST requests (login, register)
 */
function handlePost($action) {
    if (DEBUG_MODE) {
        error_log("Handling POST action: " . ($action ?? 'NULL'));
    }
    
    if ($action === 'login') {
        handleLogin();
    } elseif ($action === 'register') {
        handleRegister();
    } else {
        sendErrorResponse('Invalid action. Use action=login or action=register', 400);
    }
}

/**
 * Handle GET requests (get user profile)
 */
function handleGet($userId) {
    if (DEBUG_MODE) {
        error_log("Handling GET for user ID: " . ($userId ?? 'NULL'));
    }
    
    if (empty($userId)) {
        sendErrorResponse('User ID is required', 400);
    }
    
    getUserProfile($userId);
}

/**
 * Handle PUT requests (update user profile)
 */
function handlePut($userId) {
    if (DEBUG_MODE) {
        error_log("Handling PUT for user ID: " . ($userId ?? 'NULL'));
    }
    
    if (empty($userId)) {
        sendErrorResponse('User ID is required', 400);
    }
    
    updateUserProfile($userId);
}

/**
 * Handle user login
 */
function handleLogin() {
    global $conn;
    
    // Get request body
    $data = getRequestBody();
    
    if ($data === null) {
        sendErrorResponse('Request body is required', 400);
    }
    
    // Validate required fields
    if (empty($data['email']) || empty($data['password'])) {
        sendErrorResponse('Email and password are required', 400);
    }
    
    $email = sanitizeInput($data['email']);
    $password = $data['password']; // Don't sanitize password
    
    // Validate email format
    if (!validateEmail($email)) {
        sendErrorResponse('Invalid email format', 400);
    }
    
    if (DEBUG_MODE) {
        error_log("Login attempt for email: " . $email);
    }
    
    // Prepare statement to prevent SQL injection
    $stmt = $conn->prepare("SELECT id, email, name, password, profile_picture, bio, followers_count, following_count, posts_count, created_at FROM users WHERE email = ?");
    
    if (!$stmt) {
        error_log("Prepare failed: " . $conn->error);
        sendErrorResponse('Database error', 500);
    }
    
    $stmt->bind_param("s", $email);
    $stmt->execute();
    $result = $stmt->get_result();
    
    if ($result->num_rows === 0) {
        if (DEBUG_MODE) {
            error_log("Login failed: User not found for email " . $email);
        }
        sendErrorResponse('Invalid email or password', 401);
    }
    
    $user = $result->fetch_assoc();
    $stmt->close();
    
    // Verify password
    if (!password_verify($password, $user['password'])) {
        if (DEBUG_MODE) {
            error_log("Login failed: Invalid password for email " . $email);
        }
        sendErrorResponse('Invalid email or password', 401);
    }
    
    // Remove password from response
    unset($user['password']);
    
    // Generate token (simple implementation - use JWT in production)
    $token = generateToken($user['id']);
    
    if (DEBUG_MODE) {
        error_log("Login successful for user ID: " . $user['id']);
    }
    
    sendSuccessResponse('Login successful', [
        'user' => $user,
        'token' => $token
    ]);
}

/**
 * Handle user registration
 */
function handleRegister() {
    global $conn;
    
    // Get request body
    $data = getRequestBody();
    
    if ($data === null) {
        sendErrorResponse('Request body is required', 400);
    }
    
    // Validate required fields
    if (empty($data['email']) || empty($data['password']) || empty($data['name'])) {
        sendErrorResponse('Email, password, and name are required', 400);
    }
    
    $email = sanitizeInput($data['email']);
    $password = $data['password']; // Don't sanitize password
    $name = sanitizeInput($data['name']);
    
    // Validate email format
    if (!validateEmail($email)) {
        sendErrorResponse('Invalid email format', 400);
    }
    
    // Validate password strength
    if (!validatePassword($password)) {
        sendErrorResponse('Password must be at least 6 characters long', 400);
    }
    
    if (DEBUG_MODE) {
        error_log("Registration attempt for email: " . $email);
    }
    
    // Check if user already exists
    $stmt = $conn->prepare("SELECT id FROM users WHERE email = ?");
    if (!$stmt) {
        error_log("Prepare failed: " . $conn->error);
        sendErrorResponse('Database error', 500);
    }
    
    $stmt->bind_param("s", $email);
    $stmt->execute();
    $result = $stmt->get_result();
    
    if ($result->num_rows > 0) {
        $stmt->close();
        if (DEBUG_MODE) {
            error_log("Registration failed: Email already exists " . $email);
        }
        sendErrorResponse('Email already registered', 409);
    }
    $stmt->close();
    
    // Hash password
    $hashedPassword = password_hash($password, PASSWORD_DEFAULT);
    
    // Insert new user
    $stmt = $conn->prepare("INSERT INTO users (email, password, name) VALUES (?, ?, ?)");
    if (!$stmt) {
        error_log("Prepare failed: " . $conn->error);
        sendErrorResponse('Database error', 500);
    }
    
    $stmt->bind_param("sss", $email, $hashedPassword, $name);
    
    if (!$stmt->execute()) {
        error_log("Execute failed: " . $stmt->error);
        $stmt->close();
        sendErrorResponse('Registration failed', 500);
    }
    
    $userId = $stmt->insert_id;
    $stmt->close();
    
    if (DEBUG_MODE) {
        error_log("Registration successful for user ID: " . $userId);
    }
    
    // Get the newly created user
    $stmt = $conn->prepare("SELECT id, email, name, profile_picture, bio, followers_count, following_count, posts_count, created_at FROM users WHERE id = ?");
    $stmt->bind_param("i", $userId);
    $stmt->execute();
    $result = $stmt->get_result();
    $user = $result->fetch_assoc();
    $stmt->close();
    
    // Generate token
    $token = generateToken($userId);
    
    sendSuccessResponse('Registration successful', [
        'user' => $user,
        'token' => $token
    ], 201);
}

/**
 * Get user profile
 */
function getUserProfile($userId) {
    global $conn;
    
    if (DEBUG_MODE) {
        error_log("Getting profile for user ID: " . $userId);
    }
    
    // Validate user ID is numeric
    if (!is_numeric($userId)) {
        sendErrorResponse('Invalid user ID', 400);
    }
    
    $stmt = $conn->prepare("SELECT id, email, name, profile_picture, bio, followers_count, following_count, posts_count, created_at FROM users WHERE id = ?");
    if (!$stmt) {
        error_log("Prepare failed: " . $conn->error);
        sendErrorResponse('Database error', 500);
    }
    
    $stmt->bind_param("i", $userId);
    $stmt->execute();
    $result = $stmt->get_result();
    
    if ($result->num_rows === 0) {
        $stmt->close();
        if (DEBUG_MODE) {
            error_log("User not found: " . $userId);
        }
        sendErrorResponse('User not found', 404);
    }
    
    $user = $result->fetch_assoc();
    $stmt->close();
    
    if (DEBUG_MODE) {
        error_log("Profile retrieved successfully for user ID: " . $userId);
    }
    
    sendSuccessResponse('User profile retrieved', [
        'user' => $user
    ]);
}

/**
 * Update user profile
 */
function updateUserProfile($userId) {
    global $conn;
    
    // Get request body
    $data = getRequestBody();
    
    if ($data === null) {
        sendErrorResponse('Request body is required', 400);
    }
    
    if (DEBUG_MODE) {
        error_log("Updating profile for user ID: " . $userId);
    }
    
    // Validate user ID is numeric
    if (!is_numeric($userId)) {
        sendErrorResponse('Invalid user ID', 400);
    }
    
    // Check if user exists
    $stmt = $conn->prepare("SELECT id FROM users WHERE id = ?");
    if (!$stmt) {
        error_log("Prepare failed: " . $conn->error);
        sendErrorResponse('Database error', 500);
    }
    
    $stmt->bind_param("i", $userId);
    $stmt->execute();
    $result = $stmt->get_result();
    
    if ($result->num_rows === 0) {
        $stmt->close();
        if (DEBUG_MODE) {
            error_log("User not found: " . $userId);
        }
        sendErrorResponse('User not found', 404);
    }
    $stmt->close();
    
    // Build update query dynamically based on provided fields
    $updateFields = [];
    $params = [];
    $types = "";
    
    if (isset($data['name'])) {
        $updateFields[] = "name = ?";
        $params[] = sanitizeInput($data['name']);
        $types .= "s";
    }
    
    if (isset($data['profile_picture'])) {
        $updateFields[] = "profile_picture = ?";
        $params[] = sanitizeInput($data['profile_picture']);
        $types .= "s";
    }
    
    if (isset($data['bio'])) {
        $updateFields[] = "bio = ?";
        $params[] = sanitizeInput($data['bio']);
        $types .= "s";
    }
    
    if (empty($updateFields)) {
        sendErrorResponse('No fields to update', 400);
    }
    
    // Add user ID to params
    $params[] = $userId;
    $types .= "i";
    
    $sql = "UPDATE users SET " . implode(", ", $updateFields) . " WHERE id = ?";
    
    $stmt = $conn->prepare($sql);
    if (!$stmt) {
        error_log("Prepare failed: " . $conn->error);
        sendErrorResponse('Database error', 500);
    }
    
    $stmt->bind_param($types, ...$params);
    
    if (!$stmt->execute()) {
        error_log("Execute failed: " . $stmt->error);
        $stmt->close();
        sendErrorResponse('Update failed', 500);
    }
    
    $stmt->close();
    
    if (DEBUG_MODE) {
        error_log("Profile updated successfully for user ID: " . $userId);
    }
    
    // Get updated user profile
    $stmt = $conn->prepare("SELECT id, email, name, profile_picture, bio, followers_count, following_count, posts_count, created_at FROM users WHERE id = ?");
    $stmt->bind_param("i", $userId);
    $stmt->execute();
    $result = $stmt->get_result();
    $user = $result->fetch_assoc();
    $stmt->close();
    
    sendSuccessResponse('Profile updated successfully', [
        'user' => $user
    ]);
}

/**
 * Generate authentication token
 * 
 * @param int $userId User ID
 * @return string Token
 */
function generateToken($userId) {
    // Simple token implementation - use JWT in production
    return base64_encode($userId . ':' . time() . ':' . bin2hex(random_bytes(16)));
}

// Close database connection on script end
if ($conn !== null) {
    register_shutdown_function(function() {
        global $conn;
        if ($conn !== null) {
            $conn->close();
        }
    });
}
