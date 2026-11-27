import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Html5Qrcode } from "html5-qrcode";
import AppNav from "./AppNav.jsx";
import { API_URL, apiFetch, handleUnauthorized, readResponse } from "./api.js";
import "./FoodScan.css";

const nutrients = [
  ["calories", "Calories", "kcal"],
  ["protein", "Protein", "g"],
  ["carbs", "Carbohydrates", "g"],
  ["fat", "Fat", "g"],
  ["fiber", "Fiber", "g"],
  ["sodium", "Sodium", "mg"],
];
const valid = (n) => typeof n === "number" && Number.isFinite(n) && n >= 0;
const fmt = (n) => (valid(n) ? Number(n.toFixed(2)).toString() : "Unavailable");
const gramsOK = (n) =>
  Number.isFinite(Number(n)) && Number(n) > 0 && Number(n) <= 10000;

export default function FoodScan() {
  const navigate = useNavigate();
  const token = localStorage.getItem("token");
  const [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState("");
  const [barcode, setBarcode] = useState(""),
    [query, setQuery] = useState(""),
    [results, setResults] = useState([]);
  const [selected, setSelected] = useState(null),
    [grams, setGrams] = useState("100"),
    [cart, setCart] = useState([]),
    [meal, setMeal] = useState("BREAKFAST"),
    [preview, setPreview] = useState("");
  const [view, setView] = useState("meal");
  const [review, setReview] = useState([]);
  const scanner = useRef(null),
    cameraActive = useRef(false),
    locked = useRef(false),
    decoded = useRef(false);
  useEffect(() => {
    if (!token) navigate("/login", { replace: true });
  }, [token, navigate]);
  useEffect(
    () => () => {
      const s = scanner.current;
      if (s) {
        if (s.isScanning)
          s.stop()
            .then(() => s.clear())
            .catch(() => {});
        else
          try {
            s.clear();
          } catch {}
      }
    },
    [],
  );
  async function request(path, options = {}, timeout = 30000) {
    const response = await apiFetch(
      API_URL + "/api/nutrition" + path,
      {
        ...options,
        headers: {
          Authorization: "Bearer " + token,
          "Content-Type": "application/json",
          ...options.headers,
        },
      },
      timeout,
    );
    if (handleUnauthorized(response, navigate))
      throw new Error("Please sign in again.");
    return readResponse(response, "Nutrition request failed");
  }
  async function run(fn) {
    if (locked.current) return;
    locked.current = true;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await fn();
    } catch (e) {
      setError(e.message || "Request failed");
    } finally {
      locked.current = false;
      setBusy(false);
    }
  }
  function select(food) {
    if (
      !food?.verified ||
      food.estimated ||
      food.servingUnit !== "g" ||
      !valid(food.servingSize) ||
      food.servingSize === 0 ||
      !food.source ||
      !food.sourceId
    )
      throw new Error("No usable verified gram-based record.");
    setSelected(food);
    setGrams("100");
  }
  function validBarcode(code) {
    if (!/^(?:\d{8}|\d{12}|\d{13}|\d{14})$/.test(code)) return false;
    const digits = code.split("").map(Number);
    const check = digits.pop();
    const sum = digits
      .reverse()
      .reduce((total, d, i) => total + d * (i % 2 === 0 ? 3 : 1), 0);
    return (10 - (sum % 10)) % 10 === check;
  }
  async function lookup(code) {
    code = code.trim();
    setSelected(null);
    setResults([]);
    if (!validBarcode(code))
      throw new Error(
        "Invalid retail barcode. Check all printed digits or scan again.",
      );
    try {
      select(await request("/barcode/" + encodeURIComponent(code)));
    } catch (e) {
      throw new Error(e.message + " — Try searching the product name below.");
    }
  }
  const usable = (f) =>
    f?.verified &&
    !f.estimated &&
    f.servingUnit === "g" &&
    valid(f.servingSize) &&
    f.servingSize > 0 &&
    f.source &&
    f.sourceId;
  async function matches(name) {
    const data = await request(
      "/search?query=" + encodeURIComponent(name.trim()),
    );
    return Array.isArray(data) ? data.filter(usable).slice(0, 8) : [];
  }
  function updateReview(i, patch) {
    setReview((old) => old.map((r, j) => (j === i ? { ...r, ...patch } : r)));
  }
  async function refreshReview(i, name) {
    await run(async () => {
      updateReview(i, { options: [], choice: "" });
      const options = await matches(name);
      updateReview(i, { options, choice: "" });
    });
  }
  async function addReview() {
    await run(async () => {
      const rows = review.filter((r) => r.include);
      if (
        !rows.length ||
        rows.some((r) => r.choice === "" || !gramsOK(r.grams))
      )
        throw new Error(
          "Choose a record and enter grams for each included food.",
        );
      const items = [];
      for (const r of rows) {
        const f = r.options[Number(r.choice)];
        const food = await request(
          "/food?" +
            new URLSearchParams({ source: f.source, sourceId: f.sourceId }),
        );
        if (!usable(food)) throw new Error("No usable record for " + r.name);
        items.push({
          food,
          grams: Number(r.grams),
          id: crypto.randomUUID?.() ?? String(Date.now() + Math.random()),
        });
      }
      setCart((old) => [...old, ...items]);
      setReview([]);
      setView("cart");
      setNotice("Confirmed foods added to cart.");
    });
  }
  async function search(name) {
    setSelected(null);
    setResults([]);
    if (!name.trim()) throw new Error("Enter a food name.");
    const data = await request(
      "/search?query=" + encodeURIComponent(name.trim()),
    );
    setResults(Array.isArray(data) ? data : []);
    if (!data.length) setNotice("No matching foods found. Try another name.");
  }
  async function stopCamera() {
    const s = scanner.current;
    if (s?.isScanning) await s.stop();
    cameraActive.current = false;
    if (s) s.clear();
  }
  async function startCamera() {
    await run(async () => {
      if (cameraActive.current) return;
      const s = (scanner.current ??= new Html5Qrcode("nv-reader"));
      decoded.current = false;
      await s.start(
        { facingMode: "environment" },
        { fps: 10 },
        (code) => {
          if (decoded.current || locked.current || !validBarcode(code)) return;
          decoded.current = true;
          setBarcode(code);
          void run(async () => {
            await stopCamera();
            await lookup(code);
          });
        },
        () => {},
      );
      cameraActive.current = true;
    });
  }
  async function scanBarcodeFile(file) {
    if (!file) return;
    await run(async () => {
      await stopCamera();
      const s = (scanner.current ??= new Html5Qrcode("nv-reader"));
      const code = await s.scanFile(file, true);
      setBarcode(code);
      s.clear();
      await lookup(code);
    });
  }
  async function scanMeal(file) {
    if (!file) return;
    await run(async () => {
      setReview([]);
      setSelected(null);
      setResults([]);
      await stopCamera();
      const url = URL.createObjectURL(file);
      let image;
      try {
        image = await new Promise((resolve, reject) => {
          const img = new Image();
          img.onload = () => resolve(img);
          img.onerror = () => reject(new Error("Cannot read image"));
          img.src = url;
        });
        const canvas = document.createElement("canvas");
        const scale = Math.min(1, 1200 / Math.max(image.width, image.height));
        canvas.width = Math.max(1, Math.round(image.width * scale));
        canvas.height = Math.max(1, Math.round(image.height * scale));
        canvas
          .getContext("2d")
          .drawImage(image, 0, 0, canvas.width, canvas.height);
        const data = canvas.toDataURL("image/jpeg", 0.8);
        setPreview(data);
        const response = await request(
          "/scan-image",
          { method: "POST", body: JSON.stringify({ image: data }) },
          60000,
        );
        const parsed = JSON.parse(response.result);
        if (!Array.isArray(parsed.foods))
          throw new Error("Invalid recognition response");
        const list = parsed.foods
          .filter((f) => typeof f?.name === "string" && f.name.trim())
          .slice(0, 10);
        const rows = await Promise.all(
          list.map(async (f) => {
            let options = [];
            try {
              options = await matches(f.name);
            } catch {}
            return {
              name: f.name,
              confidence: f.confidence,
              options,
              choice: "",
              grams: "",
              include: true,
            };
          }),
        );
        setReview(rows);
        if (!list.length)
          setNotice("No food recognized. Search manually below.");
      } finally {
        URL.revokeObjectURL(url);
      }
    });
  }
  function add() {
    if (!selected || !gramsOK(grams)) return;
    setCart((old) => [
      ...old,
      {
        food: selected,
        grams: Number(grams),
        id: crypto.randomUUID?.() ?? String(Date.now() + Math.random()),
      },
    ]);
    setNotice("Added to nutrition cart. Open the cart tab to review.");
    setSelected(null);
    setResults([]);
  }
  async function logCart() {
    await run(async () => {
      let count = 0;
      for (const item of cart) {
        try {
          await request("/log-meal", {
            method: "POST",
            body: JSON.stringify({
              mealType: meal,
              source: item.food.source,
              sourceId: item.food.sourceId,
              quantityGrams: Number(item.grams),
            }),
          });
          setCart((old) => old.filter((x) => x.id !== item.id));
          count++;
        } catch (e) {
          throw new Error(
            `${count} item(s) saved. Remaining items kept in cart. ${e.message}`,
          );
        }
      }
      setNotice(`${count} item(s) saved to your meal log.`);
    });
  }
  async function changeView(next) {
    if (busy) return;
    await run(async () => {
      await stopCamera();
      setView(next);
    });
  }
  return (
    <div className="scan-page">
      <AppNav />
      <main className="scan-main">
        <header className="scan-header">
          <p>Food scanner</p>
          <h1>Scan. Confirm. Nourish.</h1>
          <span>
            Identify foods from a meal photo or barcode, confirm the exact
            record and portion, and build your nutrition cart.
          </span>
        </header>
        <nav className="scan-tabs" aria-label="Scanner views">
          {[
            ["meal", "Meal photo", "Identify foods from a photograph"],
            ["barcode", "Barcode", "Scan packaged food"],
            [
              "cart",
              `Nutrition cart (${cart.length})`,
              "Review and log your meal",
            ],
          ].map(([key, title, desc]) => (
            <button
              key={key}
              disabled={busy}
              aria-pressed={view === key}
              onClick={() => changeView(key)}
            >
              <strong>{title}</strong>
              <small>{desc}</small>
            </button>
          ))}
        </nav>
        <section className="scan-grid" hidden={view === "cart"}>
          <article className="scan-panel" hidden={view !== "meal"}>
            <h2>Meal photo</h2>
            <p>
              AI suggests visible foods, not quantities or hidden ingredients.
            </p>
            <label className="scan-upload">
              Upload meal photo
              <input
                disabled={busy}
                type="file"
                accept="image/*"
                onChange={(e) => {
                  void scanMeal(e.target.files[0]);
                  e.target.value = "";
                }}
              />
            </label>
            <label className="scan-upload">
              Capture meal photo
              <input
                disabled={busy}
                type="file"
                accept="image/*"
                capture="environment"
                onChange={(e) => {
                  void scanMeal(e.target.files[0]);
                  e.target.value = "";
                }}
              />
            </label>
            {preview && (
              <img className="scan-photo" src={preview} alt="Your meal" />
            )}
            {review.length > 0 && (
              <div className="scan-review">
                <h3>Confirm detected foods</h3>
                <p>
                  Choose the matching record and enter the amount eaten. Uncheck
                  unwanted foods.
                </p>
                {review.map((r, i) => (
                  <div className="scan-review-row" key={i}>
                    <label>
                      <input
                        type="checkbox"
                        checked={r.include}
                        disabled={busy}
                        onChange={(e) =>
                          updateReview(i, { include: e.target.checked })
                        }
                      />{" "}
                      Include
                    </label>
                    <div className="scan-review-name">
                      <input
                        aria-label="Food name"
                        value={r.name}
                        disabled={busy}
                        onChange={(e) =>
                          updateReview(i, {
                            name: e.target.value,
                            options: [],
                            choice: "",
                          })
                        }
                      />
                      <button
                        disabled={busy || !r.name.trim()}
                        onClick={() => refreshReview(i, r.name)}
                      >
                        Search
                      </button>
                    </div>
                    <select
                      aria-label="Nutrition record"
                      disabled={busy || !r.include}
                      value={r.choice}
                      onChange={(e) =>
                        updateReview(i, { choice: e.target.value })
                      }
                    >
                      <option value="">
                        {r.options.length
                          ? "Choose nutrition record"
                          : "No match — edit and search"}
                      </option>
                      {r.options.map((f, j) => (
                        <option key={j} value={j}>
                          {f.foodName} · {f.source}
                        </option>
                      ))}
                    </select>
                    <input
                      aria-label="Quantity in grams"
                      placeholder="Grams"
                      type="number"
                      min="0.01"
                      max="10000"
                      step="any"
                      disabled={busy || !r.include}
                      value={r.grams}
                      onChange={(e) =>
                        updateReview(i, { grams: e.target.value })
                      }
                    />
                  </div>
                ))}
                <button
                  disabled={
                    busy ||
                    !review.some((r) => r.include) ||
                    review.some(
                      (r) =>
                        r.include && (r.choice === "" || !gramsOK(r.grams)),
                    )
                  }
                  onClick={addReview}
                >
                  Add confirmed foods to cart
                </button>
              </div>
            )}
          </article>
          <article className="scan-panel" hidden={view !== "barcode"}>
            <h2>Packaged food</h2>
            <p>Scan a barcode or enter its digits.</p>
            <div id="nv-reader" />
            <button disabled={busy} onClick={startCamera}>
              Start camera
            </button>{" "}
            <button disabled={busy} onClick={() => run(stopCamera)}>
              Stop camera
            </button>
            <label className="scan-upload">
              Upload barcode image
              <input
                disabled={busy}
                type="file"
                accept="image/*"
                onChange={(e) => {
                  void scanBarcodeFile(e.target.files[0]);
                  e.target.value = "";
                }}
              />
            </label>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                void run(() => lookup(barcode));
              }}
            >
              <input
                value={barcode}
                onChange={(e) => setBarcode(e.target.value)}
                placeholder="Barcode"
                inputMode="numeric"
              />
              <button disabled={busy}>Look up</button>
            </form>
          </article>
        </section>
        {busy && <p role="status">Processing…</p>}
        {error && (
          <p role="alert" className="scan-error">
            {error}
          </p>
        )}
        {notice && <p role="status">{notice}</p>}
        <details
          className="scan-panel"
          hidden={view === "cart"}
          open={view === "barcode" || !!selected || results.length > 0}
        >
          <summary>Manual food search</summary>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              void run(() => search(query));
            }}
          >
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search or correct detected food"
            />
            <button disabled={busy}>Search</button>
          </form>
          {results.map((f, i) => (
            <div className="scan-line" key={i}>
              <span>
                {f.foodName}
                <small>
                  {" "}
                  — {f.source} · {f.sourceId}
                </small>
              </span>
              <button
                disabled={busy}
                onClick={() =>
                  run(async () =>
                    select(
                      await request(
                        "/food?" +
                          new URLSearchParams({
                            source: f.source,
                            sourceId: f.sourceId,
                          }),
                      ),
                    ),
                  )
                }
              >
                Select
              </button>
            </div>
          ))}
          {selected && (
            <div className="scan-record">
              <h3>{selected.foodName}</h3>
              <p>
                {selected.source} · {selected.sourceId} · per{" "}
                {selected.servingSize} g
              </p>
              <div className="scan-nutrients">
                {nutrients.map(([k, label, unit]) => (
                  <div key={k}>
                    <small>{label}</small>
                    <strong>
                      {fmt(selected[k])} {valid(selected[k]) ? unit : ""}
                    </strong>
                  </div>
                ))}
              </div>
              {selected.ingredients && (
                <p>Ingredients: {selected.ingredients}</p>
              )}
              <label>
                Confirmed quantity (g){" "}
                <input
                  type="number"
                  min="0.01"
                  max="10000"
                  step="any"
                  value={grams}
                  onChange={(e) => setGrams(e.target.value)}
                />
              </label>
              <button disabled={busy || !gramsOK(grams)} onClick={add}>
                Add to nutrition cart
              </button>
            </div>
          )}
        </details>
        <section className="scan-panel" hidden={view !== "cart"}>
          <h2>Nutrition cart</h2>
          {!cart.length && <p>No foods added yet.</p>}
          {cart.map((item) => (
            <div className="scan-line" key={item.id}>
              <span>{item.food.foodName}</span>
              <label>
                Grams{" "}
                <input
                  type="number"
                  min="0.01"
                  max="10000"
                  step="any"
                  disabled={busy}
                  value={item.grams}
                  onChange={(e) =>
                    setCart((old) =>
                      old.map((x) =>
                        x.id === item.id ? { ...x, grams: e.target.value } : x,
                      ),
                    )
                  }
                />
              </label>
              <button
                disabled={busy}
                onClick={() =>
                  setCart((old) => old.filter((x) => x.id !== item.id))
                }
              >
                Remove
              </button>
            </div>
          ))}
          {cart.length > 0 && (
            <>
              <div className="scan-nutrients">
                {nutrients.map(([k, label, unit]) => {
                  const known = cart.filter(
                    (x) => valid(x.food[k]) && gramsOK(x.grams),
                  );
                  const total = known.reduce(
                    (sum, x) =>
                      sum + (x.food[k] * Number(x.grams)) / x.food.servingSize,
                    0,
                  );
                  return (
                    <div key={k}>
                      <small>{label}</small>
                      <strong>
                        {known.length ? fmt(total) + " " + unit : "Unavailable"}
                      </strong>
                      {known.length !== cart.length && (
                        <small>Incomplete total</small>
                      )}
                    </div>
                  );
                })}
              </div>
              <label>
                Meal{" "}
                <select
                  value={meal}
                  disabled={busy}
                  onChange={(e) => setMeal(e.target.value)}
                >
                  {["BREAKFAST", "LUNCH", "DINNER", "SNACK"].map((m) => (
                    <option key={m}>{m}</option>
                  ))}
                </select>
              </label>{" "}
              <button
                disabled={busy || cart.some((x) => !gramsOK(x.grams))}
                onClick={logCart}
              >
                Log cart as meal
              </button>
            </>
          )}
        </section>
        <div className="scan-actions">
          <button onClick={() => navigate("/chat")}>Ask Nutri</button>
          <button onClick={() => navigate("/dashboard")}>Dashboard</button>
        </div>
      </main>
    </div>
  );
}
