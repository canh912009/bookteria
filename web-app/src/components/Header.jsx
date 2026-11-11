import * as React from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { styled, alpha } from "@mui/material/styles";
import Box from "@mui/material/Box";
import IconButton from "@mui/material/IconButton";
import InputBase from "@mui/material/InputBase";
import Badge from "@mui/material/Badge";
import MenuItem from "@mui/material/MenuItem";
import Menu from "@mui/material/Menu";
import Paper from "@mui/material/Paper";
import List from "@mui/material/List";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemAvatar from "@mui/material/ListItemAvatar";
import ListItemText from "@mui/material/ListItemText";
import Avatar from "@mui/material/Avatar";
import Typography from "@mui/material/Typography";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
import Divider from "@mui/material/Divider";
import ClickAwayListener from "@mui/material/ClickAwayListener";
import SearchIcon from "@mui/icons-material/Search";
import ClearIcon from "@mui/icons-material/Clear";
import MenuBookIcon from "@mui/icons-material/MenuBook";
import ArticleIcon from "@mui/icons-material/Article";
import PersonIcon from "@mui/icons-material/Person";
import AccountCircle from "@mui/icons-material/AccountCircle";
import MailIcon from "@mui/icons-material/Mail";
import NotificationsIcon from "@mui/icons-material/Notifications";
import MoreIcon from "@mui/icons-material/MoreVert";
import { logOut } from "../services/authenticationService";
import { searchDocuments } from "../services/searchService";

const Search = styled("div")(({ theme }) => ({
  position: "relative",
  borderRadius: theme.shape.borderRadius,
  backgroundColor: alpha(theme.palette.common.white, 0.15),
  "&:hover": {
    backgroundColor: alpha(theme.palette.common.white, 0.25),
  },
  marginRight: theme.spacing(2),
  marginLeft: 0,
  width: "100%",
  [theme.breakpoints.up("sm")]: {
    marginLeft: theme.spacing(3),
    width: "auto",
  },
}));

const SearchIconWrapper = styled("div")(({ theme }) => ({
  padding: theme.spacing(0, 2),
  height: "100%",
  position: "absolute",
  pointerEvents: "none",
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
}));

const StyledInputBase = styled(InputBase)(({ theme }) => ({
  color: "inherit",
  width: "100%",
  "& .MuiInputBase-input": {
    padding: theme.spacing(1, 4, 1, 0),
    paddingLeft: `calc(1em + ${theme.spacing(4)})`,
    transition: theme.transitions.create("width"),
    width: "100%",
    [theme.breakpoints.up("md")]: {
      width: "30ch",
      "&:focus": {
        width: "38ch",
      },
    },
  },
}));

/**
 * Hàm phụ trợ trả về icon tương ứng với từng loại tài liệu (book, post, user).
 */
const getTypeIcon = (type) => {
  switch ((type || "").toLowerCase()) {
    case "book":
      return <MenuBookIcon fontSize="small" />;
    case "post":
      return <ArticleIcon fontSize="small" />;
    case "user":
      return <PersonIcon fontSize="small" />;
    default:
      return <SearchIcon fontSize="small" />;
  }
};

