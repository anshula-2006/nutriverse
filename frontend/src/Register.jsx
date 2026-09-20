import { useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import authFood from "./assets/images/auth-food.jpg";
import "./styles/Auth.css";
import { API_URL, readResponse, saveSession } from "./api.js";

function Register() {
  const navigate = useNavigate();

  const [name, setName] = useState("");
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
    let accountCreated = false;

    try {
      const response = await fetch(
        `${API_URL}/api/auth/register`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            name,
            username,
            password
          })
        }
      );

      await readResponse(response, "Registration failed");
      accountCreated = true;

      const loginResponse = await fetch(
        `${API_URL}/api/auth/login`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            username,
            password
          })
        }
      );

      const loginData = await readResponse(loginResponse, "Account created, but login failed. Please sign in");
      saveSession(loginData);

      navigate("/dashboard");

    } catch (error) {
      setMessage(accountCreated
        ? "Your account was created, but automatic sign-in failed. Please use the Sign in link below."
        : error instanceof TypeError
          ? "Could not connect to the server. Please try again."
          : error.message || "Registration failed. Please try again.");
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-page">

      <div className="auth-card">

        <div className="auth-image">

          <img
            src={authFood}
            alt="Healthy nutritious foods"
          />

          <div className="auth-image-text">

            <span>🌿 NUTRIVERSE</span>

            <h1>
              Build healthier
              <br />
              habits your way.
            </h1>

            <p>
              Personalized nutrition built around
              your food choices, goals and lifestyle.
            </p>

          </div>

        </div>


        <div className="auth-form-side">

          <div className="auth-form">

            <div className="auth-brand">
              🌿 Nutri<span>Verse</span>
            </div>

            <h2>Create account</h2>

            <p className="auth-subtitle">
              Start your personalized nutrition journey.
            </p>


            <form onSubmit={handleSubmit}>

              <label htmlFor="register-name">Name</label>

              <input
                type="text"
                placeholder="What should we call you?"
                id="register-name"
                autoComplete="name"
                value={name}
                onChange={e => setName(e.target.value)}
                required
              />


              <label htmlFor="register-username">Username</label>

              <input
                type="text"
                placeholder="Choose a username"
                id="register-username"
                autoComplete="username"
                value={username}
                onChange={e => setUsername(e.target.value)}
                required
              />


              <label htmlFor="register-password">Password</label>

              <input
                type="password"
                placeholder="Create a password"
                id="register-password"
                autoComplete="new-password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                required
              />


              {message && (
                <p className="auth-error" role="alert">
                  {message}
                </p>
              )}


              <button
                className="auth-submit"
                type="submit"
                disabled={submitting}
              >
                {submitting ? "Creating account..." : "Create Account →"}
              </button>

            </form>


            <p className="auth-switch">
              Already have an account?{" "}

              <Link to="/login">
                Sign in
              </Link>
            </p>

          </div>

        </div>

      </div>

    </div>
  );
}

export default Register;
