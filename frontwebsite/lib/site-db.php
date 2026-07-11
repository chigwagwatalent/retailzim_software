<?php
function retailzim_env(string $key, ?string $default = null): ?string
{
    $value = getenv($key);
    if ($value !== false && $value !== '') {
        return $value;
    }

    return $_ENV[$key] ?? $_SERVER[$key] ?? $default;
}

function retailzim_db_config(): array
{
    return [
        'host' => retailzim_env('RETAILZIM_DB_HOST', 'localhost'),
        'port' => retailzim_env('RETAILZIM_DB_PORT', '3306'),
        'name' => retailzim_env('RETAILZIM_DB_NAME', 'connecte_retail_comunity'),
        'user' => retailzim_env('RETAILZIM_DB_USER', 'connecte_retail_comunity'),
        'pass' => retailzim_env('RETAILZIM_DB_PASS', '@cHigwagwa1t@'),
        'charset' => retailzim_env('RETAILZIM_DB_CHARSET', 'utf8mb4'),
    ];
}

function retailzim_admin_api_key(): string
{
    return (string)retailzim_env('RETAILZIM_ADMIN_API_KEY', '');
}

function retailzim_db(): ?PDO
{
    static $pdo = null;
    static $failed = false;
    if ($failed) {
        return null;
    }
    if ($pdo instanceof PDO) {
        return $pdo;
    }

    $config = retailzim_db_config();
    $dsn = sprintf(
        'mysql:host=%s;port=%s;dbname=%s;charset=%s',
        $config['host'],
        $config['port'],
        $config['name'],
        $config['charset']
    );

    try {
        $pdo = new PDO($dsn, $config['user'], $config['pass'], [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false,
        ]);
        retailzim_ensure_schema($pdo);
        return $pdo;
    } catch (Throwable $error) {
        error_log('Retail Zim database connection failed: ' . $error->getMessage());
        $failed = true;
        return null;
    }
}

