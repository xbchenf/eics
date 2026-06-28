-- EICS Test Schema (H2)
CREATE TABLE IF NOT EXISTS ek_document (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(500)  NOT NULL,
    file_name   VARCHAR(500)  NOT NULL,
    file_type   VARCHAR(50)   NOT NULL,
    file_size   BIGINT        DEFAULT 0,
    minio_path  VARCHAR(1000) DEFAULT '',
    status      VARCHAR(20)   DEFAULT 'PENDING',
    chunk_count INT           DEFAULT 0,
    create_time DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME      DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT       DEFAULT 0
);

CREATE TABLE IF NOT EXISTS ek_document_chunk (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT        NOT NULL,
    chunk_index INT           NOT NULL,
    content     VARCHAR(5000) NOT NULL,
    char_count  INT           DEFAULT 0,
    milvus_id   BIGINT        DEFAULT NULL,
    create_time DATETIME      DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cs_chat_session (
    id             VARCHAR(64) PRIMARY KEY,
    user_id        VARCHAR(100) DEFAULT '',
    status         VARCHAR(20)  DEFAULT 'BOT',
    agent_id       BIGINT       DEFAULT NULL,
    rasa_sender_id VARCHAR(100) DEFAULT '',
    create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    close_time     DATETIME     DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS cs_chat_message (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id  VARCHAR(64)  NOT NULL,
    sender_type VARCHAR(20)  NOT NULL,
    content     VARCHAR(5000) NOT NULL,
    msg_type    VARCHAR(20)  DEFAULT 'TEXT',
    metadata    VARCHAR(2000) DEFAULT NULL,
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cs_service_order (
    id                VARCHAR(64) PRIMARY KEY,
    session_id        VARCHAR(64)  NOT NULL,
    phone             VARCHAR(20)  DEFAULT '',
    fault_description VARCHAR(1000) NOT NULL,
    device_id         VARCHAR(200) DEFAULT '',
    issue_type        VARCHAR(50)  DEFAULT 'OTHER',
    status            VARCHAR(20)  DEFAULT 'PENDING',
    agent_id          BIGINT       DEFAULT NULL,
    create_time       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    resolve_time      DATETIME     DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS cs_agent (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name          VARCHAR(100) DEFAULT '',
    role          VARCHAR(20)  DEFAULT 'AGENT',
    status        VARCHAR(20)  DEFAULT 'ACTIVE',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP
);
