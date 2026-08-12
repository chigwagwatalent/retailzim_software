<?php
require_once __DIR__ . '/lib/site-content.php';
retailzim_record_visit('community');
$posts = retailzim_posts_with_answers(20);
rz_header('Retail Zim Community | Questions, Comments and Product Help', 'Join the Retail Zim community to post questions, comment on discussions, react to posts, read guides, and get product support.', 'community');

function rz_initials(string $name): string {
    $parts = preg_split('/\s+/', trim($name));
    $first = strtoupper(substr($parts[0] ?? 'R', 0, 1));
    $second = strtoupper(substr($parts[1] ?? 'Z', 0, 1));
    return $first . $second;
}
?>
<main class="community-page">
  <section class="community-hero">
    <div class="community-hero-copy"><span class="eyebrow">Retail Zim Community</span><h1>Shop owners helping shop owners.</h1><p>Share questions, comment on real retail discussions, follow product updates, and get practical answers from the Retail Zim support team.</p><div class="community-pills"><span><i class="fa-solid fa-comments"></i> Discussions</span><span><i class="fa-solid fa-circle-check"></i> Solved answers</span><span><i class="fa-solid fa-book-open"></i> Guides</span><span><i class="fa-solid fa-lightbulb"></i> Ideas</span></div></div>
    <div class="community-hero-card"><div class="community-hero-avatars"><span>RZ</span><span>TM</span><span>MG</span><span>KH</span></div><strong>One connected retail community</strong><p>Ask clearly. Learn quickly. Run your shop better.</p><a href="#create-post">Start a discussion <i class="fa-solid fa-arrow-down"></i></a></div>
  </section>

  <section class="community-layout">
    <aside class="community-left">
      <article class="community-panel community-profile"><div class="community-avatar large-avatar">RZ</div><h2>Retail Zim Community</h2><p>A practical space for customers, retailers and support.</p><div><span><strong><?= count($posts) ?></strong> discussions</span><span><strong>6</strong> topics</span></div></article>
      <nav class="community-panel topic-nav" aria-label="Community topics"><h2>Explore</h2><button class="active" type="button" data-feed-filter="all"><i class="fa-solid fa-house"></i>All discussions</button><button type="button" data-feed-filter="Question"><i class="fa-solid fa-circle-question"></i>Questions</button><button type="button" data-feed-filter="Stock control"><i class="fa-solid fa-boxes-stacked"></i>Stock control</button><button type="button" data-feed-filter="Payment"><i class="fa-solid fa-wallet"></i>Payments</button><button type="button" data-feed-filter="Guide"><i class="fa-solid fa-book-open"></i>Guides</button><button type="button" data-feed-filter="Feature request"><i class="fa-solid fa-lightbulb"></i>Feature ideas</button></nav>
    </aside>

    <section class="community-main" id="community-feed">
      <form class="community-composer" id="create-post" action="<?= htmlspecialchars(rz_url('community-post')) ?>" method="post" data-community-composer>
        <div class="composer-row"><div class="community-avatar">YOU</div><button type="button" class="composer-prompt" data-composer-expand>What would you like to ask the community?</button></div>
        <div class="composer-fields" data-composer-fields>
          <textarea name="message" required maxlength="1200" placeholder="Share a question, tip, idea or update..."></textarea>
          <div><input name="name" required maxlength="80" placeholder="Your name"><input name="shop" maxlength="80" placeholder="Shop name or city"><select name="category"><option>Question</option><option>Stock control</option><option>Payment</option><option>Receipts</option><option>Products</option><option>Guide</option><option>Feature request</option><option>Support</option></select></div>
          <footer><span><i class="fa-solid fa-image"></i> Screenshots can be described in your post</span><button class="btn btn-primary" type="submit">Post discussion <i class="fa-solid fa-paper-plane"></i></button></footer>
        </div>
      </form>

      <div class="community-tabs"><button class="active" type="button" data-feed-sort="latest">Latest</button><button type="button" data-feed-sort="popular">Popular</button><button type="button" data-feed-filter="answered">Answered</button></div>

      <div class="community-feed community-feed-rich">
        <?php foreach ($posts as $post): ?>
          <?php $postId = (int)$post['id']; $postCategory = (string)$post['category']; ?>
          <article class="community-post" data-community-post data-category="<?= htmlspecialchars($postCategory) ?>" data-status="<?= htmlspecialchars((string)$post['status']) ?>" data-likes="<?= (int)$post['likes'] ?>">
            <header><div class="member"><div class="community-avatar"><?= htmlspecialchars(rz_initials((string)$post['name'])) ?></div><span><b><?= htmlspecialchars((string)$post['name']) ?></b><small><?= htmlspecialchars((string)($post['shop'] ?: $postCategory)) ?> · <?= htmlspecialchars(date('j M Y', strtotime((string)$post['created_at']))) ?></small></span></div><em class="status <?= in_array($post['status'], ['answered','solved'], true) ? 'answered' : '' ?>"><?= htmlspecialchars(ucfirst((string)$post['status'])) ?></em></header>
            <span class="post-category"><?= htmlspecialchars($postCategory) ?></span>
            <p class="post-message"><?= nl2br(htmlspecialchars((string)$post['message'])) ?></p>

            <?php if (!empty($post['answers'])): ?>
              <div class="support-answer-list">
                <?php foreach ($post['answers'] as $answer): ?>
                  <div class="support-answer"><div class="community-avatar support-avatar"><i class="fa-solid fa-headset"></i></div><div><b><?= htmlspecialchars((string)$answer['responder']) ?> <span><i class="fa-solid fa-circle-check"></i> Official answer</span></b><p><?= nl2br(htmlspecialchars((string)$answer['answer'])) ?></p></div></div>
                <?php endforeach; ?>
              </div>
            <?php endif; ?>

            <div class="engagement-summary"><span data-like-summary><i class="fa-solid fa-thumbs-up"></i> <?= (int)$post['likes'] ?></span><span data-comment-summary><?= (int)$post['replies'] ?> comments</span></div>
            <div class="post-actions">
              <button type="button" data-engage-post="<?= $postId ?>" data-action="like"><i class="fa-regular fa-thumbs-up"></i><span>Like</span></button>
              <button type="button" data-comment-toggle><i class="fa-regular fa-comment"></i><span>Comment</span></button>
              <button type="button" data-share-post><i class="fa-solid fa-share"></i><span>Share</span></button>
            </div>

            <div class="comment-thread" data-comment-thread>
              <?php foreach (($post['comments'] ?? []) as $comment): ?>
                <div class="community-comment"><div class="community-avatar small-avatar"><?= htmlspecialchars(rz_initials((string)$comment['name'])) ?></div><div><b><?= htmlspecialchars((string)$comment['name']) ?></b><p><?= nl2br(htmlspecialchars((string)$comment['comment'])) ?></p><small><?= htmlspecialchars(date('j M · H:i', strtotime((string)$comment['created_at']))) ?></small></div></div>
              <?php endforeach; ?>
            </div>
            <form class="comment-form" data-comment-form data-demo="<?= $postId > 0 ? 'false' : 'true' ?>">
              <input type="hidden" name="post_id" value="<?= $postId ?>"><input name="name" required maxlength="80" placeholder="Your name"><div><input name="comment" required maxlength="1200" placeholder="Write a comment..."><button type="submit" aria-label="Post comment"><i class="fa-solid fa-paper-plane"></i></button></div><small data-comment-message></small>
            </form>
          </article>
        <?php endforeach; ?>
      </div>
      <div class="feed-empty" hidden data-feed-empty><i class="fa-solid fa-comments"></i><h2>No discussions in this view yet.</h2><p>Start a new post and help build the conversation.</p></div>
    </section>

    <aside class="community-right">
      <article class="community-panel community-news"><span class="section-kicker">Product news</span><i class="fa-solid fa-laptop-code"></i><h2>Windows POS and mobile selling work together.</h2><p>Use one shop account across the counter, phone and management dashboard.</p><a href="<?= htmlspecialchars(rz_url('downloads')) ?>">View downloads <i class="fa-solid fa-arrow-right"></i></a></article>
      <article class="community-panel"><h2>Popular guides</h2><div class="guide-list"><a href="<?= htmlspecialchars(rz_url('resources')) ?>"><b>01</b><span><strong>Import products</strong><small>Excel setup</small></span></a><a href="<?= htmlspecialchars(rz_url('how-it-works')) ?>"><b>02</b><span><strong>Run a shift</strong><small>Cashier workflow</small></span></a><a href="<?= htmlspecialchars(rz_url('payments')) ?>"><b>03</b><span><strong>Set up payments</strong><small>Cash, card and mobile money</small></span></a></div></article>
      <article class="community-panel community-rules"><h2>Community values</h2><p><i class="fa-solid fa-check"></i> Be practical and respectful</p><p><i class="fa-solid fa-check"></i> Protect customer information</p><p><i class="fa-solid fa-check"></i> Share enough detail to help</p></article>
    </aside>
  </section>
</main>
<?php rz_footer(); ?>