function retailzim_ensure_schema(PDO $db): void
{
    $db->exec("CREATE TABLE IF NOT EXISTS site_visits (
        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
        page VARCHAR(120) NOT NULL,
        section VARCHAR(120) NULL,
        visitor_hash CHAR(64) NULL,
        user_agent VARCHAR(255) NULL,
        referrer VARCHAR(255) NULL,
        ip_address VARCHAR(64) NULL,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (id),
        KEY idx_site_visits_page_created (page, created_at),
        KEY idx_site_visits_visitor (visitor_hash)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    $db->exec("CREATE TABLE IF NOT EXISTS community_posts (
        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
        name VARCHAR(80) NOT NULL,
        shop VARCHAR(80) NULL,
        category VARCHAR(40) NOT NULL DEFAULT 'General',
        message TEXT NOT NULL,
        status VARCHAR(24) NOT NULL DEFAULT 'open',
        likes INT UNSIGNED NOT NULL DEFAULT 0,
        replies INT UNSIGNED NOT NULL DEFAULT 0,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
        PRIMARY KEY (id),
        KEY idx_community_posts_created (created_at),
        KEY idx_community_posts_category (category),
        KEY idx_community_posts_status (status)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    $db->exec("CREATE TABLE IF NOT EXISTS community_engagements (
        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
        post_id BIGINT UNSIGNED NOT NULL,
        action ENUM('like', 'reply') NOT NULL DEFAULT 'like',
        visitor_hash CHAR(64) NULL,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (id),
        KEY idx_community_engagements_post (post_id),
        KEY idx_community_engagements_visitor (visitor_hash),
        CONSTRAINT fk_community_engagements_post
            FOREIGN KEY (post_id) REFERENCES community_posts(id)
            ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    $db->exec("CREATE TABLE IF NOT EXISTS community_answers (
        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
        post_id BIGINT UNSIGNED NOT NULL,
        responder VARCHAR(80) NOT NULL DEFAULT 'Retail Zim Support',
        answer TEXT NOT NULL,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
        PRIMARY KEY (id),
        KEY idx_community_answers_post (post_id),
        CONSTRAINT fk_community_answers_post
            FOREIGN KEY (post_id) REFERENCES community_posts(id)
            ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
}

function retailzim_now(): string
{
    return gmdate('Y-m-d H:i:s');
}

function retailzim_visitor_hash(): string
{
    $source = ($_SERVER['REMOTE_ADDR'] ?? 'local') . '|' . ($_SERVER['HTTP_USER_AGENT'] ?? 'unknown');
    return hash('sha256', $source);
}

function retailzim_limit_string(?string $value, int $length): string
{
    return substr(trim((string)$value), 0, $length);
}

function retailzim_allowed_category(string $category): string
{
    $category = retailzim_limit_string($category, 40);
    $allowed = ['Question', 'Guide', 'Feature request', 'Payment', 'Support', 'Receipts', 'Products', 'Stock control', 'General'];
    return in_array($category, $allowed, true) ? $category : 'General';
}

function retailzim_record_visit(string $page = 'home', ?string $section = null): void
{
    $db = retailzim_db();
    if (!$db) {
        return;
    }
    $stmt = $db->prepare('INSERT INTO site_visits (page, section, visitor_hash, user_agent, referrer, ip_address, created_at)
        VALUES (:page, :section, :visitor_hash, :user_agent, :referrer, :ip_address, :created_at)');
    $stmt->execute([
        ':page' => retailzim_limit_string($page, 120),
        ':section' => $section ? retailzim_limit_string($section, 120) : null,
        ':visitor_hash' => retailzim_visitor_hash(),
        ':user_agent' => retailzim_limit_string($_SERVER['HTTP_USER_AGENT'] ?? '', 255),
        ':referrer' => retailzim_limit_string($_SERVER['HTTP_REFERER'] ?? '', 255),
        ':ip_address' => retailzim_limit_string($_SERVER['REMOTE_ADDR'] ?? '', 64),
        ':created_at' => retailzim_now(),
    ]);
}

function retailzim_seed_posts(): array
{
    return [
        ['id' => 0, 'name' => 'Retail Zim Team', 'shop' => 'Platform', 'category' => 'Guide', 'message' => 'Start here: register your shop, import products from Excel, open a shift, sell, then close the shift report.', 'status' => 'featured', 'likes' => 18, 'replies' => 4, 'created_at' => retailzim_now()],
        ['id' => 0, 'name' => 'MSN Grocery', 'shop' => 'Harare', 'category' => 'Receipts', 'message' => 'How do I keep 80mm receipts centered on my thermal printer?', 'status' => 'answered', 'likes' => 12, 'replies' => 3, 'created_at' => retailzim_now()],
        ['id' => 0, 'name' => 'Tariro', 'shop' => 'Bottle Store', 'category' => 'Products', 'message' => 'Can I import product quantities, prices, and categories from one Excel document?', 'status' => 'solved', 'likes' => 9, 'replies' => 2, 'created_at' => retailzim_now()],
    ];
}

function retailzim_posts(int $limit = 6): array
{
    $db = retailzim_db();
    if (!$db) {
        return retailzim_seed_posts();
    }
    $stmt = $db->prepare('SELECT id, name, shop, category, message, status, likes, replies, created_at FROM community_posts ORDER BY id DESC LIMIT :limit');
    $stmt->bindValue(':limit', max(1, min($limit, 50)), PDO::PARAM_INT);
    $stmt->execute();
    $posts = $stmt->fetchAll();
    return $posts ?: retailzim_seed_posts();
}

function retailzim_posts_with_answers(int $limit = 50, ?string $status = null): array
{
    $db = retailzim_db();
    if (!$db) {
        return array_map(fn ($post) => $post + ['answers' => []], retailzim_seed_posts());
    }

    $sql = 'SELECT id, name, shop, category, message, status, likes, replies, created_at, updated_at
        FROM community_posts';
    $params = [];
    if ($status !== null && $status !== '') {
        $sql .= ' WHERE status = :status';
        $params[':status'] = retailzim_limit_string($status, 24);
    }
    $sql .= ' ORDER BY id DESC LIMIT :limit';

    $stmt = $db->prepare($sql);
    foreach ($params as $key => $value) {
        $stmt->bindValue($key, $value);
    }
    $stmt->bindValue(':limit', max(1, min($limit, 100)), PDO::PARAM_INT);
    $stmt->execute();
    $posts = $stmt->fetchAll();
    if (!$posts) {
        return [];
    }

    $ids = array_map('intval', array_column($posts, 'id'));
    $answersByPost = retailzim_answers_for_posts($ids);
    return array_map(function (array $post) use ($answersByPost): array {
        $post['answers'] = $answersByPost[(int)$post['id']] ?? [];
        return $post;
    }, $posts);
}

function retailzim_answers_for_posts(array $postIds): array
{
    $db = retailzim_db();
    $postIds = array_values(array_filter(array_map('intval', $postIds), fn ($id) => $id > 0));
    if (!$db || !$postIds) {
        return [];
    }

    $placeholders = implode(',', array_fill(0, count($postIds), '?'));
    $stmt = $db->prepare("SELECT id, post_id, responder, answer, created_at, updated_at
        FROM community_answers
        WHERE post_id IN ({$placeholders})
        ORDER BY id ASC");
    foreach ($postIds as $index => $postId) {
        $stmt->bindValue($index + 1, $postId, PDO::PARAM_INT);
    }
    $stmt->execute();

    $answers = [];
    foreach ($stmt->fetchAll() as $answer) {
        $answers[(int)$answer['post_id']][] = $answer;
    }
    return $answers;
}

function retailzim_create_post(array $data): bool
{
    $db = retailzim_db();
    if (!$db) {
        return false;
    }

    $name = retailzim_limit_string($data['name'] ?? '', 80);
    $message = retailzim_limit_string($data['message'] ?? '', 1200);
    if ($name === '' || $message === '') {
        return false;
    }

    $stmt = $db->prepare('INSERT INTO community_posts (name, shop, category, message, status, created_at)
        VALUES (:name, :shop, :category, :message, :status, :created_at)');
    return $stmt->execute([
        ':name' => $name,
        ':shop' => retailzim_limit_string($data['shop'] ?? '', 80),
        ':category' => retailzim_allowed_category($data['category'] ?? 'General'),
        ':message' => $message,
        ':status' => 'open',
        ':created_at' => retailzim_now(),
    ]);
}

function retailzim_engage(int $postId, string $action): bool
{
    $db = retailzim_db();
    if (!$db || $postId <= 0) {
        return false;
    }
    $action = in_array($action, ['like', 'reply'], true) ? $action : 'like';
    $column = $action === 'reply' ? 'replies' : 'likes';

    try {
        $db->beginTransaction();
        $stmt = $db->prepare('INSERT INTO community_engagements (post_id, action, visitor_hash, created_at)
            VALUES (:post_id, :action, :visitor_hash, :created_at)');
        $stmt->execute([
            ':post_id' => $postId,
            ':action' => $action,
            ':visitor_hash' => retailzim_visitor_hash(),
            ':created_at' => retailzim_now(),
        ]);
        $stmt = $db->prepare("UPDATE community_posts SET {$column} = {$column} + 1 WHERE id = :post_id");
        $stmt->execute([':post_id' => $postId]);
        return $db->commit();
    } catch (Throwable $error) {
        if ($db->inTransaction()) {
            $db->rollBack();
        }
        error_log('Retail Zim engagement failed: ' . $error->getMessage());
        return false;
    }
}

function retailzim_answer_post(int $postId, string $answer, string $responder = 'Retail Zim Support', string $status = 'answered'): bool
{
    $db = retailzim_db();
    $answer = retailzim_limit_string($answer, 4000);
    $responder = retailzim_limit_string($responder, 80);
    $status = retailzim_limit_string($status, 24);
    if (!$db || $postId <= 0 || $answer === '') {
        return false;
    }

    try {
        $db->beginTransaction();
        $stmt = $db->prepare('INSERT INTO community_answers (post_id, responder, answer, created_at)
            VALUES (:post_id, :responder, :answer, :created_at)');
        $stmt->execute([
            ':post_id' => $postId,
            ':responder' => $responder === '' ? 'Retail Zim Support' : $responder,
            ':answer' => $answer,
            ':created_at' => retailzim_now(),
        ]);
        $stmt = $db->prepare('UPDATE community_posts
            SET status = :status, replies = replies + 1, updated_at = :updated_at
            WHERE id = :post_id');
        $stmt->execute([
            ':status' => $status === '' ? 'answered' : $status,
            ':updated_at' => retailzim_now(),
            ':post_id' => $postId,
        ]);
        return $db->commit();
    } catch (Throwable $error) {
        if ($db->inTransaction()) {
            $db->rollBack();
        }
        error_log('Retail Zim answer failed: ' . $error->getMessage());
        return false;
    }
}

function retailzim_metrics(): array
{
    $db = retailzim_db();
    if (!$db) {
        return ['visits' => 0, 'unique_visitors' => 0, 'posts' => 0, 'engagements' => 0];
    }
    return [
        'visits' => (int)$db->query('SELECT COUNT(*) FROM site_visits')->fetchColumn(),
        'unique_visitors' => (int)$db->query('SELECT COUNT(DISTINCT visitor_hash) FROM site_visits')->fetchColumn(),
        'posts' => (int)$db->query('SELECT COUNT(*) FROM community_posts')->fetchColumn(),
        'engagements' => (int)$db->query('SELECT COUNT(*) FROM community_engagements')->fetchColumn(),
    ];
}

function retailzim_visit_stats(int $days = 30): array
{
    $db = retailzim_db();
    if (!$db) {
        return [
            'metrics' => retailzim_metrics(),
            'by_page' => [],
            'daily' => [],
            'recent' => [],
        ];
    }

    $days = max(1, min($days, 365));
    $since = gmdate('Y-m-d H:i:s', time() - ($days * 86400));

    $stmt = $db->prepare('SELECT page, COUNT(*) visits, COUNT(DISTINCT visitor_hash) unique_visitors
        FROM site_visits
        WHERE created_at >= :since
        GROUP BY page
        ORDER BY visits DESC');
    $stmt->execute([':since' => $since]);
    $byPage = $stmt->fetchAll();

    $stmt = $db->prepare('SELECT DATE(created_at) visit_date, COUNT(*) visits, COUNT(DISTINCT visitor_hash) unique_visitors
        FROM site_visits
        WHERE created_at >= :since
        GROUP BY DATE(created_at)
        ORDER BY visit_date ASC');
    $stmt->execute([':since' => $since]);
    $daily = $stmt->fetchAll();

    $stmt = $db->query('SELECT page, section, referrer, user_agent, created_at
        FROM site_visits
        ORDER BY id DESC
        LIMIT 25');

    return [
        'metrics' => retailzim_metrics(),
        'by_page' => $byPage,
        'daily' => $daily,
        'recent' => $stmt->fetchAll(),
    ];
}

function retailzim_json_response(array $payload, int $status = 200): void
{
    http_response_code($status);
    header('Content-Type: application/json; charset=utf-8');
    header('Access-Control-Allow-Origin: *');
    header('Access-Control-Allow-Headers: Content-Type, X-RetailZim-Admin-Key');
    header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
    echo json_encode($payload, JSON_UNESCAPED_SLASHES);
}

function retailzim_request_data(): array
{
    $contentType = $_SERVER['CONTENT_TYPE'] ?? '';
    if (stripos($contentType, 'application/json') !== false) {
        $data = json_decode(file_get_contents('php://input'), true);
        return is_array($data) ? $data : [];
    }
    return $_POST;
}

function retailzim_authorized_admin_api(): bool
{
    $expected = retailzim_admin_api_key();
    if ($expected === '') {
        return true;
    }
    $provided = $_SERVER['HTTP_X_RETAILZIM_ADMIN_KEY'] ?? '';
    return hash_equals($expected, $provided);
}
?>
