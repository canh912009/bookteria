import { BrowserRouter as Router, Route, Routes } from "react-router-dom";
import Login from "../pages/Login";
import Home from "../pages/Home";
import Profile from "../pages/Profile";
import Chat from "../pages/Chat";
import Search from "../pages/Search";

const AppRoutes = () => {
  return (
    <Router>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/" element={<Home />} />
        <Route path="/profile" element={<Profile />} />
        <Route path="/chat" element={<Chat />} />
        {/* Trang tìm kiếm toàn văn kết nối với search-service */}
        <Route path="/search" element={<Search />} />
      </Routes>
    </Router>
  );
};

export default AppRoutes;
