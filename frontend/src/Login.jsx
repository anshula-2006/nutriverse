import { useState } from "react";
import { Link } from "react-router-dom";
import "./styles/Auth.css";

function Login() {
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
            "Content-Type": "application/json",
          },

          body: JSON.stringify({
            username,
            password,
          }),
        }
      );

      const data = await response.json();

      if (!response.ok) {
        setMessage(data.message || "Invalid username or password");
        return;
      }

      // Save JWT
      localStorage.setItem("token", data.token);

      // Save basic user details
      localStorage.setItem(
        "user",
        JSON.stringify({
          id: data.userId,
          name: data.name,
          username: data.username,
          role: data.role,
        })
      );

      navigate("/chat");
      setMessage("Login successful!");

      console.log("Logged in user:", data);

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
              Your nutrition companion is ready
              whenever you are.
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
                  placeholder="Your username"
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
                  placeholder="Your password"
                  value={password}
                  onChange={(e) =>
                    setPassword(e.target.value)
                  }
                  required
                />

              </div>


              <button
                type="button"
                className="forgot"
              >
                Forgot password?
              </button>


              {message && (
                <p
                  style={{
                    textAlign: "center",
                    fontSize: "13px",
                    margin: "12px 0"
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


            <button className="guest-btn">
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