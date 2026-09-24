import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import AppNav from "./AppNav.jsx";
import scanPreview from "./assets/images/dashboard-hero.jpg";
import "./FoodScan.css";

export default function FoodScan() {
  const navigate = useNavigate();
  const token = localStorage.getItem("token");

  useEffect(() => {
    if (!token) navigate("/login", { replace: true });
  }, [navigate, token]);

  return (
    <div className="scan-page">
      <AppNav />

      <main className="scan-main">
        <header className="scan-header">
          <p>Food scanner</p>
          <h1>Under development</h1>
          <span>
            Food-image recognition is planned but is not active in the current NutriVerse build.
          </span>
        </header>

        <section className="scan-preview">
          <img src={scanPreview} alt="Meal example for the future food scanner" />

          <div>
            <p>Planned image input</p>
            <h2>Upload or capture a meal photo</h2>
            <span>
              The image will be used to suggest likely foods. The user will review the
              detected foods and portions before anything is added to the meal log.
            </span>
            <button disabled>Upload unavailable</button>
          </div>
        </section>

        <section className="scan-plan">
          <header>
            <h2>Development plan</h2>
            <p>
              The planned workflow keeps human confirmation between image recognition
              and nutrition logging.
            </p>
          </header>

          <div className="scan-table">
            <Plan number="01" title="Image input" status="Planned"
              text="Accept a meal image from the camera or file picker." />
            <Plan number="02" title="Food detection" status="Planned"
              text="Suggest likely foods and return model confidence." />
            <Plan number="03" title="Nutrition retrieval" status="Planned"
              text="Match confirmed foods to suitable USDA, IFCT or Open Food Facts records." />
            <Plan number="04" title="User review" status="Required"
              text="Allow corrections and portion entry before calculation." />
            <Plan number="05" title="Meal logging" status="Planned"
              text="Save only the user-confirmed result to Today's Meals." />
          </div>
        </section>

        <section className="scan-limitations">
          <div>
            <h2>What the implementation must handle</h2>
          </div>

          <dl>
            <div>
              <dt>Visually similar foods</dt>
              <dd>Different dishes can look similar in a single image.</dd>
            </div>
            <div>
              <dt>Hidden ingredients</dt>
              <dd>Oil, salt, fillings and recipe components may not be visible.</dd>
            </div>
            <div>
              <dt>Portion size</dt>
              <dd>A photograph alone cannot reliably provide grams or serving size.</dd>
            </div>
            <div>
              <dt>Mixed dishes</dt>
              <dd>Composite meals may require ingredient-level estimation after confirmation.</dd>
            </div>
          </dl>
        </section>

        <div className="scan-actions">
          <button onClick={() => navigate("/chat")}>Ask Nutri instead</button>
          <button onClick={() => navigate("/dashboard")}>Return to dashboard</button>
        </div>
      </main>
    </div>
  );
}

function Plan({ number, title, status, text }) {
  return (
    <article>
      <span>{number}</span>
      <strong>{title}</strong>
      <small>{status}</small>
      <p>{text}</p>
    </article>
  );
}
