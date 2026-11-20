-- D&D 活動報名系統的核心資料表。
-- MySQL 8.0.16+ 才會實際執行 CHECK constraints。

CREATE TABLE members (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    account       VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name          VARCHAR(50)  NOT NULL,
    email         VARCHAR(254) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'MEMBER',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                 ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_members PRIMARY KEY (id),
    CONSTRAINT uk_members_account UNIQUE (account),
    CONSTRAINT uk_members_email UNIQUE (email),
    CONSTRAINT chk_members_role CHECK (role IN ('MEMBER', 'ADMIN')),
    CONSTRAINT chk_members_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT chk_members_account_not_blank CHECK (CHAR_LENGTH(TRIM(account)) > 0),
    CONSTRAINT chk_members_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT chk_members_email_not_blank CHECK (CHAR_LENGTH(TRIM(email)) > 0)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE events (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    title                 VARCHAR(100) NOT NULL,
    description           TEXT         NOT NULL,
    host_name             VARCHAR(50)  NOT NULL,
    location              VARCHAR(255) NOT NULL,
    start_at              DATETIME(6)  NOT NULL,
    end_at                DATETIME(6)  NOT NULL,
    registration_deadline DATETIME(6)  NOT NULL,
    capacity              INT          NOT NULL,
    beginner_friendly     BOOLEAN      NOT NULL DEFAULT FALSE,
    status                VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    created_by            BIGINT       NOT NULL,
    created_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                       ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_events PRIMARY KEY (id),
    CONSTRAINT fk_events_created_by
        FOREIGN KEY (created_by) REFERENCES members (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_events_status CHECK (status IN ('OPEN', 'CLOSED', 'CANCELLED')),
    CONSTRAINT chk_events_capacity CHECK (capacity > 0),
    CONSTRAINT chk_events_time_order CHECK (end_at > start_at),
    CONSTRAINT chk_events_registration_deadline
        CHECK (registration_deadline < start_at),
    CONSTRAINT chk_events_title_not_blank CHECK (CHAR_LENGTH(TRIM(title)) > 0),
    CONSTRAINT chk_events_host_name_not_blank CHECK (CHAR_LENGTH(TRIM(host_name)) > 0),
    CONSTRAINT chk_events_location_not_blank CHECK (CHAR_LENGTH(TRIM(location)) > 0),

    INDEX idx_events_status_start_at (status, start_at),
    INDEX idx_events_created_by (created_by)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE registrations (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    member_id     BIGINT      NOT NULL,
    event_id      BIGINT      NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'REGISTERED',
    registered_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    cancelled_at  DATETIME(6) NULL,
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                              ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_registrations PRIMARY KEY (id),
    CONSTRAINT uk_registrations_member_event UNIQUE (member_id, event_id),
    CONSTRAINT fk_registrations_member
        FOREIGN KEY (member_id) REFERENCES members (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT fk_registrations_event
        FOREIGN KEY (event_id) REFERENCES events (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_registrations_status
        CHECK (status IN ('REGISTERED', 'CANCELLED')),
    CONSTRAINT chk_registrations_cancelled_at CHECK (
        (status = 'REGISTERED' AND cancelled_at IS NULL)
        OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL)
    ),
    CONSTRAINT chk_registrations_cancelled_time CHECK (
        cancelled_at IS NULL OR cancelled_at >= registered_at
    ),

    INDEX idx_registrations_event_status (event_id, status),
    INDEX idx_registrations_member_status (member_id, status)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
