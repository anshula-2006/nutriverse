import AppNav from "./AppNav.jsx";
import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";

import "./Profile.css";
import { API_URL, readUser, handleUnauthorized, readResponse } from "./api.js";

const EMPTY_PROFILE = {
  age: "",
  height: "",
  weight: "",
  gender: "",
  dietType: "",
  activityLevel: "",
  goal: ""
};

function Profile() {

  const navigate = useNavigate();

  const token =
    localStorage.getItem("token");

  const user = readUser();
  const savingRef = useRef(false);

  const [profile, setProfile] =
    useState(EMPTY_PROFILE);

  const [targets, setTargets] =
    useState({
      dailyCalorieTarget: null,
      dailyProteinTarget: null,
      dailyWaterTarget: null
    });

  const [loading, setLoading] =
    useState(true);

  const [editing, setEditing] =
    useState(false);

  const [saving, setSaving] =
    useState(false);

  const [message, setMessage] =
    useState("");

  const [isError, setIsError] =
    useState(false);


  // =========================================================
  // LOAD PROFILE
  // =========================================================

  const loadProfile = useCallback(async (signal) => {

    try {

      setLoading(true);

      const response = await fetch(
        `${API_URL}/api/profile`,
        {
          method: "GET",
          signal,

          headers: {
            Authorization:
              `Bearer ${token}`
          }
        }
      );

      if (handleUnauthorized(response, navigate)) {
        return;
      }

      const data = await readResponse(response, "Could not load your profile");
      if (Array.isArray(data)) throw new Error("The server returned an invalid profile.");
      if (signal?.aborted) return;

      setProfile({
        age: data.age ?? "",
        height: data.height ?? "",
        weight: data.weight ?? "",
        gender: data.gender ?? "",
        dietType: data.dietType ?? "",
        activityLevel:
          data.activityLevel ?? "",
        goal: data.goal ?? ""
      });

      setTargets({
        dailyCalorieTarget:
          data.dailyCalorieTarget ?? null,

        dailyProteinTarget:
          data.dailyProteinTarget ?? null,

        dailyWaterTarget:
          data.dailyWaterTarget ?? null
      });

    } catch (error) {
      if (error.name !== "AbortError") {
        setMessage(error.message || "Could not load your profile.");
        setIsError(true);
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


  // =========================================================
  // UPDATE INPUT
  // =========================================================

  function updateField(
    field,
    value
  ) {

    setProfile(previous => ({
      ...previous,
      [field]: value
    }));

    setMessage("");
    setIsError(false);
  }


  // =========================================================
  // SAVE PROFILE
  // =========================================================

  async function saveProfile() {

    if (savingRef.current) {
      return;
    }

    const age = Number(profile.age);
    const height = Number(profile.height);
    const weight = Number(profile.weight);
    if (!Number.isInteger(age) || age < 1 || age > 120 ||
        !Number.isFinite(height) || height < 50 || height > 250 ||
        !Number.isFinite(weight) || weight < 10 || weight > 500) {
      setMessage("Enter an age from 1 to 120, height from 50 to 250 cm, and weight from 10 to 500 kg.");
      setIsError(true);
      return;
    }
    savingRef.current = true;

    try {

      setSaving(true);
      setMessage("");
      setIsError(false);


      const response = await fetch(
        `${API_URL}/api/profile`,
        {
          method: "PATCH",

          headers: {
            "Content-Type":
              "application/json",

            Authorization:
              `Bearer ${token}`
          },

          body: JSON.stringify({
            age: Number(profile.age),

            height:
              Number(profile.height),

            weight:
              Number(profile.weight),

            gender:
              profile.gender || null,

            dietType:
              profile.dietType || null,

            activityLevel:
              profile.activityLevel || null,

            goal:
              profile.goal || null
          })
        }
      );


      if (handleUnauthorized(response, navigate)) {
        return;
      }


      const data = await readResponse(response, "Could not update your profile");
      if (Array.isArray(data)) throw new Error("The server returned an invalid profile.");

      setProfile({
        age: data.age ?? "",
        height: data.height ?? "",
        weight: data.weight ?? "",
        gender: data.gender ?? "",
        dietType: data.dietType ?? "",
        activityLevel:
          data.activityLevel ?? "",
        goal: data.goal ?? ""
      });


      setTargets({
        dailyCalorieTarget:
          data.dailyCalorieTarget ?? null,

        dailyProteinTarget:
          data.dailyProteinTarget ?? null,

        dailyWaterTarget:
          data.dailyWaterTarget ?? null
      });


      setEditing(false);

      setMessage(
        "Profile updated successfully."
      );

      setIsError(false);


    } catch (error) {

      setMessage(error.message || "Could not update your profile.");

      setIsError(true);

    } finally {

      savingRef.current = false;
      setSaving(false);
    }
  }


  // =========================================================
  // CANCEL EDIT
  // =========================================================

  async function cancelEdit() {

    setEditing(false);
    setMessage("");
    setIsError(false);

    await loadProfile();
  }


  // =========================================================
  // FORMAT
  // =========================================================

  function format(value) {

    if (typeof value !== "string" || !value) {
      return "Not set";
    }

    return value
      .replaceAll("_", " ")
      .toLowerCase()
      .replace(
        /\b\w/g,
        letter =>
          letter.toUpperCase()
      );
  }


  // =========================================================
  // LOADING
  // =========================================================

  if (loading) {

    return (
      <div className="profile-loading">
        Loading your profile...
      </div>
    );
  }


  // =========================================================
  // UI
  // =========================================================

  return (

    <div className="profile-page">


      {/* SIDEBAR */}

      <AppNav />


      {/* MAIN */}

      <main className="profile-main">


        <header className="profile-header">


          <div>

            <small>
              PERSONAL NUTRITION PROFILE
            </small>

            <h1>
              My Profile
            </h1>

            <p>
              Keep your information updated
              so Nutri can personalize your
              recommendations.
            </p>

          </div>


          {!editing && (

            <button
              className="profile-edit-button"
              onClick={() => {

                setEditing(true);
                setMessage("");
                setIsError(false);

              }}
            >
              ✏️ Edit Profile
            </button>

          )}


        </header>


        {message && (

          <div
            role={isError ? "alert" : "status"}
            className={
              isError
                ? "profile-message error"
                : "profile-message"
            }
          >
            {message}
          </div>

        )}


        {/* USER CARD */}

        <section className="profile-user-card">


          <div className="profile-avatar">

            {user.name?.[0]
              ?.toUpperCase() || "U"}

          </div>


          <div>

            <h2>
              {user.name || "User"}
            </h2>

            <p>
              {format(profile.goal)}
            </p>

          </div>


        </section>


        {/* PERSONAL INFORMATION */}

        <section className="profile-card">


          <div className="profile-card-title">

            <h2>
              Personal Details
            </h2>

            <p>
              These details help Nutri
              personalize nutrition guidance.
            </p>

          </div>


          <div className="profile-grid">


            <ProfileInput
              label="Age"
              type="number"
              value={profile.age}
              disabled={!editing || saving}
              onChange={value =>
                updateField(
                  "age",
                  value
                )
              }
            />


            <ProfileSelect
              label="Gender"
              value={profile.gender}
              disabled={!editing || saving}
              onChange={value =>
                updateField(
                  "gender",
                  value
                )
              }
              options={[
                ["", "Not set"],
                ["MALE", "Male"],
                ["FEMALE", "Female"],
                ["OTHER", "Other"]
              ]}
            />


            <ProfileInput
              label="Height"
              type="number"
              unit="cm"
              value={profile.height}
              disabled={!editing || saving}
              onChange={value =>
                updateField(
                  "height",
                  value
                )
              }
            />


            <ProfileInput
              label="Weight"
              type="number"
              unit="kg"
              value={profile.weight}
              disabled={!editing || saving}
              onChange={value =>
                updateField(
                  "weight",
                  value
                )
              }
            />


            <ProfileSelect
              label="Diet Preference"
              value={profile.dietType}
              disabled={!editing || saving}
              onChange={value =>
                updateField(
                  "dietType",
                  value
                )
              }
              options={[
                ["", "Not set"],

                [
                  "VEGETARIAN",
                  "Vegetarian"
                ],

                [
                  "VEGAN",
                  "Vegan"
                ],

                [
                  "NON_VEGETARIAN",
                  "Non Vegetarian"
                ]
              ]}
            />


            <ProfileSelect
              label="Activity Level"
              value={
                profile.activityLevel
              }
              disabled={!editing || saving}
              onChange={value =>
                updateField(
                  "activityLevel",
                  value
                )
              }
              options={[
                ["", "Not set"],

                [
                  "SEDENTARY",
                  "Sedentary"
                ],

                [
                  "LIGHTLY_ACTIVE",
                  "Lightly Active"
                ],

                [
                  "MODERATELY_ACTIVE",
                  "Moderately Active"
                ],

                [
                  "VERY_ACTIVE",
                  "Very Active"
                ],

                [
                  "EXTRA_ACTIVE",
                  "Extra Active"
                ]
              ]}
            />


            <ProfileSelect
              label="Nutrition Goal"
              value={profile.goal}
              disabled={!editing || saving}
              onChange={value =>
                updateField(
                  "goal",
                  value
                )
              }
              options={[
                ["", "Not set"],

                [
                  "WEIGHT_LOSS",
                  "Weight Loss"
                ],

                [
                  "WEIGHT_GAIN",
                  "Weight Gain"
                ],

                [
                  "MAINTENANCE",
                  "Maintenance"
                ],

                [
                  "HEALTHY_EATING",
                  "Healthy Eating"
                ],

                [
                  "FITNESS",
                  "Fitness"
                ]
              ]}
            />


          </div>


          {editing && (

            <div className="profile-actions">


              <button
                className="profile-cancel"
                onClick={cancelEdit}
                disabled={saving}
              >
                Cancel
              </button>


              <button
                className="profile-save"
                onClick={saveProfile}
                disabled={saving}
              >

                {saving
                  ? "Saving..."
                  : "Save Changes"}

              </button>


            </div>

          )}


        </section>


        {/* TARGETS */}

        <section className="profile-card">


          <div className="profile-card-title">

            <h2>
              Daily Nutrition Targets
            </h2>

            <p>
              These estimates are calculated automatically from your profile.
              They are personalized targets, not verified food composition values.
            </p>

          </div>


          <div className="profile-targets">


            <TargetCard
              icon="🔥"
              title="Calories"
              value={
                targets.dailyCalorieTarget
                  ? `${targets.dailyCalorieTarget} kcal`
                  : "Not calculated"
              }
            />


            <TargetCard
              icon="💪"
              title="Protein"
              value={
                targets.dailyProteinTarget
                  ? `${targets.dailyProteinTarget} g`
                  : "Not calculated"
              }
            />


            <TargetCard
              icon="💧"
              title="Water"
              value={
                targets.dailyWaterTarget
                  ? `${targets.dailyWaterTarget} L`
                  : "Not calculated"
              }
            />


          </div>


        </section>


      </main>


    </div>
  );
}


// =========================================================
// INPUT
// =========================================================

function ProfileInput({
  label,
  value,
  onChange,
  disabled,
  type = "text",
  unit
}) {

  return (

    <label className="profile-field">

      <span>
        {label}
      </span>


      <div className="profile-input-wrap">

        <input
          type={type}
          value={value}
          disabled={disabled}
          onChange={event =>
            onChange(
              event.target.value
            )
          }
        />

        {unit && (
          <small>
            {unit}
          </small>
        )}

      </div>

    </label>
  );
}


// =========================================================
// SELECT
// =========================================================

function ProfileSelect({
  label,
  value,
  onChange,
  disabled,
  options
}) {

  return (

    <label className="profile-field">

      <span>
        {label}
      </span>


      <select
        value={value}
        disabled={disabled}
        onChange={event =>
          onChange(
            event.target.value
          )
        }
      >

        {options.map(
          ([optionValue, labelText]) => (

            <option
              key={optionValue}
              value={optionValue}
              disabled={optionValue === "" && value !== ""}
            >
              {labelText}
            </option>

          )
        )}

      </select>

    </label>
  );
}


// =========================================================
// TARGET
// =========================================================

function TargetCard({
  icon,
  title,
  value
}) {

  return (

    <div className="profile-target">

      <span>
        {icon}
      </span>


      <div>

        <small>
          {title}
        </small>

        <strong>
          {value}
        </strong>

      </div>

    </div>
  );
}


export default Profile;
