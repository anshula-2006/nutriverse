import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import AppNav from "./AppNav.jsx";
import { API_URL, apiFetch, handleUnauthorized, readResponse, readUser } from "./api.js";
import "./Profile.css";

const EMPTY = {
  age: "", height: "", weight: "", gender: "", dietType: "",
  activityLevel: "", goal: "", foodPreferences: "",
  foodDislikes: "", dietaryRestriction: "NONE"
};

const OPTIONS = {
  gender: [["", "Not set"], ["MALE", "Male"], ["FEMALE", "Female"], ["OTHER", "Other"]],
  dietType: [["", "Not set"], ["VEGETARIAN", "Vegetarian"], ["VEGAN", "Vegan"], ["NON_VEGETARIAN", "Non vegetarian"]],
  activityLevel: [["", "Not set"], ["SEDENTARY", "Sedentary"], ["LIGHTLY_ACTIVE", "Lightly active"], ["MODERATELY_ACTIVE", "Moderately active"], ["VERY_ACTIVE", "Very active"], ["EXTRA_ACTIVE", "Extra active"]],
  goal: [["", "Not set"], ["WEIGHT_LOSS", "Weight loss"], ["WEIGHT_GAIN", "Weight gain"], ["MAINTENANCE", "Maintenance"], ["HEALTHY_EATING", "Healthy eating"], ["FITNESS", "Fitness"]],
  dietaryRestriction: [["NONE", "None"], ["GLUTEN_FREE", "Gluten free / celiac-focused"]]
};

