# API Quick Start Guide

## 🚀 Quick Deployment (5 Minutes)

### Step 1: Upload Files
Upload the `/api` folder to your web server at the document root or in a subdirectory.

### Step 2: Configure Database
Edit `api/config.php`:
```php
define('DB_HOST', 'localhost');      // Your database host
define('DB_NAME', 'picpose_db');     // Your database name
define('DB_USER', 'root');           // Your database user
define('DB_PASS', '');               // Your database password
```

### Step 3: Create Database
```sql
CREATE DATABASE picpose_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```
The users table will be created automatically.

### Step 4: Test the API
```bash
curl "https://your-domain.com/api/"
```

You should see:
```json
{
  "name": "PicPose API",
  "version": "1.0.0",
  "status": "active",
  ...
}
```

### Step 5: Test Registration
```bash
curl -X POST "https://your-domain.com/api/users.php?action=register&api_key=7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c" \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","name":"Test User","password":"test123"}'
```

Expected response:
```json
{
  "success": true,
  "message": "Registration successful",
  "user": { ... },
  "token": "..."
}
```

### Step 6: Test Login
```bash
curl -X POST "https://your-domain.com/api/users.php?action=login&api_key=7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c" \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"test123"}'
```

### Step 7: Configure Android App
The Android app is already configured to use the API. Just ensure the base URL is correct:
```kotlin
// RetrofitClient.kt
private const val BASE_URL = "https://picpose.iamakmal.in/api/"
```

## 🔧 Common Issues & Solutions

### Issue 1: HTTP 500 Error with Empty Body
**Symptoms:**
```
<-- 500 https://picpose.iamakmal.in/api/users.php?action=login&api_key=...
<-- END HTTP (0-byte body)
```

**Solutions:**
1. **Check database connection:**
   ```bash
   # Test MySQL connection
   mysql -u root -p -e "USE picpose_db;"
   ```

2. **Check error log:**
   ```bash
   tail -f /path/to/api/error.log
   ```

3. **Verify config.php:**
   - Database credentials are correct
   - Database exists
   - User has proper permissions

4. **Check PHP errors:**
   ```bash
   tail -f /var/log/apache2/error.log  # Ubuntu/Debian
   tail -f /var/log/httpd/error_log    # CentOS/RHEL
   ```

### Issue 2: Database Connection Failed
**Error:** `Database connection failed`

**Solutions:**
1. **Create database:**
   ```sql
   CREATE DATABASE picpose_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

2. **Grant permissions:**
   ```sql
   GRANT ALL PRIVILEGES ON picpose_db.* TO 'root'@'localhost';
   FLUSH PRIVILEGES;
   ```

3. **Verify MySQL is running:**
   ```bash
   sudo systemctl status mysql
   # or
   sudo systemctl status mariadb
   ```

4. **Test connection:**
   ```bash
   mysql -u root -p -h localhost
   ```

### Issue 3: Invalid API Key
**Error:** `Invalid API key` or `API key is required`

**Solutions:**
1. **Verify API key in request:**
   ```bash
   # The key should be in the URL
   ?api_key=7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c
   ```

2. **Check Android app:**
   ```kotlin
   // RetrofitClient.kt
   var defaultApiKey: String? = "7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c"
   ```

3. **Verify config.php:**
   ```php
   define('API_KEY', '7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c');
   ```

### Issue 4: CORS Errors
**Error:** `CORS policy: No 'Access-Control-Allow-Origin' header`

**Solutions:**
1. **Check .htaccess exists:**
   ```bash
   ls -la /path/to/api/.htaccess
   ```

2. **Enable mod_headers in Apache:**
   ```bash
   sudo a2enmod headers
   sudo systemctl restart apache2
   ```

3. **Verify CORS headers in config.php:**
   ```php
   header('Access-Control-Allow-Origin: *');
   header('Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS');
   header('Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With, api_key');
   ```

### Issue 5: Empty Request Body
**Error:** `Request body is required`

**Solutions:**
1. **Set Content-Type header:**
   ```bash
   curl -H "Content-Type: application/json" ...
   ```

2. **Ensure JSON is valid:**
   ```bash
   # Valid JSON
   {"email":"test@example.com","password":"test123"}
   
   # Invalid JSON (missing quotes)
   {email:test@example.com,password:test123}
   ```

3. **Check request body in Android:**
   ```kotlin
   // Should send JSON body
   @Body request: LoginRequest
   ```

## 📊 Monitoring & Debugging

### Enable Debug Mode
Edit `api/config.php`:
```php
define('DEBUG_MODE', true);  // Enable detailed logging
```

### View Error Log
```bash
tail -f /path/to/api/error.log
```

### View All Logs in Real-Time
```bash
# Terminal 1: API logs
tail -f /path/to/api/error.log

