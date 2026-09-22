import { useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import authFood from "./assets/images/auth-food.jpg";
import "./styles/Auth.css";

import {
  API_URL,
  apiFetch,
  readResponse,
  saveSession
} from "./api.js";

function Login() {
  const navigate = useNavigate();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [message, setMessage] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const submittingRef = useRef(false);

  async function handleSubmit(event) {
    event.preventDefault();

    if (submittingRef.current) return;

    submittingRef.current = true;
    setSubmitting(true);
    setMessage("");

    try {
      const response = await apiFetch(
        `${API_URL}/api/auth/login`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json"
          },
          body: JSON.stringify({
            username,
            password
          })
        }
      );

      const data = await readResponse(
        response,
        "Could not sign in"
      );

      saveSession(data);
      navigate("/dashboard");

    } catch (error) {
      setMessage(
        error?.message ||
        "Could not sign in. Please try again."
      );

    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-page">

      <div className="auth-card">

        <section className="auth-image">
          <img
            src={authFood}
            alt="Fresh ingredients prepared for a balanced meal"
          />

          <div className="auth-image-text">
            <span>NUTRIVERSE</span>

            <h1>
              Nutrition guidance
              <br />
              grounded in evidence.
            </h1>

            <p>
              Search verified food data, understand nutrition
              values, and get recommendations shaped by your
              dietary profile.
            </p>
          </div>
        </section>


        <section className="auth-form-side">

          <div className="auth-form">

            <div className="auth-brand">
              NutriVerse
            </div>

            <h2>Sign in</h2>

            <p className="auth-subtitle">
              Access your nutrition profile and continue
              your previous conversations.
            </p>


            <form onSubmit={handleSubmit}>

              <label htmlFor="login-username">
                Username
              </label>

              <input
                id="login-username"
                type="text"
                placeholder="Enter username"
                autoComplete="username"
                value={username}
                onChange={event =>
                  setUsername(event.target.value)
                }
                required
              />


              <label htmlFor="login-password">
                Password
              </label>

              <input
                id="login-password"
                type="password"
                placeholder="Enter password"
                autoComplete="current-password"
                value={password}
                onChange={event =>
                  setPassword(event.target.value)
                }
                required
              />


              {message && (
                <p
                  className="auth-error"
                  role="alert"
                >
                  {message}
                </p>
              )}


              <button
                className="auth-submit"
                type="submit"
                disabled={submitting}
              >
                {submitting
                  ? "Signing in..."
                  : "Sign in"}
              </button>

            </form>


            <p className="auth-switch">
              New to NutriVerse?{" "}

              <Link to="/register">
                Create an account
              </Link>
            </p>

          </div>

        </section>

      </div>

    </div>
  );
}

export default Login;