-- Collaboration dashboard schema. All spreadsheet data lives in MySQL (one row per non-empty cell).

CREATE TABLE department (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(32)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE app_user (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    display_name  VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    department_id BIGINT       NOT NULL,
    role          VARCHAR(16)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT fk_user_department FOREIGN KEY (department_id) REFERENCES department (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE sheet_template (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    description   VARCHAR(500),
    department_id BIGINT,
    definition    LONGTEXT     NOT NULL,
    created_by    BIGINT,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT fk_template_department FOREIGN KEY (department_id) REFERENCES department (id),
    CONSTRAINT fk_template_user FOREIGN KEY (created_by) REFERENCES app_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE sheet (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                VARCHAR(160) NOT NULL,
    description         VARCHAR(500),
    owner_department_id BIGINT       NOT NULL,
    template_id         BIGINT,
    row_count           INT          NOT NULL,
    col_count           INT          NOT NULL,
    header_rows         INT          NOT NULL DEFAULT 1,
    version             BIGINT       NOT NULL DEFAULT 0,
    created_by          BIGINT,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    CONSTRAINT fk_sheet_department FOREIGN KEY (owner_department_id) REFERENCES department (id),
    CONSTRAINT fk_sheet_template FOREIGN KEY (template_id) REFERENCES sheet_template (id) ON DELETE SET NULL,
    CONSTRAINT fk_sheet_user FOREIGN KEY (created_by) REFERENCES app_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Column metadata: display title, width, "watched" flag (changes raise IMPORTANT notifications)
-- and an optional list of department codes that may edit this column.
CREATE TABLE sheet_column (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    sheet_id         BIGINT      NOT NULL,
    col_index        INT         NOT NULL,
    title            VARCHAR(120),
    width            INT,
    watched          BOOLEAN     NOT NULL DEFAULT FALSE,
    edit_departments VARCHAR(255),
    CONSTRAINT uk_sheet_column UNIQUE (sheet_id, col_index),
    CONSTRAINT fk_column_sheet FOREIGN KEY (sheet_id) REFERENCES sheet (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Department-level sharing. The owner department is implicit and not stored here.
CREATE TABLE sheet_permission (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    sheet_id      BIGINT      NOT NULL,
    department_id BIGINT      NOT NULL,
    level         VARCHAR(16) NOT NULL,
    CONSTRAINT uk_sheet_permission UNIQUE (sheet_id, department_id),
    CONSTRAINT fk_perm_sheet FOREIGN KEY (sheet_id) REFERENCES sheet (id) ON DELETE CASCADE,
    CONSTRAINT fk_perm_department FOREIGN KEY (department_id) REFERENCES department (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE sheet_cell (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    sheet_id   BIGINT      NOT NULL,
    row_idx    INT         NOT NULL,
    col_idx    INT         NOT NULL,
    raw_value  TEXT,
    style      VARCHAR(500),
    updated_by BIGINT,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_sheet_cell UNIQUE (sheet_id, row_idx, col_idx),
    CONSTRAINT fk_cell_sheet FOREIGN KEY (sheet_id) REFERENCES sheet (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Immutable audit trail of every cell edit (answers "who moved the deadline from the 15th to the 20th, and when").
CREATE TABLE cell_change (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    sheet_id      BIGINT       NOT NULL,
    batch_id      VARCHAR(36)  NOT NULL,
    row_idx       INT          NOT NULL,
    col_idx       INT          NOT NULL,
    column_label  VARCHAR(120),
    row_label     VARCHAR(200),
    old_value     TEXT,
    new_value     TEXT,
    watched       BOOLEAN      NOT NULL DEFAULT FALSE,
    user_id       BIGINT       NOT NULL,
    department_id BIGINT       NOT NULL,
    changed_at    DATETIME(6)  NOT NULL,
    INDEX idx_change_sheet_time (sheet_id, changed_at),
    INDEX idx_change_cell (sheet_id, row_idx, col_idx),
    INDEX idx_change_time (changed_at),
    CONSTRAINT fk_change_sheet FOREIGN KEY (sheet_id) REFERENCES sheet (id) ON DELETE CASCADE,
    CONSTRAINT fk_change_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_change_department FOREIGN KEY (department_id) REFERENCES department (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE sheet_subscription (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    sheet_id   BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_subscription UNIQUE (sheet_id, user_id),
    CONSTRAINT fk_sub_sheet FOREIGN KEY (sheet_id) REFERENCES sheet (id) ON DELETE CASCADE,
    CONSTRAINT fk_sub_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE notification (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_id    BIGINT       NOT NULL,
    sheet_id        BIGINT,
    batch_id        VARCHAR(36),
    type            VARCHAR(32)  NOT NULL,
    severity        VARCHAR(16)  NOT NULL,
    title           VARCHAR(255) NOT NULL,
    message         TEXT         NOT NULL,
    actor_name      VARCHAR(100),
    actor_department VARCHAR(100),
    created_at      DATETIME(6)  NOT NULL,
    read_at         DATETIME(6),
    INDEX idx_notification_recipient (recipient_id, read_at, created_at),
    INDEX idx_notification_batch (batch_id),
    CONSTRAINT fk_notification_user FOREIGN KEY (recipient_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_sheet FOREIGN KEY (sheet_id) REFERENCES sheet (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
