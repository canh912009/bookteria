export const CONFIG = {
  API_GATEWAY: "http://192.168.105.77:8888/api/v1",
};

export const API = {
  LOGIN: "/identity/auth/token",
  MY_INFO: "/profile/users/my-profile",
  MY_POST: "/post/my-posts",
  CREATE_POST: "/post/create",
  UPDATE_PROFILE: "/profile/users/my-profile",
  UPDATE_AVATAR: "/profile/users/avatar",
  SEARCH_USER: "/profile/users/search",
  MY_CONVERSATIONS: "/chat/conversations/my-conversations",
  CREATE_CONVERSATION: "/chat/conversations/create",
  CREATE_MESSAGE: "/chat/messages/create",
  GET_CONVERSATION_MESSAGES: "/chat/messages",
  // Các endpoint của search-service đi qua API Gateway (/api/v1/search/**)
  SEARCH: "/search",
  SEARCH_DOCUMENTS: "/search/documents",
};
