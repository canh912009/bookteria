import httpClient from "../configurations/httpClient";
import { API } from "../configurations/configuration";
import { getToken } from "./localStorageService";

/**
 * Tạo header xác thực Bearer Token dùng chung cho các request gọi qua API Gateway.
 */
const getAuthHeaders = () => ({
  Authorization: `Bearer ${getToken()}`,
  "Content-Type": "application/json",
});

/**
 * Tìm kiếm toàn văn (Full-Text Search) trên Elasticsearch thông qua search-service.
 *
 * @param {Object} params - Tham số tìm kiếm
 * @param {string} [params.keyword] - Từ khóa cần tìm (ánh xạ vào query param `q`)
 * @param {string} [params.type] - Phân loại tài liệu cần lọc (`book`, `post`, `user`...)
 * @param {number} [params.page=1] - Số trang hiện tại (bắt đầu từ 1)
 * @param {number} [params.size=10] - Số lượng kết quả trên mỗi trang
 */
export const searchDocuments = async ({
  keyword = "",
  type = "",
  page = 1,
  size = 10,
} = {}) => {
  const params = { page, size };

  if (keyword && keyword.trim()) {
    params.q = keyword.trim();
  }
  if (type && type.trim()) {
    params.type = type.trim();
  }

  return await httpClient.get(API.SEARCH, {
    headers: getAuthHeaders(),
    params,
  });
};

/**
 * Đánh chỉ mục (Index) mới hoặc cập nhật một tài liệu vào Elasticsearch.
 *
 * @param {Object} documentData - Dữ liệu tài liệu (id, type, title, content, authorId, authorName, tags, thumbnailUrl)
 */
export const indexSearchDocument = async (documentData) => {
  return await httpClient.post(API.SEARCH_DOCUMENTS, documentData, {
    headers: getAuthHeaders(),
  });
};

/**
 * Lấy chi tiết một tài liệu tìm kiếm theo ID.
 *
 * @param {string} id - ID của tài liệu trong Elasticsearch
 */
export const getSearchDocumentById = async (id) => {
  return await httpClient.get(`${API.SEARCH_DOCUMENTS}/${encodeURIComponent(id)}`, {
    headers: getAuthHeaders(),
  });
};

/**
 * Xóa một tài liệu khỏi chỉ mục Elasticsearch theo ID.
 *
 * @param {string} id - ID của tài liệu cần xóa
 */
export const deleteSearchDocument = async (id) => {
  return await httpClient.delete(
    `${API.SEARCH_DOCUMENTS}/${encodeURIComponent(id)}`,
    {
      headers: getAuthHeaders(),
    }
  );
};
