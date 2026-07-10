package com.devteria.identity.repository;

import java.util.Date;
import java.util.List;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import com.devteria.identity.entity.UserSession;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<UserSession> findByUserIdAndRevokedFalseAndExpiresAtAfterOrderByLastSeenAtAsc(String userId, Date now);
}
