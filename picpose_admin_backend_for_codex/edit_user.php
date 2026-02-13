<?php
session_start();
require 'config.php';

if (!isset($_SESSION['admin'])) {
    header("Location: login.php");
    exit();
}

if (!isset($_GET['id'])) {
    header("Location: users.php");
    exit();
}

$user_id = intval($_GET['id']);

$stmt = $conn->prepare("SELECT * FROM users WHERE id = ?");
$stmt->bind_param("i", $user_id);
$stmt->execute();
$result = $stmt->get_result();
$user = $result->fetch_assoc();
$stmt->close();

if (!$user) {
    header("Location: users.php");
    exit();
}

// Resolve final profile picture (local OR social)
$picture = $user["profile_picture"] ?: $user["profile_pic"];
if ($picture && !str_starts_with($picture, "http")) {
    $picture = "https://picpose.iamakmal.in/" . ltrim($picture, "/");
}
if (!$picture) {
    $picture = "https://ui-avatars.com/api/?name=" . urlencode($user['username'] ?: "User");
}

// Provider styling
$provider = ucfirst($user['provider'] ?: "Email");

include 'includes/header.php';
?>

<h2>✏️ Edit User</h2>

<div class="card p-4 mb-4">

    <form method="POST" action="update_user.php" enctype="multipart/form-data">

        <input type="hidden" name="id" value="<?= $user['id']; ?>">

        <!-- Profile Picture Preview -->
        <div class="text-center mb-3">
            <img src="<?= $picture ?>" 
                 class="rounded-circle border" 
                 width="120" height="120" 
                 style="object-fit: cover;">
            <br><small class="text-muted">
                <?= ($user['profile_picture']) ? "Social Image (read-only)" : "Local File Upload" ?>
            </small>
        </div>

        <!-- Username -->
        <div class="mb-3">
            <label class="fw-bold">Username:</label>
            <input type="text" name="username" class="form-control"
                   value="<?= htmlspecialchars($user['username']); ?>" required>
        </div>

        <!-- Display Name -->
        <div class="mb-3">
            <label class="fw-bold">Display Name:</label>
            <input type="text" name="display_name" class="form-control"
                   value="<?= htmlspecialchars($user['display_name'] ?? $user['username']); ?>">
        </div>

        <!-- Email -->
        <div class="mb-3">
            <label class="fw-bold">Email:</label>
            <input type="email" name="email"
                   class="form-control"
                   value="<?= htmlspecialchars($user['email']); ?>"
                   <?= ($user['provider'] != 'email') ? "readonly style='background:#eee;'" : "" ?>>
            <?php if ($user['provider'] != 'email') : ?>
                <small class="text-danger">Email cannot be changed for social login users.</small>
            <?php endif; ?>
        </div>

        <!-- Login Provider -->
        <div class="mb-3">
            <label class="fw-bold">Login Method:</label><br>
            <span class="badge bg-info text-dark px-3 py-2">
                <?= htmlspecialchars($provider); ?>
            </span>
        </div>

        <!-- Bio -->
        <div class="mb-3">
            <label class="fw-bold">Bio:</label>
            <textarea name="bio" class="form-control" rows="3"><?= htmlspecialchars($user['bio']); ?></textarea>
        </div>

        <!-- Account Type -->
        <div class="mb-3">
            <label class="fw-bold">Account Type:</label>
            <select name="account_type" class="form-control" required>
                <option value="normal" <?= ($user['account_type'] == "normal" ? "selected" : "") ?>>Normal</option>
                <option value="premium" <?= ($user['account_type'] == "premium" ? "selected" : "") ?>>Premium</option>
                <option value="ad_free" <?= ($user['account_type'] == "ad_free" ? "selected" : "") ?>>Ad-Free</option>
            </select>
        </div>

        <!-- Local profile_pic file upload (ONLY IF NOT SOCIAL LOGIN) -->
        <?php if (empty($user['profile_picture'])) : ?>
            <div class="mb-3">
                <label class="fw-bold">Upload New Profile Picture:</label>
                <input type="file" name="profile_pic" class="form-control">
                <small class="text-muted">Upload JPG, PNG or WEBP</small>
            </div>
        <?php endif; ?>

        <button type="submit" class="btn btn-primary w-100 mt-3">💾 Save Changes</button>
    </form>

</div>

<?php include 'includes/footer.php'; ?>
