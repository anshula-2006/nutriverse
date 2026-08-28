import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import authFood from "./assets/images/auth-food.jpg";
import "./styles/Auth.css";

function Register() {
  const navigate = useNavigate();

  const [name, setName] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [message, setMessage] = useState("");

  async function handleSubmit(e) {
    e.preventDefault();
    setMessage("");

    try {
      const response = await fetch(
        "http://localhost:8080/api/auth/register",
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

      const data = await response.json();

      if (!response.ok) {
        setMessage(data.message || "Registration failed");
        return;
      }

      const loginResponse = await fetch(
        "http://localhost:8080/api/auth/login",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
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

      navigate("/dashboard");

    } catch {
      setMessage("Could not connect to backend");
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

              <label>Name</label>

              <input
                type="text"
                placeholder="What should we call you?"
                value={name}
                onChange={e => setName(e.target.value)}
                required
              />


              <label>Username</label>

              <input
                type="text"
                placeholder="Choose a username"
                value={username}
                onChange={e => setUsername(e.target.value)}
                required
              />


              <label>Password</label>

              <input
                type="password"
                placeholder="Create a password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                required
              />


              {message && (
                <p className="auth-error">
                  {message}
                </p>
              )}


              <button
                className="auth-submit"
                type="submit"
              >
                Create Account →
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