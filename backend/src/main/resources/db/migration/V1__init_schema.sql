-- =====================================================================
-- V1__init_schema.sql — Future Self V1 全部核心表（实施文档 §10/§35）
-- 约定：
--   * 主键 id BIGINT UNSIGNED AUTO_INCREMENT；业务表统一 created_at/updated_at DATETIME(3)
--   * 所有删除默认软删除（status 字段），不物理删除
--   * 表间不建物理外键：story 与 generation_task 存在双向引用，
--     且任务队列/软删除场景下物理外键会放大删除耦合；一致性由服务层保证
--   * 结构变更只允许新增 migration，禁止修改历史文件
-- =====================================================================

-- 10.1 用户 ----------------------------------------------------------------
CREATE TABLE app_user (
    id                   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    openid               VARCHAR(64)  NOT NULL COMMENT '微信小程序 openid',
    unionid              VARCHAR(64)  NULL COMMENT '有则保存',
    display_name         VARCHAR(64)  NOT NULL COMMENT '用户显示名',
    role                 VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT 'USER / ADMIN',
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED',
    onboarding_completed TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否完成 onboarding',
    last_active_at       DATETIME(3)  NULL COMMENT '最近活跃',
    created_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_user_openid UNIQUE (openid),
    INDEX idx_user_last_active (last_active_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='小程序用户';

-- 10.2 人生档案（一对一）----------------------------------------------------
CREATE TABLE life_profile (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id               BIGINT UNSIGNED NOT NULL COMMENT '所属用户，唯一',
    current_situation     TEXT NULL COMMENT '当前生活状态',
    focus_areas_json      JSON NULL COMMENT '关注领域数组',
    important_people_json JSON NULL COMMENT '重要人物数组',
    preferences_json      JSON NULL COMMENT '理想生活偏好',
    extra_json            JSON NULL COMMENT '扩展资料',
    profile_version       INT NOT NULL DEFAULT 1 COMMENT '每次修改 +1',
    created_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_profile_user UNIQUE (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='人生档案';

-- 10.3 目标/愿望 -------------------------------------------------------------
CREATE TABLE manifestation_goal (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT UNSIGNED NOT NULL COMMENT '所属用户',
    category         VARCHAR(40)  NOT NULL COMMENT '分类',
    title            VARCHAR(120) NOT NULL COMMENT '标题',
    description      TEXT NOT NULL COMMENT '原始描述',
    target_time_text VARCHAR(120) NULL COMMENT '用户自然语言时间，如“一年后”',
    details_json     JSON NULL COMMENT 'AI 追问答案/补充信息',
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / ARCHIVED / DELETED',
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_goal_user_status (user_id, status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='目标';

-- 10.4 故事 ------------------------------------------------------------------
CREATE TABLE story (
    id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT UNSIGNED NOT NULL COMMENT '所属用户',
    goal_id            BIGINT UNSIGNED NOT NULL COMMENT '来源目标',
    generation_task_id BIGINT UNSIGNED NULL COMMENT '来源任务（文本完成后任务回写 story_id）',
    title              VARCHAR(200) NOT NULL COMMENT '故事标题',
    summary            VARCHAR(500) NOT NULL COMMENT '首页摘要',
    content            MEDIUMTEXT NOT NULL COMMENT '完整正文',
    perspective        VARCHAR(20)  NOT NULL DEFAULT 'SECOND_PERSON' COMMENT '叙事视角',
    prompt_template_id BIGINT UNSIGNED NULL COMMENT '使用的 Prompt 版本',
    provider           VARCHAR(60)  NOT NULL COMMENT '文本 AI provider',
    model              VARCHAR(100) NOT NULL COMMENT '模型',
    profile_version    INT NOT NULL DEFAULT 1 COMMENT '生成时用户资料版本',
    is_favorite        TINYINT(1)   NOT NULL DEFAULT 0,
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DELETED / BLOCKED',
    moderation_status  VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / PASSED / BLOCKED',
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_story_user_status (user_id, status, created_at),
    INDEX idx_story_goal (goal_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='未来故事';

-- 10.5 音频资产 ---------------------------------------------------------------
CREATE TABLE audio_asset (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT UNSIGNED NOT NULL COMMENT '所属用户',
    story_id         BIGINT UNSIGNED NOT NULL COMMENT '对应故事（一个 story 仅一份当前有效音频，旧音频 superseded）',
    provider         VARCHAR(60)  NOT NULL COMMENT '音频供应商（mock/真实）',
    provider_task_id VARCHAR(160) NULL COMMENT '供应商任务 ID',
    mode             VARCHAR(40)  NOT NULL DEFAULT 'NARRATION' COMMENT 'NARRATION / NARRATION_MIX',
    voice_id         VARCHAR(120) NULL,
    style            VARCHAR(120) NULL,
    status           VARCHAR(30)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUBMITTED/PROCESSING/READY/FAILED/SUPERSEDED',
    provider_url     TEXT NULL COMMENT '供应商结果 URL，仅内部使用',
    storage_key      VARCHAR(500) NULL COMMENT '自有存储 key',
    public_url       TEXT NULL COMMENT '前端访问 URL',
    duration_ms      BIGINT NULL,
    format           VARCHAR(20)  NOT NULL DEFAULT 'mp3' COMMENT 'mp3/m4a/wav',
    request_json     JSON NULL COMMENT '脱敏后的请求摘要',
    response_json    JSON NULL COMMENT '脱敏后的供应商响应摘要',
    error_code       VARCHAR(100) NULL,
    error_message    VARCHAR(1000) NULL,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_audio_story (story_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='音频资产';

-- 10.6 生成任务（任务队列表，事实源）-------------------------------------------
CREATE TABLE generation_task (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT UNSIGNED NOT NULL,
    goal_id         BIGINT UNSIGNED NOT NULL,
    story_id        BIGINT UNSIGNED NULL COMMENT '文本完成后写入',
    task_type       VARCHAR(30)  NOT NULL DEFAULT 'STORY_WITH_AUDIO',
    status          VARCHAR(40)  NOT NULL DEFAULT 'PENDING' COMMENT '状态机见实施文档 §11',
    progress        INT NOT NULL DEFAULT 0 COMMENT '0~100 展示用途',
    retry_count     INT NOT NULL DEFAULT 0,
    max_retry_count INT NOT NULL DEFAULT 3,
    next_retry_at   DATETIME(3) NULL,
    locked_at       DATETIME(3) NULL COMMENT 'worker 锁定时间',
    worker_id       VARCHAR(100) NULL,
    error_stage     VARCHAR(40)  NULL COMMENT 'TEXT / MODERATION / AUDIO / STORAGE',
    error_code      VARCHAR(100) NULL,
    error_message   VARCHAR(1000) NULL,
    started_at      DATETIME(3)  NULL,
    finished_at     DATETIME(3)  NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_task_status_retry (status, next_retry_at, created_at),
    INDEX idx_task_user (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='生成任务队列';

-- 10.7 每日肯定句 --------------------------------------------------------------
CREATE TABLE affirmation_daily (
    id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT UNSIGNED NOT NULL,
    local_date         DATE NOT NULL COMMENT '用户本地日期',
    items_json         JSON NOT NULL COMMENT '5~10 条',
    prompt_template_id BIGINT UNSIGNED NULL,
    provider           VARCHAR(60)  NOT NULL,
    model              VARCHAR(100) NOT NULL,
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_affirmation_user_date UNIQUE (user_id, local_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='每日肯定句';

-- 10.8 Prompt 模板版本 ----------------------------------------------------------
CREATE TABLE prompt_template (
    id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    prompt_key         VARCHAR(100) NOT NULL COMMENT 'FOLLOWUP / STORY / AFFIRMATION',
    version            INT NOT NULL,
    system_prompt      MEDIUMTEXT NOT NULL,
    user_template      MEDIUMTEXT NOT NULL,
    output_schema_json JSON NULL,
    enabled            TINYINT(1) NOT NULL DEFAULT 0 COMMENT '同 key 仅一个 enabled=1',
    notes              VARCHAR(500) NULL,
    created_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_prompt_key_version UNIQUE (prompt_key, version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='Prompt 版本';

-- 10.9 AI 调用日志（不保存 API key）---------------------------------------------
CREATE TABLE ai_call_log (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    request_id            VARCHAR(64) NOT NULL,
    user_id               BIGINT UNSIGNED NULL,
    generation_task_id    BIGINT UNSIGNED NULL,
    provider_type         VARCHAR(20) NOT NULL COMMENT 'TEXT / AUDIO / MODERATION',
    provider              VARCHAR(60) NOT NULL,
    model                 VARCHAR(100) NULL,
    operation             VARCHAR(100) NOT NULL,
    latency_ms            BIGINT NOT NULL DEFAULT 0,
    success               TINYINT(1) NOT NULL DEFAULT 0,
    input_tokens          INT NULL,
    output_tokens         INT NULL,
    error_code            VARCHAR(100) NULL,
    error_message         VARCHAR(1000) NULL,
    request_summary_json  JSON NULL COMMENT '脱敏请求摘要',
    response_summary_json JSON NULL COMMENT '脱敏响应摘要',
    created_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_ai_log_request (request_id),
    INDEX idx_ai_log_task (generation_task_id),
    INDEX idx_ai_log_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='AI 调用诊断日志';

-- 10.10 后台管理员（独立凭证）---------------------------------------------------
CREATE TABLE admin_user (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(200) NOT NULL COMMENT 'BCrypt 等，禁止明文',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    last_login_at DATETIME(3)  NULL,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_admin_username UNIQUE (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='管理后台账号';

-- 35 产品行为埋点（可选表，首版启用）--------------------------------------------
CREATE TABLE user_event (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT UNSIGNED NOT NULL,
    event_name      VARCHAR(60) NOT NULL COMMENT 'APP_OPEN/GOAL_CREATED/STORY_TEXT_READY 等',
    entity_type     VARCHAR(40) NULL,
    entity_id       BIGINT UNSIGNED NULL,
    properties_json JSON NULL COMMENT '只记产品行为，不存故事正文',
    occurred_at     DATETIME(3) NOT NULL,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_event_user_time (user_id, occurred_at),
    INDEX idx_event_name (event_name, occurred_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT ='产品事件';
