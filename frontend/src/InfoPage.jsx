import { Link, useParams } from "react-router-dom";
import "./InfoPage.css";

const PAGES = {
  about: {
    label: "ABOUT",
    title: "About NutriVerse",
    intro:
      "NutriVerse is an explainable personalized nutrition system designed to combine user context with evidence-backed food information.",
    sections: [
      {
        title: "What NutriVerse does",
        text:
          "Users can maintain a nutrition profile, search foods, log meals and water, ask nutrition questions, receive personalized recommendations and estimate homemade meal nutrition."
      },
      {
        title: "Why explainability matters",
        text:
          "Nutrition recommendations should not appear as unsupported answers. NutriVerse exposes available nutrition values, reasoning, database sources and source identifiers so users can understand how a result was produced."
      },
      {
        title: "Project focus",
        text:
          "The system focuses on evidence-aware recommendation, personalization, food provenance, dietary screening and explainable retrieval rather than replacing qualified healthcare professionals."
      }
    ]
  },

  methodology: {
    label: "METHODOLOGY",
    title: "How NutriVerse works",
    intro:
      "NutriVerse combines user profile information, nutrition databases, retrieval and structured reasoning to produce explainable nutrition guidance.",
    sections: [
      {
        title: "1. User context",
        text:
          "The saved profile can include age, height, weight, activity level, nutrition goal, dietary preference, restrictions, food preferences and foods to avoid."
      },
      {
        title: "2. Nutrition retrieval",
        text:
          "When quantitative nutrition evidence is required, NutriVerse attempts to retrieve an appropriate food record rather than relying only on generated text."
      },
      {
        title: "3. Personalized recommendation",
        text:
          "Retrieved evidence is considered together with the user's request and applicable profile constraints before recommendations are presented."
      },
      {
        title: "4. Homemade meals",
        text:
          "When a prepared dish does not have a suitable direct food record, ingredient quantities can be matched individually, scaled and combined into an estimated per-serving result."
      },
      {
        title: "5. Explanation",
        text:
          "The interface distinguishes available verified source records from estimated composite results and displays provenance whenever possible."
      }
    ]
  },

  sources: {
    label: "DATA SOURCES",
    title: "Nutrition evidence and provenance",
    intro:
      "NutriVerse keeps source information visible so quantitative nutrition claims can be traced back to their available records.",
    sections: [
      {
        title: "USDA FoodData Central",
        text:
          "USDA FoodData Central is treated as the primary authoritative nutrition database. When NutriVerse retrieves a suitable USDA record, the interface can display the corresponding FoodData Central identifier."
      },
      {
        title: "Open Food Facts",
        text:
          "Open Food Facts may be used for packaged-product information where appropriate. It is presented as a product database rather than a government-authoritative food composition source."
      },
      {
        title: "Composite recipe estimates",
        text:
          "Homemade meal calculations are estimates derived from ingredient-level records and supplied quantities. They are not presented as direct verified records for the finished dish."
      },
      {
        title: "Missing values",
        text:
          "Food databases do not always provide every nutrient. NutriVerse should show unavailable values rather than inventing missing nutrition data."
      }
    ]
  },

  privacy: {
    label: "PRIVACY",
    title: "Privacy Policy",
    intro:
      "This page describes the information NutriVerse uses to provide its current academic-project functionality.",
    sections: [
      {
        title: "Information stored",
        text:
          "NutriVerse may store account information, nutrition profile details, dietary preferences, meal logs, water logs and conversation history required by the application's features."
      },
      {
        title: "How information is used",
        text:
          "Saved information is used to authenticate users, restore application data, calculate estimated targets and personalize nutrition recommendations."
      },
      {
        title: "Nutrition profile",
        text:
          "Profile information may influence recommendations and estimated nutrition targets. Users should avoid entering information they do not want stored in the application database."
      },
      {
        title: "Deployment notice",
        text:
          "This is an academic project. A public production deployment should define final retention, deletion, hosting, security and contact procedures before accepting real user data."
      }
    ]
  },

  terms: {
    label: "TERMS",
    title: "Terms of Use",
    intro:
      "By using NutriVerse, users acknowledge the limitations of an educational nutrition information system.",
    sections: [
      {
        title: "Informational use",
        text:
          "NutriVerse provides nutrition information, food recommendations and estimated calculations for educational and informational purposes."
      },
      {
        title: "No medical treatment",
        text:
          "NutriVerse is not intended to diagnose disease, prescribe treatment or replace care from a doctor, registered dietitian or other qualified healthcare professional."
      },
      {
        title: "Food data limitations",
        text:
          "Nutrition values may vary by product, preparation method, serving quantity and database record. Users remain responsible for checking relevant product labels and ingredient information."
      },
      {
        title: "Responsible use",
        text:
          "Users should not intentionally misuse the service, attempt unauthorized access or rely on the application for emergency medical decisions."
      }
    ]
  },

  disclaimer: {
    label: "NUTRITION DISCLAIMER",
    title: "Nutrition and health disclaimer",
    intro:
      "NutriVerse helps users understand nutrition information, but its outputs should be interpreted within their limitations.",
    sections: [
      {
        title: "Not medical advice",
        text:
          "Responses and recommendations are informational and are not medical advice, diagnosis or individualized clinical treatment."
      },
      {
        title: "Estimated targets",
        text:
          "Daily calorie, protein, water and related targets generated from profile information are estimates and should not be treated as prescriptions."
      },
      {
        title: "Allergies and restrictions",
        text:
          "Dietary screening is based on available food and ingredient information. It does not certify that a food is allergen-free, gluten-free or safe from cross-contact."
      },
      {
        title: "Homemade foods",
        text:
          "Composite recipe nutrition depends on the ingredient records, quantities and number of servings supplied. Actual nutrition can differ because of brands, cooking methods and measurement differences."
      },
      {
        title: "When professional advice is needed",
        text:
          "People with medical conditions, allergies, eating disorders, pregnancy-related nutrition needs or therapeutic diets should consult an appropriate qualified healthcare professional."
      }
    ]
  }
};

export default function InfoPage() {
  const { page } = useParams();
  const content = PAGES[page];

  if (!content) {
    return (
      <main className="info-page">
        <div className="info-document">
          <h1>Page not found</h1>
          <Link to="/">Return home</Link>
        </div>
      </main>
    );
  }

  return (
    <div className="info-page">

      <header className="info-nav">
        <Link to="/" className="info-brand">
          NutriVerse
        </Link>

        <Link to="/">
          Home
        </Link>
      </header>

      <main className="info-document">

        <header className="info-heading">
          <small>{content.label}</small>
          <h1>{content.title}</h1>
          <p>{content.intro}</p>
        </header>

        <div className="info-sections">
          {content.sections.map(section => (
            <section key={section.title}>
              <h2>{section.title}</h2>
              <p>{section.text}</p>
            </section>
          ))}
        </div>

        <footer className="info-footer">
          <Link to="/about">About</Link>
          <Link to="/methodology">Methodology</Link>
          <Link to="/sources">Data sources</Link>
          <Link to="/privacy">Privacy</Link>
          <Link to="/terms">Terms</Link>
          <Link to="/disclaimer">Disclaimer</Link>
        </footer>

      </main>

    </div>
  );
}