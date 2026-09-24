import { useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import authFood from "./assets/images/auth-food.jpg";
import { API_URL, apiFetch, readResponse, saveSession } from "./api.js";
import "./styles/Auth.css";

export default function Login() {
  const navigate = useNavigate();
  const submittingRef = useRef(false);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [message, setMessage] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    if (submittingRef.current) return;

    submittingRef.current = true;
    setSubmitting(true);
    setMessage("");

    try {
      const response = await apiFetch(`${API_URL}/api/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password })
      });

      const data = await readResponse(response, "Could not sign in");
      saveSession(data);
      navigate("/dashboard");
    } catch (error) {
      setMessage(error?.message || "Could not sign in. Please try again.");
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-page">
      <main className="auth-shell">
        <section className="auth-photo">
          <img src={authFood} alt="Fresh vegetables and food ingredients" />

          <div className="auth-photo-copy">
            <Link to="/">NutriVerse</Link>
            <div>
              <p>Evidence-aware nutrition</p>
              <h1>Return to your nutrition record.</h1>
              <span>
                Access your saved profile, meal history, dashboard and previous conversations.
              </span>
            </div>
          </div>
        </section>

        <section className="auth-form-side">
          <div className="auth-form">
            <p className="auth-label">Account access</p>
            <h2>Sign in</h2>
            <p className="auth-subtitle">
              Enter the username and password for your NutriVerse account.
            </p>

            <form onSubmit={handleSubmit}>
              <label htmlFor="login-username">Username</label>
              <input id="login-username" type="text" autoComplete="username"
                value={username} onChange={e => setUsername(e.target.value)} required />

              <label htmlFor="login-password">Password</label>
              <input id="login-password" type="password" autoComplete="current-password"
                value={password} onChange={e => setPassword(e.target.value)} required />

              {message && <p className="auth-error" role="alert">{message}</p>}

              <button className="auth-submit" type="submit" disabled={submitting}>
                {submitting ? "Signing in..." : "Sign in"}
              </button>
            </form>

            <p className="auth-switch">
              New to NutriVerse? <Link to="/register">Create an account</Link>
            </p>

            <div className="auth-legal">
              <Link to="/privacy">Privacy</Link>
              <Link to="/terms">Terms</Link>
              <Link to="/disclaimer">Nutrition disclaimer</Link>
            </div>
          </div>
        </section>
      </main>
    </div>
  );
}
