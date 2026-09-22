import { Link } from "react-router-dom";
import homeHero from "./assets/images/home-hero.jpg";
import "./Home.css";

export default function Home() {
  return (
    <div className="home-page">

      <header className="home-nav">
        <Link to="/" className="home-logo">
          NutriVerse
        </Link>

        <nav>
          <a href="#product">Product</a>
          <a href="#method">Method</a>
          <Link to="/login">Sign in</Link>
          <Link to="/register" className="home-start">
            Create account
          </Link>
        </nav>
      </header>


      <main>

        <section className="home-hero">
          <div className="home-copy">
            <small>EVIDENCE-AWARE PERSONAL NUTRITION</small>

            <h1>
              Nutrition guidance with the evidence in view.
            </h1>

            <p>
              NutriVerse combines your nutrition profile with
              food databases, explainable recommendations and
              ingredient-based estimates for homemade meals.
            </p>

            <div className="home-actions">
              <Link to="/register" className="primary">
                Create account
              </Link>

              <Link to="/login" className="secondary">
                Sign in
              </Link>
            </div>

            <dl className="home-facts">
              <div>
                <dt>Primary source</dt>
                <dd>USDA FoodData Central</dd>
              </div>

              <div>
                <dt>Product data</dt>
                <dd>Open Food Facts where appropriate</dd>
              </div>

              <div>
                <dt>Homemade meals</dt>
                <dd>Ingredient-based per-serving estimates</dd>
              </div>
            </dl>
          </div>


          <figure className="home-image">
            <img
              src={homeHero}
              alt="Fresh ingredients used for balanced meals"
            />

            <figcaption>
              Nutrition values, source information and estimation
              method remain visible where available.
            </figcaption>
          </figure>
        </section>


        <section className="home-product" id="product">
          <div>
            <small>THE PRODUCT</small>

            <h2>
              Built around real nutrition evidence.
            </h2>

            <p>
              NutriVerse does more than generate conversational
              answers. It connects recommendations to structured
              food information and user context.
            </p>
          </div>

          <dl className="home-record">
            <div>
              <dt>Food search</dt>
              <dd>
                Search nutrition records and keep the original
                source identifier visible.
              </dd>
            </div>

            <div>
              <dt>Personalization</dt>
              <dd>
                Use diet, goal, activity level, preferences and
                restrictions when relevant.
              </dd>
            </div>

            <div>
              <dt>Recommendations</dt>
              <dd>
                Show nutrition evidence alongside the reason a
                food matches the request.
              </dd>
            </div>

            <div>
              <dt>Recipe estimation</dt>
              <dd>
                Calculate homemade meal nutrition using verified
                ingredient records and supplied quantities.
              </dd>
            </div>
          </dl>
        </section>


        <section className="home-method" id="method">
          <header>
            <small>METHOD</small>

            <h2>
              From question to explainable result.
            </h2>
          </header>

          <div className="home-steps">
            <Step
              number="01"
              title="Understand the request"
              text="Identify the food, meal, nutrient requirement or recommendation intent."
            />

            <Step
              number="02"
              title="Apply profile context"
              text="Use saved dietary preferences, goals and restrictions where relevant."
            />

            <Step
              number="03"
              title="Retrieve evidence"
              text="Search the appropriate nutrition source before presenting quantitative food claims."
            />

            <Step
              number="04"
              title="Explain the result"
              text="Present nutrition values, reasoning, source information and estimation status."
            />
          </div>
        </section>


        <section className="home-info">
          <div>
            <small>DOCUMENTATION</small>

            <h2>
              How NutriVerse handles evidence and limitations.
            </h2>
          </div>

          <nav>
            <Link to="/about">About NutriVerse</Link>
            <Link to="/methodology">Methodology</Link>
            <Link to="/sources">Data sources</Link>
            <Link to="/disclaimer">Nutrition disclaimer</Link>
          </nav>
        </section>

      </main>


      <footer className="home-footer">
        <div>
          <strong>NutriVerse</strong>
          <small>Explainable personalized nutrition</small>
        </div>

        <div className="home-footer-links">
          <div>
            <strong>Product</strong>
            <Link to="/about">About</Link>
            <Link to="/methodology">Methodology</Link>
            <Link to="/sources">Data sources</Link>
          </div>

          <div>
            <strong>Legal</strong>
            <Link to="/privacy">Privacy</Link>
            <Link to="/terms">Terms</Link>
            <Link to="/disclaimer">Nutrition disclaimer</Link>
          </div>

          <div>
            <strong>Account</strong>
            <Link to="/login">Sign in</Link>
            <Link to="/register">Create account</Link>
          </div>
        </div>
      </footer>

    </div>
  );
}

function Step({ number, title, text }) {
  return (
    <article>
      <strong>{number}</strong>

      <div>
        <h3>{title}</h3>
        <p>{text}</p>
      </div>
    </article>
  );
}