# Terminal 2: PHP logs
tail -f /var/log/apache2/error.log

# Terminal 3: MySQL logs
tail -f /var/log/mysql/error.log
```

### Test with Verbose Output
```bash
curl -v -X POST "https://your-domain.com/api/users.php?action=login&api_key=..." \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"test123"}'
```

## 🧪 Testing Checklist

- [ ] API index page loads: `https://your-domain.com/api/`
- [ ] Database connection works
- [ ] Registration endpoint works
- [ ] Login endpoint works
- [ ] Get profile endpoint works
- [ ] Update profile endpoint works
- [ ] Invalid API key is rejected
- [ ] Invalid credentials are rejected
- [ ] Duplicate email registration is rejected
- [ ] CORS headers are present
- [ ] Error logging works
- [ ] Android app can connect

## 📝 Quick Reference

### API Endpoints
| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/api/users.php?action=register` | Register user |
| POST | `/api/users.php?action=login` | Login user |
| GET | `/api/users.php?id={id}` | Get user profile |
| PUT | `/api/users.php?id={id}` | Update user profile |

### HTTP Status Codes
| Code | Meaning |
|------|---------|
| 200 | Success |
| 201 | Created |
| 400 | Bad Request |
| 401 | Unauthorized |
| 403 | Forbidden |
| 404 | Not Found |
| 409 | Conflict |
| 500 | Server Error |

### Required Headers
```
Content-Type: application/json
```

### Required Query Parameters
```
api_key=7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c
```

## 🔒 Security Checklist for Production

- [ ] Change default API key to a secure random value
- [ ] Set `DEBUG_MODE` to `false`
- [ ] Use strong database password
- [ ] Enable HTTPS (SSL certificate)
- [ ] Set proper file permissions (644 for files)
- [ ] Disable PHP error display
- [ ] Enable PHP error logging to file
- [ ] Set up database backups
- [ ] Configure rate limiting
- [ ] Add monitoring and alerts
- [ ] Review and harden Apache/Nginx config
- [ ] Use JWT tokens instead of simple tokens
- [ ] Implement password reset functionality
- [ ] Add email verification

## 📞 Getting Help

1. **Check error log:**
   ```bash
   cat /path/to/api/error.log
   ```

2. **Run test suite:**
   ```bash
   php api/test.php
   ```

3. **Test individual endpoint:**
   ```bash
   curl -v "https://your-domain.com/api/users.php?action=..."
   ```

4. **Review documentation:**
   - `api/README.md` - Complete API documentation
   - `API_IMPLEMENTATION.md` - Implementation guide

## 🎯 Success Indicators

Your API is working correctly when:
- ✅ Registration creates new users
- ✅ Login returns user data and token
- ✅ Profile retrieval works
- ✅ Android app can authenticate
- ✅ No HTTP 500 errors
- ✅ Proper JSON responses
- ✅ CORS headers present
- ✅ Error messages are clear

## 📦 Files Overview

```
/api/
├── .htaccess          # Apache configuration
├── config.php         # Database & API config
├── users.php          # Main API endpoints
├── index.php          # API info page
├── test.php           # Test suite
├── README.md          # Full documentation
└── error.log          # Error logs (created automatically)
```

## 🚨 Emergency Fixes

### API Not Responding
```bash
# Restart web server
sudo systemctl restart apache2
# or
sudo systemctl restart httpd
```

### Database Issues
```bash
# Restart MySQL
sudo systemctl restart mysql
# or
sudo systemctl restart mariadb
```

### Clear Error Logs
```bash
# Clear old logs
> /path/to/api/error.log
```

### Reset Database
```sql
DROP TABLE users;
-- Table will be recreated automatically on next API call
```

## 📚 Additional Resources

- Full API Documentation: `api/README.md`
- Implementation Guide: `API_IMPLEMENTATION.md`
- Test Suite: `api/test.php`
- Android App Docs: `LOGIN_SETTINGS_README.md`

---

**Need more help?** Check the error logs or contact the development team.
