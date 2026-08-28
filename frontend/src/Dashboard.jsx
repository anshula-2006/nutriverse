import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import dashboardHero from "./assets/images/dashboard-hero.jpg";
import "./Dashboard.css";

const API = "http://localhost:8080";

export default function Dashboard() {
  const navigate = useNavigate();
  const user = JSON.parse(localStorage.getItem("user") || "{}");

  const [data, setData] = useState(null);
  const [showWater, setShowWater] = useState(false);
  const [waterMl, setWaterMl] = useState("");

  const [showFood, setShowFood] = useState(false);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState([]);
  const [selectedFood, setSelectedFood] = useState(null);
  const [grams, setGrams] = useState("");
  const [mealType, setMealType] = useState("SNACK");

  useEffect(() => {
    if (!user.id) navigate("/login");
    else loadDashboard();
  }, []);

  async function loadDashboard() {
    try {
      const res = await fetch(`${API}/api/dashboard/${user.id}`);
      if (res.ok) setData(await res.json());
    } catch {
      console.error("Dashboard loading failed");
    }
  }

  async function logWater() {
    const ml = Number(waterMl);
    if (ml <= 0) return alert("Enter a valid amount.");

    const res = await fetch(`${API}/api/water`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        userId: user.id,
        amountLiters: ml / 1000
      })
    });

    if (!res.ok) return alert("Could not log water.");

    setWaterMl("");
    setShowWater(false);
    loadDashboard();
  }

  async function searchFood() {
    if (!query.trim()) return;

    const res = await fetch(
      `${API}/api/nutrition/search?query=${encodeURIComponent(query)}`
    );

    if (res.ok) setResults(await res.json());
    else alert("Food search failed.");
  }

  async function logFood() {
    if (!selectedFood || Number(grams) <= 0) {
      return alert("Select a food and enter quantity.");
    }

    const res = await fetch(`${API}/api/nutrition/log-meal`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        userId: user.id,
        mealType,
        source: selectedFood.source,
        sourceId: selectedFood.sourceId,
        quantityGrams: Number(grams)
      })
    });

    if (!res.ok) return alert("Could not log food.");

    closeFood();
    loadDashboard();
  }

  function closeFood() {
    setShowFood(false);
    setQuery("");
    setResults([]);
    setSelectedFood(null);
    setGrams("");
  }

  const pct = (value, target) =>
    target ? Math.min(100, Math.round((value / target) * 100)) : 0;

  const format = value =>
    value
      ? value.replaceAll("_", " ").toLowerCase()
          .replace(/\b\w/g, c => c.toUpperCase())
      : "Not Set";

  if (!data) return <div className="dash-loading">Loading...</div>;

  const weekly = Object.entries(data.weeklyCalories || {});
  const max = Math.max(...weekly.map(([, v]) => v), data.calorieTarget || 1);

  const carbs = data.carbsConsumed || 0;
  const protein = data.proteinConsumed || 0;
  const fat = data.fatConsumed || 0;
  const totalMacro = carbs * 4 + protein * 4 + fat * 9;

  const carbPct = totalMacro ? Math.round(carbs * 4 / totalMacro * 100) : 0;
  const proteinPct = totalMacro ? Math.round(protein * 4 / totalMacro * 100) : 0;
  const fatPct = Math.max(0, 100 - carbPct - proteinPct);

  const caloriesLeft = Math.max(0,
    Math.round(data.calorieTarget - data.caloriesConsumed)
  );

  const proteinLeft = Math.max(0,
    Math.round(data.proteinTarget - data.proteinConsumed)
  );

  const waterLeft = Math.max(0,
    (data.waterTarget - data.waterConsumed).toFixed(1)
  );

  return (
    <div className="dash-page">

      <aside className="dash-sidebar">
        <div className="dash-brand">🌿 <b>NutriVerse</b></div>

        <nav>
          <button className="active">🏠 Dashboard</button>
          <button disabled>🍽️ Meals</button>
          <button disabled>📷 Food Scanner</button>
          <button onClick={() => navigate("/chat")}>✨ AI Assistant</button>
          <button disabled>📈 Progress</button>
          <button disabled>🛒 Grocery List</button>
          <button disabled>⚙️ Settings</button>
        </nav>

        <div className="dash-user">
          <span>{data.name?.[0]?.toUpperCase()}</span>
          <div>
            <strong>{data.name}</strong>
            <small>{format(data.goal)}</small>
          </div>
        </div>
      </aside>


      <main className="dash-main">

        <section className="dash-hero">
          <div>
            <small>YOUR DAILY NUTRITION</small>

            <h1>
              Good to see you, {data.name} 🌿
            </h1>

            <p>
              You have <strong>{caloriesLeft} kcal</strong> remaining today.
              Small choices build healthier habits.
            </p>

            <div>
              <button onClick={() => setShowFood(true)}>＋ Add Food</button>
              <button onClick={() => navigate("/chat")}>Ask Nutri ✨</button>
            </div>
          </div>

          <img src={dashboardHero} alt="Healthy ingredients" />
        </section>


        <section className="dash-metrics">
          <Metric
            icon="🔥" title="Calories"
            value={`${data.caloriesConsumed} kcal`}
            text={`${pct(data.caloriesConsumed, data.calorieTarget)}% of goal`}
          />

          <Metric
            icon="💪" title="Protein"
            value={`${data.proteinConsumed} g`}
            text={`${proteinLeft} g remaining`}
          />

          <Metric
            icon="💧" title="Water"
            value={`${data.waterConsumed} L`}
            text={`${waterLeft} L remaining`}
            action={() => setShowWater(true)}
          />

          <Metric
            icon="⚖️" title="BMI"
            value={data.bmi}
            text={data.bmiCategory}
          />
        </section>


        <section className="dash-grid">

          <div className="dash-card">
            <h3>Weekly Calories</h3>

            <div className="dash-bars">
              {weekly.map(([day, value]) => (
                <div key={day}>
                  <small>{Math.round(value)}</small>

                  <span
                    style={{
                      height: value
                        ? `${Math.max(12, value / max * 130)}px`
                        : "4px"
                    }}
                  />

                  <small>{day}</small>
                </div>
              ))}
            </div>
          </div>


          <div className="dash-card">
            <h3>Today's Macros</h3>

            <div className="dash-macros">
              <div
                className="dash-donut"
                style={{
                  background: `conic-gradient(
                    #749c65 0 ${carbPct}%,
                    #91b2a0 ${carbPct}% ${carbPct + proteinPct}%,
                    #d9a66c ${carbPct + proteinPct}% 100%
                  )`
                }}
              >
                <span>{data.caloriesConsumed}<small>kcal</small></span>
              </div>

              <div>
                <p>Carbs <b>{carbs}g</b></p>
                <p>Protein <b>{protein}g</b></p>
                <p>Fat <b>{fat}g</b></p>
              </div>
            </div>
          </div>

        </section>


        <section className="dash-bottom">

          <div className="dash-card">
            <div className="dash-title">
              <h3>Today's Meals</h3>
              <button onClick={() => setShowFood(true)}>＋ Add</button>
            </div>

            {!data.todayMeals?.length ? (
              <p className="dash-muted">No meals logged yet.</p>
            ) : data.todayMeals.map(meal => (
              <div className="dash-meal" key={meal.id}>
                <div>
                  <strong>{format(meal.mealType)}</strong>
                  <small>{meal.foodName}</small>
                </div>

                <b>{meal.calories} kcal</b>
              </div>
            ))}
          </div>


          <div className="dash-card dash-focus">
            <h3>Your Focus Today 🌱</h3>

            <p>💪 <span><b>{proteinLeft}g protein</b> remaining</span></p>
            <p>💧 <span><b>{waterLeft}L water</b> remaining</span></p>
            <p>🔥 <span><b>{caloriesLeft} kcal</b> available</span></p>

            <button onClick={() => navigate("/chat")}>
              Ask Nutri for suggestions →
            </button>
          </div>

        </section>

      </main>


      {showWater && (
        <Modal title="💧 Log Water" close={() => setShowWater(false)}>
          <div className="dash-input">
            <input
              type="number"
              placeholder="350"
              value={waterMl}
              onChange={e => setWaterMl(e.target.value)}
            />
            <span>mL</span>
          </div>

          <button className="dash-save" onClick={logWater}>
            Add Water
          </button>
        </Modal>
      )}


      {showFood && (
        <Modal title="🍽️ Add Food" close={closeFood}>

          {!selectedFood ? (
            <>
              <div className="dash-search">
                <input
                  placeholder="Search food or brand..."
                  value={query}
                  onChange={e => setQuery(e.target.value)}
                  onKeyDown={e => e.key === "Enter" && searchFood()}
                />
                <button onClick={searchFood}>Search</button>
              </div>

              <div className="dash-results">
                {results.map(food => (
                  <button
                    key={food.sourceId}
                    onClick={() => setSelectedFood(food)}
                  >
                    <strong>{food.foodName}</strong>
                    <small>
                      {Math.round(food.calories || 0)} kcal / 100g
                    </small>
                  </button>
                ))}
              </div>
            </>
          ) : (
            <>
              <div className="dash-selected">
                <b>{selectedFood.foodName}</b>
                <small>{Math.round(selectedFood.calories || 0)} kcal / 100g</small>
              </div>

              <select
                value={mealType}
                onChange={e => setMealType(e.target.value)}
              >
                <option value="BREAKFAST">Breakfast</option>
                <option value="LUNCH">Lunch</option>
                <option value="DINNER">Dinner</option>
                <option value="SNACK">Snack</option>
              </select>

              <div className="dash-input">
                <input
                  type="number"
                  placeholder="Quantity"
                  value={grams}
                  onChange={e => setGrams(e.target.value)}
                />
                <span>g</span>
              </div>

              <button className="dash-save" onClick={logFood}>
                Add Meal
              </button>
            </>
          )}

        </Modal>
      )}

    </div>
  );
}


function Metric({ icon, title, value, text, action }) {
  return (
    <div className="dash-metric">
      <span>{icon}</span>
      <small>{title}</small>
      <h2>{value}</h2>
      <p>{text}</p>

      {action && <button onClick={action}>＋ Log</button>}
    </div>
  );
}


function Modal({ title, close, children }) {
  return (
    <div className="dash-overlay">
      <div className="dash-modal">
        <button className="dash-close" onClick={close}>✕</button>
        <h2>{title}</h2>
        {children}
      </div>
    </div>
  );
}