-- 全栈项目生成模块 - 存量环境升级脚本
-- 日期：2026-09-13
-- 适用：已按旧版 init.sql 建库的环境（开发库 / 生产库）
-- 说明：新环境直接用 init.sql 建库即可，无需执行本脚本
-- 关联：设计文档第 4.2 节 / 任务文档 T-02

ALTER TABLE app
    ADD COLUMN generateBackend tinyint  default 0 not null comment '是否同步生成 Node.js 后端（0-否 1-是）' AFTER deployedTime,
    ADD COLUMN nodePort        int                 null comment '全栈应用部署宿主端口' AFTER generateBackend,
    ADD COLUMN containerName   varchar(64)         null comment '全栈应用 Docker 容器名' AFTER nodePort,
    ADD COLUMN containerStatus varchar(16)         null comment '容器状态：running/stopped/failed' AFTER containerName;
