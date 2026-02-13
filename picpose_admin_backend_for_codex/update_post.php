<?php
require 'config.php';

if ($_SERVER["REQUEST_METHOD"] == "POST") {
    $id = intval($_POST['post_id']);
    $title = trim($_POST['title']);
    $category_id = intval($_POST['category_id']);
    $content = trim($_POST['content']);
    $video_url = trim($_POST['video_url']);

    // Convert selected subcategories into a comma-separated string
    $subcategories = isset($_POST['subcategories']) ? implode(',', $_POST['subcategories']) : '';

    // Handle Image Upload (if provided)
    if (!empty($_FILES['image']['name'])) {
        $upload_dir = "uploads/posts_pic/";
        $file_ext = strtolower(pathinfo($_FILES['image']['name'], PATHINFO_EXTENSION));
        $new_image_name = time() . "." . $file_ext;
        $target_file = $upload_dir . $new_image_name;

        if (move_uploaded_file($_FILES['image']['tmp_name'], $target_file)) {
            $image_sql = ", image_url='$target_file'";
        }
    } else {
        $image_sql = "";
    }

    // Update post query
    $sql = "UPDATE posts SET title=?, category_id=?, content=?, video_url=?, tags=? $image_sql WHERE id=?";
    $stmt = $conn->prepare($sql);
    $stmt->bind_param("sisssi", $title, $category_id, $content, $video_url, $subcategories, $id);

    if ($stmt->execute()) {
        header("Location: manage_posts.php?success=Post updated successfully!");
    } else {
        header("Location: edit_post.php?id=$id&error=Error updating post.");
    }
}
?>
