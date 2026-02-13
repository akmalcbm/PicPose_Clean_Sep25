<?php
session_start();
require 'config.php';

if ($_SERVER["REQUEST_METHOD"] == "POST") {
    $email = trim($_POST['email']);
    $password = $_POST['password'];

    // STEP 1: Check for failed login attempts
    $sql_attempts = "SELECT attempts, last_attempt FROM login_attempts WHERE email=? LIMIT 1";
    $stmt_attempts = $conn->prepare($sql_attempts);
    $stmt_attempts->bind_param("s", $email);
    $stmt_attempts->execute();
    $result_attempts = $stmt_attempts->get_result();
    $attempt_data = $result_attempts->fetch_assoc();
    $stmt_attempts->close();

    if ($attempt_data && $attempt_data['attempts'] >= 3
    && (time() - strtotime($attempt_data['last_attempt'])) < 900) {
        die("<script>alert('Too many failed attempts. Try again in 15 minutes.'); window.location.href='login.php';</script>");
    }

    // STEP 2: Query admin_users by email
    $sql = "SELECT * FROM admin_users WHERE email=? LIMIT 1";
    $stmt = $conn->prepare($sql);
    $stmt->bind_param("s", $email);
    $stmt->execute();
    $result = $stmt->get_result();

    if ($result->num_rows == 1) {
        $user = $result->fetch_assoc();

        // Verify password
        if (password_verify($password, $user['password'])) {
            // Successful login: set reliable session keys
            session_regenerate_id(true); // important for security
            $_SESSION['admin'] = $user['email'];          // keep existing usage
            $_SESSION['admin_id'] = (int) $user['id'];    // strongly recommended
            $_SESSION['admin_email'] = $user['email'];
            $_SESSION['admin_username'] = $user['username'];

            // Reset failed attempts after successful login
            $reset_attempts = "DELETE FROM login_attempts WHERE email=?";
            $stmt_reset = $conn->prepare($reset_attempts);
            $stmt_reset->bind_param("s", $email);
            $stmt_reset->execute();
            $stmt_reset->close();

            header("Location: index.php");
            exit();
        } else {
            $error = "Invalid Credentials!";
            // Update failed login attempts (requires UNIQUE index on email)
            $update_attempts = "INSERT INTO login_attempts (email, attempts, last_attempt) VALUES (?, 1, NOW())
                                ON DUPLICATE KEY UPDATE attempts = attempts + 1, last_attempt = NOW()";
            $stmt_update = $conn->prepare($update_attempts);
            $stmt_update->bind_param("s", $email);
            $stmt_update->execute();
            $stmt_update->close();
        }
    } else {
        // No user with that email
        $error = "Invalid Credentials!";
        // Optionally still record attempt to avoid revealing account existence
        $update_attempts = "INSERT INTO login_attempts (email, attempts, last_attempt) VALUES (?, 1, NOW())
                            ON DUPLICATE KEY UPDATE attempts = attempts + 1, last_attempt = NOW()";
        $stmt_update = $conn->prepare($update_attempts);
        $stmt_update->bind_param("s", $email);
        $stmt_update->execute();
        $stmt_update->close();
    }

    if (isset($stmt) && $stmt instanceof mysqli_stmt) { $stmt->close(); }
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>PicPose Admin Login</title>
  <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
  <style>
    /* (your existing CSS) */
    body { background-color: #f8f9fa; display:flex; justify-content:center; align-items:center; height:100vh; margin:0; }
    .login-container { width:100%; max-width:380px; padding:20px; background:white; border-radius:10px; box-shadow:0px 4px 10px rgba(0,0,0,0.1); text-align:center;}
    .login-container h2 { font-size:24px; margin-bottom:10px; }
    .login-container h5 { font-size:16px; margin-bottom:20px; color:#6c757d; }
    .form-control { height:45px; font-size:14px; }
    .btn-primary { height:45px; font-size:16px; }
    .alert { font-size:14px; padding:10px; }
  </style>
</head>
<body>
<div class="login-container">
  <h2>📷 PicPose Admin</h2>
  <h5>Login to Continue</h5>
  <?php if (isset($error)) { ?>
    <div class="alert alert-danger"><?= htmlspecialchars($error); ?></div>
  <?php } ?>
  <form method="POST">
    <div class="mb-3">
      <input type="email" class="form-control" name="email" placeholder="Email" required>
    </div>
    <div class="mb-3">
      <input type="password" class="form-control" name="password" placeholder="Password" required>
    </div>
    <button type="submit" class="btn btn-primary w-100">Login</button>
  </form>
</div>
</body>
</html>
