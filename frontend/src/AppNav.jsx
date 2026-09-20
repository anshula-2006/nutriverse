import { NavLink, useNavigate } from "react-router-dom";
import { clearSession, readUser } from "./api.js";

export default function AppNav({ name }) {
  const navigate = useNavigate();
  const displayName = name || readUser().name || "User";

  function logout() {
    clearSession();
    navigate("/", { replace: true });
  }

  return (
    <aside className="app-nav">
      <NavLink to="/dashboard" className="app-brand">NutriVerse</NavLink>
      <nav aria-label="Main navigation">
        <NavLink to="/dashboard">Dashboard</NavLink>
        <NavLink to="/chat">AI Assistant</NavLink>
        <NavLink to="/profile">My Profile</NavLink>
      </nav>
      <div className="app-nav-account">
        <div className="app-nav-user">
          <span aria-hidden="true">{displayName[0].toUpperCase()}</span>
          <div><strong>{displayName}</strong><small>NutriVerse member</small></div>
        </div>
        <button type="button" onClick={logout}>Logout</button>
      </div>
    </aside>
  );
}
