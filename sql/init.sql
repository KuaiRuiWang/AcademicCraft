-- 用户表
create table user
(
    id            bigint auto_increment comment 'id'
        primary key,
    userAccount   varchar(256)                           not null comment '账号',
    userPassword  varchar(512)                           not null comment '密码',
    userName      varchar(256)                           null comment '用户昵称',
    userAvatar    varchar(1024)                          null comment '用户头像',
    userProfile   varchar(512)                           null comment '用户简介',
    userRole      varchar(256) default 'user'            not null comment '用户角色：user/ admin',
    vipExpireTime datetime                               null comment '会员过期时间',
    vipCode       varchar(128)                           null comment '会员兑换码',
    vipNumber     bigint                                 null comment '会员编号',
    editTime      datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime    datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime    datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete      tinyint      default 0                 not null comment '是否删除',
    constraint uk_userAccount unique (userAccount)
)comment '用户' collate = utf8mb4_unicode_ci;

create index idx_userName
    on user (userName);
-- 应用表
create table app
(
    id              bigint auto_increment comment 'id' primary key,
    appName         varchar(256)                        null comment '应用名称',
    cover           varchar(512)                        null comment '应用封面',
    initPrompt      text                                null comment '应用初始化的 prompt',
    codeGenType     varchar(64)                         null comment '代码生成类型（枚举）',
    deployKey       varchar(64)                         null comment '部署标识',
    deployedTime    datetime                            null comment '部署时间',
    generateBackend tinyint      default 0              not null comment '是否同步生成 Node.js 后端（0-否 1-是）',
    generateThesis  tinyint      default 0              not null comment '自动生成论文初稿',
    thesisTitle     varchar(200)                        null comment '论文题目',
    nodePort        int                                 null comment '全栈应用部署宿主端口',
    containerName   varchar(64)                         null comment '全栈应用 Docker 容器名',
    containerStatus varchar(16)                         null comment '容器状态：running/stopped/failed',
    priority        int          default 0              not null comment '优先级',
    userId          bigint                              not null comment '创建用户id',
    editTime        datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime      datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime      datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete        tinyint      default 0              not null comment '是否删除',
    UNIQUE KEY uk_deployKey (deployKey), -- 确保部署标识唯一
    INDEX idx_appName (appName),         -- 提升基于应用名称的查询性能
    INDEX idx_userId (userId)            -- 提升基于用户 ID 的查询性能
) comment '应用' collate = utf8mb4_unicode_ci;

-- 应用论文任务与章节
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

-- 对话历史表
create table chat_history
(
    id          bigint auto_increment comment 'id' primary key,
    message     mediumtext                         not null comment '消息',
    renderData  mediumtext                         null comment 'AG-UI 展示快照，不参与模型上下文',
    messageType varchar(32)                        not null comment 'user/ai',
    appId       bigint                             not null comment '应用id',
    userId      bigint                             not null comment '创建用户id',
    createTime  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete    tinyint  default 0                 not null comment '是否删除',
    INDEX idx_appId (appId),                       -- 提升基于应用的查询性能
    INDEX idx_createTime (createTime),             -- 提升基于时间的查询性能
    INDEX idx_appId_createTime (appId, createTime) -- 游标查询核心索引
) comment '对话历史' collate = utf8mb4_unicode_ci;

-- 项目分类表（管理员维护的固定技术栈分类）
create table project_category
(
    id          bigint auto_increment comment 'id' primary key,
    name        varchar(50)                        not null comment '分类名称',
    icon        varchar(255)                       null comment '分类图标URL',
    sortOrder   int      default 0                 not null comment '排序权重',
    createTime  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete    tinyint  default 0                 not null comment '是否删除'
) comment '项目分类' collate = utf8mb4_unicode_ci;

-- 项目商品表
create table project
(
    id            bigint auto_increment comment 'id' primary key,
    name          varchar(100)                          not null comment '项目名称',
    categoryId    bigint                                not null comment '分类ID',
    techTags      varchar(500)                          null comment '自定义技术标签（JSON数组）',
    description   varchar(500)                          null comment '项目简介',
    detailContent mediumtext                            null comment '项目详情（富文本HTML）',
    coverImage    varchar(500)                          null comment '封面图URL',
    price         decimal(10,2) default 0.00            not null comment '价格',
    sourceType    tinyint      default 1                not null comment '交付方式：1-ZIP下载 2-网盘链接 3-两者都有',
    sourceUrl     varchar(500)                          null comment '源码地址',
    sourceAttribution varchar(500)                      null comment '模板来源或授权方',
    licenseTerms  varchar(1000)                         null comment '许可名称、商用和再分发条件',
    demoUrl       varchar(500)                          null comment '在线演示地址',
    authorId      bigint                                not null comment '卖家用户ID',
    status        tinyint      default 0                not null comment '状态：0-草稿 1-已发布 2-已下架',
    auditReason   varchar(500)                          null comment 'AI审核原因（字段保留）',
    salesCount    int          default 0                not null comment '销量',
    viewCount     int          default 0                not null comment '浏览量',
    avgRating     decimal(2,1) default 0.0              not null comment '平均评分',
    editTime      datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime    datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime    datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete      tinyint      default 0                not null comment '是否删除',
    INDEX idx_categoryId (categoryId),
    INDEX idx_authorId (authorId),
    INDEX idx_status (status),
    INDEX idx_name (name)
) comment '项目商品' collate = utf8mb4_unicode_ci;

-- 项目介绍图片表
create table project_image
(
    id          bigint auto_increment comment 'id' primary key,
    projectId   bigint                             not null comment '项目ID',
    imageUrl    varchar(500)                       not null comment '图片URL',
    sortOrder   int      default 0                 not null comment '排序',
    type        tinyint  default 0                 not null comment '类型：0-截图预览 1-详情介绍图',
    createTime  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    isDelete    tinyint  default 0                 not null comment '是否删除',
    INDEX idx_projectId (projectId)
) comment '项目图片' collate = utf8mb4_unicode_ci;

-- 购买记录表
create table project_order
(
    id          bigint auto_increment comment 'id' primary key,
    projectId   bigint                                not null comment '项目ID',
    buyerId     bigint                                not null comment '购买者ID',
    sellerId    bigint                                not null comment '卖家ID',
    amount      decimal(10,2)                         not null comment '金额',
    status      tinyint      default 0                not null comment '状态：0-待支付 1-已支付 2-已退款',
    payTime     datetime                              null comment '支付时间',
    createTime  datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime  datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete    tinyint      default 0                not null comment '是否删除',
    INDEX idx_buyerId (buyerId),
    INDEX idx_projectId (projectId),
    INDEX idx_sellerId (sellerId)
) comment '购买记录' collate = utf8mb4_unicode_ci;

-- 项目评价表
create table project_review
(
    id          bigint auto_increment comment 'id' primary key,
    projectId   bigint                             not null comment '项目ID',
    userId      bigint                             not null comment '评价用户ID',
    orderId     bigint                             not null comment '关联订单ID',
    rating      tinyint                            not null comment '评分：1-5',
    content     text                               null comment '评价内容',
    createTime  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    isDelete    tinyint  default 0                 not null comment '是否删除',
    INDEX idx_projectId (projectId),
    INDEX idx_userId (userId),
    UNIQUE KEY uk_orderId (orderId)
) comment '项目评价' collate = utf8mb4_unicode_ci;
