import { useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import authFood from "./assets/images/auth-food.jpg";
import "./styles/Auth.css";
import { API_URL, readResponse, saveSession } from "./api.js";

function Login() {
  const navigate = useNavigate();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [message, setMessage] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const submittingRef = useRef(false);

  async function handleSubmit(e) {
    e.preventDefault();
    if (submittingRef.current) return;
    submittingRef.current = true;
    setSubmitting(true);
    setMessage("");

    try {
      const response = await fetch(
        `${API_URL}/api/auth/login`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ username, password })
        }
      );

      const data = await readResponse(response, "Could not sign in");
      saveSession(data);

      navigate("/dashboard");

    } catch (error) {
      setMessage(error instanceof TypeError
        ? "Could not connect to the server. Please try again."
        : error.message || "Could not sign in. Please try again.");
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-page">

      <div className="auth-card">

        <div className="auth-image">
          <img src={authFood} alt="Healthy nutritious foods" />

          <div className="auth-image-text">
            <span>🌿 NUTRIVERSE</span>

            <h1>
              Nourish better.
              <br />
              Feel better.
            </h1>

            <p>
              Personalized nutrition that understands
              your goals and preferences.
            </p>
          </div>
        </div>


        <div className="auth-form-side">

          <div className="auth-form">

            <div className="auth-brand">
              🌿 Nutri<span>Verse</span>
            </div>

            <h2>Welcome back</h2>

            <p className="auth-subtitle">
              Continue your nutrition journey.
            </p>


            <form onSubmit={handleSubmit}>

              <label htmlFor="login-username">Username</label>

              <input
                type="text"
                placeholder="Enter your username"
                id="login-username"
                autoComplete="username"
                value={username}
                onChange={e => setUsername(e.target.value)}
                required
              />


              <label htmlFor="login-password">Password</label>

              <input
                type="password"
                placeholder="Enter your password"
                id="login-password"
                autoComplete="current-password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                required
              />


              {message && (
                <p className="auth-error" role="alert">
                  {message}
                </p>
              )}


              <button className="auth-submit" type="submit" disabled={submitting}>
                {submitting ? "Signing in..." : "Sign In →"}
              </button>

            </form>


            <p className="auth-switch">
              New to NutriVerse?{" "}

              <Link to="/register">
                Create an account
              </Link>
            </p>

          </div>

        </div>

      </div>

    </div>
  );
}

export default Login;
