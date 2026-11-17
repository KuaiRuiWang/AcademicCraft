-- AG-UI 智能体分组对话：存量数据库部署新版后端前执行一次。
-- 新建数据库直接使用 init.sql；旧消息的 renderData 为空，继续按 Markdown 展示。
ALTER TABLE chat_history
    ADD COLUMN renderData MEDIUMTEXT NULL COMMENT 'AG-UI 展示快照，不参与模型上下文' AFTER message;
