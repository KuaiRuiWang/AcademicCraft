-- 存量数据库执行一次；不会修改已有应用与对话数据。
ALTER TABLE app ADD COLUMN generateThesis tinyint NOT NULL DEFAULT 0 COMMENT '自动生成论文初稿';
ALTER TABLE app ADD COLUMN thesisTitle varchar(200) NULL COMMENT '论文题目';

CREATE TABLE IF NOT EXISTS app_thesis (
    appId bigint NOT NULL PRIMARY KEY,
    title varchar(200) NOT NULL,
    status varchar(24) NOT NULL COMMENT 'pending/generating/ready/failed/stale',
    stage varchar(100) NOT NULL,
    revision varchar(36) NOT NULL,
    sourceVersion varchar(64) NULL,
    sourceContext mediumtext NULL,
    errorMessage varchar(1000) NULL,
    createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '应用论文任务' COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS app_thesis_chapter (
    id bigint NOT NULL PRIMARY KEY,
    appId bigint NOT NULL,
    chapterNo int NOT NULL,
    title varchar(100) NOT NULL,
    content mediumtext NULL,
    status varchar(24) NOT NULL COMMENT 'pending/generating/ready/failed',
    sourceVersion varchar(64) NULL,
    userEdited tinyint NOT NULL DEFAULT 0,
    errorMessage varchar(1000) NULL,
    UNIQUE KEY uk_app_chapter (appId, chapterNo)
) COMMENT '应用论文章节' COLLATE = utf8mb4_unicode_ci;
