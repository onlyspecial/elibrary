CREATE TABLE IF NOT EXISTS `user` (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  username      VARCHAR(64)  NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  role          VARCHAR(32)  NOT NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS physical_work (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  title         VARCHAR(255) NOT NULL,
  author        VARCHAR(255) NOT NULL,
  content_type  VARCHAR(32)  NOT NULL,
  description   TEXT,
  isbn_or_issn  VARCHAR(64),
  deleted       TINYINT(1)   NOT NULL DEFAULT 0,
  created_by    BIGINT,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_physical_work_title (title),
  KEY idx_physical_work_author (author)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS physical_copy (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  work_id    BIGINT       NOT NULL,
  copy_no    VARCHAR(64)  NOT NULL,
  status     VARCHAR(32)  NOT NULL,
  deleted    TINYINT(1)   NOT NULL DEFAULT 0,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_work_copy (work_id, copy_no),
  KEY idx_work_status (work_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS borrow_record (
  id               BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id          BIGINT   NOT NULL,
  physical_work_id BIGINT   NOT NULL,
  physical_copy_id BIGINT   NOT NULL,
  borrowed_at      DATETIME NOT NULL,
  returned_at      DATETIME NULL,
  KEY idx_user (user_id),
  KEY idx_copy_open (physical_copy_id, returned_at),
  KEY idx_work_returned_user (physical_work_id, returned_at, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS digital_item (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  title             VARCHAR(255) NOT NULL,
  author            VARCHAR(255) NOT NULL,
  content_type      VARCHAR(32)  NOT NULL,
  description       TEXT,
  isbn_or_issn      VARCHAR(64),
  pdf_path          VARCHAR(512) NOT NULL,
  pdf_original_name VARCHAR(255) NOT NULL,
  deleted           TINYINT(1)   NOT NULL DEFAULT 0,
  created_by        BIGINT,
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_digital_item_title (title),
  KEY idx_digital_item_author (author)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
