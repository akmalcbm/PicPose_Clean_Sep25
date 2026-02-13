<?php
session_start();
require 'config.php';

if (!isset($_SESSION['admin'])) {
    header("Location: login.php");
    exit();
}

// Fetch all posts with category names
$sql = "SELECT posts.*, categories.name AS category_name FROM posts
        LEFT JOIN categories ON posts.category_id = categories.id
        ORDER BY posts.created_at DESC";
$result = $conn->query($sql);

include 'includes/header.php';
?>

<h2>📝 Manage Posts</h2>

<?php if (isset($_GET['success'])) { ?>
    <div class="alert alert-success"><?= htmlspecialchars($_GET['success']); ?></div>
<?php } ?>

<table class="table table-bordered">
    <thead>
        <tr>
            <th>ID</th>
            <th>Title</th>
            <th>Category</th>
            <th>Image</th>
            <th>Video</th>
            <th>Actions</th>
        </tr>
    </thead>
    <tbody>
        <?php while ($row = $result->fetch_assoc()) { ?>
            <tr>
                <td><?= $row['id']; ?></td>
                <td><?= htmlspecialchars($row['title']); ?></td>
                <td><?= htmlspecialchars($row['category_name'] ?? "No Category"); ?></td>
                <td>
                    <?php if (!empty($row['image_url'])) { ?>
                        <img src="<?= htmlspecialchars($row['image_url']); ?>" width="50">
                    <?php } else { echo "No Image"; } ?>
                </td>
                <td>
                    <?php if (!empty($row['video_url'])) { ?>
                        <a href="<?= htmlspecialchars($row['video_url']); ?>" target="_blank">Watch</a>
                    <?php } else { echo "No Video"; } ?>
                </td>
                <td>
                    <a href="edit_post.php?id=<?= $row['id']; ?>" class="btn btn-primary btn-sm">Edit</a>
                    <button class="btn btn-danger btn-sm delete-btn" data-id="<?= $row['id']; ?>">Delete</button>
                </td>
            </tr>
        <?php } ?>
    </tbody>
</table>

<!-- Delete Confirmation Modal -->
<div id="deleteModal" class="modal fade" tabindex="-1" aria-labelledby="deleteModalLabel" aria-hidden="true">
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title">Confirm Deletion</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body">
                <p id="deleteMessage"></p>
            </div>
            <div class="modal-footer">
                <form method="POST" action="delete_post.php">
                    <input type="hidden" name="delete_id" id="delete_id">
                    <button type="submit" class="btn btn-danger">Yes, Delete</button>
                    <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
                </form>
            </div>
        </div>
    </div>
</div>

<script>
    document.addEventListener("DOMContentLoaded", function () {
        let deleteButtons = document.querySelectorAll(".delete-btn");
        deleteButtons.forEach(function (button) {
            button.addEventListener("click", function () {
                let postId = this.getAttribute("data-id");
                document.getElementById("deleteMessage").innerHTML = "Are you sure you want to delete this post?";
                document.getElementById("delete_id").value = postId;
                let deleteModal = new bootstrap.Modal(document.getElementById("deleteModal"));
                deleteModal.show();
            });
        });
    });
</script>

<?php include 'includes/footer.php'; ?>
