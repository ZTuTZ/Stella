package com.futureself.generation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 生成任务 worker（实施文档 §11.3）：
 * MySQL generation_task 为队列事实源；fixedDelay 扫描推进，单次一个阶段。
 * 多实例部署时再引入 Redis 分布式锁 / SKIP LOCKED（V1 单实例）。
 */
@Component
public class GenerationWorker {

    private final GenerationOrchestrator orchestrator;

    public GenerationWorker(GenerationOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Scheduled(fixedDelayString = "${app.generation.worker-delay-ms:1500}")
    public void tick() {
        try {
            orchestrator.processNext();
        } catch (RuntimeException e) {
            // worker 不因单次异常退出：记录后继续下一轮
            org.slf4j.LoggerFactory.getLogger(GenerationWorker.class)
                    .warn("worker tick failed: {}", e.getClass().getSimpleName());
        }
    }
}
