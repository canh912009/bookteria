package com.devteria.identity.entity;

import java.util.Date;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class UserSession {
    @Id
    String id;

    String userId;
    String tokenId;
    String deviceId;
    String deviceName;
    Date createdAt;
    Date lastSeenAt;
    Date expiresAt;
    boolean revoked;
    String revokedReason;
}
