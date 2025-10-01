# 🚀 Deployment Checklist for PicPose API

## Pre-Deployment

### ✅ Verify Files
- [ ] `/api/config.php` exists (6.7KB)
- [ ] `/api/users.php` exists (12KB)
- [ ] `/api/.htaccess` exists (1.7KB)
- [ ] `/api/README.md` exists (7.6KB)
- [ ] `/api/test.php` exists (8.5KB)
- [ ] `/api/index.php` exists (1.2KB)

### ✅ Review Configuration
- [ ] Check database credentials in `api/config.php`
- [ ] Verify API key matches Android app
- [ ] Review `DEBUG_MODE` setting (true for dev, false for production)

## Deployment Steps

### Step 1: Upload Files
- [ ] Upload entire `/api` directory to web server
- [ ] Verify upload location: `https://picpose.iamakmal.in/api/`
- [ ] Confirm all 6 files are uploaded

### Step 2: Configure Server
- [ ] Check Apache/Nginx is running
- [ ] Enable `mod_rewrite` (if using Apache)
- [ ] Enable `mod_headers` (if using Apache)
- [ ] Verify PHP version is 7.4 or higher
- [ ] Check MySQL/MariaDB is running

### Step 3: Database Setup
- [ ] Connect to MySQL: `mysql -u root -p`
- [ ] Create database: `CREATE DATABASE picpose_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`
- [ ] Grant permissions (if needed): `GRANT ALL ON picpose_db.* TO 'your_user'@'localhost';`
- [ ] Update `api/config.php` with correct credentials:
  ```php
  define('DB_HOST', 'localhost');
  define('DB_NAME', 'picpose_db');
  define('DB_USER', 'your_user');
  define('DB_PASS', 'your_password');
  ```
- [ ] Test database connection

### Step 4: File Permissions
- [ ] Set file permissions: `chmod 644 api/*.php`
- [ ] Set directory permissions: `chmod 755 api/`
- [ ] Set .htaccess permissions: `chmod 644 api/.htaccess`
- [ ] Ensure web server can write to `api/` directory (for error.log)

### Step 5: Security Configuration
- [ ] Change default API key (if needed):
  ```php
  // In api/config.php
  define('API_KEY', 'your-new-secure-api-key');
  
  // In Android app: RetrofitClient.kt
  var defaultApiKey: String? = "your-new-secure-api-key"
  ```
- [ ] Set `DEBUG_MODE` to `false` for production
- [ ] Disable PHP error display for production:
  ```php
  ini_set('display_errors', 0);
  ```
- [ ] Verify HTTPS is enabled (SSL certificate)

## Testing

### Step 6: Test API Endpoints

#### Test 1: API Index
```bash
curl "https://picpose.iamakmal.in/api/"
```
Expected: JSON response with API info
- [ ] Passes

#### Test 2: Registration
```bash
curl -X POST "https://picpose.iamakmal.in/api/users.php?action=register&api_key=7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c" \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","name":"Test User","password":"test123"}'
```
Expected: HTTP 201 with user data and token
- [ ] Passes

#### Test 3: Login
```bash
curl -X POST "https://picpose.iamakmal.in/api/users.php?action=login&api_key=7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c" \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"test123"}'
```
Expected: HTTP 200 with user data and token
- [ ] Passes

#### Test 4: Get Profile
```bash
curl "https://picpose.iamakmal.in/api/users.php?id=1&api_key=7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c"
```
Expected: HTTP 200 with user profile
- [ ] Passes

#### Test 5: Update Profile
```bash
curl -X PUT "https://picpose.iamakmal.in/api/users.php?id=1&api_key=7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c" \
  -H "Content-Type: application/json" \
  -d '{"name":"Updated Name","bio":"Updated bio"}'
```
Expected: HTTP 200 with updated user profile
- [ ] Passes

#### Test 6: Invalid API Key
```bash
curl "https://picpose.iamakmal.in/api/users.php?id=1&api_key=invalid"
```
Expected: HTTP 403 with error message
- [ ] Passes

#### Test 7: Invalid Credentials
```bash
curl -X POST "https://picpose.iamakmal.in/api/users.php?action=login&api_key=7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c" \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"wrongpassword"}'
```
Expected: HTTP 401 with error message
- [ ] Passes

#### Test 8: Duplicate Email
Register the same email twice
Expected: HTTP 409 with error message
- [ ] Passes

### Step 7: Run Automated Test Suite
```bash
php api/test.php
```
Expected: All 14 tests pass
- [ ] Passes

