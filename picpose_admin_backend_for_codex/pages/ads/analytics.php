<?php
session_start();
require '../../config.php';

// Use the SAME session check as main admin panel
if (!isset($_SESSION['admin']) || empty($_SESSION['admin'])) {
    // Redirect to main admin login if not logged in
    header("Location: " . BASE_URL . "/login.php");
    exit();
}

// CSRF protection
if (empty($_SESSION['csrf_token'])) {
    $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
}


// Date range handling
$range = isset($_GET['range']) ? cleanInput($_GET['range']) : '7d';
$allowed_ranges = ['today', '7d', '30d', '90d'];
if (!in_array($range, $allowed_ranges)) {
    $range = '7d';
}

// Define date ranges
$date_ranges = [
    'today' => [
        'where' => "stat_date = CURDATE()",
        'label' => "Today",
        'days' => 1
    ],
    '7d' => [
        'where' => "stat_date >= CURDATE() - INTERVAL 6 DAY",
        'label' => "Last 7 Days",
        'days' => 7
    ],
    '30d' => [
        'where' => "stat_date >= CURDATE() - INTERVAL 29 DAY",
        'label' => "Last 30 Days",
        'days' => 30
    ],
    '90d' => [
        'where' => "stat_date >= CURDATE() - INTERVAL 89 DAY",
        'label' => "Last 90 Days",
        'days' => 90
    ]
];

$current_range = $date_ranges[$range];

// KPI Summary with error handling
try {
    $summary_stmt = $conn->prepare("
        SELECT
            COALESCE(SUM(impressions), 0) as impressions,
            COALESCE(SUM(clicks), 0) as clicks,
            COALESCE(SUM(revenue), 0) as revenue,
            COUNT(DISTINCT placement_key) as placements
        FROM ad_analytics
        WHERE " . $current_range['where']
    );
    $summary_stmt->execute();
    $summary_result = $summary_stmt->get_result();
    $summary = $summary_result->fetch_assoc();
    $summary_stmt->close();
} catch (Exception $e) {
    error_log("Analytics Summary Error: " . $e->getMessage());
    $summary = ['impressions' => 0, 'clicks' => 0, 'revenue' => 0, 'placements' => 0];
}

$impressions = (int)($summary['impressions'] ?? 0);
$clicks = (int)($summary['clicks'] ?? 0);
$revenue = (float)($summary['revenue'] ?? 0);
$placements = (int)($summary['placements'] ?? 0);
$ctr = $impressions > 0 ? round(($clicks / $impressions) * 100, 2) : 0;
$rpm = $impressions > 0 ? round(($revenue / $impressions) * 1000, 2) : 0;
$avg_cpc = $clicks > 0 ? round($revenue / $clicks, 4) : 0;

