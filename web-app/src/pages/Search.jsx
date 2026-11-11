import React, { useState, useEffect, useCallback } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  Box,
  Card,
  CardContent,
  Typography,
  TextField,
  Button,
  IconButton,
  Chip,
  Stack,
  Avatar,
  Divider,
  CircularProgress,
  Pagination,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  MenuItem,
  Snackbar,
  Alert,
  InputAdornment,
  Tooltip,
} from "@mui/material";
import SearchIcon from "@mui/icons-material/Search";
import ClearIcon from "@mui/icons-material/Clear";
import AddCircleOutlineIcon from "@mui/icons-material/AddCircleOutline";
import AutoAwesomeIcon from "@mui/icons-material/AutoAwesome";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import MenuBookIcon from "@mui/icons-material/MenuBook";
import ArticleIcon from "@mui/icons-material/Article";
import PersonIcon from "@mui/icons-material/Person";
import LabelOutlinedIcon from "@mui/icons-material/LabelOutlined";
import dayjs from "dayjs";
import Scene from "./Scene";
import { isAuthenticated, logOut } from "../services/authenticationService";
import {
  searchDocuments,
  indexSearchDocument,
  deleteSearchDocument,
} from "../services/searchService";

/**
 * Danh sách bộ lọc phân loại tài liệu (type) hỗ trợ trên giao diện.
 */
const TYPE_FILTERS = [
  { value: "", label: "Tất cả" },
  { value: "book", label: "Sách (Book)" },
  { value: "post", label: "Bài viết (Post)" },
  { value: "user", label: "Người dùng (User)" },
];

/**
 * Bộ dữ liệu mẫu (Sample Data) giúp khởi tạo nhanh các tài liệu vào Elasticsearch
 * để kiểm thử tính năng Full-Text Search, Fuzzy Search và lọc theo phân loại.
 */
const SAMPLE_DOCUMENTS = [
  {
    id: "book-dune-01",
    type: "book",
    title: "Dune - Xứ Cát",
    content:
      "Kiệt tác khoa học viễn tưởng của Frank Herbert xoay quanh hành tinh sa mạc Arrakis, gia tộc Atreides và nguồn hương dược Melange quý giá.",
    authorId: "author-herbert",
    authorName: "Frank Herbert",
    tags: ["sci-fi", "classic", "novel"],
  },
  {
    id: "book-clean-code-02",
    type: "book",
    title: "Clean Code - Mã Sạch",
    content:
      "Cẩm nang viết code dễ đọc, dễ bảo trì và kiến trúc phần mềm chuyên nghiệp dành cho lập trình viên Java, Spring Boot và React.",
    authorId: "author-martin",
    authorName: "Robert C. Martin",
    tags: ["programming", "java", "software-engineering"],
  },
  {
    id: "post-elasticsearch-01",
    type: "post",
    title: "Hướng dẫn tích hợp Elasticsearch vào Spring Boot Microservices",
    content:
      "Sử dụng NativeQuery kết hợp multiMatch (title^3, content, authorName, tags) và fuzziness AUTO để tìm kiếm nhanh và chính xác.",
    authorId: "user-devteria",
    authorName: "Devteria",
    tags: ["elasticsearch", "springboot", "microservice"],
  },
];

/**
 * Trả về màu sắc và biểu tượng tương ứng với từng loại tài liệu.
 */
const getTypeBadgeProps = (type) => {
  switch ((type || "").toLowerCase()) {
    case "book":
      return {
        color: "primary",
        icon: <MenuBookIcon fontSize="small" />,
        label: "Book",
      };
    case "post":
      return {
        color: "success",
        icon: <ArticleIcon fontSize="small" />,
        label: "Post",
      };
    case "user":
      return {
        color: "secondary",
        icon: <PersonIcon fontSize="small" />,
        label: "User",
      };
    default:
      return {
        color: "default",
        icon: <LabelOutlinedIcon fontSize="small" />,
        label: type || "Other",
      };
  }
};

