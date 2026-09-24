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
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
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
    const term = query.trim();
    if (!term) return;
    searchRef.current?.abort();
    const controller = new AbortController();
    searchRef.current = controller;
    setSearching(true);
    setSearched(false);
    setResults([]);
    try {
      const response = await fetch(
        `${API_URL}/api/nutrition/search?query=${encodeURIComponent(term)}`,
        { headers: { Authorization: `Bearer ${token}` }, signal: controller.signal }
      );
      if (handleUnauthorized(response, navigate)) return;
      const foods = await readResponse(response, "Food search failed");
      if (!controller.signal.aborted) {
        setResults(cleanFoodResults(Array.isArray(foods) ? foods : [], term));
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
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
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
  if (loading && !data) return <PageMessage text="Loading your dashboard..." />;
  if (!data) return <PageMessage text={error || "Dashboard unavailable"} retry={() => loadDashboard()} />;
  const calories = number(data.caloriesConsumed);
  const protein = number(data.proteinConsumed);
  const carbs = number(data.carbsConsumed);
  const fat = number(data.fatConsumed);
  const water = number(data.waterConsumed);
  const calorieTarget = optional(data.calorieTarget);
  const proteinTarget = optional(data.proteinTarget);
  const waterTarget = optional(data.waterTarget);
  const macroCalories = carbs * 4 + protein * 4 + fat * 9;
  const carbPct = macroCalories ? Math.round((carbs * 4 / macroCalories) * 100) : 0;
  const proteinPct = macroCalories ? Math.round((protein * 4 / macroCalories) * 100) : 0;
  const fatPct = macroCalories ? Math.max(0, 100 - carbPct - proteinPct) : 0;
  const weekly = Object.entries(data.weeklyCalories || {});
  const maxWeek = Math.max(1, ...weekly.map(([, value]) => number(value)));
  return (
    <div className="dash-page">
      <AppNav name={data.name} />
      <main className="dash-main">
        <section className="dash-heading">
          <div>
            <span className="dash-eyebrow">TODAY · {todayLabel()}</span>
            <h1>{greeting()}, <em>{data.name}</em></h1>
            <p>Here is your nutrition summary for today.</p>
          </div>
          <div className="dash-heading-actions">
            <button onClick={() => setFoodOpen(true)}>+ Add food</button>
            <button className="soft" onClick={() => setWaterOpen(true)}>Log water</button>
            <button className="soft" onClick={() => navigate("/chat")}>Ask Nutri</button>
          </div>
        </section>
        {error && <div className="dash-error">{error}</div>}
        <section className="dash-summary">
          <Summary icon="◒" label="Calories Consumed" value={`${Math.round(calories)} kcal`}
            target={calorieTarget} used={calories}
            note={calorieTarget ? `${Math.round(calorieTarget)} kcal daily goal` : "Set a target in profile"} />
          <Summary icon="●" label="Protein Intake" value={`${round(protein)} g`}
            target={proteinTarget} used={protein}
            note={proteinTarget ? `${round(proteinTarget)} g daily goal` : "Set a target in profile"} />
          <Summary icon="◉" label="Water Hydration" value={`${round(water)} L`}
            target={waterTarget} used={water}
            note={waterTarget ? `${round(waterTarget)} L daily goal` : "Set a target in profile"} />
          <article className="dash-stat">
            <div className="stat-title"><span className="stat-icon">◆</span><span>Body Mass Index</span></div>
            <strong>{data.bmi ?? "--"}</strong>
            <small className="bmi-pill">{data.bmiCategory || "Complete profile"}</small>
            <p>Calculated from your saved profile.</p>
          </article>
        </section>
        <section className="dash-analytics">
          <article className="dash-panel weekly-panel">
            <PanelTitle label="LAST 7 DAYS" title="Calorie intake" side={calorieTarget ? `Goal ${Math.round(calorieTarget)} kcal` : ""} />
            <div className="week-chart">
              {weekly.length ? weekly.map(([day, value]) => (
                <div className="week-item" key={day}>
                  <span>{Math.round(number(value))}</span>
                  <div className="week-track">
                    <i style={{ height: `${Math.max(6, number(value) / maxWeek * 100)}%` }} />
                  </div>
                  <small>{day}</small>
                </div>
              )) : <div className="chart-empty">No nutrition history yet.</div>}
            </div>
          </article>
          <article className="dash-panel macro-panel">
            <PanelTitle label="TODAY" title="Macronutrient distribution" />
            <div className="macro-layout">
              <div className="macro-donut"
                style={{ "--carbs": `${carbPct}%`, "--protein": `${carbPct + proteinPct}%` }}>
                <div><strong>{Math.round(calories)}</strong><small>kcal</small></div>
              </div>
              <div className="macro-list">
                <Macro label="Carbohydrates" grams={carbs} percent={carbPct} tone="carbs" />
                <Macro label="Protein" grams={protein} percent={proteinPct} tone="protein" />
                <Macro label="Fats" grams={fat} percent={fatPct} tone="fat" />
              </div>
            </div>
          </article>
        </section>
        <section className="dash-bottom">
          <article className="dash-panel meals-panel">
            <div className="panel-title-row">
              <div><span>MEAL LOG</span><h2>Today's meals</h2></div>
              <button onClick={() => setFoodOpen(true)}>Add food</button>
            </div>
            {!data.todayMeals?.length ? (
              <div className="no-meals">
                <div><strong>No meals logged yet</strong><p>Tell Nutri what you ate or add a food manually.</p></div>
                <button onClick={() => navigate("/chat")}>Tell Nutri what I ate</button>
              </div>
            ) : (
              <div className="meal-list">
                {data.todayMeals.map((meal, index) => (
                  <div className="meal-row" key={meal.id || index}>
                    <div className="meal-dot" />
                    <div className="meal-main">
                      <small>{format(meal.mealType)}</small>
                      <strong>{meal.foodName}</strong>
                      <Source food={meal} />
                    </div>
                    <div className="meal-values">
                      <strong>{nutrition(meal.calories, "kcal")}</strong>
                      <span>{nutrition(meal.protein, "g protein")}</span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </article>
          <article className="dash-panel progress-panel">
            <PanelTitle label="DAILY PROGRESS" title="Remaining today" />
            <Progress label="Energy" value={remaining(calorieTarget, calories, "kcal")} />
            <Progress label="Protein" value={remaining(proteinTarget, protein, "g")} />
            <Progress label="Water" value={remaining(waterTarget, water, "L")} />
            <button className="progress-cta" onClick={() => navigate("/chat")}>Get meal suggestions →</button>
          </article>
        </section>
      </main>
      {waterOpen && (
        <div className="dash-overlay" onMouseDown={e => e.target === e.currentTarget && setWaterOpen(false)}>
          <section className="dash-modal">
            <div className="modal-head"><h2>Log water</h2><button onClick={() => setWaterOpen(false)}>Close</button></div>
            <label>Amount</label>
            <div className="input-with-unit"><input type="number" min="1" placeholder="350" value={waterMl}
              onChange={e => setWaterMl(e.target.value)} /><span>mL</span></div>
            <button className="modal-primary" onClick={logWater} disabled={saving === "water"}>
              {saving === "water" ? "Saving..." : "Add water"}
            </button>
          </section>
        </div>
      )}
      {foodOpen && (
        <div className="dash-overlay" onMouseDown={e => e.target === e.currentTarget && closeFood()}>
          <section className="dash-modal food-modal">
            <div className="modal-head"><h2>{selectedFood ? "Add meal" : "Find a food"}</h2><button onClick={closeFood}>Close</button></div>
            {!selectedFood ? <>
              <div className="food-search"><input value={query} placeholder="Search idli, poha, dal..."
                onChange={e => setQuery(e.target.value)} onKeyDown={e => e.key === "Enter" && searchFood()} />
                <button onClick={searchFood} disabled={searching || !query.trim()}>{searching ? "Searching..." : "Search"}</button>
              </div>
              {searched && !results.length && <p className="search-empty">No relevant foods found.</p>}
              <div className="food-results">{results.map((food, index) => (
                <button key={`${food.source}-${food.sourceId}-${index}`} onClick={() => setSelectedFood(food)}>
                  <div><strong>{food.foodName}</strong><Source food={food} /></div>
                  <span>{nutrition(food.calories, "kcal")}</span>
                </button>
              ))}</div>
            </> : <div className="selected-food">
              <div className="selected-food-head"><div><small>SELECTED FOOD</small><h3>{selectedFood.foodName}</h3><Source food={selectedFood} /></div>
                <button onClick={() => setSelectedFood(null)}>Change</button></div>
              <label>Meal</label>
              <select value={mealType} onChange={e => setMealType(e.target.value)}>
                <option value="BREAKFAST">Breakfast</option><option value="LUNCH">Lunch</option>
                <option value="DINNER">Dinner</option><option value="SNACK">Snack</option>
              </select>
              <label>Quantity</label>
              <div className="input-with-unit"><input type="number" min="1" placeholder="100" value={grams}
                onChange={e => setGrams(e.target.value)} /><span>g</span></div>
              <button className="secondary-modal" onClick={() => navigate("/chat", {
                state: { food: { foodName: selectedFood.foodName, source: selectedFood.source, sourceId: selectedFood.sourceId } }
              })}>Ask Nutri about this food</button>
              <button className="modal-primary" onClick={logFood} disabled={saving === "food"}>
                {saving === "food" ? "Adding..." : "Add to meal log"}
              </button>
            </div>}
          </section>
        </div>
      )}
    </div>
  );
}
function Summary({ icon, label, value, target, used, note }) {
  const progress = target ? Math.min(100, Math.round((used / target) * 100)) : 0;
  return <article className="dash-stat"><div className="stat-title"><span className="stat-icon">{icon}</span><span>{label}</span></div>
    <strong>{value}</strong><div className="stat-track"><i style={{ width: `${progress}%` }} /></div><p>{note}</p></article>;
}
function PanelTitle({ label, title, side }) {
  return <div className="panel-title-row"><div><span>{label}</span><h2>{title}</h2></div>{side && <small>{side}</small>}</div>;
}
function Macro({ label, grams, percent, tone }) {
  return <div className="macro-row"><i className={tone} /><div><strong>{label}</strong><small>{round(grams)} g</small></div><span>{percent}%</span></div>;
}
function Progress({ label, value }) { return <div className="progress-row"><span>{label}</span><strong>{value}</strong></div>; }
function Source({ food }) {
  return <small className="food-source">{food?.source || "Source unavailable"}{food?.sourceId ? ` · ID ${food.sourceId}` : ""}</small>;
}
function PageMessage({ text, retry }) {
  return <div className="dash-message"><h2>{text}</h2>{retry && <button onClick={retry}>Try again</button>}</div>;
}
function cleanFoodResults(foods, query) {
  if (!foods.length) return [];
  const norm = value => String(value || "").toLowerCase().replace(/[^a-z0-9 ]/g, " ").replace(/\s+/g, " ").trim();
  const search = norm(query);
  const words = search.split(" ").filter(word => word.length >= 3);
  const relevant = foods.slice(1).filter(food => {
    const name = norm(food.foodName);
    return name.includes(search) || (search.includes(name) && name.length >= 3) || words.some(word => name.includes(word));
  });
  return [foods[0], ...relevant].slice(0, 6);
}
const number = value => Number.isFinite(Number(value)) ? Number(value) : 0;
const optional = value => value == null || !Number.isFinite(Number(value)) ? null : Number(value);
const round = value => Math.round(number(value) * 10) / 10;
const nutrition = (value, unit) => Number.isFinite(Number(value)) ? `${round(value)} ${unit}` : "N/A";
const remaining = (target, used, unit) => target == null ? "Target not set" : `${round(Math.max(0, target - used))} ${unit}`;
const format = value => value ? value.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, c => c.toUpperCase()) : "Meal";
const greeting = () => new Date().getHours() < 12 ? "Good morning" : new Date().getHours() < 18 ? "Good afternoon" : "Good evening";
const todayLabel = () => new Intl.DateTimeFormat("en-IN", { day: "2-digit", month: "short", year: "numeric" }).format(new Date());