// Daily graph data
try {
    $daily_stmt = $conn->prepare("
        SELECT 
            stat_date,
            COALESCE(SUM(impressions), 0) as impressions,
            COALESCE(SUM(clicks), 0) as clicks,
            COALESCE(SUM(revenue), 0) as revenue
        FROM ad_analytics
        WHERE " . $current_range['where'] . "
        GROUP BY stat_date
        ORDER BY stat_date ASC
    ");
    $daily_stmt->execute();
    $daily_result = $daily_stmt->get_result();
    
    $dates = $impArr = $clickArr = $revArr = [];
    while ($row = $daily_result->fetch_assoc()) {
        $dates[] = date('M d', strtotime($row['stat_date']));
        $impArr[] = (int)$row['impressions'];
        $clickArr[] = (int)$row['clicks'];
        $revArr[] = (float)$row['revenue'];
    }
    $daily_stmt->close();
} catch (Exception $e) {
    error_log("Daily Graph Error: " . $e->getMessage());
    $dates = $impArr = $clickArr = $revArr = [];
}

// Placement performance
try {
    $placement_stmt = $conn->prepare("
        SELECT
            COALESCE(a.placement_key, 'Unknown') as placement_key,
            COALESCE(p.screen_hint, 'Unknown Screen') as screen_hint,
            COALESCE(p.ad_type, 'Unknown') as ad_type,
            COALESCE(SUM(a.impressions), 0) as impressions,
            COALESCE(SUM(a.clicks), 0) as clicks,
            COALESCE(SUM(a.revenue), 0) as revenue,
            ROUND(
                CASE 
                    WHEN SUM(a.impressions) > 0 
                    THEN (SUM(a.clicks) / SUM(a.impressions)) * 100 
                    ELSE 0 
                END, 2
            ) as ctr,
            ROUND(
                CASE 
                    WHEN SUM(a.impressions) > 0 
                    THEN (SUM(a.revenue) / SUM(a.impressions)) * 1000 
                    ELSE 0 
                END, 4
            ) as rpm
        FROM ad_analytics a
        LEFT JOIN ad_placements p ON a.placement_key = p.key_name
        WHERE " . $current_range['where'] . "
        GROUP BY a.placement_key
        HAVING impressions > 0
        ORDER BY revenue DESC
        LIMIT 20
    ");
    $placement_stmt->execute();
    $placement_result = $placement_stmt->get_result();
} catch (Exception $e) {
    error_log("Placement Query Error: " . $e->getMessage());
    $placement_result = null;
}

// Network performance
try {
    $network_stmt = $conn->prepare("
        SELECT
            COALESCE(n.code, 'Unknown') as network_code,
            COALESCE(n.display_name, 'Unknown Network') as network_name,
            COALESCE(COUNT(DISTINCT u.id), 0) as total_units,
            COALESCE(SUM(CASE WHEN u.is_live = 1 THEN 1 ELSE 0 END), 0) as live_units
        FROM ad_network_units u
        LEFT JOIN ad_networks n ON u.network_id = n.id
        WHERE u.enabled = 1
        GROUP BY n.id, n.code, n.display_name
        ORDER BY network_name
    ");
    $network_stmt->execute();
    $network_result = $network_stmt->get_result();
} catch (Exception $e) {
    error_log("Network Query Error: " . $e->getMessage());
    $network_result = null;
}

include '../../includes/header.php';
?>

<div class="container-fluid">
    <!-- Page Header -->
    <div class="page-header mb-4">
        <div class="d-flex justify-content-between align-items-center">
            <div class="d-flex align-items-center gap-3">
                <div class="icon-wrapper bg-primary">
                    <i class="bi bi-graph-up text-white"></i>
                </div>
                <div>
                    <h1 class="mb-1">Ad Analytics Dashboard</h1>
                    <nav aria-label="breadcrumb">
                        <ol class="breadcrumb mb-0">
                            <li class="breadcrumb-item"><a href="../dashboard.php">Dashboard</a></li>
                            <li class="breadcrumb-item"><a href="index.php">Ads Management</a></li>
                            <li class="breadcrumb-item active">Analytics</li>
                        </ol>
                    </nav>
                </div>
            </div>
            
            <!-- Date Range Selector -->
            <div class="dropdown">
                <button class="btn btn-outline-primary dropdown-toggle" type="button" 
                        data-bs-toggle="dropdown" aria-expanded="false">
                    <i class="bi bi-calendar-range me-2"></i>
                    <?php echo htmlspecialchars($current_range['label']); ?>
                </button>
                <ul class="dropdown-menu">
                    <?php foreach ($date_ranges as $key => $range_info): ?>
                        <li>
                            <a class="dropdown-item <?php echo $range == $key ? 'active' : ''; ?>" 
                               href="?range=<?php echo $key; ?>">
                                <?php echo htmlspecialchars($range_info['label']); ?>
                            </a>
                        </li>
                    <?php endforeach; ?>
                </ul>
            </div>
        </div>
    </div>

    <!-- KPI Cards -->
    <div class="row g-3 mb-4">
        <div class="col-md-2 col-sm-6">
            <div class="card stat-card" style="background: linear-gradient(135deg, #4361ee, #3a0ca3);">
                <div class="card-body">
                    <div class="d-flex justify-content-between align-items-start">
                        <div>
                            <h6 class="text-white-50 mb-1">Impressions</h6>
                            <h3 class="text-white mb-0"><?php echo number_format($impressions); ?></h3>
                        </div>
                        <div class="icon-wrapper bg-white bg-opacity-25">
                            <i class="bi bi-eye text-white"></i>
                        </div>
                    </div>
                    <div class="mt-2">
                        <small class="text-white-75">
                            <i class="bi bi-arrow-up-right me-1"></i>
                            <?php echo $placements; ?> active placements
                        </small>
                    </div>
                </div>
            </div>
        </div>
        
        <div class="col-md-2 col-sm-6">
            <div class="card stat-card" style="background: linear-gradient(135deg, #4cc9f0, #4895ef);">
                <div class="card-body">
                    <div class="d-flex justify-content-between align-items-start">
                        <div>
                            <h6 class="text-white-50 mb-1">Clicks</h6>
                            <h3 class="text-white mb-0"><?php echo number_format($clicks); ?></h3>
                        </div>
                        <div class="icon-wrapper bg-white bg-opacity-25">
                            <i class="bi bi-cursor text-white"></i>
                        </div>
                    </div>
                    <div class="mt-2">
                        <small class="text-white-75">
                            <i class="bi bi-percent me-1"></i>
                            CTR: <?php echo $ctr; ?>%
                        </small>
                    </div>
                </div>
            </div>
        </div>
        
        <div class="col-md-2 col-sm-6">
            <div class="card stat-card" style="background: linear-gradient(135deg, #f72585, #b5179e);">
                <div class="card-body">
                    <div class="d-flex justify-content-between align-items-start">
                        <div>
                            <h6 class="text-white-50 mb-1">Revenue</h6>
                            <h3 class="text-white mb-0">₹<?php echo number_format($revenue, 2); ?></h3>
                        </div>
                        <div class="icon-wrapper bg-white bg-opacity-25">
                            <i class="bi bi-currency-rupee text-white"></i>
                        </div>
                    </div>
                    <div class="mt-2">
                        <small class="text-white-75">
                            <i class="bi bi-graph-up-arrow me-1"></i>
                            RPM: ₹<?php echo $rpm; ?>
                        </small>
                    </div>
                </div>
            </div>
        </div>
        
        <div class="col-md-2 col-sm-6">
            <div class="card stat-card" style="background: linear-gradient(135deg, #7209b7, #560bad);">
                <div class="card-body">
                    <div class="d-flex justify-content-between align-items-start">
                        <div>
                            <h6 class="text-white-50 mb-1">CTR</h6>
                            <h3 class="text-white mb-0"><?php echo $ctr; ?>%</h3>
                        </div>
                        <div class="icon-wrapper bg-white bg-opacity-25">
                            <i class="bi bi-percent text-white"></i>
                        </div>
                    </div>
                    <div class="mt-2">
                        <small class="text-white-75">
                            <i class="bi bi-activity me-1"></i>
                            Click-through rate
                        </small>
                    </div>
                </div>
            </div>
        </div>
        
        <div class="col-md-2 col-sm-6">
            <div class="card stat-card" style="background: linear-gradient(135deg, #f8961e, #f3722c);">
                <div class="card-body">
                    <div class="d-flex justify-content-between align-items-start">
                        <div>
                            <h6 class="text-white-50 mb-1">RPM</h6>
                            <h3 class="text-white mb-0">₹<?php echo $rpm; ?></h3>
                        </div>
                        <div class="icon-wrapper bg-white bg-opacity-25">
                            <i class="bi bi-speedometer2 text-white"></i>
                        </div>
                    </div>
                    <div class="mt-2">
                        <small class="text-white-75">
                            <i class="bi bi-cash-coin me-1"></i>
                            Revenue per 1000 impressions
                        </small>
                    </div>
                </div>
            </div>
        </div>
        
        <div class="col-md-2 col-sm-6">
            <div class="card stat-card" style="background: linear-gradient(135deg, #43aa8b, #4d908e);">
                <div class="card-body">
                    <div class="d-flex justify-content-between align-items-start">
                        <div>
                            <h6 class="text-white-50 mb-1">Avg. CPC</h6>
                            <h3 class="text-white mb-0">₹<?php echo number_format($avg_cpc, 4); ?></h3>
                        </div>
                        <div class="icon-wrapper bg-white bg-opacity-25">
                            <i class="bi bi-tag text-white"></i>
                        </div>
                    </div>
                    <div class="mt-2">
                        <small class="text-white-75">
                            <i class="bi bi-currency-exchange me-1"></i>
                            Cost per click
                        </small>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <!-- Charts Section -->
    <div class="row g-3 mb-4">
        <div class="col-lg-8">
            <div class="card">
                <div class="card-header bg-light">
                    <h5 class="mb-0">
                        <i class="bi bi-bar-chart-line me-2"></i>
                        Daily Performance Trends
                    </h5>
                </div>
                <div class="card-body">
                    <canvas id="performanceChart" height="250"></canvas>
                </div>
            </div>
        </div>
        
        <div class="col-lg-4">
            <div class="card">
                <div class="card-header bg-light">
                    <h5 class="mb-0">
                        <i class="bi bi-pie-chart me-2"></i>
                        Network Distribution
                    </h5>
                </div>
                <div class="card-body">
                    <canvas id="networkChart" height="250"></canvas>
                </div>
            </div>
        </div>
    </div>

    <!-- Placement Performance Table -->
    <div class="card mb-4">
        <div class="card-header bg-light d-flex justify-content-between align-items-center">
            <h5 class="mb-0">
                <i class="bi bi-table me-2"></i>
                Placement Performance
            </h5>
            <div class="btn-group">
                <button type="button" class="btn btn-sm btn-outline-secondary" onclick="exportToCSV()">
                    <i class="bi bi-download me-1"></i> Export CSV
                </button>
                <button type="button" class="btn btn-sm btn-outline-secondary" onclick="printTable()">
                    <i class="bi bi-printer me-1"></i> Print
                </button>
            </div>
        </div>
        <div class="card-body p-0">
            <div class="table-responsive">
                <table class="table table-hover mb-0" id="placementTable">
                    <thead class="table-light">
                        <tr>
                            <th>Placement</th>
                            <th>Screen</th>
                            <th>Type</th>
                            <th class="text-end">Impressions</th>
                            <th class="text-end">Clicks</th>
                            <th class="text-end">CTR</th>
                            <th class="text-end">Revenue</th>
                            <th class="text-end">RPM</th>
                            <th class="text-center">Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        <?php if ($placement_result && $placement_result->num_rows > 0): ?>
                            <?php while($placement = $placement_result->fetch_assoc()): ?>
                                <tr>
                                    <td>
                                        <code class="text-primary"><?php echo htmlspecialchars($placement['placement_key']); ?></code>
                                    </td>
                                    <td>
                                        <small><?php echo htmlspecialchars($placement['screen_hint']); ?></small>
                                    </td>
                                    <td>
                                        <span class="badge bg-secondary">
                                            <?php echo htmlspecialchars($placement['ad_type']); ?>
                                        </span>
                                    </td>
                                    <td class="text-end">
                                        <?php echo number_format($placement['impressions']); ?>
                                    </td>
                                    <td class="text-end">
                                        <?php echo number_format($placement['clicks']); ?>
                                    </td>
                                    <td class="text-end">
                                        <span class="badge bg-<?php echo ($placement['ctr'] > 5) ? 'success' : (($placement['ctr'] > 2) ? 'warning' : 'danger'); ?>">
                                            <?php echo $placement['ctr']; ?>%
                                        </span>
                                    </td>
                                    <td class="text-end text-success fw-bold">
                                        ₹<?php echo number_format($placement['revenue'], 2); ?>
                                    </td>
                                    <td class="text-end">
                                        <small>₹<?php echo $placement['rpm']; ?></small>
                                    </td>
                                    <td class="text-center">
                                        <a href="placement_detail.php?key=<?php echo urlencode($placement['placement_key']); ?>" 
                                           class="btn btn-sm btn-outline-primary">
                                            <i class="bi bi-zoom-in"></i>
                                        </a>
                                    </td>
                                </tr>
                            <?php endwhile; ?>
                        <?php else: ?>
                            <tr>
                                <td colspan="9" class="text-center py-4">
                                    <div class="text-muted">
                                        <i class="bi bi-inbox display-6"></i>
                                        <p class="mt-2">No analytics data available for the selected period</p>
                                    </div>
                                </td>
                            </tr>
                        <?php endif; ?>
                    </tbody>
                </table>
            </div>
        </div>
    </div>

    <!-- Network Status -->
    <div class="row">
        <div class="col-md-6">
            <div class="card">
                <div class="card-header bg-light">
                    <h5 class="mb-0">
                        <i class="bi bi-diagram-3 me-2"></i>
                        Network Status
                    </h5>
                </div>
                <div class="card-body">
                    <?php if ($network_result && $network_result->num_rows > 0): ?>
                        <div class="list-group list-group-flush">
                            <?php while($network = $network_result->fetch_assoc()): ?>
                                <div class="list-group-item d-flex justify-content-between align-items-center">
                                    <div>
                                        <span class="badge bg-primary me-2"><?php echo htmlspecialchars($network['network_code']); ?></span>
                                        <?php echo htmlspecialchars($network['network_name']); ?>
                                    </div>
                                    <div>
                                        <span class="badge bg-success">
                                            <?php echo $network['live_units']; ?> Live
                                        </span>
                                        <span class="badge bg-secondary ms-1">
                                            <?php echo $network['total_units']; ?> Total
                                        </span>
                                    </div>
                                </div>
                            <?php endwhile; ?>
                        </div>
                    <?php else: ?>
                        <div class="text-center text-muted py-4">
                            <i class="bi bi-wifi-off display-6"></i>
                            <p class="mt-2">No network data available</p>
                        </div>
                    <?php endif; ?>
                </div>
            </div>
        </div>
        
        <div class="col-md-6">
            <div class="card">
                <div class="card-header bg-light">
                    <h5 class="mb-0">
                        <i class="bi bi-speedometer2 me-2"></i>
                        Quick Actions
                    </h5>
                </div>
                <div class="card-body">
                    <div class="d-grid gap-2">
                        <a href="global_settings.php" class="btn btn-outline-primary">
                            <i class="bi bi-gear me-2"></i> Global Settings
                        </a>
                        <a href="placements.php" class="btn btn-outline-success">
                            <i class="bi bi-plus-circle me-2"></i> Manage Placements
                        </a>
                        <a href="units.php" class="btn btn-outline-info">
                            <i class="bi bi-puzzle me-2"></i> Ad Units
                        </a>
                        <button type="button" class="btn btn-outline-warning" onclick="refreshAnalytics()">
                            <i class="bi bi-arrow-clockwise me-2"></i> Refresh Data
                        </button>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>

<!-- JavaScript -->
<script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
<script src="https://cdn.jsdelivr.net/npm/chartjs-adapter-date-fns"></script>

<script>
// Performance Chart
const perfCtx = document.getElementById('performanceChart').getContext('2d');
new Chart(perfCtx, {
    type: 'line',
    data: {
        labels: <?php echo json_encode($dates); ?>,
        datasets: [
            {
                label: 'Impressions',
                data: <?php echo json_encode($impArr); ?>,
                borderColor: '#4361ee',
                backgroundColor: 'rgba(67, 97, 238, 0.1)',
                borderWidth: 2,
                tension: 0.4,
                fill: true
            },
            {
                label: 'Clicks',
                data: <?php echo json_encode($clickArr); ?>,
                borderColor: '#4cc9f0',
                backgroundColor: 'rgba(76, 201, 240, 0.1)',
                borderWidth: 2,
                tension: 0.4,
                fill: true
            },
            {
                label: 'Revenue (₹)',
                data: <?php echo json_encode($revArr); ?>,
                borderColor: '#f72585',
                backgroundColor: 'rgba(247, 37, 133, 0.1)',
                borderWidth: 2,
                tension: 0.4,
                fill: true,
                yAxisID: 'y1'
            }
        ]
    },
    options: {
        responsive: true,
        interaction: {
            mode: 'index',
            intersect: false
        },
        scales: {
            x: {
                grid: {
                    display: false
                }
            },
            y: {
                type: 'linear',
                display: true,
                position: 'left',
                beginAtZero: true,
                grid: {
                    drawBorder: false
                }
            },
            y1: {
                type: 'linear',
                display: true,
                position: 'right',
                beginAtZero: true,
                grid: {
                    drawOnChartArea: false
                },
                ticks: {
                    callback: function(value) {
                        return '₹' + value;
                    }
                }
            }
        },
        plugins: {
            legend: {
                position: 'top',
            },
            tooltip: {
                callbacks: {
                    label: function(context) {
                        let label = context.dataset.label || '';
                        if (label) {
                            label += ': ';
                        }
                        if (context.datasetIndex === 2) {
                            label += '₹' + context.parsed.y.toFixed(2);
                        } else {
                            label += context.parsed.y.toLocaleString();
                        }
                        return label;
                    }
                }
            }
        }
    }
});

// Network Chart
const networkData = {
    labels: [],
    datasets: [{
        data: [],
        backgroundColor: [
            '#4361ee', '#4cc9f0', '#f72585', '#7209b7', 
            '#f8961e', '#43aa8b', '#9d4edd', '#ff6d00'
        ],
        borderWidth: 2,
        borderColor: '#fff'
    }]
};

<?php 
// Reset network result pointer
if ($network_result) {
    $network_result->data_seek(0);
    $network_chart_data = [];
    while($network = $network_result->fetch_assoc()) {
        $network_chart_data[] = [
            'name' => $network['network_name'],
            'value' => $network['live_units']
        ];
    }
    echo "networkData.labels = " . json_encode(array_column($network_chart_data, 'name')) . ";";
    echo "networkData.datasets[0].data = " . json_encode(array_column($network_chart_data, 'value')) . ";";
}
?>

const networkCtx = document.getElementById('networkChart').getContext('2d');
new Chart(networkCtx, {
    type: 'doughnut',
    data: networkData,
    options: {
        responsive: true,
        cutout: '60%',
        plugins: {
            legend: {
                position: 'bottom'
            },
            tooltip: {
                callbacks: {
                    label: function(context) {
                        const label = context.label || '';
                        const value = context.raw || 0;
                        const total = context.dataset.data.reduce((a, b) => a + b, 0);
                        const percentage = Math.round((value / total) * 100);
                        return `${label}: ${value} units (${percentage}%)`;
                    }
                }
            }
        }
    }
});

// Export functions
function exportToCSV() {
    const table = document.getElementById('placementTable');
    const rows = table.querySelectorAll('tr');
    let csv = [];
    
    rows.forEach(row => {
        const rowData = [];
        row.querySelectorAll('th, td').forEach(cell => {
            // Remove badge HTML and get text content
            let text = cell.textContent.replace(/\n/g, '').trim();
            text = text.replace(/₹/g, 'INR ');
            rowData.push(`"${text}"`);
        });
        csv.push(rowData.join(','));
    });
    
    const csvContent = csv.join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    const url = URL.createObjectURL(blob);
    
    link.setAttribute('href', url);
    link.setAttribute('download', `ad-analytics-<?php echo date('Y-m-d'); ?>.csv`);
    link.style.visibility = 'hidden';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
}

function printTable() {
    const printContent = document.getElementById('placementTable').outerHTML;
    const originalContent = document.body.innerHTML;
    
    document.body.innerHTML = `
        <html>
            <head>
                <title>Ad Analytics Report - <?php echo date('Y-m-d'); ?></title>
                <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
                <style>
                    @media print {
                        .no-print { display: none !important; }
                        table { width: 100% !important; }
                        th, td { padding: 8px !important; }
                    }
                    body { padding: 20px; }
                    h3 { margin-bottom: 20px; }
                </style>
            </head>
            <body>
                <div class="container-fluid">
                    <h3>Ad Analytics Report - <?php echo $current_range['label']; ?></h3>
                    <p class="text-muted">Generated on: <?php echo date('F j, Y H:i'); ?></p>
                    ${printContent}
                </div>
            </body>
        </html>
    `;
    
    window.print();
    document.body.innerHTML = originalContent;
    window.location.reload();
}

function refreshAnalytics() {
    const btn = event.target;
    const originalText = btn.innerHTML;
    
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span> Refreshing...';
    btn.disabled = true;
    
    setTimeout(() => {
        window.location.reload();
    }, 1000);
}

// Sidebar toggle
function toggleSidebar() {
    document.querySelector('.sidebar').classList.toggle('show');
    document.querySelector('.sidebar-overlay').classList.toggle('show');
}
</script>

<?php include '../../includes/footer.php'; ?>