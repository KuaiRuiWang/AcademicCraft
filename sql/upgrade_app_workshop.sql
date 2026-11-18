-- 存量库执行一次。原项目与订单保留，已有上架项目转为待核对的草稿。
ALTER TABLE project
    ADD COLUMN sourceAttribution varchar(500) NULL COMMENT '模板来源或授权方' AFTER sourceUrl,
    ADD COLUMN licenseTerms varchar(1000) NULL COMMENT '许可名称、商用和再分发条件' AFTER sourceAttribution;

UPDATE project
SET status = 0
WHERE isDelete = 0 AND status = 1 AND sourceAttribution IS NULL;
