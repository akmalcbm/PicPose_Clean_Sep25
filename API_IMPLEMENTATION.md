# API Implementation Guide

## Summary

This document describes the backend API implementation for the PicPose mobile application. The API addresses the HTTP 500 errors that were occurring during user login and registration.

## Problem Solved

The Android app was receiving HTTP 500 errors with empty response bodies when attempting to:
- Login: `POST /api/users.php?action=login&api_key=...`
- Register: `POST /api/users.php?action=register&api_key=...`

## Root Causes Identified and Fixed

1. **Missing API Implementation**: The API files didn't exist in the repository
2. **No API Key Validation**: Requests include `api_key` parameter but no validation was in place
3. **No Database Connection**: No database configuration or connection handling
4. **No Error Handling**: No proper error logging or debugging capabilities
5. **Missing CORS Headers**: Required for mobile app compatibility
6. **No Input Validation**: No validation or sanitization of user inputs

## Solution Implemented

### Files Created

1. **`api/config.php`** (6.8KB)
   - Database connection management
   - API key validation
   - CORS headers
   - Error handling functions
   - Input sanitization functions
   - Request body parsing
   - Database table initialization

2. **`api/users.php`** (11.9KB)
   - Login endpoint handler
   - Registration endpoint handler
   - Get user profile endpoint
   - Update user profile endpoint
   - Comprehensive error handling
   - Request routing
   - Password hashing and verification

3. **`api/README.md`** (7.7KB)
   - Complete API documentation
   - Endpoint specifications
   - Request/response examples
   - Error codes reference
   - Testing instructions
   - Deployment checklist
   - Troubleshooting guide

4. **`api/test.php`** (8.6KB)
   - Automated test suite
   - 14 comprehensive tests
   - API key validation tests
   - Registration validation tests
   - Login validation tests
   - Profile operation tests
   - Error handling tests

5. **`api/index.php`** (1.2KB)
   - API information page
   - Endpoint listing
   - Status indicator

6. **`api/.htaccess`** (1.7KB)
   - Apache configuration
   - CORS headers
   - Security headers
   - File protection
   - MIME types

## Key Features

### 1. API Key Validation
```php
define('API_KEY', '7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c');
```
- All requests must include valid API key
- Checks query params, POST params, and headers
- Returns 401/403 for invalid/missing keys

### 2. Database Connection
```php
define('DB_HOST', 'localhost');
define('DB_NAME', 'picpose_db');
define('DB_USER', 'root');
define('DB_PASS', '');
```
- Automatic database connection
- Connection pooling and reuse
- Error handling with detailed logging
- Automatic table creation on first run

### 3. CORS Support
```php
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With, api_key');
```
- Enables mobile app to make cross-origin requests
- Handles preflight OPTIONS requests
- Configurable allowed origins

### 4. Error Handling & Debugging
```php
define('DEBUG_MODE', true);
```
- Comprehensive error logging to `error.log`
- Detailed error messages in debug mode
- Request/response logging
- SQL error logging
- JSON error handling

### 5. Security Features
- **Password Hashing**: Using `password_hash()` with bcrypt
- **SQL Injection Prevention**: Prepared statements for all queries
- **Input Sanitization**: All user inputs are sanitized
- **Email Validation**: Proper email format validation
- **Password Strength**: Minimum 6 characters
- **XSS Prevention**: HTML entity encoding

### 6. Input Validation
- Required field validation
- Email format validation
- Password strength validation
- Numeric ID validation
- JSON parsing with error handling

## API Endpoints

### 1. Login
```
POST /api/users.php?action=login&api_key={api_key}
Content-Type: application/json

{
  "email": "akmalcbm@gmail.com",
  "password": "akmal123"
}
```

**Success Response (200):**
```json
{
  "success": true,
  "message": "Login successful",
  "user": {
    "id": "1",
    "email": "akmalcbm@gmail.com",
    "name": "Akmal Ansari",
    "profile_picture": null,
    "bio": null,
    "followers_count": 0,
    "following_count": 0,
    "posts_count": 0,
    "created_at": "2024-10-01 12:00:00"
  },
  "token": "base64_encoded_token"
}
```

### 2. Registration
```
POST /api/users.php?action=register&api_key={api_key}
Content-Type: application/json

{
  "email": "akmalcbm@gmail.com",
  "name": "Akmal Ansari",
  "password": "akmal123"
}
```

**Success Response (201):**
```json
{
  "success": true,
  "message": "Registration successful",
  "user": { ... },
  "token": "base64_encoded_token"
}
```

### 3. Get User Profile
```
GET /api/users.php?id=1&api_key={api_key}
```

### 4. Update User Profile
```
PUT /api/users.php?id=1&api_key={api_key}
Content-Type: application/json

{
  "name": "New Name",
  "bio": "New bio",
  "profile_picture": "https://example.com/photo.jpg"
}
```

## Database Schema

```sql
CREATE TABLE users (
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
```

The table is automatically created when the API is first accessed.

## Deployment Instructions

