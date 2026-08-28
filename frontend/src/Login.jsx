import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import authFood from "./assets/images/auth-food.jpg";
import "./styles/Auth.css";

function Login() {
  const navigate = useNavigate();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [message, setMessage] = useState("");

  async function handleSubmit(e) {
    e.preventDefault();
    setMessage("");

    try {
      const response = await fetch(
        "http://localhost:8080/api/auth/login",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ username, password })
        }
      );

      const data = await response.json();

      if (!response.ok) {
        setMessage(data.message || "Invalid username or password");
        return;
      }

      localStorage.setItem("token", data.token);

      localStorage.setItem(
        "user",
        JSON.stringify({
          id: data.userId,
          name: data.name,
          username: data.username,
          role: data.role
        })
      );

      navigate("/dashboard");

    } catch {
      setMessage("Could not connect to backend");
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

              <label>Username</label>

              <input
                type="text"
                placeholder="Enter your username"
                value={username}
                onChange={e => setUsername(e.target.value)}
                required
              />


              <label>Password</label>

              <input
                type="password"
                placeholder="Enter your password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                required
              />


              {message && (
                <p className="auth-error">
                  {message}
                </p>
              )}


              <button className="auth-submit" type="submit">
                Sign In →
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