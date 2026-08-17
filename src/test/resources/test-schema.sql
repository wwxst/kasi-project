-- H2测试表结构（与MySQL兼容）

CREATE TABLE IF NOT EXISTS short_drama_provider (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_code VARCHAR(32) NOT NULL,
    provider_name VARCHAR(64) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (provider_code)
);

CREATE TABLE IF NOT EXISTS short_drama_connection (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_id BIGINT NOT NULL,
    connection_name VARCHAR(64) NOT NULL,
    partner_id VARCHAR(64) NOT NULL,
    api_key_ciphertext TEXT NOT NULL,
    currency CHAR(3) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_by BIGINT DEFAULT NULL,
    updated_by BIGINT DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (provider_id),
    CONSTRAINT fk_test_drama_connection_provider
        FOREIGN KEY (provider_id) REFERENCES short_drama_provider (id)
);

MERGE INTO short_drama_provider (provider_code, provider_name, status)
KEY (provider_code) VALUES ('GOODSHORT', 'GoodShort', 1);

CREATE TABLE IF NOT EXISTS sys_admin_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(255) NOT NULL,
    real_name VARCHAR(64) NOT NULL,
    mobile VARCHAR(32) DEFAULT NULL,
    email VARCHAR(128) DEFAULT NULL,
    avatar_url VARCHAR(512) DEFAULT NULL,
    department_id BIGINT DEFAULT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    is_super_admin TINYINT NOT NULL DEFAULT 0,
    last_login_at TIMESTAMP DEFAULT NULL,
    last_login_ip VARCHAR(64) DEFAULT NULL,
    password_changed_at TIMESTAMP DEFAULT NULL,
    remark VARCHAR(500) DEFAULT NULL,
    created_by BIGINT DEFAULT NULL,
    updated_by BIGINT DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (username),
    UNIQUE (mobile),
    UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS promotion_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_no CHAR(12) NOT NULL,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(64) DEFAULT NULL,
    real_name VARCHAR(64) DEFAULT NULL,
    mobile VARCHAR(32) DEFAULT NULL,
    email VARCHAR(128) DEFAULT NULL,
    avatar_url VARCHAR(512) DEFAULT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    register_source VARCHAR(32) DEFAULT NULL,
    last_login_at TIMESTAMP DEFAULT NULL,
    last_login_ip VARCHAR(64) DEFAULT NULL,
    remark VARCHAR(500) DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_no),
    UNIQUE (mobile),
    UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS promotion_media_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    media_type VARCHAR(32) NOT NULL,
    external_account_id VARCHAR(128) NOT NULL,
    account_name VARCHAR(128) DEFAULT NULL,
    account_link VARCHAR(512) DEFAULT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    data_version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (media_type, external_account_id),
    CONSTRAINT fk_test_media_account_user
        FOREIGN KEY (user_id) REFERENCES promotion_user (id)
);

CREATE TABLE IF NOT EXISTS provider_media_filing (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    connection_id BIGINT NOT NULL,
    media_account_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    submitted_data_version INT DEFAULT NULL,
    remote_status VARCHAR(64) DEFAULT NULL,
    external_filing_id VARCHAR(128) DEFAULT NULL,
    filing_time TIMESTAMP DEFAULT NULL,
    operate_time TIMESTAMP DEFAULT NULL,
    next_action VARCHAR(16) NOT NULL DEFAULT 'SUBMIT',
    next_action_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retry_count INT NOT NULL DEFAULT 0,
    last_submitted_at TIMESTAMP DEFAULT NULL,
    last_queried_at TIMESTAMP DEFAULT NULL,
    last_error_code VARCHAR(64) DEFAULT NULL,
    last_error_message VARCHAR(512) DEFAULT NULL,
    lease_owner VARCHAR(64) DEFAULT NULL,
    lease_until TIMESTAMP DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (connection_id, media_account_id),
    CONSTRAINT fk_test_filing_connection
        FOREIGN KEY (connection_id) REFERENCES short_drama_connection (id),
    CONSTRAINT fk_test_filing_media_account
        FOREIGN KEY (media_account_id) REFERENCES promotion_media_account (id)
);
