<?php
require_once __DIR__ . '/lib/site-db.php';
$metrics = retailzim_metrics();
$posts = retailzim_posts(12);
?>
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Retail Zim Site Analytics</title>
  <link rel="icon" type="image/png" href="img/favicon.png">
  <link rel="shortcut icon" href="img/favicon.png">
  <link rel="stylesheet" href="css/retailzim-front.css?v=9">
</head>
<body>
  <main class="analytics-page">
    <a class="btn btn-light" href="./">Back to website</a>
    <h1>Website Analytics</h1>
    <p>MySQL-backed visits, community posts, and engagement for the Retail Zim front website.</p>
    <div class="metric-grid">
      <article><strong><?= (int)$metrics['visits'] ?></strong><span>Total visits</span></article>
      <article><strong><?= (int)$metrics['unique_visitors'] ?></strong><span>Unique visitors</span></article>
      <article><strong><?= (int)$metrics['posts'] ?></strong><span>Community posts</span></article>
      <article><strong><?= (int)$metrics['engagements'] ?></strong><span>Engagements</span></article>
    </div>
    <section class="analytics-list">
      <h2>Recent Community Posts</h2>
      <?php foreach ($posts as $post): ?>
        <article>
          <b><?= htmlspecialchars($post['category']) ?></b>
          <h3><?= htmlspecialchars($post['name']) ?><?= !empty($post['shop']) ? ' - ' . htmlspecialchars($post['shop']) : '' ?></h3>
          <p><?= htmlspecialchars($post['message']) ?></p>
          <small><?= (int)$post['likes'] ?> likes, <?= (int)$post['replies'] ?> replies</small>
        </article>
      <?php endforeach; ?>
    </section>
  </main>
</body>
</html>
