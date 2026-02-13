# PicPose API Documentation

## Base URL
```
https://picpose.iamakmal.in/api/
```

## Authentication
All API requests require an API key passed as a query parameter:
```
?api_key=7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c
```

## Endpoints

### 1. User Registration
**POST** `/api/users.php?action=register&api_key=YOUR_KEY`

**Request Body:**
```json
{
  "name": "John Doe",
  "email": "john@example.com", 
  "password": "password123"
}
```

**Response (201):**
```json
{
  "status": "success",
  "message": "Registration successful",
  "user": {
    "id": 1,
    "username": "John Doe",
    "email": "john@example.com",
    "profile_pic": "uploads/users_profilepic/default.png"
  }
}
```

### 2. User Login
**POST** `/api/users.php?action=login&api_key=YOUR_KEY`

**Request Body:**
```json
{
  "email": "john@example.com",
  "password": "password123"
}
```

**Response (200):**
```json
{
  "status": "success", 
  "message": "Login successful",
  "user": {
    "id": 1,
    "username": "John Doe",
    "email": "john@example.com", 
    "profile_pic": "uploads/users_profilepic/default.png"
  }
}
```

### 3. Get User Profile
**GET** `/api/users.php?id=1&api_key=YOUR_KEY`

**Response (200):**
```json
{
  "status": "success",
  "user": {
    "id": 1,
    "username": "John Doe", 
    "email": "john@example.com",
    "profile_pic": "uploads/users_profilepic/default.png",
    "created_at": "2025-10-01 17:30:00"
  }
}
```

## Error Responses

**400 Bad Request:**
```json
{
  "status": "error",
  "message": "Username, email and password are required."
}
```

**401 Unauthorized:**
```json
{
  "status": "error", 
  "message": "Invalid email or password."
}
```

**409 Conflict:**
```json
{
  "status": "error",
  "message": "Email already registered."
}
```

**500 Internal Server Error:**
```json
{
  "status": "error",
  "message": "Internal server error"
}
```