export default function Search() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  // Đọc từ khóa `q` và bộ lọc `type` từ URL (ví dụ: /search?q=dune&type=book)
  const initialKeyword = searchParams.get("q") || "";
  const initialType = searchParams.get("type") || "";

  // State điều khiển bộ lọc và phân trang tìm kiếm
  const [keywordInput, setKeywordInput] = useState(initialKeyword);
  const [activeKeyword, setActiveKeyword] = useState(initialKeyword);
  const [selectedType, setSelectedType] = useState(initialType);
  const [page, setPage] = useState(1);
  const pageSize = 8;

  // State lưu kết quả trả về từ search-service
  const [documents, setDocuments] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(false);

  // State quản lý hộp thoại (Dialog) tạo/cập nhật tài liệu tìm kiếm thủ công
  const [openIndexDialog, setOpenIndexDialog] = useState(false);
  const [submittingDoc, setSubmittingDoc] = useState(false);
  const [docForm, setDocForm] = useState({
    id: "",
    type: "book",
    title: "",
    content: "",
    authorName: "",
    tagsText: "",
  });

  // State hiển thị thông báo Snackbar
  const [snackbar, setSnackbar] = useState({
    open: false,
    message: "",
    severity: "success",
  });

  const showMessage = (message, severity = "success") => {
    setSnackbar({ open: true, message, severity });
  };

  // Đồng bộ lại state khi URL thay đổi (ví dụ khi người dùng tìm từ thanh Header)
  useEffect(() => {
    const qParam = searchParams.get("q") || "";
    const typeParam = searchParams.get("type") || "";
    setKeywordInput(qParam);
    setActiveKeyword(qParam);
    setSelectedType(typeParam);
    setPage(1);
  }, [searchParams]);

  /**
   * Hàm gọi API search-service để lấy danh sách tài liệu khớp điều kiện.
   */
  const fetchSearchResults = useCallback(async () => {
    if (!isAuthenticated()) {
      navigate("/login");
      return;
    }

    setLoading(true);
    try {
      const response = await searchDocuments({
        keyword: activeKeyword,
        type: selectedType,
        page,
        size: pageSize,
      });
      const result = response?.data?.result;
      setDocuments(result?.data || []);
      setTotalElements(result?.totalElements || 0);
      setTotalPages(result?.totalPages || 0);
    } catch (error) {
      console.error("Error searching documents:", error);
      if (error.response?.status === 401) {
        logOut();
        navigate("/login");
        return;
      }
      showMessage(
        "Không thể tải kết quả tìm kiếm. Hãy kiểm tra search-service & Elasticsearch đã chạy chưa.",
        "error"
      );
    } finally {
      setLoading(false);
    }
  }, [activeKeyword, selectedType, page, navigate]);

  useEffect(() => {
    fetchSearchResults();
  }, [fetchSearchResults]);

  /**
   * Cập nhật URL query params (?q=...&type=...) khi người dùng bấm Tìm kiếm hoặc đổi bộ lọc.
   */
  const updateUrlParams = (newKeyword, newType) => {
    const nextParams = {};
    if (newKeyword && newKeyword.trim()) {
      nextParams.q = newKeyword.trim();
    }
    if (newType) {
      nextParams.type = newType;
    }
    setSearchParams(nextParams);
  };

  const handleSearchSubmit = (e) => {
    if (e) e.preventDefault();
    setPage(1);
    setActiveKeyword(keywordInput.trim());
    updateUrlParams(keywordInput, selectedType);
  };

  const handleTypeChange = (newType) => {
    setPage(1);
    setSelectedType(newType);
    updateUrlParams(keywordInput, newType);
  };

  const handleClearSearch = () => {
    setKeywordInput("");
    setActiveKeyword("");
    setPage(1);
    updateUrlParams("", selectedType);
  };

  /**
   * Xử lý đánh chỉ mục (Index) một tài liệu mới hoặc cập nhật tài liệu từ Dialog.
   */
  const handleCreateOrUpdateDocument = async () => {
    if (!docForm.id.trim() || !docForm.type.trim() || !docForm.title.trim()) {
      showMessage("Vui lòng nhập đầy đủ ID, Phân loại (Type) và Tiêu đề!", "warning");
      return;
    }

    setSubmittingDoc(true);
    try {
      const tags = docForm.tagsText
        .split(",")
        .map((tag) => tag.trim())
        .filter(Boolean);

      await indexSearchDocument({
        id: docForm.id.trim(),
        type: docForm.type.trim(),
        title: docForm.title.trim(),
        content: docForm.content.trim(),
        authorName: docForm.authorName.trim(),
        tags,
      });

      showMessage("Đã lưu tài liệu vào chỉ mục Elasticsearch thành công!");
      setOpenIndexDialog(false);
      setDocForm({
        id: "",
        type: "book",
        title: "",
        content: "",
        authorName: "",
        tagsText: "",
      });
      fetchSearchResults();
    } catch (error) {
      console.error("Error indexing document:", error);
      showMessage("Lỗi khi đánh chỉ mục tài liệu vào search-service.", "error");
    } finally {
      setSubmittingDoc(false);
    }
  };

  /**
   * Nạp nhanh bộ dữ liệu mẫu (Sách & Bài viết) vào Elasticsearch chỉ với 1 cú nhấp chuột.
   */
  const handleSeedSampleData = async () => {
    setSubmittingDoc(true);
    try {
      await Promise.all(
        SAMPLE_DOCUMENTS.map((doc) => indexSearchDocument(doc))
      );
      showMessage("Đã nạp 3 tài liệu mẫu vào Elasticsearch! Hãy thử tìm kiếm.");
      fetchSearchResults();
    } catch (error) {
      console.error("Error seeding sample documents:", error);
      showMessage("Không thể nạp dữ liệu mẫu. Hãy kiểm tra search-service.", "error");
    } finally {
      setSubmittingDoc(false);
    }
  };

  /**
   * Xóa một tài liệu khỏi chỉ mục Elasticsearch theo ID.
   */
  const handleDeleteDocument = async (id) => {
    try {
      await deleteSearchDocument(id);
      showMessage(`Đã xóa tài liệu "${id}" khỏi chỉ mục tìm kiếm.`);
      fetchSearchResults();
    } catch (error) {
      console.error("Error deleting document:", error);
      showMessage("Không thể xóa tài liệu.", "error");
    }
  };

  return (
    <Scene>
      <Snackbar
        open={snackbar.open}
        autoHideDuration={5000}
        onClose={() => setSnackbar((prev) => ({ ...prev, open: false }))}
        anchorOrigin={{ vertical: "top", horizontal: "right" }}
        sx={{ mt: "64px" }}
      >
        <Alert
          severity={snackbar.severity}
          onClose={() => setSnackbar((prev) => ({ ...prev, open: false }))}
          sx={{ width: "100%" }}
        >
          {snackbar.message}
        </Alert>
      </Snackbar>

      <Box
        sx={{
          width: "100%",
          maxWidth: 780,
          mt: 3,
          mb: 5,
          px: 2,
          display: "flex",
          flexDirection: "column",
          gap: 2.5,
        }}
      >
        {/* Khung điều khiển Tìm kiếm & Bộ lọc */}
        <Card sx={{ boxShadow: 3, borderRadius: 2, p: 2.5 }}>
          <Box
            sx={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              flexWrap: "wrap",
              gap: 1,
              mb: 2,
            }}
          >
            <Box>
              <Typography variant="h6" sx={{ fontWeight: 700 }}>
                Tìm kiếm toàn văn (Elasticsearch)
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Tra cứu sách, bài viết và người dùng với hỗ trợ tìm kiếm mờ (Fuzzy Search)
              </Typography>
            </Box>
            <Stack direction="row" spacing={1}>
              <Tooltip title="Tự động thêm 3 tài liệu mẫu (Dune, Clean Code, Spring Boot) vào Elasticsearch để test">
                <Button
                  size="small"
                  variant="outlined"
                  startIcon={<AutoAwesomeIcon />}
                  onClick={handleSeedSampleData}
                  disabled={submittingDoc}
                >
                  Nạp dữ liệu mẫu
                </Button>
              </Tooltip>
              <Button
                size="small"
                variant="contained"
                startIcon={<AddCircleOutlineIcon />}
                onClick={() =>
                  setDocForm((prev) => ({
                    ...prev,
                    id: `doc-${Date.now()}`,
                  })) || setOpenIndexDialog(true)
                }
              >
                Thêm Document
              </Button>
            </Stack>
          </Box>

          {/* Thanh nhập từ khóa tìm kiếm */}
          <Box
            component="form"
            onSubmit={handleSearchSubmit}
            sx={{ display: "flex", gap: 1.5, mb: 2 }}
          >
            <TextField
              fullWidth
              size="small"
              placeholder="Nhập từ khóa (tiêu đề, nội dung, tên tác giả, tag)..."
              value={keywordInput}
              onChange={(e) => setKeywordInput(e.target.value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon color="action" />
                  </InputAdornment>
                ),
                endAdornment: keywordInput && (
                  <InputAdornment position="end">
                    <IconButton size="small" onClick={handleClearSearch}>
                      <ClearIcon fontSize="small" />
                    </IconButton>
                  </InputAdornment>
                ),
              }}
            />
            <Button
              type="submit"
              variant="contained"
              sx={{ px: 3, whiteSpace: "nowrap" }}
            >
              Tìm kiếm
            </Button>
          </Box>

          {/* Các nút Chip lọc theo loại tài liệu (type) */}
          <Stack
            direction="row"
            spacing={1}
            alignItems="center"
            flexWrap="wrap"
            useFlexGap
          >
            <Typography variant="body2" color="text.secondary" sx={{ mr: 0.5 }}>
              Phân loại:
            </Typography>
            {TYPE_FILTERS.map((filter) => (
              <Chip
                key={filter.value || "all"}
                label={filter.label}
                clickable
                color={selectedType === filter.value ? "primary" : "default"}
                variant={selectedType === filter.value ? "filled" : "outlined"}
                onClick={() => handleTypeChange(filter.value)}
              />
            ))}
          </Stack>
        </Card>

        {/* Khu vực hiển thị danh sách kết quả tìm kiếm */}
        <Card sx={{ boxShadow: 2, borderRadius: 2, p: 2.5 }}>
          <Box
            sx={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              mb: 2,
            }}
          >
            <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
              {activeKeyword
                ? `Kết quả cho "${activeKeyword}" (${totalElements})`
                : `Tài liệu đã đánh chỉ mục (${totalElements})`}
            </Typography>
            {selectedType && (
              <Chip
                size="small"
                label={`Đang lọc: ${selectedType.toUpperCase()}`}
                onDelete={() => handleTypeChange("")}
              />
            )}
          </Box>

          <Divider sx={{ mb: 2 }} />

          {loading ? (
            <Box sx={{ display: "flex", justifyContent: "center", py: 6 }}>
              <CircularProgress />
            </Box>
          ) : documents.length === 0 ? (
            <Box sx={{ textAlign: "center", py: 5 }}>
              <Typography variant="body1" sx={{ fontWeight: 500, mb: 1 }}>
                Không tìm thấy tài liệu nào phù hợp.
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                Bạn có thể bấm nút "Nạp dữ liệu mẫu" ở góc trên hoặc tạo một bài viết mới để dữ liệu tự động đồng bộ vào Elasticsearch.
              </Typography>
            </Box>
          ) : (
            <Stack spacing={2}>
              {documents.map((doc) => {
                const badge = getTypeBadgeProps(doc.type);
                return (
                  <Card
                    key={doc.id}
                    variant="outlined"
                    sx={{
                      borderRadius: 2,
                      transition: "box-shadow 0.2s",
                      "&:hover": { boxShadow: 2 },
                    }}
                  >
                    <CardContent sx={{ pb: "16px !important" }}>
                      <Box
                        sx={{
                          display: "flex",
                          justifyContent: "space-between",
                          alignItems: "flex-start",
                          gap: 2,
                        }}
                      >
                        <Box sx={{ display: "flex", gap: 2, width: "100%" }}>
                          <Avatar
                            src={doc.thumbnailUrl || ""}
                            sx={{
                              bgcolor: `${badge.color}.main`,
                              width: 44,
                              height: 44,
                              mt: 0.5,
                            }}
                          >
                            {badge.icon}
                          </Avatar>
                          <Box sx={{ flexGrow: 1 }}>
                            <Stack
                              direction="row"
                              spacing={1}
                              alignItems="center"
                              flexWrap="wrap"
                              sx={{ mb: 0.5 }}
                            >
                              <Chip
                                size="small"
                                color={badge.color}
                                icon={badge.icon}
                                label={badge.label}
                                sx={{ height: 22, fontWeight: 600 }}
                              />
                              <Typography
                                variant="subtitle1"
                                sx={{ fontWeight: 700 }}
                              >
                                {doc.title}
                              </Typography>
                            </Stack>

                            {doc.content && (
                              <Typography
                                variant="body2"
                                color="text.primary"
                                sx={{ mb: 1, whiteSpace: "pre-line" }}
                              >
                                {doc.content}
                              </Typography>
                            )}

                            <Stack
                              direction="row"
                              spacing={1.5}
                              alignItems="center"
                              flexWrap="wrap"
                              useFlexGap
                            >
                              {doc.authorName && (
                                <Typography
                                  variant="caption"
                                  color="text.secondary"
                                >
                                  Tác giả: <strong>{doc.authorName}</strong>
                                </Typography>
                              )}
                              {doc.createdAt && (
                                <Typography
                                  variant="caption"
                                  color="text.secondary"
                                >
                                  • {dayjs(doc.createdAt).format("DD/MM/YYYY HH:mm")}
                                </Typography>
                              )}
                              <Typography
                                variant="caption"
                                color="text.disabled"
                              >
                                • ID: {doc.id}
                              </Typography>
                            </Stack>

                            {Array.isArray(doc.tags) && doc.tags.length > 0 && (
                              <Stack
                                direction="row"
                                spacing={0.8}
                                sx={{ mt: 1 }}
                                flexWrap="wrap"
                                useFlexGap
                              >
                                {doc.tags.map((tag) => (
                                  <Chip
                                    key={tag}
                                    label={`#${tag}`}
                                    size="small"
                                    variant="outlined"
                                    onClick={() => {
                                      setKeywordInput(tag);
                                      setActiveKeyword(tag);
                                      updateUrlParams(tag, selectedType);
                                    }}
                                    sx={{ height: 20, fontSize: 11 }}
                                  />
                                ))}
                              </Stack>
                            )}
                          </Box>
                        </Box>

                        <Tooltip title="Xóa tài liệu này khỏi Elasticsearch index">
                          <IconButton
                            size="small"
                            color="error"
                            onClick={() => handleDeleteDocument(doc.id)}
                          >
                            <DeleteOutlineIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      </Box>
                    </CardContent>
                  </Card>
                );
              })}
            </Stack>
          )}

          {/* Phân trang kết quả */}
          {totalPages > 1 && (
            <Box sx={{ display: "flex", justifyContent: "center", mt: 3 }}>
              <Pagination
                count={totalPages}
                page={page}
                color="primary"
                onChange={(_, value) => setPage(value)}
              />
            </Box>
          )}
        </Card>
      </Box>

      {/* Dialog thêm mới hoặc cập nhật tài liệu vào Elasticsearch */}
      <Dialog
        open={openIndexDialog}
        onClose={() => setOpenIndexDialog(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle sx={{ fontWeight: 700 }}>
          Đánh chỉ mục tài liệu (Index Search Document)
        </DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2} sx={{ mt: 0.5 }}>
            <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
              <TextField
                label="Document ID *"
                size="small"
                fullWidth
                value={docForm.id}
                onChange={(e) =>
                  setDocForm((prev) => ({ ...prev, id: e.target.value }))
                }
                helperText="Nếu trùng ID đã có sẽ tự động cập nhật (Upsert)"
              />
              <TextField
                select
                label="Phân loại (Type) *"
                size="small"
                sx={{ minWidth: 160 }}
                value={docForm.type}
                onChange={(e) =>
                  setDocForm((prev) => ({ ...prev, type: e.target.value }))
                }
              >
                <MenuItem value="book">book (Sách)</MenuItem>
                <MenuItem value="post">post (Bài viết)</MenuItem>
                <MenuItem value="user">user (Người dùng)</MenuItem>
              </TextField>
            </Stack>
            <TextField
              label="Tiêu đề (Title) *"
              size="small"
              fullWidth
              value={docForm.title}
              onChange={(e) =>
                setDocForm((prev) => ({ ...prev, title: e.target.value }))
              }
            />
            <TextField
              label="Nội dung (Content)"
              size="small"
              fullWidth
              multiline
              rows={3}
              value={docForm.content}
              onChange={(e) =>
                setDocForm((prev) => ({ ...prev, content: e.target.value }))
              }
            />
            <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
              <TextField
                label="Tên tác giả (Author Name)"
                size="small"
                fullWidth
                value={docForm.authorName}
                onChange={(e) =>
                  setDocForm((prev) => ({
                    ...prev,
                    authorName: e.target.value,
                  }))
                }
              />
              <TextField
                label="Tags (cách nhau bởi dấu phẩy)"
                size="small"
                fullWidth
                placeholder="java, springboot, book"
                value={docForm.tagsText}
                onChange={(e) =>
                  setDocForm((prev) => ({ ...prev, tagsText: e.target.value }))
                }
              />
            </Stack>
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button onClick={() => setOpenIndexDialog(false)}>Hủy</Button>
          <Button
            variant="contained"
            onClick={handleCreateOrUpdateDocument}
            disabled={submittingDoc}
          >
            {submittingDoc ? "Đang lưu..." : "Lưu vào Elasticsearch"}
          </Button>
        </DialogActions>
      </Dialog>
    </Scene>
  );
}
