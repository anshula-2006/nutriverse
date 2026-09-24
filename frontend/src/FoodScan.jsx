import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import AppNav from "./AppNav.jsx";
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
        <section className="scan-hero">
          <div className="scan-copy">
            <span className="scan-badge">UNDER DEVELOPMENT</span>
            <small>FOOD IMAGE RECOGNITION</small>
            <h1>Snap a meal.<br />Understand what is on your plate.</h1>
            <p>
              Food Scan is a planned NutriVerse feature that will identify likely foods
              from a meal image, connect them to verified nutrition sources, and let you
              review everything before saving it to your meal log.
            </p>
            <div className="scan-actions">
              <button disabled>Upload photo — coming soon</button>
              <button className="scan-secondary" onClick={() => navigate("/chat")}>Ask Nutri instead</button>
            </div>
          </div>

          <div className="scan-preview">
            <div className="camera-card">
              <div className="camera-icon">⌁</div>
              <span>IMAGE AREA</span>
              <strong>Your meal photo will appear here</strong>
              <p>JPG, PNG and camera capture are planned.</p>
            </div>
            <div className="scan-floating-card">
              <span>PLANNED OUTPUT</span>
              <strong>Detected foods + confidence</strong>
              <small>Then verified nutrition evidence</small>
            </div>
          </div>
        </section>

        <section className="scan-roadmap">
          <header>
            <small>HOW IT WILL WORK</small>
            <h2>What we want Food Scan to do</h2>
            <p>The scanner will never silently add an AI guess to your nutrition history.</p>
          </header>
          <div className="scan-steps">
            <Step number="01" title="Capture or upload" text="Take a photo of a plate or choose an existing meal image." />
            <Step number="02" title="Detect likely foods" text="The vision model suggests the foods it can recognize and shows confidence." />
            <Step number="03" title="Verify nutrition" text="Detected foods are matched with NutriVerse nutrition sources such as USDA, IFCT or Open Food Facts." />
            <Step number="04" title="Review before logging" text="You confirm the foods and portions before anything is saved to Today's Meals." />
          </div>
        </section>

        <section className="scan-note">
          <div>
            <span>WHY THIS IS NOT ACTIVE YET</span>
            <h2>Accuracy first, then automation.</h2>
          </div>
          <p>
            Image recognition can confuse visually similar foods and cannot reliably know
            recipe ingredients or portion size from a photo alone. NutriVerse will keep a
            human confirmation step before calculating or logging nutrition.
          </p>
        </section>

        <section className="scan-status">
          <div><span>01</span><strong>UI prototype</strong><small>Current stage</small></div>
          <div><span>02</span><strong>Food recognition model</strong><small>Planned</small></div>
          <div><span>03</span><strong>Evidence matching</strong><small>Planned</small></div>
          <div><span>04</span><strong>Meal-log integration</strong><small>Planned</small></div>
        </section>
      </main>
    </div>
  );
}

function Step({ number, title, text }) {
  return (
    <article className="scan-step">
      <span>{number}</span>
      <h3>{title}</h3>
      <p>{text}</p>
    </article>
  );
}
