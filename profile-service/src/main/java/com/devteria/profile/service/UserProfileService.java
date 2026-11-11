package com.devteria.profile.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.devteria.event.dto.SearchIndexEvent;
import com.devteria.profile.dto.request.ProfileCreationRequest;
import com.devteria.profile.dto.request.SearchUserRequest;
import com.devteria.profile.dto.request.UpdateProfileRequest;
import com.devteria.profile.dto.response.UserProfileResponse;
import com.devteria.profile.entity.OutboxEvent;
import com.devteria.profile.entity.OutboxStatus;
import com.devteria.profile.entity.UserProfile;
import com.devteria.profile.exception.AppException;
import com.devteria.profile.exception.ErrorCode;
import com.devteria.profile.mapper.UserProfileMapper;
import com.devteria.profile.repository.OutboxEventRepository;
import com.devteria.profile.repository.UserProfileRepository;
import com.devteria.profile.repository.httpclient.FileClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

/**
 * Tầng nghiệp vụ quản lý hồ sơ người dùng (UserProfileService) áp dụng Transactional Outbox Pattern trên Neo4j.
 *
 * Vì Debezium CDC không có connector cho Neo4j, Transactional Outbox là giải pháp chuẩn xác nhất:
 * - Khi createProfile, updateMyProfile, hoặc updateAvatar được gọi, cả node "user_profile" và node "outbox_event"
 *   đều được ghi trong cùng một @Transactional của Neo4j.
 * - Tiến trình OutboxRelayService sẽ đọc các node "outbox_event" trạng thái PENDING và đẩy qua Kafka tới search-service.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class UserProfileService {
    UserProfileRepository userProfileRepository;
    OutboxEventRepository outboxEventRepository;
    FileClient fileClient;
    UserProfileMapper userProfileMapper;
    ObjectMapper objectMapper;

    /**
     * Tạo hồ sơ người dùng mới và lưu sự kiện Outbox trong cùng 1 Neo4j Transaction.
     */
    @Transactional
    public UserProfileResponse createProfile(ProfileCreationRequest request) {
        UserProfile userProfile = userProfileMapper.toUserProfile(request);
        userProfile = userProfileRepository.save(userProfile);

        // Lưu sự kiện Outbox trong cùng transaction với UserProfile
        saveProfileOutboxEvent(userProfile, "UPSERT");

        return userProfileMapper.toUserProfileResponse(userProfile);
    }

    public UserProfileResponse getByUserId(String userId) {
        UserProfile userProfile = userProfileRepository
                .findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        return userProfileMapper.toUserProfileResponse(userProfile);
    }

    public UserProfileResponse getProfile(String id) {
        UserProfile userProfile =
                userProfileRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        return userProfileMapper.toUserProfileResponse(userProfile);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<UserProfileResponse> getAllProfiles() {
        var profiles = userProfileRepository.findAll();

        return profiles.stream().map(userProfileMapper::toUserProfileResponse).toList();
    }

    public UserProfileResponse getMyProfile() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String userId = authentication.getName();

        var profile = userProfileRepository
                .findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        return userProfileMapper.toUserProfileResponse(profile);
    }

    /**
     * Cập nhật thông tin hồ sơ và ghi sự kiện Outbox trong cùng 1 Neo4j Transaction.
     */
    @Transactional
    public UserProfileResponse updateMyProfile(UpdateProfileRequest request) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String userId = authentication.getName();

        var profile = userProfileRepository
                .findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        userProfileMapper.update(profile, request);
        UserProfile savedProfile = userProfileRepository.save(profile);

        // Ghi sự kiện Outbox trong cùng transaction để đồng bộ sang Elasticsearch
        saveProfileOutboxEvent(savedProfile, "UPSERT");

        return userProfileMapper.toUserProfileResponse(savedProfile);
    }

    /**
     * Cập nhật ảnh đại diện:
     * Tải ảnh lên file-service trước, sau đó mở transaction cập nhật UserProfile + ghi OutboxEvent nguyên tử.
     */
    @Transactional
    public UserProfileResponse updateAvatar(MultipartFile file) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String userId = authentication.getName();

        var profile = userProfileRepository
                .findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        var response = fileClient.uploadMedia(file);

        profile.setAvatar(response.getResult().getUrl());
        UserProfile savedProfile = userProfileRepository.save(profile);

        // Ghi sự kiện Outbox cập nhật thumbnailUrl trong cùng transaction
        saveProfileOutboxEvent(savedProfile, "UPSERT");

        return userProfileMapper.toUserProfileResponse(savedProfile);
    }

    public List<UserProfileResponse> search(SearchUserRequest request) {
        var userId = SecurityContextHolder.getContext().getAuthentication().getName();
        List<UserProfile> userProfiles = userProfileRepository.findAllByUsernameLike(request.getKeyword());
        return userProfiles.stream()
                .filter(userProfile -> !userId.equals(userProfile.getUserId()))
                .map(userProfileMapper::toUserProfileResponse)
                .toList();
    }

    /**
     * Tạo và lưu node OutboxEvent trong Neo4j cùng transaction với thao tác lưu UserProfile.
     */
    private void saveProfileOutboxEvent(UserProfile profile, String action) {
        String fullName = Stream.of(profile.getFirstName(), profile.getLastName())
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" "));
        String displayTitle = StringUtils.hasText(profile.getUsername())
                ? profile.getUsername()
                : (StringUtils.hasText(fullName) ? fullName : profile.getId());
        String content = Stream.of(fullName, profile.getEmail(), profile.getCity())
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" | "));
        String documentId = "user-" + profile.getId();
        Instant now = Instant.now();

        SearchIndexEvent eventPayload = SearchIndexEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .action(action)
                .documentId(documentId)
                .type("user")
                .title(displayTitle)
                .content(content)
                .authorId(profile.getUserId())
                .authorName(StringUtils.hasText(fullName) ? fullName : displayTitle)
                .tags(Set.of("user", "profile"))
                .thumbnailUrl(profile.getAvatar())
                .updatedAt(now)
                .version(now.toEpochMilli())
                .build();

        try {
            String payloadJson = objectMapper.writeValueAsString(eventPayload);
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType("USER_PROFILE")
                    .aggregateId(documentId)
                    .eventType(action)
                    .payload(payloadJson)
                    .status(OutboxStatus.PENDING)
                    .retryCount(0)
                    .createdAt(now)
                    .build();

            outboxEventRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize profile outbox event payload", e);
        }
    }
}
