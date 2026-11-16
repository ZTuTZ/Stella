package com.futureself.profile.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LifeProfileRepository extends JpaRepository<LifeProfile, Long> {

    Optional<LifeProfile> findByUserId(Long userId);
}
