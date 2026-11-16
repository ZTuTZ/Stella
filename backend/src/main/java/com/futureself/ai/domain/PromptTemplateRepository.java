package com.futureself.ai.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, Long> {

    Optional<PromptTemplate> findByPromptKeyAndEnabledTrue(String promptKey);
}
