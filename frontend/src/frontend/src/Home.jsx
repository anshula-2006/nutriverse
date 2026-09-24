import { Link } from "react-router-dom";
import homeHero from "./assets/images/home-hero.jpg";
import "./Home.css";

export default function Home() {
  return (
    <div className="home-page">
      <header className="home-nav">
        <Link to="/" className="home-logo">NutriVerse</Link>

        <nav>
          <a href="#product">Product</a>
          <a href="#method">Method</a>
          <Link to="/sources">Sources</Link>
          <Link to="/login">Sign in</Link>
          <Link to="/register" className="home-start">Create account</Link>
        </nav>
      </header>

      <main>
        <section className="home-hero">
          <div className="home-copy">
            <p className="home-label">Evidence-aware personal nutrition</p>

            <h1>Nutrition guidance with the food record in view.</h1>

            <p className="home-lead">
              NutriVerse combines your saved nutrition profile with food databases,
              meal logging and explainable recommendations.
            </p>

            <div className="home-actions">
              <Link to="/register">Create account</Link>
              <Link to="/login" className="secondary">Sign in</Link>
            </div>

            <dl className="home-facts">
              <div>
                <dt>Food evidence</dt>
                <dd>Source records and identifiers remain visible where available.</dd>
              </div>
              <div>
                <dt>Personalization</dt>
                <dd>Diet, goal, preferences and restrictions can shape recommendations.</dd>
              </div>
              <div>
                <dt>Recipe estimates</dt>
                <dd>Homemade dishes can be calculated from ingredient-level records.</dd>
              </div>
            </dl>
          </div>

          <figure className="home-image">
            <img src={homeHero} alt="Fresh vegetables and ingredients" />
            <figcaption>
              Food photography supports the interface, while nutrition claims come from
              the configured data sources.
            </figcaption>
          </figure>
        </section>

        <section className="home-product" id="product">
          <header>
            <p>Product</p>
            <h2>Built around evidence, not just generated text.</h2>
          </header>

          <div className="home-product-list">
            <Product title="Nutri assistant"
              text="Ask for nutrition guidance, meal ideas, recipe help and food information."
              route="/chat" />
            <Product title="Dashboard"
              text="Review meals, hydration, macronutrients and profile-based targets."
              route="/dashboard" />
            <Product title="Food search"
              text="Search a food record and retain its source information before logging it."
              route="/dashboard" />
            <Product title="Profile"
              text="Store the context used for personalization and target estimates."
              route="/profile" />
          </div>
        </section>

        <section className="home-method" id="method">
          <header>
            <p>Method</p>
            <h2>From request to explainable result.</h2>
          </header>

          <ol>
            <Method number="01" title="Understand the request"
              text="Identify whether the user needs a food fact, recommendation, recipe or meal action." />
            <Method number="02" title="Apply relevant profile context"
              text="Use saved dietary preferences, goals and restrictions where they affect the answer." />
            <Method number="03" title="Retrieve nutrition evidence"
              text="Use an appropriate food source before presenting quantitative nutrition claims." />
            <Method number="04" title="Explain the result"
              text="Present values, reasoning, source information and estimation status." />
          </ol>
        </section>

        <section className="home-docs">
          <div>
            <p>Documentation</p>
            <h2>Sources, limitations and legal pages stay visible.</h2>
          </div>

          <nav>
            <Link to="/about">About</Link>
            <Link to="/methodology">Methodology</Link>
            <Link to="/sources">Data sources</Link>
            <Link to="/privacy">Privacy</Link>
            <Link to="/terms">Terms</Link>
            <Link to="/disclaimer">Nutrition disclaimer</Link>
          </nav>
        </section>
      </main>

      <footer className="home-footer">
        <strong>NutriVerse</strong>
        <span>Explainable personalized nutrition</span>
        <Link to="/privacy">Privacy</Link>
        <Link to="/terms">Terms</Link>
      </footer>
    </div>
  );
}

function Product({ title, text, route }) {
  return (
    <article>
      <div>
        <h3>{title}</h3>
        <p>{text}</p>
      </div>
      <Link to={route}>Open feature</Link>
    </article>
  );
}

function Method({ number, title, text }) {
  return (
    <li>
      <span>{number}</span>
      <div>
        <h3>{title}</h3>
        <p>{text}</p>
      </div>
    </li>
  );
}
