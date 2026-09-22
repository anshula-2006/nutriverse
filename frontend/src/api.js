import { Capacitor } from "@capacitor/core";

const platform = Capacitor.getPlatform();

const WEB_API =
  import.meta.env.VITE_API_URL?.trim()
  || "http://localhost:8080";

const ANDROID_API =
  import.meta.env.VITE_ANDROID_API_URL?.trim()
  || "http://10.0.2.2:8080";

export const API_URL = (
  platform === "android"
    ? ANDROID_API
    : WEB_API
).replace(/\/+$/, "");


// ---------------------------------------------------------
// FETCH WITH TIMEOUT
// ---------------------------------------------------------

export async function apiFetch(
  url,
  options = {},
  timeout = 15000
) {
  const controller = new AbortController();

  const timer = setTimeout(
    () => controller.abort(),
    timeout
  );

  try {
    return await fetch(url, {
      ...options,
      signal: controller.signal
    });

  } catch (error) {
    if (error.name === "AbortError") {
      throw new Error(
        "Server connection timed out. Please try again."
      );
    }

    throw error;

  } finally {
    clearTimeout(timer);
  }
}


// ---------------------------------------------------------
// USER SESSION
// ---------------------------------------------------------

export function readUser() {
  try {
    const user = JSON.parse(
      localStorage.getItem("user") || "{}"
    );

    return user
      && typeof user === "object"
      && !Array.isArray(user)
      ? {
          name:
            typeof user.name === "string"
              ? user.name
              : "User"
        }
      : {};

  } catch {
    localStorage.removeItem("user");
    return {};
  }
}


export function clearSession() {
  localStorage.removeItem("token");
  localStorage.removeItem("user");
}


export function saveSession(data) {
  if (
    typeof data?.token !== "string"
    || !data.token.trim()
  ) {
    throw new Error(
      "The server did not return a login token."
    );
  }

  localStorage.setItem(
    "token",
    data.token
  );

  localStorage.setItem(
    "user",
    JSON.stringify({
      name: data.name,
      username: data.username
    })
  );
}


// ---------------------------------------------------------
// RESPONSE HELPERS
// ---------------------------------------------------------

export function handleUnauthorized(
  response,
  navigate
) {
  if (response.status !== 401)
    return false;

  clearSession();

  navigate("/login", {
    replace: true
  });

  return true;
}


export async function readResponse(
  response,
  fallback
) {
  const data = await response
    .json()
    .catch(() => null);

  if (!response.ok) {
    const detail =
      data?.message
      || data?.detail
      || data?.error;

    throw new Error(
      typeof detail === "string"
        ? detail
        : `${fallback} (${response.status})`
    );
  }

  if (
    data === null
    || typeof data !== "object"
  ) {
    throw new Error(
      "The server returned an invalid response."
    );
  }

  return data;
}