import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import "./styles/Auth.css";

function Login() {

  const navigate = useNavigate();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [message, setMessage] = useState("");

  const handleSubmit = async (e) => {
    e.preventDefault();

    setMessage("");

    try {

      const response = await fetch(
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

      const data = await response.json();

      if (!response.ok) {
        setMessage(data.message || "Invalid username or password");
        return;
      }

      // Save JWT
      localStorage.setItem("token", data.token);

      // Save logged-in user
      localStorage.setItem(
        "user",
        JSON.stringify({
          id: data.userId,
          name: data.name,
          username: data.username,
          role: data.role
        })
      );

      // Redirect to chatbot
      navigate("/chat");

    } catch (error) {
      console.error(error);
      setMessage("Could not connect to backend");
    }
  };


  return (
    <div className="auth-page">

      <div className="auth-container">

        {/* LEFT SIDE */}
        <div className="left-content">

          <div className="top-logo">
            NUTRIVERSE AI
          </div>

          <div className="hero-content">

            <h1>
              Welcome
              <br />
              back.
            </h1>

            <p>
              Your nutrition companion is ready whenever
              you are.
            </p>

          </div>

          <div className="corner-link">

            <span>New here?</span>

            <Link to="/register">
              Join us →
            </Link>

          </div>

        </div>


        {/* RIGHT SIDE */}
        <div className="glass-panel">

          <div className="form-content">

            <div className="brand">
              Nutri<span>Verse</span>
            </div>

            <p className="tagline">
              YOUR AI NUTRITION COMPANION
            </p>

            <h2>Sign in</h2>


            <form onSubmit={handleSubmit}>

              <div className="form-group">

                <label>Username</label>

                <input
                  type="text"
                  placeholder="Enter your username"
                  value={username}
                  onChange={(e) =>
                    setUsername(e.target.value)
                  }
                  required
                />

              </div>


              <div className="form-group">

                <label>Password</label>

                <input
                  type="password"
                  placeholder="Enter your password"
                  value={password}
                  onChange={(e) =>
                    setPassword(e.target.value)
                  }
                  required
                />

              </div>


              <div
                style={{
                  textAlign: "right",
                  marginBottom: "15px"
                }}
              >
                <Link
                  to="/forgot-password"
                  style={{
                    color: "white",
                    fontSize: "13px",
                    textDecoration: "none"
                  }}
                >
                  Forgot password?
                </Link>
              </div>


              {message && (
                <p
                  style={{
                    textAlign: "center",
                    marginBottom: "12px"
                  }}
                >
                  {message}
                </p>
              )}


              <button
                className="primary-btn"
                type="submit"
              >
                Sign In
              </button>

            </form>


            <div className="or-divider">
              <span>or</span>
            </div>


            <button
              className="guest-btn"
              type="button"
            >
              Continue as Guest →
            </button>


            <p className="bottom-link">

              New here?{" "}

              <Link to="/register">
                Create account
              </Link>

            </p>

          </div>

        </div>

      </div>

    </div>
  );
}

export default Login;