### Step 8: Test with Android App
- [ ] Open PicPose app
- [ ] Try to register new user
- [ ] Verify registration success
- [ ] Try to login with registered user
- [ ] Verify login success
- [ ] Check user profile loads
- [ ] Try to update profile
- [ ] Verify profile update success

## Monitoring

### Step 9: Check Logs
- [ ] View error log: `tail -f api/error.log`
- [ ] Check for any errors or warnings
- [ ] Verify requests are being logged (if DEBUG_MODE is true)

### Step 10: Database Verification
- [ ] Connect to database: `mysql -u your_user -p picpose_db`
- [ ] Check users table exists: `SHOW TABLES;`
- [ ] View table structure: `DESCRIBE users;`
- [ ] Check registered users: `SELECT id, email, name, created_at FROM users;`

## Post-Deployment

### Step 11: Security Hardening
- [ ] Ensure `DEBUG_MODE` is `false` in production
- [ ] Verify HTTPS is working (SSL certificate valid)
- [ ] Check file permissions are correct (644 for files, 755 for directories)
- [ ] Review Apache/Nginx configuration for security best practices
- [ ] Consider implementing rate limiting
- [ ] Set up database backups

### Step 12: Monitoring Setup
- [ ] Set up error log monitoring
- [ ] Configure alerts for critical errors
- [ ] Set up database backup schedule
- [ ] Monitor API response times
- [ ] Set up uptime monitoring

### Step 13: Documentation
- [ ] Share API documentation with team (`api/README.md`)
- [ ] Document production configuration
- [ ] Document emergency procedures
- [ ] Create runbook for common issues

## Rollback Plan

### If Deployment Fails
1. Check error logs: `tail -f api/error.log`
2. Check web server logs
3. Check database connection
4. Verify file permissions
5. Review configuration in `api/config.php`
6. Test individual components:
   - Database connection
   - API key validation
   - CORS headers
   - File access

### Emergency Contacts
- Database admin: _____________
- System admin: _____________
- Development team: _____________

## Success Criteria

Deployment is successful when:
- ✅ API index page loads correctly
- ✅ All 8 manual tests pass
- ✅ Automated test suite passes (14/14 tests)
- ✅ Android app can register users
- ✅ Android app can login users
- ✅ Android app can fetch user profiles
- ✅ No HTTP 500 errors
- ✅ All responses are valid JSON
- ✅ CORS headers are present
- ✅ Error logging is working
- ✅ Database operations are successful

## Additional Recommendations

### Performance
- [ ] Enable opcache for PHP
- [ ] Configure MySQL query cache
- [ ] Set up CDN for static assets (if any)
- [ ] Enable gzip compression
- [ ] Configure database connection pooling

### Security
- [ ] Implement rate limiting (e.g., fail2ban)
- [ ] Set up Web Application Firewall (WAF)
- [ ] Configure security headers in .htaccess
- [ ] Implement JWT tokens instead of simple tokens
- [ ] Add password reset functionality
- [ ] Implement email verification
- [ ] Add two-factor authentication (2FA)

### Monitoring
- [ ] Set up application performance monitoring (APM)
- [ ] Configure database monitoring
- [ ] Set up real-time error tracking (e.g., Sentry)
- [ ] Monitor API response times
- [ ] Track API usage metrics

## Troubleshooting Guide

### Issue: HTTP 500 Error
**Solution:**
1. Check `api/error.log`
2. Check web server error logs
3. Verify database connection
4. Check file permissions

### Issue: Database Connection Failed
**Solution:**
1. Verify MySQL is running: `sudo systemctl status mysql`
2. Check database credentials in `api/config.php`
3. Test connection: `mysql -u your_user -p`
4. Create database if missing

### Issue: CORS Errors
**Solution:**
1. Enable `mod_headers`: `sudo a2enmod headers`
2. Restart Apache: `sudo systemctl restart apache2`
3. Verify CORS headers in response
4. Check `.htaccess` file exists

### Issue: Invalid API Key
**Solution:**
1. Verify API key in request matches `config.php`
2. Check Android app has correct API key
3. Verify API key is being sent in request

## Sign-off

### Deployment Team
- [ ] Deployed by: _________________ Date: _________
- [ ] Tested by: _________________ Date: _________
- [ ] Approved by: _________________ Date: _________

### Verification
- [ ] All tests passed
- [ ] Android app tested and working
- [ ] No errors in logs
- [ ] Database is accessible
- [ ] Backups configured
- [ ] Monitoring enabled

---

**Deployment Status:** [ ] Complete [ ] Failed [ ] Rolled Back

**Notes:**
_____________________________________________________________________
_____________________________________________________________________
_____________________________________________________________________
