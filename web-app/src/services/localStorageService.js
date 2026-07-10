export const KEY_TOKEN = "accessToken";
export const KEY_DEVICE_ID = "deviceId";

export const setToken = (token) => {
  localStorage.setItem(KEY_TOKEN, token);
};

export const getToken = () => {
  return localStorage.getItem(KEY_TOKEN);
};

export const removeToken = () => {
  return localStorage.removeItem(KEY_TOKEN);
};

export const getDeviceId = () => {
  const currentDeviceId = localStorage.getItem(KEY_DEVICE_ID);

  if (currentDeviceId) return currentDeviceId;

  const newDeviceId =
    window.crypto?.randomUUID?.() ??
    `${Date.now()}-${Math.random().toString(36).substring(2)}`;

  localStorage.setItem(KEY_DEVICE_ID, newDeviceId);

  return newDeviceId;
};
