# PicPose API Documentation

## Overview

This API provides user authentication and profile management endpoints for the PicPose mobile application.

## Base URL

```
https://picpose.iamakmal.in/api/
```

## Authentication

All API requests must include an `api_key` parameter:

```
?api_key=7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c
```

## Endpoints

### 1. User Login

**Endpoint:** `POST /api/users.php?action=login&api_key=...`

**Request Body:**
```json
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
    "created_at": "2024-01-01 12:00:00"
  },
  "token": "MTo..."
}
```

**Error Response (401):**
```json
{
  "success": false,
  "message": "Invalid email or password"
}
```

### 2. User Registration

**Endpoint:** `POST /api/users.php?action=register&api_key=...`

**Request Body:**
```json
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
  "user": {
    "id": "1",
    "email": "akmalcbm@gmail.com",
    "name": "Akmal Ansari",
    "profile_picture": null,
    "bio": null,
    "followers_count": 0,
    "following_count": 0,
    "posts_count": 0,
    "created_at": "2024-01-01 12:00:00"
  },
  "token": "MTo..."
}
```

**Error Responses:**
- **409:** Email already registered
- **400:** Invalid input (missing fields, invalid email format, weak password)

### 3. Get User Profile

**Endpoint:** `GET /api/users.php?id={userId}&api_key=...`

**Success Response (200):**
```json
{
  "success": true,
  "message": "User profile retrieved",
  "user": {
    "id": "1",
    "email": "akmalcbm@gmail.com",
    "name": "Akmal Ansari",
    "profile_picture": "https://example.com/photo.jpg",
    "bio": "Photography enthusiast",
    "followers_count": 150,
    "following_count": 200,
    "posts_count": 50,
    "created_at": "2024-01-01 12:00:00"
  }
}
```

**Error Response (404):**
```json
{
  "success": false,
  "message": "User not found"
}
```

### 4. Update User Profile

**Endpoint:** `PUT /api/users.php?id={userId}&api_key=...`

**Request Body:**
```json
{
  "name": "Akmal Ansari Updated",
  "profile_picture": "https://example.com/new-photo.jpg",
  "bio": "Professional photographer"
}
```

**Success Response (200):**
```json
{
  "success": true,
  "message": "Profile updated successfully",
  "user": {
    "id": "1",
    "email": "akmalcbm@gmail.com",
    "name": "Akmal Ansari Updated",
    "profile_picture": "https://example.com/new-photo.jpg",
    "bio": "Professional photographer",
    "followers_count": 150,
    "following_count": 200,
    "posts_count": 50,
    "created_at": "2024-01-01 12:00:00"
  }
}
```

## Error Codes

- **200:** Success
- **201:** Created
- **400:** Bad Request (invalid input)
- **401:** Unauthorized (invalid credentials)
- **403:** Forbidden (invalid API key)
- **404:** Not Found (user doesn't exist)
- **405:** Method Not Allowed
- **409:** Conflict (email already exists)
- **500:** Internal Server Error

## Security Features

1. **API Key Validation:** All requests require a valid API key
2. **Password Hashing:** Passwords are hashed using PHP's `password_hash()`
3. **SQL Injection Prevention:** Prepared statements are used for all database queries
4. **Input Sanitization:** All user inputs are sanitized
5. **CORS Support:** CORS headers are included for mobile app compatibility
6. **Error Logging:** Comprehensive error logging for debugging

## Database Configuration

The API expects a MySQL database with the following configuration:

**Database:** `picpose_db`
**Host:** `localhost`
**User:** `root`
**Password:** (empty for development)

### Users Table Schema

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

## Debug Mode

Debug mode is enabled by default in `config.php`:

```php
define('DEBUG_MODE', true);
```

When enabled:
- Detailed error messages are returned in responses
- All requests and responses are logged to `api/error.log`
- SQL errors are logged with details

**Important:** Set `DEBUG_MODE` to `false` in production to hide sensitive information.

## Configuration

Edit `api/config.php` to update:

1. **Database credentials:**
   ```php
   define('DB_HOST', 'localhost');
   define('DB_NAME', 'picpose_db');
   define('DB_USER', 'root');
   define('DB_PASS', '');
   ```

2. **API key:**
   ```php
   define('API_KEY', 'your-secure-api-key');
   ```

3. **Debug mode:**
   ```php
   define('DEBUG_MODE', false); // Set to false in production
   ```

## Testing

### Using cURL

**Login:**
```bash
curl -X POST "https://picpose.iamakmal.in/api/users.php?action=login&api_key=7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c" \
  -H "Content-Type: application/json" \
  -d '{"email":"akmalcbm@gmail.com","password":"akmal123"}'
```

**Register:**
```bash
curl -X POST "https://picpose.iamakmal.in/api/users.php?action=register&api_key=7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c" \
  -H "Content-Type: application/json" \
  -d '{"email":"akmalcbm@gmail.com","name":"Akmal Ansari","password":"akmal123"}'
```

**Get Profile:**
```bash
curl "https://picpose.iamakmal.in/api/users.php?id=1&api_key=7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c"
```

**Update Profile:**
```bash
curl -X PUT "https://picpose.iamakmal.in/api/users.php?id=1&api_key=7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c" \
  -H "Content-Type: application/json" \
  -d '{"name":"Akmal Updated","bio":"New bio"}'
```

## Troubleshooting

### 500 Internal Server Error with Empty Body

1. Check `api/error.log` for detailed error messages
2. Verify database connection settings in `config.php`
3. Ensure database and users table exist
4. Check PHP error logs: `tail -f /var/log/apache2/error.log` (or equivalent)
5. Verify API key is correct

### Database Connection Issues

1. Verify MySQL is running: `sudo service mysql status`
2. Check database credentials in `config.php`
3. Create database if it doesn't exist: `CREATE DATABASE picpose_db;`
4. Grant permissions: `GRANT ALL ON picpose_db.* TO 'root'@'localhost';`

### CORS Issues

If the mobile app can't connect:
1. Verify CORS headers are set in `config.php`
2. Check Apache/Nginx configuration for CORS support
3. Ensure OPTIONS requests are handled correctly

## Deployment Checklist

- [ ] Set `DEBUG_MODE` to `false` in production
- [ ] Use strong database password
- [ ] Change default API key to a secure random value
- [ ] Enable HTTPS (SSL certificate)
- [ ] Set appropriate file permissions (644 for PHP files)
- [ ] Configure PHP error logging to file, not display
- [ ] Set up database backups
- [ ] Configure rate limiting
- [ ] Add monitoring and alerting

## Support

For issues or questions, check the error logs at `api/error.log` or contact the development team.
