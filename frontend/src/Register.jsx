import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import "./styles/Auth.css";

function Register() {

  const navigate = useNavigate();

  const [name, setName] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [message, setMessage] = useState("");

  const handleSubmit = async (e) => {
    e.preventDefault();

    try {

      // 1. Register user
      const response = await fetch(
        "http://localhost:8080/api/auth/register",
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json"
          },
          body: JSON.stringify({
            name,
            username,
            password
          })
        }
      );

      const data = await response.json();

      if (!response.ok) {
        setMessage(data.message || "Registration failed");
        return;
      }

      // 2. Automatically login after registration
      const loginResponse = await fetch(
        "http://localhost:8080/api/auth/login",
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

      const loginData = await loginResponse.json();

      if (!loginResponse.ok) {
        setMessage("Account created, but login failed.");
        return;
      }

      // 3. Save login information
      localStorage.setItem("token", loginData.token);

      localStorage.setItem(
        "user",
        JSON.stringify({
          id: loginData.userId,
          name: loginData.name,
          username: loginData.username,
          role: loginData.role
        })
      );

      // 4. Go directly to chat
      navigate("/chat");

    } catch (error) {
      setMessage("Could not connect to backend");
    }
  };

  return (
    <div className="auth-page">

      <div className="auth-container">

        <div className="left-content">

          <div className="top-logo">
            NUTRIVERSE AI
          </div>

          <div className="hero-content">
            <h1>
              Eat better,
              <br />
              your way.
            </h1>

            <p>
              Nutrition that listens, learns,
              and grows with you.
            </p>
          </div>

          <div className="corner-link">
            <span>Already here?</span>

            <Link to="/login">
              Sign in →
            </Link>
          </div>

        </div>

        <div className="glass-panel">

          <div className="form-content">

            <div className="brand">
              Nutri<span>Verse</span>
            </div>

            <p className="tagline">
              YOUR AI NUTRITION COMPANION
            </p>

            <h2>Create account</h2>

            <form onSubmit={handleSubmit}>

              <div className="form-group">
                <label>Name</label>

                <input
                  type="text"
                  placeholder="What should we call you?"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label>Username</label>

                <input
                  type="text"
                  placeholder="Choose a username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label>Password</label>

                <input
                  type="password"
                  placeholder="Create a password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
              </div>

              {message && (
                <p style={{ textAlign: "center" }}>
                  {message}
                </p>
              )}

              <button
                className="primary-btn"
                type="submit"
              >
                Create Account
              </button>

            </form>

            <div className="or-divider">
              <span>or</span>
            </div>

            <button className="guest-btn">
              Continue as Guest →
            </button>

            <p className="bottom-link">
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