export default function Profile() {
  const navigate = useNavigate();
  const token = localStorage.getItem("token");
  const user = readUser();
  const savingRef = useRef(false);

  const [profile, setProfile] = useState(EMPTY);
  const [targets, setTargets] = useState({});
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState(false);

  function apply(data) {
    setProfile({
      age: data.age ?? "",
      height: data.height ?? "",
      weight: data.weight ?? "",
      gender: data.gender ?? "",
      dietType: data.dietType ?? "",
      activityLevel: data.activityLevel ?? "",
      goal: data.goal ?? "",
      foodPreferences: data.foodPreferences ?? "",
      foodDislikes: data.foodDislikes ?? "",
      dietaryRestriction: data.dietaryRestriction ?? "NONE"
    });

    setTargets({
      calories: data.dailyCalorieTarget ?? null,
      protein: data.dailyProteinTarget ?? null,
      water: data.dailyWaterTarget ?? null
    });
  }

  const loadProfile = useCallback(async signal => {
    setLoading(true);
    try {
      const response = await fetch(`${API_URL}/api/profile`, {
        headers: { Authorization: `Bearer ${token}` },
        signal
      });

      if (handleUnauthorized(response, navigate)) return;
      const data = await readResponse(response, "Could not load your profile");
      if (!data || Array.isArray(data)) throw new Error("Invalid profile response.");
      if (!signal?.aborted) apply(data);
    } catch (e) {
      if (e.name !== "AbortError") {
        setMessage(e.message || "Could not load your profile.");
        setError(true);
      }
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
    loadProfile(controller.signal);
    return () => controller.abort();
  }, [loadProfile, navigate, token]);

  function change(name, value) {
    setProfile(p => ({ ...p, [name]: value }));
    setMessage("");
    setError(false);
  }

  function validate() {
    const age = Number(profile.age);
    const height = Number(profile.height);
    const weight = Number(profile.weight);

    if (!Number.isInteger(age) || age < 1 || age > 120) return "Enter an age from 1 to 120.";
    if (!Number.isFinite(height) || height < 50 || height > 250) return "Enter height from 50 to 250 cm.";
    if (!Number.isFinite(weight) || weight < 10 || weight > 500) return "Enter weight from 10 to 500 kg.";
    if (profile.foodPreferences.trim().length > 200) return "Food preferences are too long.";
    if (profile.foodDislikes.trim().length > 300) return "Foods to avoid are too long.";
    return null;
  }

  async function save() {
    if (savingRef.current) return;

    const validation = validate();
    if (validation) {
      setMessage(validation);
      setError(true);
      return;
    }

    savingRef.current = true;
    setSaving(true);
    setMessage("");

    try {
      const response = await apiFetch(`${API_URL}/api/profile`, {
        method: "PATCH",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`
        },
        body: JSON.stringify({
          age: Number(profile.age),
          height: Number(profile.height),
          weight: Number(profile.weight),
          gender: profile.gender || null,
          dietType: profile.dietType || null,
          activityLevel: profile.activityLevel || null,
          goal: profile.goal || null,
          foodPreferences: profile.foodPreferences.trim(),
          foodDislikes: profile.foodDislikes.trim(),
          dietaryRestriction: profile.dietaryRestriction || "NONE"
        })
      });

      if (handleUnauthorized(response, navigate)) return;
      const data = await readResponse(response, "Could not update your profile");
      apply(data);
      setEditing(false);
      setMessage("Profile updated successfully.");
      setError(false);
    } catch (e) {
      setMessage(e.message || "Could not update your profile.");
      setError(true);
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }

  async function cancel() {
    setEditing(false);
    setMessage("");
    setError(false);
    await loadProfile();
  }

  if (loading) {
    return (
      <div className="profile-loading">
        <div />
        <div />
        <div />
      </div>
    );
  }

  return (
    <div className="profile-page">
      <AppNav name={user.name} />

      <main className="profile-main">
        <header className="profile-header">
          <div>
            <small>PERSONAL NUTRITION PROFILE</small>
            <h1>Profile</h1>
            <p>Used to calculate estimated targets and personalize nutrition recommendations.</p>
          </div>

          {!editing && (
            <button onClick={() => setEditing(true)}>Edit profile</button>
          )}
        </header>

        {message && (
          <p className={`profile-message ${error ? "error" : ""}`} role={error ? "alert" : "status"}>
            {message}
          </p>
        )}

        <section className="profile-summary">
          <div><small>ACCOUNT</small><strong>{user.name || "User"}</strong></div>
          <div><small>CURRENT GOAL</small><strong>{format(profile.goal)}</strong></div>
        </section>

        <section className="profile-section">
          <div className="profile-section-head">
            <h2>Nutrition preferences</h2>
            <p>These details influence personalization but do not replace clinical assessment.</p>
          </div>

          <div className="profile-grid">
            <Field label="Age" type="number" value={profile.age} disabled={!editing || saving} onChange={v => change("age", v)} />
            <Field label="Gender" value={profile.gender} options={OPTIONS.gender} disabled={!editing || saving} onChange={v => change("gender", v)} />
            <Field label="Height" type="number" unit="cm" value={profile.height} disabled={!editing || saving} onChange={v => change("height", v)} />
            <Field label="Weight" type="number" unit="kg" value={profile.weight} disabled={!editing || saving} onChange={v => change("weight", v)} />
            <Field label="Diet preference" value={profile.dietType} options={OPTIONS.dietType} disabled={!editing || saving} onChange={v => change("dietType", v)} />
            <Field label="Activity level" value={profile.activityLevel} options={OPTIONS.activityLevel} disabled={!editing || saving} onChange={v => change("activityLevel", v)} />
            <Field label="Nutrition goal" value={profile.goal} options={OPTIONS.goal} disabled={!editing || saving} onChange={v => change("goal", v)} />
            <Field label="Dietary restriction" value={profile.dietaryRestriction} options={OPTIONS.dietaryRestriction} disabled={!editing || saving} onChange={v => change("dietaryRestriction", v)} />
            <Field label="Food preferences" value={profile.foodPreferences} placeholder="Oats, paneer, South Indian food" disabled={!editing || saving} onChange={v => change("foodPreferences", v)} />
            <Field label="Foods to avoid" value={profile.foodDislikes} placeholder="Peanuts, mushrooms" disabled={!editing || saving} onChange={v => change("foodDislikes", v)} />
          </div>

          <p className="profile-note">
            Gluten-free screening detects available ingredient conflicts. Packaged-food labels and cross-contact information should still be checked.
          </p>

          {editing && (
            <div className="profile-actions">
              <button className="secondary" onClick={cancel} disabled={saving}>Cancel</button>
              <button onClick={save} disabled={saving}>{saving ? "Saving..." : "Save changes"}</button>
            </div>
          )}
        </section>

        <section className="profile-section">
          <div className="profile-section-head">
            <h2>Estimated daily targets</h2>
            <p>Profile-based estimates, not clinical prescriptions.</p>
          </div>

          <div className="profile-targets">
            <Target label="Calories" value={target(targets.calories, "kcal")} />
            <Target label="Protein" value={target(targets.protein, "g")} />
            <Target label="Water" value={target(targets.water, "L")} />
          </div>
        </section>
      </main>
    </div>
  );
}

function Field({ label, value, onChange, disabled, options, type = "text", unit, placeholder }) {
  return (
    <label className="profile-field">
      <span>{label}</span>
      <div>
        {options ? (
          <select value={value} disabled={disabled} onChange={e => onChange(e.target.value)}>
            {options.map(([value, text]) => <option key={value} value={value}>{text}</option>)}
          </select>
        ) : (
          <input type={type} value={value} disabled={disabled} placeholder={placeholder} onChange={e => onChange(e.target.value)} />
        )}
        {unit && <small>{unit}</small>}
      </div>
    </label>
  );
}

function Target({ label, value }) {
  return <div className="profile-target"><span>{label}</span><strong>{value}</strong></div>;
}

const target = (value, unit) => value == null ? "Not calculated" : `${value} ${unit}`;

function format(value) {
  if (!value) return "Not set";
  return value.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, c => c.toUpperCase());
}