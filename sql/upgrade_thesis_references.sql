-- 已经有论文表的数据库执行一次。
ALTER TABLE app_thesis ADD COLUMN referenceSnapshot mediumtext NULL COMMENT '按源码版本冻结的可核验文献';
