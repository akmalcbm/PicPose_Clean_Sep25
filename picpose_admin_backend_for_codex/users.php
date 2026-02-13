<?php
error_reporting(E_ALL);
ini_set('display_errors', 1);

session_start();
require 'config.php';

if (!isset($_SESSION['admin'])) {
    header("Location: login.php");
    exit();
}

// DELETE USER ------------------------------
if (isset($_GET['delete'])) {
    $userId = intval($_GET['delete']);

    // Optional: prevent admin from deleting themselves
    if ($_SESSION['admin_id'] == $userId) {
        $_SESSION['error'] = "❌ You cannot delete your own admin account!";
        header("Location: users.php");
        exit();
    }

    $stmt = $conn->prepare("DELETE FROM users WHERE id = ?");
    $stmt->bind_param("i", $userId);

    if ($stmt->execute()) {
        $_SESSION['success'] = "✅ User deleted successfully!";
    } else {
        $_SESSION['error'] = "❌ Error deleting user!";
    }

    $stmt->close();
    header("Location: users.php");
    exit();
}

// SEARCH -----------------------------------
$search = $_GET['search'] ?? '';

// PAGINATION --------------------------------
$limit = 10;
$page = isset($_GET['page']) ? intval($_GET['page']) : 1;
$offset = ($page - 1) * $limit;

// Fetch users (supports username, email & display_name)
$sql = "
    SELECT id, username, display_name, email, profile_pic, profile_picture,
           provider, bio, created_at
    FROM users
    WHERE username LIKE ? OR email LIKE ? OR display_name LIKE ?
    ORDER BY created_at DESC
    LIMIT ? OFFSET ?
";

$stmt = $conn->prepare($sql);
$term = "%$search%";
$stmt->bind_param("sssii", $term, $term, $term, $limit, $offset);
$stmt->execute();
$result = $stmt->get_result();

$totalUsersRes = $conn->query("
    SELECT COUNT(*) AS total FROM users
    WHERE username LIKE '%$search%' OR email LIKE '%$search%' OR display_name LIKE '%$search%'
");
$totalUsers = $totalUsersRes->fetch_assoc()['total'];
$totalPages = ceil($totalUsers / $limit);

include 'includes/header.php';
?>

<h2>👥 Manage Users</h2>

<!-- SUCCESS MESSAGE -->
<?php if (isset($_SESSION['success'])) { ?>
    <div class="alert alert-success"><?= $_SESSION['success']; ?></div>
<?php unset($_SESSION['success']); } ?>

<!-- ERROR MESSAGE -->
<?php if (isset($_SESSION['error'])) { ?>
    <div class="alert alert-danger"><?= $_SESSION['error']; ?></div>
<?php unset($_SESSION['error']); } ?>

<!-- SEARCH BAR -->
<form method="GET" action="users.php" class="mb-3">
    <div class="input-group">
        <input type="text" name="search" class="form-control"
               placeholder="Search by username, email, or name..."
               value="<?= htmlspecialchars($search) ?>">
        <button type="submit" class="btn btn-primary">🔍 Search</button>
    </div>
</form>

<!-- USERS TABLE -->
<table class="table table-bordered table-striped">
    <thead class="table-dark">
        <tr>
            <th>ID</th>
            <th>Profile</th>
            <th>Name</th>
            <th>Email</th>
            <th>Login Type</th>
            <th>Bio</th>
            <th>Joined On</th>
            <th style="width:120px;">Actions</th>
        </tr>
    </thead>

    <tbody>
        <?php while ($row = $result->fetch_assoc()) {

            // Final name logic (same as Android User model)
            $finalName = $row['display_name']
                ?: ($row['username'] ?: "Unknown User");

            // Profile Picture logic
            $picture = $row['profile_picture'] ?: $row['profile_pic'];

            // Prepend domain if needed
            if ($picture && !str_starts_with($picture, "http")) {
                $picture = "https://picpose.iamakmal.in/" . ltrim($picture, "/");
            }

            // Placeholder avatar
            if (!$picture) {
                $picture = "https://ui-avatars.com/api/?name=" . urlencode($finalName) . "&size=100";
            }

            // Provider label formatting
            $provider = ucfirst($row['provider'] ?: "Email");
        ?>
        <tr>
            <td><?= $row['id'] ?></td>

            <!-- Profile Pic -->
            <td>
                <img src="<?= $picture ?>" width="50" height="50"
                     class="rounded-circle border" style="object-fit:cover;">
            </td>

            <!-- Name -->
            <td><?= htmlspecialchars($finalName) ?></td>

            <!-- Email -->
            <td><?= htmlspecialchars($row['email']) ?></td>

            <!-- Login Type -->
            <td>
                <span class="badge bg-info text-dark">
                    <?= $provider ?>
                </span>
            </td>

            <!-- BIO -->
            <td style="max-width:220px;">
                <?= $row['bio'] ? nl2br(htmlspecialchars($row['bio'])) : "<i>No bio</i>" ?>
            </td>

            <td><?= date("d M Y", strtotime($row['created_at'])) ?></td>

            <!-- ACTION BUTTONS -->
            <td>
                <a href="edit_user.php?id=<?= $row['id'] ?>" class="btn btn-warning btn-sm">✏️ Edit</a>
                <a href="javascript:void(0);" onclick="confirmDelete(<?= $row['id'] ?>)"
                   class="btn btn-danger btn-sm">🗑 Delete</a>
            </td>
        </tr>
        <?php } ?>
    </tbody>
</table>

<!-- PAGINATION -->
<nav>
    <ul class="pagination">
        <?php for ($i = 1; $i <= $totalPages; $i++) { ?>
            <li class="page-item <?= ($i == $page) ? 'active' : '' ?>">
                <a class="page-link"
                    href="users.php?search=<?= urlencode($search) ?>&page=<?= $i ?>">
                    <?= $i ?>
                </a>
            </li>
        <?php } ?>
    </ul>
</nav>

<script>
function confirmDelete(id) {
    if (confirm("Are you sure you want to delete this user?")) {
        window.location.href = "users.php?delete=" + id;
    }
}
</script>

<?php include 'includes/footer.php'; ?>
