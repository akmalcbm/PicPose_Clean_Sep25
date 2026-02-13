<?php
session_start();
error_reporting(E_ALL);
ini_set('display_errors', 1);

require 'config.php';

ini_set('display_errors', 1);
ini_set('display_startup_errors', 1);
error_reporting(E_ALL);

if (!isset($_SESSION['admin'])) {
    header("Location: login.php");
    exit();
}

// IMPORTANT: your subdomain docroot is public_html/picpose_admin
// Files live in public_html/picpose_admin/uploads/categories
// Public URL on subdomain must be /uploads/categories/filename.ext

// Configuration
$UPLOAD_DIR = __DIR__ . '/uploads/categories/';   // filesystem directory (absolute)
$PUBLIC_PATH_PREFIX = 'uploads/categories/';      // public path from subdomain root

// Helpers
function slugify($text) {
    $text = preg_replace('~[^\pL\d]+~u', '-', $text);
    $text = @iconv('UTF-8', 'ASCII//TRANSLIT', $text);
    $text = preg_replace('~[^-\w]+~', '', $text);
    $text = trim($text, '-');
    $text = preg_replace('~-+~', '-', $text);
    $text = strtolower($text);
    return $text ?: 'n-a';
}
function uniqueSlug(mysqli $conn, $slug, $excludeId = null) {
    $base = $slug;
    $i = 0;
    while (true) {
        $candidate = $i === 0 ? $base : $base . '-' . $i;
        if ($excludeId) {
            $stmt = $conn->prepare("SELECT id FROM categories WHERE slug = ? AND id <> ? LIMIT 1");
            $stmt->bind_param("si", $candidate, $excludeId);
        } else {
            $stmt = $conn->prepare("SELECT id FROM categories WHERE slug = ? LIMIT 1");
            $stmt->bind_param("s", $candidate);
        }
        $stmt->execute();
        $stmt->store_result();
        if ($stmt->num_rows === 0) {
            $stmt->close();
            return $candidate;
        }
        $stmt->close();
        $i++;
    }
}
function handleImageUpload($fieldName, $UPLOAD_DIR, $PUBLIC_PATH_PREFIX, &$error) {
    if (!isset($_FILES[$fieldName]) || $_FILES[$fieldName]['error'] === UPLOAD_ERR_NO_FILE) {
        return [null, null]; // no upload
    }
    $file = $_FILES[$fieldName];
    if ($file['error'] !== UPLOAD_ERR_OK) {
        $error = "Image upload error code: " . $file['error'];
        return [null, null];
    }
    $maxSize = 5 * 1024 * 1024; // 5MB
    if ($file['size'] > $maxSize) {
        $error = "Image too large. Max 5MB.";
        return [null, null];
    }
    if (!class_exists('finfo')) {
        $error = "PHP fileinfo extension is not enabled on the server.";
        return [null, null];
    }
    $finfo = new finfo(FILEINFO_MIME_TYPE);
    $mime = $finfo->file($file['tmp_name']);
    $allowed = [
        'image/jpeg' => 'jpg',
        'image/png'  => 'png',
        'image/webp' => 'webp',
    ];
    if (!isset($allowed[$mime])) {
        $error = "Invalid image type. Allowed: JPG, PNG, WEBP.";
        return [null, null];
    }
    $ext = $allowed[$mime];
    $base = bin2hex(random_bytes(8)) . '_' . time();
    $filename = $base . '.' . $ext;

    if (!is_dir($UPLOAD_DIR)) {
        @mkdir($UPLOAD_DIR, 0775, true);
    }
    if (!is_dir($UPLOAD_DIR) || !is_writable($UPLOAD_DIR)) {
        $error = "Upload directory is missing or not writable: " . $UPLOAD_DIR;
        return [null, null];
    }

    $dest = rtrim($UPLOAD_DIR, '/\\') . DIRECTORY_SEPARATOR . $filename;
    if (!@move_uploaded_file($file['tmp_name'], $dest)) {
        $error = "Failed to move uploaded file.";
        return [null, null];
    }
    // Save a web-visible relative path without the 'picpose_admin/' prefix
    return [$dest, $PUBLIC_PATH_PREFIX . $filename];
}
function deleteIfFile($filepath) {
    if ($filepath && is_file($filepath)) {
        @unlink($filepath);
    }
}
function hasColumn(mysqli $conn, $table, $column) {
    $sql = "SELECT COUNT(*) AS c
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ? LIMIT 1";
    $stmt = $conn->prepare($sql);
    $stmt->bind_param("ss", $table, $column);
    $stmt->execute();
    $res = $stmt->get_result();
    $row = $res->fetch_assoc();
    $stmt->close();
    return (int)$row['c'] === 1;
}
function wouldCreateCycle(mysqli $conn, int $categoryId, ?int $newParentId): bool {
    if ($newParentId === null || $newParentId === 0) return false;
    if ($newParentId === $categoryId) return true;
    $current = $newParentId;
    $seen = 0;
    while ($current) {
        $stmt = $conn->prepare("SELECT parent_id FROM categories WHERE id = ? LIMIT 1");
        $stmt->bind_param("i", $current);
        $stmt->execute();
        $res = $stmt->get_result();
        $row = $res->fetch_assoc();
        $stmt->close();
        if (!$row) break;
        $current = $row['parent_id'] !== null ? (int)$row['parent_id'] : null;
        if ($current === $categoryId) return true;
        if (++$seen > 1000) break;
    }
    return false;
}
// Normalize a stored image_path to a correct public URL for the current subdomain
function normalizePublicUrl($storedPath) {
    if (empty($storedPath)) return '';
    if (preg_match('#^https?://#i', $storedPath)) return $storedPath; // already absolute
    $path = ltrim($storedPath, '/');
    // If it mistakenly includes 'picpose_admin/', strip it (subdomain docroot is already picpose_admin)
    if (strpos($path, 'picpose_admin/') === 0) {
        $path = substr($path, strlen('picpose_admin/'));
    }
    return '/' . $path;
}


