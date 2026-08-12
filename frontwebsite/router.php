<?php
$requestPath = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?? '/';
$staticPath = realpath(__DIR__ . str_replace('/', DIRECTORY_SEPARATOR, $requestPath));
if ($requestPath !== '/' && $staticPath !== false && is_file($staticPath) && str_starts_with($staticPath, __DIR__)) {
    return false;
}

$routes = [
    '' => 'index.php',
    'home' => 'index.php',
    'platform' => 'platform.php',
    'how-it-works' => 'how-it-works.php',
    'payments' => 'payments.php',
    'downloads' => 'downloads.php',
    'community' => 'community.php',
    'pricing' => 'pricing.php',
    'resources' => 'resources.php',
    'analytics' => 'analytics.php',
    'community-post' => 'community-post.php',
    'community-engage' => 'community-engage.php',
    'community-comment' => 'community-comment.php',
    'track' => 'track.php',
    'api/community/posts' => 'api-community-posts.php',
    'api/community/answer' => 'api-community-answer.php',
    'api/visits/stats' => 'api-visit-stats.php',
];

$path = $requestPath;
$base = rtrim(str_replace('\\', '/', dirname($_SERVER['SCRIPT_NAME'] ?? '')), '/');
if ($base !== '' && $base !== '/' && strpos($path, $base) === 0) {
    $path = substr($path, strlen($base));
}

$route = trim($path, '/');
$route = preg_replace('/\.php$/', '', $route);

if (!array_key_exists($route, $routes)) {
    http_response_code(404);
    require __DIR__ . '/index.php';
    exit;
}

require __DIR__ . '/' . $routes[$route];
