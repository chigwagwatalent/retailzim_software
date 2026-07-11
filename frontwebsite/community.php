<?php
require_once __DIR__ . '/lib/site-content.php';
retailzim_record_visit('community');
$posts = retailzim_posts_with_answers(8);
rz_header('Retail Zim Community | Questions, Guides and Product Help', 'Ask questions, share ideas, read guides, and follow Retail Zim product updates in the customer community.', 'community');
?>
<main>
  <section class="community-hero">
    <div class="community-hero-copy">
      <span class="eyebrow">Retail Zim Community</span>
      <h1>Learn retail faster with people using the same system.</h1>
      <p>Ask questions, follow product updates, read setup guides, and get practical answers for POS, stock, payments, receipts, and shift reports.</p>
      <div class="community-pills">
        <span>Questions</span>
        <span>Guides</span>
        <span>News</span>
        <span>Feature ideas</span>
        <span>Support answers</span>
      </div>
    </div>
    <div class="community-spotlight">
      <article>
        <span class="support-badge">Solved by Retail Zim Support</span>
        <h2>How do I import products from Excel?</h2>
        <p>Upload your product sheet, match columns, preview the changes, then confirm the import.</p>
        <div class="answer-preview"><b>Step-by-step answer</b><span>Download template -> fill products -> import -> review stock quantities.</span></div>
      </article>
    </div>
  </section>

  <section class="community-layout">
    <aside class="community-panel community-topics">
      <h2>Browse topics</h2>
      <a class="topic-card active" href="#community-feed"><i>?</i><span><b>Help questions</b><small>POS, setup, and daily use</small></span><strong>42</strong></a>
      <a class="topic-card" href="#community-feed"><i>$</i><span><b>Payments</b><small>Cash, card, mobile money</small></span><strong>8</strong></a>
      <a class="topic-card" href="#community-feed"><i>#</i><span><b>Stock control</b><small>Imports and quantities</small></span><strong>16</strong></a>
      <a class="topic-card" href="#community-feed"><i>R</i><span><b>Receipts</b><small>Thermal printer setup</small></span><strong>7</strong></a>
      <a class="topic-card" href="#community-feed"><i>N</i><span><b>News</b><small>Product updates</small></span><strong>5</strong></a>
    </aside>

    <section class="community-main" id="community-feed">
      <form class="community-composer" action="<?= htmlspecialchars(rz_url('community-post')) ?>" method="post">
        <div class="composer-row">
          <div class="community-avatar">RZ</div>
          <input name="message" required placeholder="Ask about your shop, POS, stock, payments, or reports...">
          <button class="btn btn-primary" type="submit">Post</button>
        </div>
        <div class="composer-more">
          <input name="name" required placeholder="Your name">
          <input name="shop" placeholder="Shop name or city">
          <select name="category">
            <option>Question</option>
            <option>Guide</option>
            <option>Feature request</option>
            <option>Payment</option>
            <option>Support</option>
          </select>
        </div>
        <div class="composer-actions"><span>Add screenshot</span><span>Choose category</span><span>Mark urgent</span></div>
      </form>

      <nav class="community-tabs" aria-label="Community filters">
        <a class="active" href="#community-feed">Featured</a>
        <a href="#community-feed">Latest</a>
        <a href="#community-feed">Answered</a>
        <a href="#community-guides">Guides</a>
        <a href="#community-feed">Ideas</a>
      </nav>

      <div class="community-feed community-feed-rich">
        <article class="community-post featured-post">
          <header>
            <div class="member"><div class="community-avatar">MG</div><span><b>MSN Grocery</b><small>Harare - 12 minutes ago</small></span></div>
            <em class="status answered">Answered</em>
          </header>
          <h2>My 80mm receipt is printing too far to the right. What should I check?</h2>
          <p>The prices are being cut on the right side of the paper when we print from the Windows POS.</p>
          <blockquote><b>Retail Zim Support:</b> Open printer settings, choose 80mm paper width, then use the Retail Zim receipt alignment option. Keep margins at zero and print a test receipt.</blockquote>
          <footer><span><button type="button">24 likes</button><button type="button">8 replies</button><button type="button">Follow</button></span><strong>Receipts</strong></footer>
        </article>

        <?php foreach ($posts as $post): ?>
          <article class="community-post">
            <header>
              <div class="member"><div class="community-avatar"><?= htmlspecialchars(strtoupper(substr($post['name'], 0, 1) . (!empty($post['shop']) ? substr($post['shop'], 0, 1) : ''))) ?></div><span><b><?= htmlspecialchars($post['name']) ?></b><small><?= !empty($post['shop']) ? htmlspecialchars($post['shop']) . ' - ' : '' ?>Community post</small></span></div>
              <em class="status"><?= htmlspecialchars($post['status']) ?></em>
            </header>
            <h2><?= htmlspecialchars($post['category']) ?></h2>
            <p><?= htmlspecialchars($post['message']) ?></p>
            <?php if (!empty($post['answers'])): ?>
              <?php foreach ($post['answers'] as $answer): ?>
                <blockquote><b><?= htmlspecialchars($answer['responder']) ?>:</b> <?= htmlspecialchars($answer['answer']) ?></blockquote>
              <?php endforeach; ?>
            <?php endif; ?>
            <footer><span><button type="button" data-engage-post="<?= (int)$post['id'] ?>" data-action="like"><?= (int)$post['likes'] ?> likes</button><button type="button" data-engage-post="<?= (int)$post['id'] ?>" data-action="reply"><?= (int)$post['replies'] ?> replies</button><button type="button">Follow</button></span><strong><?= htmlspecialchars($post['category']) ?></strong></footer>
          </article>
        <?php endforeach; ?>
      </div>
    </section>

    <aside class="community-side">
      <article class="community-news-card">
        <span class="eyebrow">Product news</span>
        <h2>Windows POS installer is ready</h2>
        <p>Download the latest POS build, install it on your counter machine, and connect your shop account.</p>
        <a class="btn btn-light" href="<?= htmlspecialchars(rz_url('downloads')) ?>">View downloads</a>
      </article>
      <article class="community-panel" id="community-guides">
        <h2>Popular guides</h2>
        <div class="guide-list">
          <a href="<?= htmlspecialchars(rz_url('how-it-works')) ?>"><b>1</b><span><strong>Open and close a shift</strong><small>Cashier daily workflow</small></span></a>
          <a href="<?= htmlspecialchars(rz_url('resources')) ?>"><b>2</b><span><strong>Import products</strong><small>Excel template setup</small></span></a>
          <a href="<?= htmlspecialchars(rz_url('payments')) ?>"><b>3</b><span><strong>Set up payments</strong><small>Cash, swipe, mobile money</small></span></a>
        </div>
      </article>
      <article class="community-panel">
        <h2>Active members</h2>
        <div class="guide-list">
          <a href="#community-feed"><b>RZ</b><span><strong>Retail Zim Support</strong><small>Official answers</small></span></a>
          <a href="#community-feed"><b>MG</b><span><strong>MSN Grocery</strong><small>Receipt setup</small></span></a>
          <a href="#community-feed"><b>TM</b><span><strong>Tariro Mini Mart</strong><small>Stock imports</small></span></a>
        </div>
      </article>
    </aside>
  </section>
</main>
<?php rz_footer(); ?>
