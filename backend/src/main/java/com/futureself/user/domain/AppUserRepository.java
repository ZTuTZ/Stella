package com.futureself.user.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByOpenid(String openid);

    /** 后台用户检索：keyword 可空，匹配昵称/openid。 */
    @Query("select u from AppUser u where (:keyword is null or u.displayName like concat('%', :keyword, '%') or u.openid like concat('%', :keyword, '%'))")
    Page<AppUser> adminPage(@Param("keyword") String keyword, Pageable pageable);

    long countByLastActiveAtAfter(LocalDateTime time);
}
