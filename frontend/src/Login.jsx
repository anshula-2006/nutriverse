import { Link } from "react-router-dom";
import "./styles/Auth.css";

function Login() {
    const handleSubmit = (e) => {
        e.preventDefault();
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
                        <Link to="/register">Join us →</Link>
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

                        <h2>Sign in</h2>

                        <form onSubmit={handleSubmit}>

                            <div className="form-group">
                                <label>Username</label>
                                <input
                                    type="text"
                                    placeholder="Your username"
                                    required
                                />
                            </div>

                            <div className="form-group">
                                <label>Password</label>
                                <input
                                    type="password"
                                    placeholder="Your password"
                                    required
                                />
                            </div>

                            <button
                                type="button"
                                className="forgot"
                            >
                                Forgot password?
                            </button>

                            <button className="primary-btn">
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