### 1. Upload Files
Upload the entire `/api` directory to your web server:
```
/api/
  ├── .htaccess
  ├── config.php
  ├── users.php
  ├── index.php
  ├── test.php
  └── README.md
```

### 2. Configure Database
Edit `api/config.php` and update database credentials:
```php
define('DB_HOST', 'your_host');
define('DB_NAME', 'your_database');
define('DB_USER', 'your_username');
define('DB_PASS', 'your_password');
```

### 3. Create Database
```sql
CREATE DATABASE picpose_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

The users table will be created automatically on first access.

### 4. Set File Permissions
```bash
chmod 644 api/*.php
chmod 644 api/.htaccess
chmod 755 api/
```

### 5. Configure API Key (Optional)
For production, change the API key in `config.php`:
```php
define('API_KEY', 'your-secure-random-api-key');
```

Also update in Android app:
```kotlin
// RetrofitClient.kt
var defaultApiKey: String? = "your-secure-random-api-key"
```

### 6. Disable Debug Mode
For production, set in `config.php`:
```php
define('DEBUG_MODE', false);
```

### 7. Test the API
Access the API index:
```
https://picpose.iamakmal.in/api/
```

Or run the test suite:
```bash
php api/test.php
```

Or use curl:
```bash
curl -X POST "https://picpose.iamakmal.in/api/users.php?action=register&api_key=7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c" \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","name":"Test User","password":"test123"}'
```

## Testing

### Manual Testing with cURL

**1. Test Registration:**
```bash
curl -v -X POST "http://localhost/api/users.php?action=register&api_key=7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c" \
  -H "Content-Type: application/json" \
  -d '{"email":"akmalcbm@gmail.com","name":"Akmal Ansari","password":"akmal123"}'
```

**2. Test Login:**
```bash
curl -v -X POST "http://localhost/api/users.php?action=login&api_key=7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c" \
  -H "Content-Type: application/json" \
  -d '{"email":"akmalcbm@gmail.com","password":"akmal123"}'
```

**3. Test Get Profile:**
```bash
curl -v "http://localhost/api/users.php?id=1&api_key=7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c"
```

### Automated Testing
```bash
php api/test.php
```

This runs 14 comprehensive tests covering:
- API key validation
- Registration validation
- Login validation  
- Profile operations
- Error handling

## Troubleshooting

### HTTP 500 Error
1. Check `api/error.log` for detailed error messages
2. Verify database connection settings
3. Ensure database exists and is accessible
4. Check PHP error logs
5. Verify API key is correct

### Database Connection Failed
1. Verify MySQL is running
2. Check database credentials in `config.php`
3. Create database: `CREATE DATABASE picpose_db;`
4. Grant permissions to database user

### Empty Response Body
1. Check for PHP syntax errors
2. Verify `error.log` exists and is writable
3. Check web server error logs
4. Enable `display_errors` temporarily for debugging

### CORS Issues
1. Verify `.htaccess` is being processed
2. Check Apache has `mod_headers` enabled
3. Verify CORS headers in `config.php`

## Monitoring

### Error Logs
Monitor `api/error.log` for:
- Database connection errors
- API key validation failures
- SQL errors
- Invalid requests
- Authentication failures

### Log Format
```
[2024-10-01 12:00:00] Error response [401]: Invalid email or password
[2024-10-01 12:00:01] Login attempt for email: user@example.com
[2024-10-01 12:00:02] Login successful for user ID: 1
```

## Security Considerations

1. **Change Default API Key**: Use a strong random key in production
2. **Use HTTPS**: Always use SSL/TLS in production
3. **Disable Debug Mode**: Set `DEBUG_MODE` to `false` in production
4. **Strong Database Password**: Use a strong password for database access
5. **File Permissions**: Ensure proper file permissions (644 for files, 755 for directories)
6. **Rate Limiting**: Consider implementing rate limiting for production
7. **JWT Tokens**: Consider using JWT instead of simple tokens for production
8. **Password Policy**: Enforce stronger password requirements in production

## Compatibility

- **PHP**: 7.4 or higher
- **MySQL**: 5.7 or higher (or MariaDB 10.2+)
- **Apache**: 2.4+ with `mod_rewrite` and `mod_headers`
- **Android App**: Compatible with existing Retrofit implementation

## Next Steps

1. Deploy to production server
2. Update database credentials
3. Change API key to secure value
4. Disable debug mode
5. Test with Android app
6. Monitor error logs
7. Implement additional security measures
8. Set up database backups
9. Configure SSL/TLS
10. Set up monitoring and alerts

## Support

For issues:
1. Check `api/error.log`
2. Check web server error logs
3. Review API documentation in `api/README.md`
4. Run test suite: `php api/test.php`
5. Contact development team

## Changelog

### Version 1.0.0 (2024-10-01)
- Initial API implementation
- User authentication (login/register)
- User profile management (get/update)
- API key validation
- CORS support
- Comprehensive error handling
- Debug logging
- Automated test suite
- Complete documentation