export default function Header() {
  const navigate = useNavigate();
  const location = useLocation();

  const [anchorEl, setAnchorEl] = React.useState(null);
  const [mobileMoreAnchorEl, setMobileMoreAnchorEl] = React.useState(null);

  // State quản lý từ khóa tìm kiếm và danh sách gợi ý nhanh trên Header
  const [searchQuery, setSearchQuery] = React.useState("");
  const [suggestions, setSuggestions] = React.useState([]);
  const [totalSuggestions, setTotalSuggestions] = React.useState(0);
  const [searching, setSearching] = React.useState(false);
  const [showDropdown, setShowDropdown] = React.useState(false);

  const isMenuOpen = Boolean(anchorEl);
  const isMobileMenuOpen = Boolean(mobileMoreAnchorEl);

  // Đồng bộ ô tìm kiếm trên Header với query param ?q=... khi đang ở trang /search
  React.useEffect(() => {
    if (location.pathname === "/search") {
      const params = new URLSearchParams(location.search);
      setSearchQuery(params.get("q") || "");
    }
  }, [location.pathname, location.search]);

  // Kỹ thuật Debounce (trì hoãn 350ms sau khi ngừng gõ) để tránh gọi API liên tục mỗi lần nhấn phím
  React.useEffect(() => {
    const trimmed = searchQuery.trim();
    if (!trimmed) {
      setSuggestions([]);
      setTotalSuggestions(0);
      setSearching(false);
      return;
    }

    const timerId = setTimeout(async () => {
      try {
        setSearching(true);
        const response = await searchDocuments({
          keyword: trimmed,
          page: 1,
          size: 5,
        });
        const pageResult = response?.data?.result;
        setSuggestions(pageResult?.data || []);
        setTotalSuggestions(pageResult?.totalElements || 0);
      } catch (error) {
        console.error("Quick search error:", error);
        setSuggestions([]);
        setTotalSuggestions(0);
      } finally {
        setSearching(false);
      }
    }, 350);

    return () => clearTimeout(timerId);
  }, [searchQuery]);

  /**
   * Chuyển hướng sang trang kết quả tìm kiếm đầy đủ (/search?q=...)
   */
  const goToSearchPage = (keyword = searchQuery, type = "") => {
    setShowDropdown(false);
    const params = new URLSearchParams();
    if (keyword && keyword.trim()) {
      params.set("q", keyword.trim());
    }
    if (type) {
      params.set("type", type);
    }
    const queryString = params.toString();
    navigate(queryString ? `/search?${queryString}` : "/search");
  };

  /**
   * Xử lý khi người dùng nhấn phím Enter hoặc Escape trong ô tìm kiếm
   */
  const handleSearchKeyDown = (event) => {
    if (event.key === "Enter") {
      event.preventDefault();
      goToSearchPage(searchQuery);
    } else if (event.key === "Escape") {
      setShowDropdown(false);
    }
  };

  const handleProfileMenuOpen = (event) => {
    setAnchorEl(event.currentTarget);
  };

  const handleMobileMenuClose = () => {
    setMobileMoreAnchorEl(null);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
    handleMobileMenuClose();
  };

  const handleOpenProfile = () => {
    setAnchorEl(null);
    navigate("/profile");
  };

  const handleMobileMenuOpen = (event) => {
    setMobileMoreAnchorEl(event.currentTarget);
  };

  const handleLogout = () => {
    handleMenuClose();
    logOut();
    window.location.href = "/login";
  };

  const menuId = "primary-search-account-menu";
  const renderMenu = (
    <Menu
      anchorEl={anchorEl}
      anchorOrigin={{
        vertical: "top",
        horizontal: "right",
      }}
      id={menuId}
      keepMounted
      transformOrigin={{
        vertical: "top",
        horizontal: "right",
      }}
      open={isMenuOpen}
      onClose={handleMenuClose}
    >
      <MenuItem onClick={handleOpenProfile}>Profile</MenuItem>
      <MenuItem onClick={handleMenuClose}>Settings</MenuItem>
      <MenuItem onClick={handleLogout}>Log Out</MenuItem>
    </Menu>
  );

  const mobileMenuId = "primary-search-account-menu-mobile";
  const renderMobileMenu = (
    <Menu
      anchorEl={mobileMoreAnchorEl}
      anchorOrigin={{
        vertical: "top",
        horizontal: "right",
      }}
      id={mobileMenuId}
      keepMounted
      transformOrigin={{
        vertical: "top",
        horizontal: "right",
      }}
      open={isMobileMenuOpen}
      onClose={handleMobileMenuClose}
    >
      <MenuItem>
        <IconButton size="large" aria-label="show 2 new mails" color="inherit">
          <Badge badgeContent={2} color="error">
            <MailIcon />
          </Badge>
        </IconButton>
        <p>Messages</p>
      </MenuItem>
      <MenuItem>
        <IconButton
          size="large"
          aria-label="show 4 new notifications"
          color="inherit"
        >
          <Badge badgeContent={4} color="error">
            <NotificationsIcon />
          </Badge>
        </IconButton>
        <p>Notifications</p>
      </MenuItem>
      <MenuItem onClick={handleProfileMenuOpen}>
        <IconButton
          size="large"
          aria-label="account of current user"
          aria-controls="primary-search-account-menu"
          aria-haspopup="true"
          color="inherit"
        >
          <AccountCircle />
        </IconButton>
        <p>Profile</p>
      </MenuItem>
    </Menu>
  );

  return (
    <>
      <IconButton
        size="large"
        edge="start"
        color="inherit"
        aria-label="open home"
        sx={{ mr: 2 }}
        onClick={() => navigate("/")}
      >
        <Box
          component={"img"}
          style={{
            width: "35px",
            height: "35px",
            borderRadius: 6,
          }}
          src="/logo/devteria-logo.png"
        />
      </IconButton>

      {/* Khung tìm kiếm trên Header tích hợp Dropdown gợi ý trực tiếp từ Elasticsearch */}
      <ClickAwayListener onClickAway={() => setShowDropdown(false)}>
        <Box sx={{ position: "relative", width: { xs: "100%", sm: "auto" } }}>
          <Search>
            <SearchIconWrapper>
              <SearchIcon />
            </SearchIconWrapper>
            <StyledInputBase
              placeholder="Tìm sách, bài viết, người dùng… (Nhấn Enter)"
              inputProps={{ "aria-label": "search" }}
              value={searchQuery}
              onFocus={() => setShowDropdown(true)}
              onChange={(e) => {
                setSearchQuery(e.target.value);
                setShowDropdown(true);
              }}
              onKeyDown={handleSearchKeyDown}
            />
            {searchQuery && (
              <IconButton
                size="small"
                aria-label="clear search"
                onClick={() => {
                  setSearchQuery("");
                  setSuggestions([]);
                  setTotalSuggestions(0);
                }}
                sx={{
                  position: "absolute",
                  right: 4,
                  top: "50%",
                  transform: "translateY(-50%)",
                  color: "inherit",
                }}
              >
                <ClearIcon fontSize="small" />
              </IconButton>
            )}
          </Search>

          {/* Bảng gợi ý nhanh (Dropdown) hiển thị khi người dùng đang gõ từ khóa */}
          {showDropdown && searchQuery.trim() && (
            <Paper
              elevation={6}
              sx={{
                position: "absolute",
                top: "calc(100% + 8px)",
                left: { xs: 0, sm: 24 },
                width: { xs: "100%", sm: 420 },
                maxHeight: 420,
                overflowY: "auto",
                zIndex: 1400,
                borderRadius: 2,
                color: "text.primary",
              }}
            >
              {searching ? (
                <Box
                  sx={{
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    gap: 1.5,
                    p: 2.5,
                  }}
                >
                  <CircularProgress size={20} />
                  <Typography variant="body2" color="text.secondary">
                    Đang tìm kiếm trên Elasticsearch...
                  </Typography>
                </Box>
              ) : suggestions.length > 0 ? (
                <>
                  <List dense disablePadding>
                    {suggestions.map((item) => (
                      <ListItemButton
                        key={item.id}
                        onClick={() => goToSearchPage(item.title, item.type)}
                        sx={{ py: 1, px: 2 }}
                      >
                        <ListItemAvatar>
                          <Avatar
                            src={item.thumbnailUrl || ""}
                            sx={{
                              width: 36,
                              height: 36,
                              bgcolor: "primary.main",
                            }}
                          >
                            {getTypeIcon(item.type)}
                          </Avatar>
                        </ListItemAvatar>
                        <ListItemText
                          primary={
                            <Box
                              sx={{
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "space-between",
                                gap: 1,
                              }}
                            >
                              <Typography
                                variant="body2"
                                sx={{
                                  fontWeight: 600,
                                  overflow: "hidden",
                                  textOverflow: "ellipsis",
                                  whiteSpace: "nowrap",
                                }}
                              >
                                {item.title}
                              </Typography>
                              <Chip
                                label={item.type}
                                size="small"
                                variant="outlined"
                                sx={{
                                  height: 20,
                                  fontSize: 11,
                                  textTransform: "uppercase",
                                }}
                              />
                            </Box>
                          }
                          secondary={
                            <Typography
                              variant="caption"
                              color="text.secondary"
                              sx={{
                                display: "block",
                                overflow: "hidden",
                                textOverflow: "ellipsis",
                                whiteSpace: "nowrap",
                              }}
                            >
                              {item.authorName ? `${item.authorName} • ` : ""}
                              {item.content || "Không có mô tả"}
                            </Typography>
                          }
                        />
                      </ListItemButton>
                    ))}
                  </List>
                  <Divider />
                  <ListItemButton
                    onClick={() => goToSearchPage(searchQuery)}
                    sx={{ justifyContent: "center", py: 1.2 }}
                  >
                    <Typography
                      variant="body2"
                      color="primary"
                      sx={{ fontWeight: 600 }}
                    >
                      Xem tất cả {totalSuggestions} kết quả cho "{searchQuery.trim()}"
                    </Typography>
                  </ListItemButton>
                </>
              ) : (
                <Box sx={{ p: 2, textAlign: "center" }}>
                  <Typography variant="body2" color="text.secondary">
                    Không tìm thấy kết quả cho "{searchQuery.trim()}"
                  </Typography>
                  <Typography
                    variant="caption"
                    color="primary"
                    sx={{
                      cursor: "pointer",
                      display: "block",
                      mt: 0.5,
                      fontWeight: 600,
                    }}
                    onClick={() => goToSearchPage(searchQuery)}
                  >
                    Mở trang Tìm kiếm nâng cao & Quản lý dữ liệu mẫu →
                  </Typography>
                </Box>
              )}
            </Paper>
          )}
        </Box>
      </ClickAwayListener>

      <Box sx={{ flexGrow: 1 }} />
      <Box sx={{ display: { xs: "none", md: "flex" } }}>
        <IconButton size="large" aria-label="show 4 new mails" color="inherit">
          <Badge badgeContent={4} color="error">
            <MailIcon />
          </Badge>
        </IconButton>
        <IconButton
          size="large"
          aria-label="show 17 new notifications"
          color="inherit"
        >
          <Badge badgeContent={17} color="error">
            <NotificationsIcon />
          </Badge>
        </IconButton>
        <IconButton
          size="large"
          edge="end"
          aria-label="account of current user"
          aria-controls={menuId}
          aria-haspopup="true"
          onClick={handleProfileMenuOpen}
          color="inherit"
        >
          <AccountCircle />
        </IconButton>
      </Box>
      <Box sx={{ display: { xs: "flex", md: "none" } }}>
        <IconButton
          size="large"
          aria-label="show more"
          aria-controls={mobileMenuId}
          aria-haspopup="true"
          onClick={handleMobileMenuOpen}
          color="inherit"
        >
          <MoreIcon />
        </IconButton>
      </Box>
      {renderMobileMenu}
      {renderMenu}
    </>
  );
}
