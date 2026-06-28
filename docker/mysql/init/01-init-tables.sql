-- ============================================================================
-- EICS 数据库初始化脚本
-- 由 MySQL 容器首次启动时自动执行
-- ============================================================================

-- 知识库文档主表
CREATE TABLE IF NOT EXISTS ek_document (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    title       VARCHAR(500)  NOT NULL COMMENT '文档标题',
    file_name   VARCHAR(500)  NOT NULL COMMENT '原始文件名',
    file_type   VARCHAR(50)   NOT NULL COMMENT '文件类型：pdf/docx/txt/md',
    file_size   BIGINT        DEFAULT 0 COMMENT '文件大小（字节）',
    minio_path  VARCHAR(1000) DEFAULT '' COMMENT 'MinIO存储路径',
    status      VARCHAR(20)   DEFAULT 'PENDING' COMMENT '状态：PENDING/PARSING/READY/FAILED',
    chunk_count INT           DEFAULT 0 COMMENT '切片总数',
    create_time DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT       DEFAULT 0 COMMENT '逻辑删除：0未删/1已删',
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档主表';

-- 文档切片向量表
CREATE TABLE IF NOT EXISTS ek_document_chunk (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    document_id BIGINT        NOT NULL COMMENT '所属文档ID',
    chunk_index INT           NOT NULL COMMENT '切片序号（从0开始）',
    content     MEDIUMTEXT    NOT NULL COMMENT '切片文本内容',
    char_count  INT           DEFAULT 0 COMMENT '切片字符数',
    milvus_id   BIGINT        DEFAULT NULL COMMENT 'Milvus向量ID',
    create_time DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_document_id (document_id),
    INDEX idx_milvus_id (milvus_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档切片向量表';

-- 聊天会话主表
CREATE TABLE IF NOT EXISTS cs_chat_session (
    id            VARCHAR(64)  PRIMARY KEY COMMENT '会话ID（UUID）',
    user_id       VARCHAR(100) DEFAULT '' COMMENT '用户标识',
    status        VARCHAR(20)  DEFAULT 'BOT' COMMENT '状态：BOT/WAITING/AGENT/CLOSED',
    agent_id      BIGINT       DEFAULT NULL COMMENT '分配坐席ID',
    rasa_sender_id VARCHAR(100) DEFAULT '' COMMENT 'Rasa sender_id',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    close_time    DATETIME     DEFAULT NULL COMMENT '关闭时间',
    INDEX idx_status (status),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天会话主表';

-- 聊天消息明细表
CREATE TABLE IF NOT EXISTS cs_chat_message (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    session_id  VARCHAR(64)   NOT NULL COMMENT '所属会话ID',
    sender_type VARCHAR(20)   NOT NULL COMMENT '发送方：USER/BOT/AGENT',
    content     MEDIUMTEXT    NOT NULL COMMENT '消息内容',
    msg_type    VARCHAR(20)   DEFAULT 'TEXT' COMMENT '消息类型：TEXT/IMAGE/FILE',
    metadata    TEXT          DEFAULT NULL COMMENT '附加元数据JSON',
    create_time DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_session_id (session_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息明细表';

-- 客服工单表
CREATE TABLE IF NOT EXISTS cs_service_order (
    id                VARCHAR(64)  PRIMARY KEY COMMENT '工单ID（UUID）',
    session_id        VARCHAR(64)  NOT NULL COMMENT '关联会话ID',
    phone             VARCHAR(20)  DEFAULT '' COMMENT '用户手机号',
    fault_description TEXT         NOT NULL COMMENT '故障描述',
    device_id         VARCHAR(200) DEFAULT '' COMMENT '设备编号',
    issue_type        VARCHAR(50)  DEFAULT 'OTHER' COMMENT '问题类型',
    status            VARCHAR(20)  DEFAULT 'PENDING' COMMENT '状态：PENDING/PROCESSING/RESOLVED/CLOSED',
    priority          VARCHAR(10)  DEFAULT 'P2' COMMENT '优先级：P0/P1/P2/P3',
    sla_deadline      DATETIME     DEFAULT NULL COMMENT 'SLA截止时间',
    first_response_time DATETIME   DEFAULT NULL COMMENT '首次响应时间（认领时记录）',
    agent_id          BIGINT       DEFAULT NULL COMMENT '受理坐席ID',
    create_time       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    resolve_time      DATETIME     DEFAULT NULL COMMENT '解决时间',
    INDEX idx_session_id (session_id),
    INDEX idx_status (status),
    INDEX idx_priority (priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客服工单表';

-- 人工坐席表
-- 系统用户表（统一管理普通用户/坐席/管理员）
CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username        VARCHAR(50)   NOT NULL COMMENT '登录用户名',
    password_hash   VARCHAR(255)  NOT NULL COMMENT 'BCrypt 密码哈希',
    name            VARCHAR(100)  DEFAULT '' COMMENT '姓名',
    phone           VARCHAR(20)   DEFAULT '' COMMENT '手机号',
    role            VARCHAR(20)   DEFAULT 'USER' COMMENT '角色：USER/AGENT/ADMIN',
    status          VARCHAR(20)   DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
    create_time     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX idx_username (username),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- 种子数据：默认管理员
INSERT INTO sys_user (username, password_hash, name, phone, role, status)
VALUES ('admin', '$2b$10$4.yc8UZ.ubEbGJIz6Fbl5uxRujsuxaKhakEib9Ve3sVsWYib.ljD2', '管理员', '', 'ADMIN', 'ACTIVE')
ON DUPLICATE KEY UPDATE username = username;

-- 种子数据：测试普通用户
INSERT INTO sys_user (username, password_hash, name, phone, role, status)
VALUES ('testuser', '$2b$10$4.yc8UZ.ubEbGJIz6Fbl5uxRujsuxaKhakEib9Ve3sVsWYib.ljD2', '测试用户', '', 'USER', 'ACTIVE')
ON DUPLICATE KEY UPDATE username = username;

-- 旧坐席表（保留兼容，逐步废弃）
CREATE TABLE IF NOT EXISTS cs_agent (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '坐席ID',
    username        VARCHAR(50)   NOT NULL COMMENT '登录用户名',
    password_hash   VARCHAR(255)  NOT NULL COMMENT 'BCrypt 密码哈希',
    name            VARCHAR(100)  DEFAULT '' COMMENT '坐席姓名（显示用）',
    role            VARCHAR(20)   DEFAULT 'AGENT' COMMENT '角色：ADMIN/AGENT',
    status          VARCHAR(20)   DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
    create_time     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人工坐席表';

-- 坐席快捷回复表
CREATE TABLE IF NOT EXISTS cs_quick_reply (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    agent_id    BIGINT       DEFAULT NULL COMMENT '所属坐席ID（NULL=公用）',
    title       VARCHAR(100) NOT NULL COMMENT '按钮标题',
    content     TEXT         NOT NULL COMMENT '回复内容',
    category    VARCHAR(50)  DEFAULT '其他' COMMENT '分组',
    sort_order  INT          DEFAULT 0 COMMENT '排序',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_agent_id (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='坐席快捷回复表';

-- 种子数据：公用快捷回复
INSERT INTO cs_quick_reply (agent_id, title, content, category, sort_order) VALUES
(NULL, '问候语', '您好，请问有什么可以帮您？', '问候', 1),
(NULL, '请稍候', '请稍候，我帮您查询一下...', '常用', 2),
(NULL, '已记录', '您的问题已记录，我们会尽快处理。', '常用', 3),
(NULL, '重启试试', '请尝试重启设备后再试，如果问题依旧请告诉我。', '技术', 4),
(NULL, '结束语', '感谢您的反馈，祝您工作愉快！', '结束', 5)
ON DUPLICATE KEY UPDATE title = title;

-- 满意度评价表
CREATE TABLE IF NOT EXISTS cs_satisfaction (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    session_id      VARCHAR(64)  NOT NULL COMMENT '关联会话ID',
    order_id        BIGINT       DEFAULT NULL COMMENT '关联工单ID（可为空）',
    user_id         BIGINT       NOT NULL COMMENT '评价用户ID',
    agent_id        BIGINT       DEFAULT NULL COMMENT '被评价坐席ID（可为空）',
    rating          TINYINT      NOT NULL COMMENT '评分 1-5',
    comment         VARCHAR(500) DEFAULT NULL COMMENT '评价留言',
    source          VARCHAR(20)  DEFAULT 'SESSION' COMMENT '来源: SESSION/ORDER/MANUAL',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE INDEX idx_session (session_id),
    INDEX idx_agent (agent_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='满意度评价表';

-- 种子数据：默认管理员坐席（密码: admin123）
INSERT INTO cs_agent (username, password_hash, name, role, status)
VALUES ('admin', '$2b$10$4.yc8UZ.ubEbGJIz6Fbl5uxRujsuxaKhakEib9Ve3sVsWYib.ljD2', '管理员', 'ADMIN', 'ACTIVE')
ON DUPLICATE KEY UPDATE username = username;
