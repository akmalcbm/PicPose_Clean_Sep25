<?php
session_start();
require '../config.php';

// Debug mode
error_reporting(E_ALL);
ini_set('display_errors', 1);

// Test JSON encoding
$test_placement = [
    'id' => 1,
    'key_name' => 'test_placement',
    'ad_type' => 'banner',
    'enabled' => 1
];

echo "<h2>Debug Placements Page</h2>";
echo "<h3>JSON Test:</h3>";
echo "json_encode: " . json_encode($test_placement) . "<br>";
echo "addslashes(json_encode): " . addslashes(json_encode($test_placement)) . "<br>";
echo "htmlspecialchars(json_encode): " . htmlspecialchars(json_encode($test_placement)) . "<br>";

// Test JavaScript
echo "<h3>JavaScript Test:</h3>";
?>
<script>
// Test 1: Direct JSON
const placement1 = <?php echo json_encode($test_placement); ?>;
console.log("Test 1 (Direct):", placement1);

// Test 2: Stringified then parsed
const placement2 = JSON.parse('<?php echo addslashes(json_encode($test_placement)); ?>');
console.log("Test 2 (Parsed):", placement2);

// Test 3: Function test
function testEdit(placement) {
    console.log("Edit function received:", placement);
    alert("ID: " + placement.id + ", Name: " + placement.key_name);
}
</script>

<button onclick="testEdit(<?php echo json_encode($test_placement); ?>)">
    Test Edit (Direct JSON)
</button>

<button onclick="testEdit(JSON.parse('<?php echo addslashes(json_encode($test_placement)); ?>'))">
    Test Edit (Parsed JSON)
</button>