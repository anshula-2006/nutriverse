import { Link } from "react-router-dom";
import homeHero from "./assets/images/home-hero.jpg";
import "./Home.css";

function Home() {
  return (
    <div className="home-page">

      <nav className="home-nav">

        <div className="home-logo">
          🌿 Nutri<span>Verse</span>
        </div>

        <div className="home-links">
          <a href="#features">Features</a>
          <a href="#how">How it works</a>

          <Link to="/login" className="home-login">
            Login
          </Link>

          <Link to="/register" className="home-start">
            Get Started
          </Link>
        </div>

      </nav>


      <main className="home-hero">

        <div className="home-text">

          <span className="home-label">
            AI-POWERED PERSONALIZED NUTRITION
          </span>

          <h1>
            Eat better.
            <br />
            Understand <span>why.</span>
          </h1>

          <p>
            NutriVerse helps you make healthier food choices
            with personalized recommendations based on your
            goals, preferences and nutrition needs.
          </p>

          <div className="home-buttons">

            <Link to="/register" className="primary-home-btn">
              Start Your Journey →
            </Link>

            <Link to="/login" className="secondary-home-btn">
              I already have an account
            </Link>

          </div>


          <div className="home-trust">
            <span>🥗 Personalized</span>
            <span>🧠 Explainable</span>
            <span>🌿 Health Focused</span>
          </div>

        </div>


        <div className="home-image">

          <img
            src={homeHero}
            alt="Healthy nutritious foods"
          />

          <div className="home-floating-card">
            <strong>🌱 Better choices, one meal at a time.</strong>
            <small>
              Nutrition recommendations designed around you.
            </small>
          </div>

        </div>

      </main>


      <section className="home-features" id="features">

        <div>
          <span>🤖</span>
          <h3>AI Nutrition Assistant</h3>
          <p>
            Ask Nutri about meals, recipes and healthier choices.
          </p>
        </div>

        <div>
          <span>🎯</span>
          <h3>Personalized Nutrition</h3>
          <p>
            Recommendations adapt to your diet, goals and lifestyle.
          </p>
        </div>

        <div>
          <span>🔎</span>
          <h3>Explainable Choices</h3>
          <p>
            Understand why foods and recipes are recommended.
          </p>
        </div>

      </section>


      <section className="home-how" id="how">

        <span>HOW NUTRIVERSE WORKS</span>

        <h2>
          Nutrition that understands you.
        </h2>

        <div className="home-steps">

          <div>
            <strong>01</strong>
            <h3>Tell us about you</h3>
            <p>Your diet, goals and lifestyle.</p>
          </div>

          <div>
            <strong>02</strong>
            <h3>Ask Nutri</h3>
            <p>Get personalized food and recipe guidance.</p>
          </div>

          <div>
            <strong>03</strong>
            <h3>Understand why</h3>
            <p>See how foods connect to nutrients and your goals.</p>
          </div>

        </div>

      </section>

    </div>
  );
}

export default Home;