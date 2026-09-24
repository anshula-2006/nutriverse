import { Link, NavLink, useNavigate } from "react-router-dom";
import { clearSession, readUser } from "./api.js";
import "./AppNav.css";

export default function AppNav({ name }) {
  const navigate = useNavigate();
  const displayName = name || readUser().name || "User";
  const active = ({ isActive }) => isActive ? "active" : "";

  function logout() {
    clearSession();
    navigate("/", { replace: true });
  }

  return (
    <header className="app-nav">
      <Link to="/dashboard" className="app-brand">
        <strong>NutriVerse</strong>
        <small>Evidence-aware nutrition</small>
      </Link>

      <nav className="app-links" aria-label="Application navigation">
        <NavLink to="/dashboard" className={active}>Dashboard</NavLink>
        <NavLink to="/chat" className={active}>Nutri assistant</NavLink>
        <NavLink to="/food-scan" className={active}>Food scanner</NavLink>
        <NavLink to="/profile" className={active}>Profile</NavLink>
      </nav>

      <div className="app-account">
        <Link className="app-source-link" to="/sources">Sources</Link>

        <div className="app-user">
          <small>Signed in as</small>
          <strong title={displayName}>{displayName}</strong>
        </div>

        <button type="button" onClick={logout}>Sign out</button>
      </div>
    </header>
  );
}
