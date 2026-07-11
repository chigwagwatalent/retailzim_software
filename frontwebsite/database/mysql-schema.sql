CREATE DATABASE IF NOT EXISTS connecte_retail_comunity
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE connecte_retail_comunity;

CREATE TABLE IF NOT EXISTS site_visits (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS community_posts (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS community_engagements (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS community_answers (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO community_posts (name, shop, category, message, status, likes, replies)
SELECT 'Retail Zim Team', 'Platform', 'Guide',
  'Start here: register your shop, import products from Excel, open a shift, sell, then close the shift report.',
  'featured', 18, 4
WHERE NOT EXISTS (SELECT 1 FROM community_posts LIMIT 1);

INSERT INTO community_posts (name, shop, category, message, status, likes, replies)
SELECT 'MSN Grocery', 'Harare', 'Receipts',
  'How do I keep 80mm receipts centered on my thermal printer?',
  'answered', 12, 3
WHERE (SELECT COUNT(*) FROM community_posts) = 1;

INSERT INTO community_posts (name, shop, category, message, status, likes, replies)
SELECT 'Tariro', 'Bottle Store', 'Products',
  'Can I import product quantities, prices, and categories from one Excel document?',
  'solved', 9, 2
WHERE (SELECT COUNT(*) FROM community_posts) = 2;
