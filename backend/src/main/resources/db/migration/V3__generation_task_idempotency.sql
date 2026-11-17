-- V1.0.1 H-06：生成任务持久幂等（Redis 只做缓存，DB 为事实源）
-- 兼容历史数据：新列 nullable，不回填；MySQL UNIQUE 允许多行 NULL。
ALTER TABLE generation_task
    ADD COLUMN idempotency_key VARCHAR(128) NULL COMMENT 'Idempotency-Key 请求头（持久幂等）' AFTER task_type,
    ADD UNIQUE KEY uk_task_user_idempotency (user_id, idempotency_key);
