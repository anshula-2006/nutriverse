import { Link, NavLink, useNavigate } from "react-router-dom";
import { clearSession, readUser } from "./api.js";
import "./AppNav.css";

export default function AppNav({ name }) {
  const navigate = useNavigate();
  const displayName = name || readUser().name || "User";
  const navClass = ({ isActive }) => isActive ? "active" : "";

  function logout() {
    clearSession();
    navigate("/", { replace: true });
  }

  return (
    <header className="app-nav">
      <Link to="/dashboard" className="app-brand">
        <span className="brand-mark">✦</span>
        <span>NutriVerse<small>AI Nutrition Assistant</small></span>
      </Link>

      <nav className="app-links" aria-label="Main navigation">
        <NavLink to="/dashboard" className={navClass}>Dashboard</NavLink>
        <NavLink to="/chat" className={navClass}>Nutri assistant</NavLink>
        <NavLink to="/food-scan" className={navClass}>Food scanner</NavLink>
        <NavLink to="/profile" className={navClass}>Profile</NavLink>
      </nav>

      <div className="app-account">
        <div className="app-reference-links">
          <Link to="/sources">Sources</Link>
          <Link to="/disclaimer">Disclaimer</Link>
        </div>
        <div className="app-user">
          <small>SIGNED IN AS</small>
          <strong>{displayName}</strong>
        </div>
        <button type="button" onClick={logout}>Sign out</button>
      </div>
    </header>
  );
}
