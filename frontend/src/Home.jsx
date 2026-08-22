import { Link } from "react-router-dom";

function Home() {
    return (
        <div>
            <h1>NutriVerse</h1>
            <p>AI-Powered Personalized Nutrition Recommendation System</p>

            <Link to="/login">
                <button>Login</button>
            </Link>

            <Link to="/register">
                <button>Register</button>
            </Link>
        </div>
    );
}

export default Home;