/* ===================================================
   📊 CATEGORY POST COUNT CACHING SYSTEM
   ---------------------------------------------------
   - Maintains ai_count and guide_count in category_stats
   - Auto-refresh if older than 10 minutes
=================================================== */
function refreshCategoryStatsCache(mysqli $conn) {
    // Check table existence
    $res = $conn->query("SHOW TABLES LIKE 'category_stats'");
    if (!$res || $res->num_rows === 0) return;

    // --- Refresh AI counts ---
    $conn->query("
    INSERT INTO category_stats (category_id, ai_count)
    SELECT p.category_id, COUNT(*) AS cnt
    FROM ai_posts p
    INNER JOIN categories c ON p.category_id = c.id
    WHERE p.category_id IS NOT NULL
    GROUP BY p.category_id
    ON DUPLICATE KEY UPDATE ai_count = VALUES(ai_count)
");

$conn->query("
    INSERT INTO category_stats (category_id, guide_count)
    SELECT g.category_id, COUNT(*) AS cnt
    FROM guide_posts g
    INNER JOIN categories c ON g.category_id = c.id
    WHERE g.category_id IS NOT NULL
    GROUP BY g.category_id
    ON DUPLICATE KEY UPDATE guide_count = VALUES(guide_count)
");


    // --- Ensure all categories exist in cache (even zero counts) ---
    $conn->query("
        INSERT IGNORE INTO category_stats (category_id, ai_count, guide_count)
        SELECT id, 0, 0 FROM categories
    ");
}

function getCategoryStats(mysqli $conn): array {
    $stats = [];
    $res = $conn->query("SELECT category_id, ai_count, guide_count, updated_at FROM category_stats");
    if ($res) {
        while ($r = $res->fetch_assoc()) {
            $stats[(int)$r['category_id']] = [
                'ai' => (int)$r['ai_count'],
                'guide' => (int)$r['guide_count'],
                'updated_at' => $r['updated_at']
            ];
        }
    }
    return $stats;
}

function needsStatsRefresh(mysqli $conn): bool {
    $res = $conn->query("SELECT MAX(updated_at) AS last_update FROM category_stats");
    if (!$res) return true;
    $row = $res->fetch_assoc();
    if (empty($row['last_update'])) return true;
    $last = strtotime($row['last_update']);
    return (time() - $last) > 600; // > 10 minutes old
}



// Self-checks (no output yet)
$problems = [];
$requiredColumns = ['slug', 'image_path'];
foreach ($requiredColumns as $col) {
    if (!hasColumn($conn, 'categories', $col)) {
        $problems[] = "Missing DB column: categories.$col";
    }
}
if (!function_exists('iconv')) {
    $problems[] = "PHP iconv extension is not enabled.";
}
if (!is_dir($UPLOAD_DIR)) {
    @mkdir($UPLOAD_DIR, 0775, true);
}
if (!is_dir($UPLOAD_DIR)) {
    $problems[] = "Upload directory does not exist and could not be created: " . $UPLOAD_DIR;
} elseif (!is_writable($UPLOAD_DIR)) {
    $problems[] = "Upload directory is not writable: " . $UPLOAD_DIR;
}

// Handle POST actions BEFORE any output
if ($_SERVER["REQUEST_METHOD"] === "POST") {
    // Add
    if (isset($_POST['add_category'])) {
        $name = trim($_POST['name']);
        $slug = trim($_POST['slug'] ?? '');
        $parent_id = $_POST['parent_id'] !== "" ? intval($_POST['parent_id']) : NULL;

        if ($name === '') {
            $error = "Category name cannot be empty!";
        } else {
            if ($slug === '') $slug = slugify($name);
            $slug = uniqueSlug($conn, $slug);

            $uploadError = null;
            list($fsPath, $publicPath) = handleImageUpload('image', $UPLOAD_DIR, $PUBLIC_PATH_PREFIX, $uploadError);
            if ($uploadError) {
                $error = $uploadError;
            } else {
                $stmt = $conn->prepare("INSERT INTO categories (name, slug, parent_id, image_path) VALUES (?, ?, ?, ?)");
                $stmt->bind_param("ssis", $name, $slug, $parent_id, $publicPath);
                if ($stmt->execute()) {
                    $success = "Category added successfully!";
                } else {
                    if (!empty($fsPath)) deleteIfFile($fsPath);
                    $error = "Error adding category! " . $conn->error;
                }
                $stmt->close();
            }
        }
    }

    // Update (Edit)
    if (isset($_POST['update_category'])) {
        $id = intval($_POST['id'] ?? 0);
        $name = trim($_POST['name'] ?? '');
        $slug = trim($_POST['slug'] ?? '');
        $parent_id = ($_POST['parent_id'] ?? '') !== '' ? intval($_POST['parent_id']) : NULL;
        $remove_image = isset($_POST['remove_image']) ? (int)$_POST['remove_image'] : 0;

        if ($id <= 0) {
            $error = "Invalid category ID.";
        } elseif ($name === '') {
            $error = "Category name cannot be empty.";
        } elseif (wouldCreateCycle($conn, $id, $parent_id)) {
            $error = "Invalid parent category: this would create a circular hierarchy.";
        } else {
            $stmtCur = $conn->prepare("SELECT image_path FROM categories WHERE id = ? LIMIT 1");
            $stmtCur->bind_param("i", $id);
            $stmtCur->execute();
            $resCur = $stmtCur->get_result();
            $current = $resCur->fetch_assoc();
            $stmtCur->close();

            if (!$current) {
                $error = "Category not found.";
            } else {
                if ($slug === '') $slug = slugify($name);
                $slug = uniqueSlug($conn, $slug, $id);

                $newPublicPath = null;
                $uploadError = null;

                if ($remove_image === 1) {
                    $newPublicPath = null; // will set to NULL below
                }
                if (isset($_FILES['image']) && $_FILES['image']['error'] !== UPLOAD_ERR_NO_FILE) {
                    list($fsPath, $publicPath) = handleImageUpload('image', $UPLOAD_DIR, $PUBLIC_PATH_PREFIX, $uploadError);
                    if ($uploadError) {
                        $error = $uploadError;
                    } else {
                        $newPublicPath = $publicPath;
                    }
                }

                if (empty($error)) {
                    if ($newPublicPath === null) {
                        if ($remove_image === 1) {
                            $stmt = $conn->prepare("UPDATE categories SET name = ?, slug = ?, parent_id = ?, image_path = NULL WHERE id = ?");
                            $stmt->bind_param("ssii", $name, $slug, $parent_id, $id);
                        } else {
                            $stmt = $conn->prepare("UPDATE categories SET name = ?, slug = ?, parent_id = ? WHERE id = ?");
                            $stmt->bind_param("ssii", $name, $slug, $parent_id, $id);
                        }
                    } else {
                        $stmt = $conn->prepare("UPDATE categories SET name = ?, slug = ?, parent_id = ?, image_path = ? WHERE id = ?");
                        $stmt->bind_param("ssisi", $name, $slug, $parent_id, $newPublicPath, $id);
                    }

                    if ($stmt && $stmt->execute()) {
                        if ($newPublicPath !== null || $remove_image === 1) {
                            if (!empty($current['image_path'])) {
                                $fsOld = rtrim($UPLOAD_DIR, '/\\') . DIRECTORY_SEPARATOR . basename($current['image_path']);
                                deleteIfFile($fsOld);
                            }
                        }
                        $success = "Category updated successfully!";
                    } else {
                        if (isset($fsPath) && $fsPath) deleteIfFile($fsPath);
                        $error = "Failed to update category. " . $conn->error;
                    }
                    if ($stmt) $stmt->close();
                }
            }
        }
    }

    // Delete (redirect BEFORE any output)
    if (isset($_POST['delete_id'])) {
        $delete_id = intval($_POST['delete_id']);

        // Check for children; prevent deleting parents with children
        $stmtCheck = $conn->prepare("SELECT COUNT(*) FROM categories WHERE parent_id = ?");
        $stmtCheck->bind_param("i", $delete_id);
        $stmtCheck->execute();
        $stmtCheck->bind_result($childCount);
        $stmtCheck->fetch();
        $stmtCheck->close();

        if ($childCount > 0) {
            $error = "Cannot delete: this category has subcategories. Delete or reassign them first.";
        } else {
            $stmtImg = $conn->prepare("SELECT image_path FROM categories WHERE id = ?");
            $stmtImg->bind_param("i", $delete_id);
            $stmtImg->execute();
            $resImg = $stmtImg->get_result();
            $imgRow = $resImg->fetch_assoc();
            $stmtImg->close();

            $stmtDel = $conn->prepare("DELETE FROM categories WHERE id = ?");
            $stmtDel->bind_param("i", $delete_id);
            $stmtDel->execute();
            $affected = $stmtDel->affected_rows;
            $stmtDel->close();

            if ($affected > 0) {
                if (!empty($imgRow['image_path'])) {
                    $fsPath = rtrim($UPLOAD_DIR, '/\\') . DIRECTORY_SEPARATOR . basename($imgRow['image_path']);
                    deleteIfFile($fsPath);
                }
                header("Location: manage_categories.php?deleted=1");
                exit();
            } else {
                $error = "Delete failed or category does not exist.";
            }
        }
    }
}

// Fetch categories (after any changes)
$res = $conn->query("SELECT id, name, parent_id, slug, image_path FROM categories ORDER BY parent_id ASC, name ASC");
$allCats = $res ? $res->fetch_all(MYSQLI_ASSOC) : [];
$byId = [];
foreach ($allCats as $c) { $byId[(int)$c['id']] = $c; }


/* ==========================================
   🔥 OPTIMIZED POST COUNT FETCH (CACHED)
========================================== */
if (needsStatsRefresh($conn)) {
    refreshCategoryStatsCache($conn);
}

$stats = getCategoryStats($conn);

$aiCounts = [];
$guideCounts = [];

foreach ($stats as $cid => $s) {
    $aiCounts[$cid] = $s['ai'];
    $guideCounts[$cid] = $s['guide'];
}



// Now it’s safe to output
include 'includes/header.php';
?>

<?php
// NEW: simple totals for display (optional)
$totalAI = array_sum($aiCounts);
$totalGuides = array_sum($guideCounts);
?>
<h2 class="d-flex align-items-center gap-2">
  📂 Manage Categories
  <small class="text-muted" style="font-size:.95rem;">
    (<?= (int)$totalAI; ?> AI | <?= (int)$totalGuides; ?> Guides)
  </small>
</h2>


<?php if (!empty($problems)) { ?>
    <div class="alert alert-danger">
        <strong>Setup issues detected:</strong>
        <ul>
            <?php foreach ($problems as $p) { echo "<li>" . htmlspecialchars($p) . "</li>"; } ?>
        </ul>
        <?php if (in_array('Missing DB column: categories.slug', $problems, true) || in_array('Missing DB column: categories.image_path', $problems, true)) { ?>
            <p>Run this SQL to add the missing columns:</p>
            <pre><code>ALTER TABLE categories
  ADD COLUMN IF NOT EXISTS slug VARCHAR(255) NULL UNIQUE AFTER name,
  ADD COLUMN IF NOT EXISTS image_path VARCHAR(255) NULL AFTER slug;</code></pre>
        <?php } ?>
    </div>
<?php } ?>

<?php if (isset($_GET['deleted'])) { ?>
    <div class="alert alert-success">Category deleted successfully!</div>
<?php } ?>
<?php if (isset($success)) { ?>
    <div class="alert alert-success"><?= htmlspecialchars($success); ?></div>
<?php } ?>
<?php if (!empty($error)) { ?>
    <div class="alert alert-danger"><?= htmlspecialchars($error); ?></div>
<?php } ?>

<div class="card p-3 mb-4">
    <h5>Add New Category</h5>
    <form method="POST" action="" enctype="multipart/form-data">
        <div class="mb-3">
            <label>Category Name:</label>
            <input type="text" name="name" class="form-control" required>
        </div>
        <div class="mb-3">
            <label>Slug (Optional, auto-generated if blank):</label>
            <input type="text" name="slug" class="form-control" placeholder="men-prompts">
        </div>
        <div class="mb-3">
            <label>Parent Category (Optional):</label>
            <select name="parent_id" class="form-control">
                <option value="">None (Main Category)</option>
                <?php foreach ($allCats as $cat) { if ($cat['parent_id'] === NULL) { ?>
                    <option value="<?= intval($cat['id']); ?>"><?= htmlspecialchars($cat['name']); ?></option>
                <?php }} ?>
            </select>
        </div>
        <div class="mb-3">
            <label>Category Image (JPG/PNG/WEBP, max 5MB):</label>
            <input type="file" name="image" accept=".jpg,.jpeg,.png,.webp" class="form-control">
            <small class="text-muted">Shown as circular thumbnail in the app.</small>
        </div>
        <button type="submit" name="add_category" class="btn btn-primary">Add Category</button>
    </form>
</div>

<!-- Categories Table -->
<table class="table table-bordered align-middle">
        <thead>
        <tr>
            <th style="width:60px;">ID</th>
            <th style="width:80px;">Image</th>
            <th>Category Name</th>
            <th>Slug</th>
            <th style="min-width:160px;">Posts</th> <!-- NEW -->
            <th>Parent Category</th>
            <th style="width:200px;">Actions</th>
        </tr>
    </thead>

    <tbody>
        <?php foreach ($allCats as $row) {
            $parentName = "Main Category";
            if (!empty($row['parent_id'])) {
                $pid = (int)$row['parent_id'];
                if (isset($byId[$pid])) $parentName = htmlspecialchars($byId[$pid]['name']);
            }
            // Normalize whatever is stored to a correct public URL on this subdomain
            $imgSrc = normalizePublicUrl($row['image_path'] ?? '');
            $aiCount = $aiCounts[(int)$row['id']] ?? 0;       // NEW
            $guideCount = $guideCounts[(int)$row['id']] ?? 0; // NEW

        ?>
            <tr>
                <td><?= intval($row['id']); ?></td>
                <td>
                    <?php if ($imgSrc) { ?>
                        <img src="<?= htmlspecialchars($imgSrc); ?>" alt="<?= htmlspecialchars($row['name']); ?>" style="width:56px;height:56px;object-fit:cover;border-radius:50%;border:2px solid #3b4859;">
                    <?php } else { ?>
                        <div style="width:56px;height:56px;border-radius:50%;background:#e9ecef;border:2px dashed #cbd3da;"></div>
                    <?php } ?>
                </td>
                <td><?= htmlspecialchars($row['name']); ?></td>
                
                <td><code><?= htmlspecialchars($row['slug'] ?? ''); ?></code></td>

<!-- NEW: Posts (split by AI | Guides) -->
<td>
    <?php if ($aiCount > 0 || $guideCount > 0): ?>
        <?php if ($aiCount > 0): ?>
            <span class="badge bg-primary me-1"><?= $aiCount; ?> AI</span>
        <?php endif; ?>
        <?php if ($guideCount > 0): ?>
            <span class="badge bg-teal text-dark me-1" style="background-color:#14b8a6 !important; color:#0b1220 !important;">
                <?= $guideCount; ?> Guides
            </span>
        <?php endif; ?>
    <?php else: ?>
        <span class="badge bg-secondary">0</span>
    <?php endif; ?>
</td>

<td><?= $parentName; ?></td>

                
                <td>
                    <button class="btn btn-secondary btn-sm edit-btn"
                        data-id="<?= intval($row['id']); ?>"
                        data-name="<?= htmlspecialchars($row['name']); ?>"
                        data-slug="<?= htmlspecialchars($row['slug'] ?? ''); ?>"
                        data-parent-id="<?= htmlspecialchars($row['parent_id'] ?? ''); ?>"
                        data-image-src="<?= htmlspecialchars($imgSrc); ?>"
                    >Edit</button>
                    <button class="btn btn-danger btn-sm delete-btn"
                        data-id="<?= intval($row['id']); ?>"
                        data-name="<?= htmlspecialchars($row['name']); ?>"
                        data-parent="<?= $parentName; ?>">Delete</button>
                </td>
            </tr>
        <?php } ?>
    </tbody>
</table>

<!-- Edit Category Modal -->
<div id="editModal" class="modal fade" tabindex="-1" aria-labelledby="editModalLabel" aria-hidden="true">
  <div class="modal-dialog">
    <form method="POST" action="" enctype="multipart/form-data" class="modal-content">
      <div class="modal-header">
        <h5 class="modal-title" id="editModalLabel">Edit Category</h5>
        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
      </div>
      <div class="modal-body">
          <input type="hidden" name="id" id="edit_id">
          <div class="mb-3">
              <label>Name</label>
              <input type="text" name="name" id="edit_name" class="form-control" required>
          </div>
          <div class="mb-3">
              <label>Slug (Optional, auto-generated if blank)</label>
              <input type="text" name="slug" id="edit_slug" class="form-control">
          </div>
          <div class="mb-3">
              <label>Parent Category (Optional)</label>
              <select name="parent_id" id="edit_parent_id" class="form-control">
                  <option value="">None (Main Category)</option>
                  <?php foreach ($allCats as $cat) { if ($cat['parent_id'] === NULL) { ?>
                      <option value="<?= intval($cat['id']); ?>"><?= htmlspecialchars($cat['name']); ?></option>
                  <?php }} ?>
              </select>
              <small class="text-muted">Cannot set a category as its own parent or under its descendant.</small>
          </div>
          <div class="mb-2 d-flex align-items-center gap-3">
              <div>
                  <div class="mb-1">Current Image</div>
                  <img id="edit_preview" src="" alt="No image" style="width:64px;height:64px;object-fit:cover;border-radius:50%;border:2px solid #3b4859;display:none;">
                  <div id="edit_no_image" style="width:64px;height:64px;border-radius:50%;background:#e9ecef;border:2px dashed #cbd3da;"></div>
              </div>
              <div class="flex-grow-1">
                  <label>Replace Image (JPG/PNG/WEBP, max 5MB)</label>
                  <input type="file" name="image" accept=".jpg,.jpeg,.png,.webp" class="form-control">
                  <div class="form-check mt-2">
                      <input class="form-check-input" type="checkbox" value="1" id="edit_remove_image" name="remove_image">
                      <label class="form-check-label" for="edit_remove_image">
                          Remove current image
                      </label>
                  </div>
              </div>
          </div>
      </div>
      <div class="modal-footer">
        <button type="submit" name="update_category" class="btn btn-primary">Save changes</button>
        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
      </div>
    </form>
  </div>
</div>

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
                <form method="POST" action="">
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
        // Delete
        document.querySelectorAll(".delete-btn").forEach(function (button) {
            button.addEventListener("click", function () {
                let categoryId = this.getAttribute("data-id");
                let categoryName = this.getAttribute("data-name");
                let parentCategory = this.getAttribute("data-parent");

                document.getElementById("deleteMessage").innerHTML =
                    `Are you sure you want to delete <strong>${categoryName}</strong>? <br> This is a ${parentCategory === "Main Category" ? "Main Category" : "Subcategory under " + parentCategory}.`;
                document.getElementById("delete_id").value = categoryId;

                let deleteModal = new bootstrap.Modal(document.getElementById("deleteModal"));
                deleteModal.show();
            });
        });

        // Edit
        const editModalEl = document.getElementById("editModal");
        const editModal = new bootstrap.Modal(editModalEl);
        const editId = document.getElementById("edit_id");
        const editName = document.getElementById("edit_name");
        const editSlug = document.getElementById("edit_slug");
        const editParent = document.getElementById("edit_parent_id");
        const editPreview = document.getElementById("edit_preview");
        const editNoImage = document.getElementById("edit_no_image");
        const editRemoveImage = document.getElementById("edit_remove_image");

        document.querySelectorAll(".edit-btn").forEach(function(btn) {
            btn.addEventListener("click", function() {
                const id = this.getAttribute("data-id");
                const name = this.getAttribute("data-name") || "";
                const slug = this.getAttribute("data-slug") || "";
                const parentId = this.getAttribute("data-parent-id") || "";
                const img = this.getAttribute("data-image-src") || "";

                editId.value = id;
                editName.value = name;
                editSlug.value = slug;

                Array.from(editParent.options).forEach(opt => {
                    opt.disabled = (opt.value === id);
                });
                editParent.value = parentId;

                if (img) {
                    editPreview.src = img + (img.indexOf('?') === -1 ? '?v=' + Date.now() : '');
                    editPreview.style.display = "block";
                    editNoImage.style.display = "none";
                } else {
                    editPreview.style.display = "none";
                    editNoImage.style.display = "block";
                }
                editRemoveImage.checked = false;

                editModal.show();
            });
        });
    });
</script>

<?php include 'includes/footer.php'; ?>