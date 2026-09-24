import { useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import authFood from "./assets/images/auth-food.jpg";
import { API_URL, apiFetch, readResponse, saveSession } from "./api.js";
import "./styles/Auth.css";

export default function Register() {
  const navigate = useNavigate();
  const submittingRef = useRef(false);
  const [form, setForm] = useState({ name: "", username: "", password: "" });
  const [message, setMessage] = useState("");
  const [submitting, setSubmitting] = useState(false);

  function change(field, value) {
    setForm(current => ({ ...current, [field]: value }));
    setMessage("");
  }

  function validate() {
    const name = form.name.trim();
    const username = form.username.trim();
    const passwordBytes = new TextEncoder().encode(form.password).length;

    if (!name) return "Enter your name.";
    if (name.length > 100) return "Name must be under 100 characters.";
    if (username.length < 3 || username.length > 30) {
      return "Username must contain 3 to 30 characters.";
    }
    if (form.password.length < 6) return "Password must contain at least 6 characters.";
    if (passwordBytes > 72) return "Password is too long.";
    return null;
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (submittingRef.current) return;

    const validation = validate();
    if (validation) {
      setMessage(validation);
      return;
    }

    submittingRef.current = true;
    setSubmitting(true);
    setMessage("");

    const name = form.name.trim();
    const username = form.username.trim();

    try {
      const registerResponse = await apiFetch(`${API_URL}/api/auth/register`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name, username, password: form.password })
      });

      await readResponse(registerResponse, "Registration failed");

      const loginResponse = await apiFetch(`${API_URL}/api/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password: form.password })
      });

      const loginData = await readResponse(
        loginResponse,
        "Account created, but automatic sign-in failed"
      );

      saveSession(loginData);
      navigate("/dashboard", { replace: true });
    } catch (error) {
      setMessage(error.message || "Registration failed. Please try again.");
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
              <p>New account</p>
              <h1>Build a nutrition profile you can actually inspect.</h1>
              <span>
                Create an account first, then add only the profile details you want used
                for personalization.
              </span>
            </div>
          </div>
        </section>

        <section className="auth-form-side">
          <div className="auth-form">
            <p className="auth-label">Create account</p>
            <h2>Get started</h2>
            <p className="auth-subtitle">Set up your NutriVerse login.</p>

            <form onSubmit={handleSubmit}>
              <label htmlFor="register-name">Name</label>
              <input id="register-name" type="text" autoComplete="name"
                maxLength={100} value={form.name}
                onChange={e => change("name", e.target.value)} required />

              <label htmlFor="register-username">Username</label>
              <input id="register-username" type="text" autoComplete="username"
                minLength={3} maxLength={30} value={form.username}
                onChange={e => change("username", e.target.value)} required />

              <label htmlFor="register-password">Password</label>
              <input id="register-password" type="password" autoComplete="new-password"
                minLength={6} value={form.password}
                onChange={e => change("password", e.target.value)} required />

              {message && <p className="auth-error" role="alert">{message}</p>}

              <button className="auth-submit" type="submit" disabled={submitting}>
                {submitting ? "Creating account..." : "Create account"}
              </button>
            </form>

            <p className="auth-switch">
              Already have an account? <Link to="/login">Sign in</Link>
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
