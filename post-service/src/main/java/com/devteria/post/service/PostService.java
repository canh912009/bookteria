package com.devteria.post.service;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.devteria.event.dto.SearchIndexEvent;
import com.devteria.post.dto.PageResponse;
import com.devteria.post.dto.request.PostRequest;
import com.devteria.post.dto.response.PostResponse;
import com.devteria.post.dto.response.UserProfileResponse;
import com.devteria.post.entity.OutboxEvent;
import com.devteria.post.entity.OutboxStatus;
import com.devteria.post.entity.Post;
import com.devteria.post.mapper.PostMapper;
import com.devteria.post.repository.OutboxEventRepository;
import com.devteria.post.repository.PostRepository;
import com.devteria.post.repository.httpclient.ProfileClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

/**
 * Tầng nghiệp vụ của post-service áp dụng mẫu thiết kế Transactional Outbox Pattern.
 *
 * Luồng hoạt động chuẩn khi tạo bài viết (createPost):
 * 1. Lấy thông tin username từ profile-service.
 * 2. Thực thi trong MongoDB Transaction (qua TransactionTemplate + MongoTransactionManager):
 *    - Bước 2a: Lưu bài viết mới vào collection "post" của MongoDB.
 *    - Bước 2b: Lưu OutboxEvent(status = PENDING) vào collection "outbox_event".
 *    (Nếu container MongoDB trên máy chưa kịp khởi động lại với --replSet rs0, hệ thống tự động
 *     fallback ghi trực tiếp Post + OutboxEvent để không bị lỗi 400).
 * 3. Tiến trình nền OutboxRelayService sẽ tự động đọc bản ghi Outbox PENDING và đẩy qua Kafka tới search-service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PostService {
	DateTimeFormatter dateTimeFormatter;
	PostRepository postRepository;
	OutboxEventRepository outboxEventRepository;
	PostMapper postMapper;
	ProfileClient profileClient;
	ObjectMapper objectMapper;
	TransactionTemplate transactionTemplate;

	/**
	 * Tạo bài viết mới và ghi sự kiện Outbox trong cùng một Transaction nguyên tử.
	 */
	public PostResponse createPost(PostRequest request) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		String userId = authentication.getName();

		// Lấy thông tin username để đóng gói sẵn vào sự kiện tìm kiếm (denormalized document)
		UserProfileResponse userProfile = getUserProfileSafely(userId);
		String username = userProfile != null ? userProfile.getUsername() : userId;

		Instant now = Instant.now();
		Post newPost = Post.builder()
				.content(request.getContent())
				.userId(userId)
				.createdDate(now)
				.modifiedDate(now)
				.build();

		Post savedPost;
		try {
			// Thực thi nguyên tử (Atomic Transaction) khi MongoDB chạy chế độ Replica Set (rs0)
			savedPost = transactionTemplate.execute(status -> {
				Post p = postRepository.save(newPost);
				savePostOutboxEvent(p, username, "UPSERT");
				return p;
			});
		} catch (Exception ex) {
			// Phòng trường hợp container MongoDB cũ đang chạy ở chế độ Standalone (chưa bật --replSet rs0)
			if (isStandaloneMongoTransactionError(ex)) {
				log.warn(
						"MongoDB is running in standalone mode (not Replica Set rs0). Saving Post and OutboxEvent without multi-doc transaction.");
				savedPost = postRepository.save(newPost);
				savePostOutboxEvent(savedPost, username, "UPSERT");
			} else {
				throw ex;
			}
		}

		PostResponse postResponse = postMapper.toPostResponse(savedPost);
		postResponse.setCreated(dateTimeFormatter.format(savedPost.getCreatedDate()));
		postResponse.setUsername(username);
		return postResponse;
	}

	public PageResponse<PostResponse> getMyPosts(int page, int size) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		String userId = authentication.getName();

		UserProfileResponse userProfile = getUserProfileSafely(userId);
		Sort sort = Sort.by("createdDate").descending();

		Pageable pageable = PageRequest.of(page - 1, size, sort);
		var pageData = postRepository.findAllByUserId(userId, pageable);

		String username = userProfile != null ? userProfile.getUsername() : null;
		var postList = pageData.getContent().stream()
				.map(post -> {
					var postResponse = postMapper.toPostResponse(post);
					postResponse.setCreated(dateTimeFormatter.format(post.getCreatedDate()));
					postResponse.setUsername(username);
					return postResponse;
				})
				.toList();

		return PageResponse.<PostResponse>builder()
				.currentPage(page)
				.pageSize(pageData.getSize())
				.totalPages(pageData.getTotalPages())
				.totalElements(pageData.getTotalElements())
				.data(postList)
				.build();
	}

	private UserProfileResponse getUserProfileSafely(String userId) {
		try {
			return profileClient.getProfile(userId).getResult();
		} catch (Exception e) {
			log.error("Error while getting user profile for userId={}", userId, e);
			return null;
		}
	}

	private boolean isStandaloneMongoTransactionError(Throwable ex) {
		Throwable current = ex;
		while (current != null) {
			String msg = current.getMessage();
			if (msg != null
					&& (msg.contains("Transaction numbers are only allowed on a replica set")
							|| msg.contains("Sessions are not supported"))) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	/**
	 * Tạo và lưu bản ghi OutboxEvent cho bài viết.
	 */
	private void savePostOutboxEvent(Post post, String username, String action) {
		String rawContent = StringUtils.hasText(post.getContent()) ? post.getContent().trim() : "Bài viết mới";
		String title = rawContent.length() > 60 ? rawContent.substring(0, 60) + "..." : rawContent;
		String documentId = "post-" + post.getId();
		Instant now = Instant.now();

		SearchIndexEvent eventPayload = SearchIndexEvent.builder()
				.eventId(UUID.randomUUID().toString())
				.action(action)
				.documentId(documentId)
				.type("post")
				.title(title)
				.content(rawContent)
				.authorId(post.getUserId())
				.authorName(username)
				.tags(Set.of("post"))
				.createdAt(post.getCreatedDate())
				.updatedAt(post.getModifiedDate() != null ? post.getModifiedDate() : now)
				.version(now.toEpochMilli())
				.build();

		try {
			String payloadJson = objectMapper.writeValueAsString(eventPayload);
			OutboxEvent outboxEvent = OutboxEvent.builder()
					.id(eventPayload.getEventId())
					.aggregateType("POST")
					.aggregateId(documentId)
					.eventType(action)
					.payload(payloadJson)
					.status(OutboxStatus.PENDING)
					.retryCount(0)
					.createdAt(now)
					.build();

			outboxEventRepository.save(outboxEvent);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize post outbox event payload", e);
		}
	}
}
