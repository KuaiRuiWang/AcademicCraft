-- 对话历史消息字段扩容 - 存量环境升级脚本
-- 日期：2026-09-14
-- 适用：已按旧版 init.sql 建库的环境（开发库 / 生产库）
-- 说明：新环境直接用 init.sql 建库即可，无需执行本脚本
-- 关联：任务文档 T-18 端到端联调用例 7
--
-- 背景：message 原为 text（上限 65535 字节），AI 单次回复超出即触发
--       MySQL「Data too long for column 'message'」，异常沿 SSE 流抛出，
--       导致流中断、done 事件不送达、AI 回复丢失（实测一次纯 Vue 生成回复
--       达 141042 字节）。改为 mediumtext（上限 16777215 字节）。
--       纯新增字段类型变更，不改列名与数据，存量数据不受影响。

ALTER TABLE chat_history
    MODIFY COLUMN message mediumtext NOT NULL COMMENT '消息';
