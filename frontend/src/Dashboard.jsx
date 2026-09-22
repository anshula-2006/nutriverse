import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import AppNav from "./AppNav.jsx";
import { API_URL, apiFetch, handleUnauthorized, readResponse } from "./api.js";
import "./Dashboard.css";

export default function Dashboard() {
  const navigate = useNavigate();
  const token = localStorage.getItem("token");
  const busyRef = useRef(false);
  const searchRef = useRef(null);

  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState("");

  const [waterOpen, setWaterOpen] = useState(false);
  const [waterMl, setWaterMl] = useState("");

  const [foodOpen, setFoodOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState([]);
  const [searching, setSearching] = useState(false);
  const [searched, setSearched] = useState(false);
  const [selectedFood, setSelectedFood] = useState(null);
  const [grams, setGrams] = useState("");
  const [mealType, setMealType] = useState("SNACK");

  const loadDashboard = useCallback(async signal => {
    try {
      setLoading(true);
      setError("");

      const response = await fetch(`${API_URL}/api/dashboard`, {
        headers: { Authorization: `Bearer ${token}` },
        signal
      });

      if (handleUnauthorized(response, navigate)) return;
      const result = await readResponse(response, "Could not load dashboard");

      if (!signal?.aborted) setData(result);
    } catch (e) {
      if (e.name !== "AbortError") setError(e.message || "Could not load dashboard.");
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, [navigate, token]);

  useEffect(() => {
    if (!token) {
      navigate("/login", { replace: true });
      return;
    }

    const controller = new AbortController();
    loadDashboard(controller.signal);
    return () => controller.abort();
  }, [loadDashboard, navigate, token]);

  async function logWater() {
    const ml = Number(waterMl);
    if (!Number.isFinite(ml) || ml <= 0 || busyRef.current) return;

    busyRef.current = true;
    setSaving("water");

    try {
      const response = await apiFetch(`${API_URL}/api/water`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`
        },
        body: JSON.stringify({ amountLiters: ml / 1000 })
      });

      if (handleUnauthorized(response, navigate)) return;
      await readResponse(response, "Could not log water");

      setWaterMl("");
      setWaterOpen(false);
      await loadDashboard();
    } catch (e) {
      setError(e.message || "Could not log water.");
    } finally {
      busyRef.current = false;
      setSaving("");
    }
  }

  async function searchFood() {
    if (!query.trim()) return;

    searchRef.current?.abort();
    const controller = new AbortController();
    searchRef.current = controller;

    setSearching(true);
    setSearched(false);
    setResults([]);

    try {
      const response = await fetch(
        `${API_URL}/api/nutrition/search?query=${encodeURIComponent(query.trim())}`,
        {
          headers: { Authorization: `Bearer ${token}` },
          signal: controller.signal
        }
      );

      if (handleUnauthorized(response, navigate)) return;
      const foods = await readResponse(response, "Food search failed");

      if (!controller.signal.aborted) {
        setResults(Array.isArray(foods) ? foods : []);
        setSearched(true);
      }
    } catch (e) {
      if (e.name !== "AbortError") setError(e.message || "Food search failed.");
    } finally {
      if (!controller.signal.aborted) setSearching(false);
    }
  }

  async function logFood() {
    const quantity = Number(grams);
    if (!selectedFood || !Number.isFinite(quantity) || quantity <= 0 || busyRef.current) return;

    busyRef.current = true;
    setSaving("food");

    try {
      const response = await apiFetch(`${API_URL}/api/nutrition/log-meal`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`
        },
        body: JSON.stringify({
          mealType,
          source: selectedFood.source,
          sourceId: selectedFood.sourceId,
          quantityGrams: quantity
        })
      });

      if (handleUnauthorized(response, navigate)) return;
      await readResponse(response, "Could not log food");

      closeFood();
      await loadDashboard();
    } catch (e) {
      setError(e.message || "Could not log food.");
    } finally {
      busyRef.current = false;
      setSaving("");
    }
  }

  function closeFood() {
    searchRef.current?.abort();
    setFoodOpen(false);
    setQuery("");
    setResults([]);
    setSelectedFood(null);
    setGrams("");
    setMealType("SNACK");
    setSearched(false);
  }

  if (loading && !data) return <DashboardLoader />;

  if (!data) {
    return (
      <div className="dash-empty">
        <h2>Dashboard unavailable</h2>
        <p>{error}</p>
        <button onClick={() => loadDashboard()}>Try again</button>
      </div>
    );
  }

  const calories = n(data.caloriesConsumed);
  const protein = n(data.proteinConsumed);
  const carbs = n(data.carbsConsumed);
  const fat = n(data.fatConsumed);
  const water = n(data.waterConsumed);

  const calorieTarget = optional(data.calorieTarget);
  const proteinTarget = optional(data.proteinTarget);
  const waterTarget = optional(data.waterTarget);

  const macroCalories = carbs * 4 + protein * 4 + fat * 9;
  const carbPct = macroCalories ? Math.round((carbs * 4 / macroCalories) * 100) : 0;
  const proteinPct = macroCalories ? Math.round((protein * 4 / macroCalories) * 100) : 0;
  const fatPct = macroCalories ? Math.max(0, 100 - carbPct - proteinPct) : 0;

  const weekly = Object.entries(data.weeklyCalories || {});
  const maxWeek = Math.max(1, ...weekly.map(([, value]) => n(value)));

  return (
    <div className="dash-page">
      <AppNav name={data.name} />

      <main className="dash-main">
        <section className="dash-top">
          <div>
            <small>TODAY'S NUTRITION</small>
            <h1>Hello, {data.name}</h1>
            <p>
              Track your meals, hydration and daily nutrition in one place.
            </p>
          </div>

          <div className="dash-top-actions">
            <button onClick={() => setFoodOpen(true)}>Add food</button>
            <button className="secondary" onClick={() => setWaterOpen(true)}>
              Log water
            </button>
            <button className="secondary" onClick={() => navigate("/chat")}>
              Ask Nutri
            </button>
          </div>
        </section>

        {error && <p className="dash-error">{error}</p>}

        <section className="dash-summary">
          <SummaryCard
            label="Calories"
            value={`${Math.round(calories)} kcal`}
            target={calorieTarget}
            used={calories}
            targetText={calorieTarget ? `${Math.round(calorieTarget)} kcal target` : "No target yet"}
          />

          <SummaryCard
            label="Protein"
            value={`${round(protein)} g`}
            target={proteinTarget}
            used={protein}
            targetText={proteinTarget ? `${round(proteinTarget)} g target` : "No target yet"}
          />

          <SummaryCard
            label="Water"
            value={`${round(water)} L`}
            target={waterTarget}
            used={water}
            targetText={waterTarget ? `${round(waterTarget)} L target` : "No target yet"}
          />

          <div className="dash-stat">
            <span>BMI</span>
            <strong>{data.bmi ?? "--"}</strong>
            <small>{data.bmiCategory || "Complete your profile"}</small>
          </div>
        </section>

        <section className="dash-layout">
          <article className="dash-panel macro-panel">
            <div className="panel-heading">
              <div>
                <small>DAILY MACROS</small>
                <h2>Macro balance</h2>
              </div>
              <span>{Math.round(macroCalories)} kcal from macros</span>
            </div>

            <div className="macro-content">
              <div
                className="macro-donut"
                style={{
                  "--carbs": `${carbPct}%`,
                  "--protein": `${carbPct + proteinPct}%`
                }}
              >
                <div>
                  <strong>{Math.round(calories)}</strong>
                  <small>kcal today</small>
                </div>
              </div>

              <div className="macro-legend">
                <MacroRow label="Carbohydrates" value={carbs} percent={carbPct} type="carbs" />
                <MacroRow label="Protein" value={protein} percent={proteinPct} type="protein" />
                <MacroRow label="Fat" value={fat} percent={fatPct} type="fat" />
              </div>
            </div>
          </article>

          <article className="dash-panel week-panel">
            <div className="panel-heading">
              <div>
                <small>LAST 7 DAYS</small>
                <h2>Weekly energy</h2>
              </div>
            </div>

            <div className="week-chart">
              {weekly.length === 0 ? (
                <p>No nutrition history yet.</p>
              ) : (
                weekly.map(([day, value]) => (
                  <div className="week-item" key={day}>
                    <span className="week-value">{Math.round(n(value))}</span>
                    <div className="week-bar-track">
                      <span style={{ height: `${Math.max(5, n(value) / maxWeek * 100)}%` }} />
                    </div>
                    <small>{day}</small>
                  </div>
                ))
              )}
            </div>
          </article>
        </section>

        <section className="dash-lower">
          <article className="dash-panel meals-panel">
            <div className="panel-heading">
              <div>
                <small>MEAL LOG</small>
                <h2>Today's meals</h2>
              </div>
              <button className="bubble-small" onClick={() => setFoodOpen(true)}>
                Add food
              </button>
            </div>

            {!data.todayMeals?.length ? (
              <div className="dash-no-meals">
                <p>No meals logged yet.</p>
                <button onClick={() => setFoodOpen(true)}>Log your first meal</button>
              </div>
            ) : (
              <div className="meal-list">
                {data.todayMeals.map((meal, index) => (
                  <div className="meal-row" key={meal.id || index}>
                    <div>
                      <small>{format(meal.mealType)}</small>
                      <strong>{meal.foodName}</strong>
                      <FoodSource food={meal} />
                    </div>

                    <div className="meal-nutrition">
                      <span>{nutrition(meal.calories, "kcal")}</span>
                      <span>{nutrition(meal.protein, "g protein")}</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </article>

          <article className="dash-panel target-panel">
            <small>DAILY PROGRESS</small>
            <h2>Remaining today</h2>

            <Remaining
              label="Energy"
              value={remaining(calorieTarget, calories, "kcal")}
            />

            <Remaining
              label="Protein"
              value={remaining(proteinTarget, protein, "g")}
            />

            <Remaining
              label="Water"
              value={remaining(waterTarget, water, "L")}
            />

            <button onClick={() => navigate("/chat")}>
              Get meal suggestions
            </button>
          </article>
        </section>
      </main>

      {waterOpen && (
        <div className="dash-overlay" onMouseDown={e => e.target === e.currentTarget && setWaterOpen(false)}>
          <section className="dash-modal">
            <div className="modal-head">
              <h2>Log water</h2>
              <button onClick={() => setWaterOpen(false)}>Close</button>
            </div>

            <label>Amount</label>
            <div className="dash-input-row">
              <input
                type="number"
                min="1"
                placeholder="350"
                value={waterMl}
                onChange={e => setWaterMl(e.target.value)}
              />
              <span>mL</span>
            </div>

            <button className="modal-save" onClick={logWater} disabled={saving === "water"}>
              {saving === "water" ? "Saving..." : "Add water"}
            </button>
          </section>
        </div>
      )}

      {foodOpen && (
        <div className="dash-overlay" onMouseDown={e => e.target === e.currentTarget && closeFood()}>
          <section className="dash-modal food-modal">
            <div className="modal-head">
              <h2>{selectedFood ? "Add meal" : "Find a food"}</h2>
              <button onClick={closeFood}>Close</button>
            </div>

            {!selectedFood ? (
              <>
                <div className="food-search">
                  <input
                    value={query}
                    placeholder="Search food or product"
                    onChange={e => setQuery(e.target.value)}
                    onKeyDown={e => e.key === "Enter" && searchFood()}
                  />
                  <button onClick={searchFood} disabled={searching || !query.trim()}>
                    {searching ? "Searching..." : "Search"}
                  </button>
                </div>

                {searched && !results.length && <p>No matching foods found.</p>}

                <div className="food-results">
                  {results.map((food, index) => (
                    <button
                      key={`${food.source}-${food.sourceId}-${index}`}
                      onClick={() => setSelectedFood(food)}
                    >
                      <div>
                        <strong>{food.foodName}</strong>
                        <FoodSource food={food} />
                      </div>
                      <span>{nutrition(food.calories, "kcal")}</span>
                    </button>
                  ))}
                </div>
              </>
            ) : (
              <div className="selected-food">
                <div className="selected-food-head">
                  <div>
                    <small>SELECTED FOOD</small>
                    <h3>{selectedFood.foodName}</h3>
                    <FoodSource food={selectedFood} />
                  </div>
                  <button onClick={() => setSelectedFood(null)}>Change</button>
                </div>

                <label>Meal</label>
                <select value={mealType} onChange={e => setMealType(e.target.value)}>
                  <option value="BREAKFAST">Breakfast</option>
                  <option value="LUNCH">Lunch</option>
                  <option value="DINNER">Dinner</option>
                  <option value="SNACK">Snack</option>
                </select>

                <label>Quantity</label>
                <div className="dash-input-row">
                  <input
                    type="number"
                    min="1"
                    placeholder="100"
                    value={grams}
                    onChange={e => setGrams(e.target.value)}
                  />
                  <span>g</span>
                </div>

                <button
                  className="ask-food"
                  onClick={() => navigate("/chat", {
                    state: {
                      food: {
                        foodName: selectedFood.foodName,
                        source: selectedFood.source,
                        sourceId: selectedFood.sourceId
                      }
                    }
                  })}
                >
                  Ask Nutri about this food
                </button>

                <button className="modal-save" onClick={logFood} disabled={saving === "food"}>
                  {saving === "food" ? "Adding..." : "Add to meal log"}
                </button>
              </div>
            )}
          </section>
        </div>
      )}
    </div>
  );
}

function SummaryCard({ label, value, target, used, targetText }) {
  const progress = target ? Math.min(100, Math.round((used / target) * 100)) : 0;

  return (
    <div className="dash-stat">
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{targetText}</small>
      <div className="stat-track">
        <span style={{ width: `${progress}%` }} />
      </div>
    </div>
  );
}

function MacroRow({ label, value, percent, type }) {
  return (
    <div className="macro-row">
      <i className={type} />
      <div>
        <span>{label}</span>
        <small>{percent}% of macro calories</small>
      </div>
      <strong>{round(value)} g</strong>
    </div>
  );
}

function Remaining({ label, value }) {
  return (
    <div className="remaining-row">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function FoodSource({ food }) {
  return (
    <small className="food-source">
      {food?.source || "Source unavailable"}
      {food?.sourceId ? ` · ID ${food.sourceId}` : ""}
    </small>
  );
}

function DashboardLoader() {
  return (
    <div className="dash-empty">
      <h2>Loading your dashboard...</h2>
    </div>
  );
}

const n = value => Number.isFinite(Number(value)) ? Number(value) : 0;
const optional = value => value == null || !Number.isFinite(Number(value)) ? null : Number(value);
const round = value => Math.round(n(value) * 10) / 10;

function remaining(target, used, unit) {
  if (target == null) return "Target not set";
  return `${round(Math.max(0, target - used))} ${unit}`;
}

function nutrition(value, unit) {
  return Number.isFinite(Number(value)) ? `${round(value)} ${unit}` : "N/A";
}

function format(value) {
  if (!value) return "Meal";
  return value.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, c => c.toUpperCase());
}