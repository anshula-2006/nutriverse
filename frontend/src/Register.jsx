import { Link } from "react-router-dom";
import "./styles/Auth.css";

function Register() {
    const handleSubmit = (e) => {
        e.preventDefault();
    };

    return (
        <div className="auth-page">
            <div className="auth-container">

                {/* LEFT */}
                <div className="left-content">
                    <div className="top-logo">NUTRIVERSE AI</div>

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
                        <Link to="/login">Sign in →</Link>
                    </div>
                </div>

                {/* RIGHT GLASS PANEL */}
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
                                    required
                                />
                            </div>

                            <div className="form-group">
                                <label>Username</label>
                                <input
                                    type="text"
                                    placeholder="Choose a username"
                                    required
                                />
                            </div>

                            <div className="form-group">
                                <label>Password</label>
                                <input
                                    type="password"
                                    placeholder="Create a password"
                                    required
                                />
                            </div>

                            <button className="primary-btn">
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
                            <Link to="/login">Sign in</Link>
                        </p>

                    </div>
                </div>

            </div>
        </div>
    );
}

export default Register;