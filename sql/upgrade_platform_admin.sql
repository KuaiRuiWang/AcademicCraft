CREATE TABLE IF NOT EXISTS platform_admin_role (
    id bigint NOT NULL PRIMARY KEY,
    name varchar(80) NOT NULL,
    permissions varchar(2048) NOT NULL DEFAULT '',
    createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_platform_admin_role_name (name)
) COMMENT '平台管理端角色' COLLATE = utf8mb4_unicode_ci;

INSERT IGNORE INTO platform_admin_role (id, name, permissions) VALUES (1, '超级管理员', '');

CREATE TABLE IF NOT EXISTS platform_admin (
    id bigint NOT NULL PRIMARY KEY,
    username varchar(64) NOT NULL,
    displayName varchar(80) NOT NULL,
    passwordHash varchar(100) NOT NULL,
    roleId bigint NOT NULL,
    enabled tinyint NOT NULL DEFAULT 1,
    sessionVersion int NOT NULL DEFAULT 1,
    createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_platform_admin_username (username),
    CONSTRAINT fk_platform_admin_role FOREIGN KEY (roleId) REFERENCES platform_admin_role (id)
) COMMENT '独立管理端账号' COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS platform_model (
    id bigint NOT NULL PRIMARY KEY,
    name varchar(100) NOT NULL,
    protocol varchar(32) NOT NULL,
    baseUrl varchar(512) NOT NULL,
    modelId varchar(180) NOT NULL,
    apiKeyCiphertext text NOT NULL,
    maxTokens int NULL,
    temperature decimal(4, 3) NULL,
    createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_platform_model_name (name)
) COMMENT '平台模型及加密凭据' COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS platform_model_binding (
    stepKey varchar(80) NOT NULL PRIMARY KEY,
    modelConfigId bigint NOT NULL,
    updateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_platform_binding_model FOREIGN KEY (modelConfigId) REFERENCES platform_model (id)
) COMMENT 'AI 步骤与模型绑定' COLLATE = utf8mb4_unicode_ci;

ALTER TABLE project
    MODIFY authorId bigint NULL COMMENT '前台作者 ID',
    ADD COLUMN adminAuthorId bigint NULL COMMENT '后台发布管理员 ID';

ALTER TABLE project_order MODIFY sellerId bigint NULL COMMENT '前台作者 ID，平台发布的模板为空';
