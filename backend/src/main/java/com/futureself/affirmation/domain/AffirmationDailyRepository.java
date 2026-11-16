package com.futureself.affirmation.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface AffirmationDailyRepository extends JpaRepository<AffirmationDaily, Long> {

    Optional<AffirmationDaily> findByUserIdAndLocalDate(Long userId, LocalDate localDate);
}
