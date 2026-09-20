import { Capacitor } from "@capacitor/core";

const configuredApiUrl = import.meta.env.VITE_API_URL?.trim();
const developmentApiUrl = Capacitor.getPlatform() === "android"
  ? "http://10.0.2.2:8080"
  : "http://localhost:8080";

export const API_URL = (configuredApiUrl || developmentApiUrl).replace(/\/+$/, "");

// Stored user details are for display only. The server identifies users from JWTs.
export function readUser() {
  try {
    const user = JSON.parse(localStorage.getItem("user") || "{}");
    return user && typeof user === "object" && !Array.isArray(user)
      ? { name: typeof user.name === "string" ? user.name : "User" }
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

export function handleUnauthorized(response, navigate) {
  if (response.status !== 401) return false;
  clearSession();
  navigate("/login", { replace: true });
  return true;
}

export async function readResponse(response, fallback) {
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    const detail = data?.message || data?.detail || data?.error;
    throw new Error(typeof detail === "string" ? detail : `${fallback} (${response.status})`);
  }
  if (data === null || typeof data !== "object") {
    throw new Error("The server returned an invalid response. Please try again.");
  }
  return data;
}

export function saveSession(data) {
  if (typeof data.token !== "string" || !data.token.trim()) {
    throw new Error("The server did not return a login token. Please sign in again.");
  }
  localStorage.setItem("token", data.token);
  localStorage.setItem("user", JSON.stringify({ name: data.name, username: data.username }));
}
