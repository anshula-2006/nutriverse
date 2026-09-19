import AppNav from "./AppNav.jsx";
import { useCallback, useEffect, useId, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { API_URL, handleUnauthorized, readResponse } from "./api.js";

import dashboardHero from "./assets/images/dashboard-hero.jpg";
import "./Dashboard.css";

export default function Dashboard() {
  const navigate = useNavigate();
  const token = localStorage.getItem("token");
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState("");
  const mutationRef = useRef(false);
  const searchRef = useRef(null);
  const [searching, setSearching] = useState(false);
  const [searched, setSearched] = useState(false);
  const [showWater, setShowWater] = useState(false);
  const [waterMl, setWaterMl] = useState("");
  const [showFood, setShowFood] = useState(false);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState([]);
  const [selectedFood, setSelectedFood] = useState(null);
  const [grams, setGrams] = useState("");
  const [mealType, setMealType] = useState("SNACK");

  const loadDashboard = useCallback(async (signal) => {
    setLoading(true);
    setError("");
    try {
      const response = await fetch(`${API_URL}/api/dashboard`, {
        headers: { Authorization: `Bearer ${token}` }, signal
      });
      if (handleUnauthorized(response, navigate)) return;
      const dashboard = await readResponse(response, "Could not load your dashboard");
      if (Array.isArray(dashboard) || (dashboard.todayMeals != null && !Array.isArray(dashboard.todayMeals))) {
        throw new Error("The server returned an invalid dashboard.");
      }
      if (!signal?.aborted) setData(dashboard);
    } catch (failure) {
      if (failure.name !== "AbortError") setError(failure.message || "Could not load your dashboard.");
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

  useEffect(() => () => searchRef.current?.abort(), []);

  async function logWater() {
    if (mutationRef.current) return;
    const ml = Number(waterMl);
    if (!Number.isFinite(ml) || ml <= 0) {
      setError("Enter a valid amount of water.");
      return;
    }
    mutationRef.current = true;
    setSaving("water");
    setError("");
    try {
      const response = await fetch(`${API_URL}/api/water`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
        body: JSON.stringify({ amountLiters: ml / 1000 })
      });
      if (handleUnauthorized(response, navigate)) return;
      await readResponse(response, "Could not log water");
      setWaterMl("");
      setShowWater(false);
      await loadDashboard();
    } catch (failure) {
      setError(failure.message || "Could not log water.");
    } finally {
      mutationRef.current = false;
      setSaving("");
    }
  }

  function updateQuery(value) {
    searchRef.current?.abort();
    setQuery(value);
    setResults([]);
    setSearched(false);
    setSearching(false);
  }

  async function searchFood() {
    if (!query.trim()) return;
    searchRef.current?.abort();
    const controller = new AbortController();
    searchRef.current = controller;
    setSearching(true);
    setSearched(false);
    setError("");
    setResults([]);
    try {
      const response = await fetch(`${API_URL}/api/nutrition/search?query=${encodeURIComponent(query.trim())}`, {
        headers: { Authorization: `Bearer ${token}` }, signal: controller.signal
      });
      if (handleUnauthorized(response, navigate)) return;
      const foods = await readResponse(response, "Food search failed");
      if (!Array.isArray(foods) || foods.some(food => !food || typeof food.foodName !== "string" || !food.sourceId)) {
        throw new Error("The server returned invalid food search results.");
      }
      if (!controller.signal.aborted) {
        setResults(foods);
        setSearched(true);
      }
    } catch (failure) {
      if (failure.name !== "AbortError") setError(failure.message || "Food search failed.");
    } finally {
      if (!controller.signal.aborted) setSearching(false);
    }
  }

  async function logFood() {
    if (mutationRef.current) return;
    const quantityGrams = Number(grams);
    if (!selectedFood || !Number.isFinite(quantityGrams) || quantityGrams <= 0) {
      setError("Select a food and enter a valid quantity.");
      return;
    }
    mutationRef.current = true;
    setSaving("food");
    setError("");
    try {
      const response = await fetch(`${API_URL}/api/nutrition/log-meal`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
        body: JSON.stringify({ mealType, source: selectedFood.source, sourceId: selectedFood.sourceId, quantityGrams })
      });
      if (handleUnauthorized(response, navigate)) return;
      await readResponse(response, "Could not log food");
      closeFood();
      await loadDashboard();
    } catch (failure) {
      setError(failure.message || "Could not log food.");
    } finally {
      mutationRef.current = false;
      setSaving("");
    }
  }

  function closeFood() {
    searchRef.current?.abort();
    setSearching(false);
    setSearched(false);
    setShowFood(false);
    setQuery("");
    setResults([]);
    setSelectedFood(null);
    setGrams("");
    setMealType("SNACK");
  }

  // =========================================================
  // HELPERS
  // =========================================================

  function pct(value, target) {
    if (!target) {
      return 0;
    }

    return Math.min(
      100,
      Math.round(
        ((value || 0) / target) * 100
      )
    );
  }

  function format(value) {
    if (typeof value !== "string" || !value) {
      return "Not Set";
    }

    return value
      .replaceAll("_", " ")
      .toLowerCase()
      .replace(
        /\b\w/g,
        character =>
          character.toUpperCase()
      );
  }

  // =========================================================
  // LOADING
  // =========================================================

  if (!data) {
    return (
      <div className="dash-loading">
        {loading ? "Loading your dashboard..." : (
          <div role="alert">
            <p>{error || "Your dashboard is unavailable."}</p>
            <button onClick={() => loadDashboard()}>Try again</button>
          </div>
        )}
      </div>
    );
  }

  // =========================================================
  // VALUES
  // =========================================================

  const weekly =
    Object.entries(
      data.weeklyCalories || {}
    );

  const max = Math.max(
    ...weekly.map(
      ([, value]) => value
    ),
    data.calorieTarget || 1
  );

  const carbs =
    data.carbsConsumed || 0;

  const protein =
    data.proteinConsumed || 0;

  const fat =
    data.fatConsumed || 0;

  const totalMacro =
    carbs * 4 +
    protein * 4 +
    fat * 9;

  const carbPct =
    totalMacro
      ? Math.round(
          ((carbs * 4) / totalMacro) * 100
        )
      : 0;

  const proteinPct =
    totalMacro
      ? Math.round(
          ((protein * 4) / totalMacro) * 100
        )
      : 0;

  const caloriesLeft = data.calorieTarget == null ? null : Math.max(0, Math.round(data.calorieTarget - data.caloriesConsumed));
  const proteinLeft = data.proteinTarget == null ? null : Math.max(0, Math.round(data.proteinTarget - data.proteinConsumed));
  const waterLeft = data.waterTarget == null ? null : Math.max(0, Number((data.waterTarget - data.waterConsumed).toFixed(1)));
  const remaining = (value, unit) => value == null ? "Target not calculated" : `${value} ${unit} remaining`;

  // =========================================================
  // UI
  // =========================================================

  return (
    <div className="dash-page">

      {/* SIDEBAR */}

      <AppNav name={data.name} />

      {/* MAIN */}

      <main className="dash-main">
        {error && !showFood && !showWater && <p role="alert">{error}</p>}

        {/* HERO */}

        <section className="dash-hero">

          <div>

            <small>
              YOUR DAILY NUTRITION
            </small>

            <h1>
              Good to see you, {data.name} 🌿
            </h1>

            <p>
              {caloriesLeft == null
                ? "Complete your profile to calculate an estimated daily target."
                : `${caloriesLeft} kcal remaining against your estimated daily target.`}
              {" "}Small choices build healthier habits.
            </p>

            <div>

              <button
                onClick={() =>
                  setShowFood(true)
                }
              >
                ＋ Add Food
              </button>

              <button
                onClick={() =>
                  navigate("/chat")
                }
              >
                Ask Nutri ✨
              </button>

            </div>

          </div>

          <img
            src={dashboardHero}
            alt="Healthy ingredients"
          />

        </section>

        {/* METRICS */}

        <section className="dash-metrics">

          <Metric
            icon="🔥"
            title="Calories"
            value={`${data.caloriesConsumed || 0} kcal`}
            text={data.calorieTarget == null ? "Target not calculated" : `${pct(data.caloriesConsumed, data.calorieTarget)}% of estimated goal`}
          />

          <Metric
            icon="💪"
            title="Protein"
            value={`${data.proteinConsumed || 0} g`}
            text={remaining(proteinLeft, "g")}
          />

          <Metric
            icon="💧"
            title="Water"
            value={`${data.waterConsumed || 0} L`}
            text={remaining(waterLeft, "L")}
            action={() =>
              setShowWater(true)
            }
          />

          <Metric
            icon="⚖️"
            title="BMI"
            value={data.bmi || "-"}
            text={data.bmiCategory || "Not available"}
          />

        </section>

        <p className="dash-muted">
          {data.nutritionIncomplete
            ? "Some logged foods have missing values or provenance. These totals include available values only. "
            : "Totals use available values from logged foods and may be incomplete when a source omits nutrients. "}
          Daily targets are estimates calculated from your profile.
        </p>

        {/* CHARTS */}

        <section className="dash-grid">

          <div className="dash-card">

            <h3>
              Weekly Calories
            </h3>

            <div className="dash-bars">

              {weekly.map(
                ([day, value]) => (

                  <div key={day}>

                    <small>
                      {Math.round(value)}
                    </small>

                    <span
                      style={{
                        height: value
                          ? `${Math.max(
                              12,
                              (value / max) * 130
                            )}px`
                          : "4px"
                      }}
                    />

                    <small>
                      {day}
                    </small>

                  </div>

                )
              )}

            </div>

          </div>

          <div className="dash-card">

            <h3>
              Today's Macros
            </h3>

            <div className="dash-macros">

              <div
                className="dash-donut"
                style={{
                  background: `conic-gradient(
                    #749c65 0 ${carbPct}%,
                    #91b2a0 ${carbPct}% ${
                      carbPct + proteinPct
                    }%,
                    #d9a66c ${
                      carbPct + proteinPct
                    }% 100%
                  )`
                }}
              >

                <span>
                  {data.caloriesConsumed || 0}
                  <small>
                    kcal
                  </small>
                </span>

              </div>

              <div>

                <p>
                  Carbs{" "}
                  <b>{carbs}g</b>
                </p>

                <p>
                  Protein{" "}
                  <b>{protein}g</b>
                </p>

                <p>
                  Fat{" "}
                  <b>{fat}g</b>
                </p>

              </div>

            </div>

          </div>

        </section>

        {/* LOWER SECTION */}

        <section className="dash-bottom">

          {/* TODAY'S MEALS */}

          <div className="dash-card">

            <div className="dash-title">

              <h3>
                Today's Meals
              </h3>

              <button
                onClick={() =>
                  setShowFood(true)
                }
              >
                ＋ Add
              </button>

            </div>

            {!data.todayMeals?.length ? (

              <p className="dash-muted">
                No meals logged yet.
              </p>

            ) : (

              data.todayMeals.map(
                meal => (

                  <div
                    className="dash-meal"
                    key={meal.id}
                  >

                    <div>

                      <strong>
                        {format(
                          meal.mealType
                        )}
                      </strong>

                      <small>
                        {meal.foodName}
                      </small>
                      <FoodSource food={meal} linked />

                    </div>

                    <b>
                      {nutritionValue(meal.calories, "kcal")}
                    </b>

                  </div>

                )
              )

            )}

          </div>

          {/* FOCUS */}

          <div className="dash-card dash-focus">

            <h3>
              Your Focus Today 🌱
            </h3>

            <p>
              💪{" "}
              <span>
                {remaining(proteinLeft, "g protein")}
              </span>
            </p>
            <p>{remaining(waterLeft, "L water")}</p>
            <p>{remaining(caloriesLeft, "kcal")}</p>

            <button
              onClick={() =>
                navigate("/chat")
              }
            >
              Ask Nutri for suggestions →
            </button>

          </div>

        </section>

      </main>

      {/* WATER MODAL */}

      {showWater && (

        <Modal
          title="💧 Log Water"
          close={() =>
            !saving && setShowWater(false)
          }
        >

          {error && <p role="alert">{error}</p>}

          <div className="dash-input">

            <input
              type="number"
              placeholder="350"
              value={waterMl}
              onChange={event =>
                setWaterMl(
                  event.target.value
                )
              }
            />

            <span>
              mL
            </span>

          </div>

          <button
            className="dash-save"
            onClick={logWater}
            disabled={Boolean(saving)}
          >
            {saving === "water" ? "Adding..." : "Add Water"}
          </button>

        </Modal>

      )}

      {/* FOOD MODAL */}

      {showFood && (

        <Modal
          title="🍽️ Add Food"
          close={() => !saving && closeFood()}
        >

          {error && <p role="alert">{error}</p>}
          {!selectedFood ? (

            <>

              <div className="dash-search">

                <input
                  placeholder="Search food or brand..."
                  value={query}
                  onChange={event =>
                    updateQuery(event.target.value)
                  }
                  onKeyDown={event => {
                    if (
                      event.key === "Enter"
                    ) {
                      searchFood();
                    }
                  }}
                />

                <button
                  onClick={searchFood}
                  disabled={searching || !query.trim()}
                >
                  {searching ? "Searching..." : "Search"}
                </button>

              </div>

              {searched && results.length === 0 && <p role="status">No foods found. Try another search.</p>}
              <div className="dash-results">

                {results.map(
                  food => (

                    <button
                      key={
                        `${food.source}:${food.sourceId}`
                      }
                      onClick={() =>
                        setSelectedFood(
                          food
                        )
                      }
                    >

                      <strong>
                        {food.foodName}
                      </strong>

                      <small>
                        {nutritionValue(food.calories, "kcal")} / {food.servingSize ?? "Unknown"}{food.servingUnit || ""}
                      </small>
                      <FoodSource food={food} />

                    </button>

                  )
                )}

              </div>

            </>

          ) : (

            <>

              <div className="dash-selected">

                <b>
                  {selectedFood.foodName}
                </b>

                <small>
                  {nutritionValue(selectedFood.calories, "kcal")} / {selectedFood.servingSize ?? "Unknown"}{selectedFood.servingUnit || ""}
                </small>

              </div>

              <FoodSource food={selectedFood} linked />
              <button type="button" onClick={() => navigate("/chat", {
                state: { food: { source: selectedFood.source, sourceId: selectedFood.sourceId, foodName: selectedFood.foodName } }
              })}>
                Ask about this food
              </button>

              <select
                value={mealType}
                onChange={event =>
                  setMealType(
                    event.target.value
                  )
                }
              >

                <option value="BREAKFAST">
                  Breakfast
                </option>

                <option value="LUNCH">
                  Lunch
                </option>

                <option value="DINNER">
                  Dinner
                </option>

                <option value="SNACK">
                  Snack
                </option>

              </select>

              <div className="dash-input">

                <input
                  type="number"
                  placeholder="Quantity"
                  value={grams}
                  onChange={event =>
                    setGrams(
                      event.target.value
                    )
                  }
                />

                <span>
                  g
                </span>

              </div>

              <button
                className="dash-save"
                onClick={logFood}
                disabled={Boolean(saving)}
              >
                {saving === "food" ? "Adding..." : "Add Meal"}
              </button>

            </>

          )}

        </Modal>

      )}

    </div>
  );
}


// =========================================================
// METRIC COMPONENT
// =========================================================

function Metric({
  icon,
  title,
  value,
  text,
  action
}) {
  return (
    <div className="dash-metric">

      <span>
        {icon}
      </span>

      <small>
        {title}
      </small>

      <h2>
        {value}
      </h2>

      <p>
        {text}
      </p>

      {action && (
        <button onClick={action}>
          ＋ Log
        </button>
      )}

    </div>
  );
}


// =========================================================
// MODAL COMPONENT
// =========================================================

function Modal({
  title,
  close,
  children
}) {
  const dialogRef = useRef(null);
  const titleId = useId();

  useEffect(() => {
    const dialog = dialogRef.current;
    dialog.showModal();
    return () => dialog.close();
  }, []);

  return (
      <dialog
        ref={dialogRef}
        className="dash-modal"
        aria-labelledby={titleId}
        onCancel={event => { event.preventDefault(); close(); }}
      >

        <button
          className="dash-close"
          aria-label="Close dialog"
          onClick={close}
        >
          ✕
        </button>

        <h2 id={titleId}>
          {title}
        </h2>

        {children}

      </dialog>
  );
}

function nutritionValue(value, unit) {
  return typeof value === "number" && Number.isFinite(value)
    ? `${Math.round(value * 100) / 100} ${unit}`
    : "Value unavailable";
}

function FoodSource({ food, linked = false }) {
  const authoritative = food.source === "USDA FoodData Central" &&
    food.sourceType === "AUTHORITATIVE_DATABASE" && food.verified === true &&
    food.estimated === false && /^\d+$/.test(String(food.sourceId || ""));
  const product = food.source === "Open Food Facts" && food.sourceType === "PRODUCT_DATABASE";
  const sourceUrl = authoritative
    ? `https://fdc.nal.usda.gov/food-details/${encodeURIComponent(food.sourceId)}/nutrients`
    : product && /^\d+$/.test(String(food.sourceId || ""))
      ? `https://world.openfoodfacts.org/product/${encodeURIComponent(food.sourceId)}` : null;
  return (
    <small className="food-source">
      <span>{authoritative ? "Authoritative government data" : product ? "Non-government product database" : "Source not verified."}</span>
      <span>{food.source || "Unknown source"}{food.sourceId ? ` - ID: ${food.sourceId}` : " - no source ID"}</span>
      {linked && sourceUrl && <a href={sourceUrl} target="_blank" rel="noreferrer">View source record</a>}
    </small>
  );
}
