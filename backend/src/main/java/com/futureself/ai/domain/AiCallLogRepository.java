package com.futureself.ai.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiCallLogRepository extends JpaRepository<AiCallLog, Long> {

    /** 后台分页：requestId/operation/providerType 可空。 */
    @Query("select l from AiCallLog l where (:requestId is null or l.requestId = :requestId) "
            + "and (:operation is null or l.operation = :operation) "
            + "and (:providerType is null or l.providerType = :providerType)")
    Page<AiCallLog> adminPage(@Param("requestId") String requestId,
                              @Param("operation") String operation,
                              @Param("providerType") String providerType,
                              Pageable pageable);

    /** 成功调用平均耗时（dashboard，§20.1）。 */
    @Query("select avg(l.latencyMs) from AiCallLog l where l.providerType = :providerType and l.success = true")
    Double avgSuccessLatency(@Param("providerType") String providerType);
}
