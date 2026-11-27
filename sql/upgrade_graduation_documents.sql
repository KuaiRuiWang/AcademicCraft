CREATE TABLE IF NOT EXISTS app_graduation_document (
    appId BIGINT NOT NULL,
    kind VARCHAR(16) NOT NULL,
    templateId VARCHAR(36),
    templateName VARCHAR(255),
    templateFields TEXT,
    templateFile MEDIUMBLOB,
    status VARCHAR(16) NOT NULL DEFAULT 'waiting',
    revision VARCHAR(36),
    errorMessage VARCHAR(255),
    resultTemplateName VARCHAR(255),
    sections MEDIUMTEXT,
    documentFile MEDIUMBLOB,
    PRIMARY KEY (appId, kind)
) COLLATE = utf8mb4_unicode_ci;
