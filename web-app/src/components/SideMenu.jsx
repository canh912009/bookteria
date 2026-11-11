import * as React from "react";
import Divider from "@mui/material/Divider";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import Toolbar from "@mui/material/Toolbar";
import HomeIcon from "@mui/icons-material/Home";
import SearchIcon from "@mui/icons-material/Search";
import PeopleIcon from "@mui/icons-material/People";
import GroupsIcon from "@mui/icons-material/Groups";
import ChatIcon from "@mui/icons-material/Chat";
import { Link, useLocation } from "react-router-dom";

function SideMenu() {
  const location = useLocation();

  return (
    <>
      <Toolbar />
      <List>
        <ListItem key={"home"} disablePadding>
          <ListItemButton
            component={Link}
            to="/"
            selected={location.pathname === "/"}
          >
            <ListItemIcon>
              <HomeIcon />
            </ListItemIcon>
            <ListItemText
              primary={"Home"}
              primaryTypographyProps={{ style: { fontWeight: "bold" } }}
            />
          </ListItemButton>
        </ListItem>
        {/* Mục Search dẫn tới trang Tìm kiếm toàn văn (/search) */}
        <ListItem key={"search"} disablePadding>
          <ListItemButton
            component={Link}
            to="/search"
            selected={location.pathname === "/search"}
          >
            <ListItemIcon>
              <SearchIcon />
            </ListItemIcon>
            <ListItemText
              primary={"Search"}
              primaryTypographyProps={{ style: { fontWeight: "bold" } }}
            />
          </ListItemButton>
        </ListItem>
        <ListItem key={"friends"} disablePadding>
          <ListItemButton>
            <ListItemIcon>
              <PeopleIcon />
            </ListItemIcon>
            <ListItemText
              primary={"Friends"}
              primaryTypographyProps={{ style: { fontWeight: "bold" } }}
            />
          </ListItemButton>
        </ListItem>{" "}
        <ListItem key={"groups"} disablePadding>
          <ListItemButton>
            <ListItemIcon>
              <GroupsIcon />
            </ListItemIcon>
            <ListItemText
              primary={"Groups"}
              primaryTypographyProps={{ style: { fontWeight: "bold" } }}
            />
          </ListItemButton>
        </ListItem>
        <ListItem key={"chat"} disablePadding>
          <ListItemButton
            component={Link}
            to="/chat"
            selected={location.pathname === "/chat"}
          >
            <ListItemIcon>
              <ChatIcon />
            </ListItemIcon>
            <ListItemText
              primary={"Chat"}
              primaryTypographyProps={{ style: { fontWeight: "bold" } }}
            />
          </ListItemButton>
        </ListItem>
      </List>
      <Divider />
    </>
  );
}

export default SideMenu;
