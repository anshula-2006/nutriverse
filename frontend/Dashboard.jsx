import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { getDashboard } from "./services/dashboardApi";

export default function Dashboard() {
  const navigate = useNavigate();

  const [dashboard, setDashboard] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const storedUser = JSON.parse(
    localStorage.getItem("user") || "{}"
  );

  useEffect(() => {
    if (!storedUser?.id) {
      navigate("/login");
      return;
    }

    loadDashboard();
  }, []);

  async function loadDashboard() {
    try {
      setLoading(true);

      const data = await getDashboard(storedUser.id);

      setDashboard(data);
      setError("");
    } catch (err) {
      console.error(err);
      setError("Could not load dashboard.");
    } finally {
      setLoading(false);
    }
  }

  function percentage(current, target) {
    if (!target || target <= 0) return 0;

    return Math.min(
      100,
      Math.round((current / target) * 100)
    );
  }

  if (loading) {
    return (
      <div style={styles.center}>
        Loading NutriVerse dashboard...
      </div>
    );
  }

  if (error) {
    return (
      <div style={styles.center}>
        {error}
      </div>
    );
  }

  if (!dashboard) {
    return null;
  }

  const caloriePercent = percentage(
    dashboard.caloriesConsumed,
    dashboard.calorieTarget
  );

  const proteinPercent = percentage(
    dashboard.proteinConsumed,
    dashboard.proteinTarget
  );

  const waterPercent = percentage(
    dashboard.waterConsumed,
    dashboard.waterTarget
  );

  const weeklyEntries = Object.entries(
    dashboard.weeklyCalories || {}
  );

  const maxWeeklyCalories = Math.max(
    ...weeklyEntries.map(([, value]) => value),
    dashboard.calorieTarget || 1
  );

  return (
    <div style={styles.page}>

      {/* SIDEBAR */}
      <aside style={styles.sidebar}>

        <div style={styles.logo}>
          <div style={styles.logoIcon}>🌿</div>

          <div>
            <div style={styles.logoTitle}>
              NutriVerse
            </div>

            <div style={styles.logoSubtitle}>
              AI Nutrition Assistant
            </div>
          </div>
        </div>

        <SidebarItem
          icon="🏠"
          label="Dashboard"
          active
        />

        <SidebarItem
          icon="🍽️"
          label="Meal Plans"
        />

        <SidebarItem
          icon="🥗"
          label="Meals"
        />

        <SidebarItem
          icon="📷"
          label="Food Scanner"
        />

        <SidebarItem
          icon="🤖"
          label="AI Assistant"
          onClick={() => navigate("/chat")}
        />

        <SidebarItem
          icon="📈"
          label="Progress"
        />

        <SidebarItem
          icon="💧"
          label="Water Tracker"
        />

        <SidebarItem
          icon="🏋️"
          label="Workout"
        />

        <SidebarItem
          icon="🛒"
          label="Grocery List"
        />

        <SidebarItem
          icon="📊"
          label="Reports"
        />

        <SidebarItem
          icon="⚙️"
          label="Settings"
        />

        <div style={styles.sidebarBottom}>
          <div style={styles.userCard}>
            <div style={styles.avatar}>
              {dashboard.name
                ?.charAt(0)
                ?.toUpperCase()}
            </div>

            <div>
              <div style={styles.userName}>
                {dashboard.name}
              </div>

              <div style={styles.userGoal}>
                {formatText(dashboard.goal)}
              </div>
            </div>
          </div>

          <div style={styles.helpCard}>
            <strong>Need Help?</strong>

            <p style={styles.helpText}>
              Chat with your AI nutrition assistant
            </p>

            <button
              style={styles.chatButton}
              onClick={() => navigate("/chat")}
            >
              Chat Now
            </button>
          </div>
        </div>
      </aside>


      {/* MAIN */}
      <main style={styles.main}>

        {/* TOP */}
        <div style={styles.topBar}>

          <div>
            <h1 style={styles.heading}>
              Good to see you,{" "}
              <span style={styles.green}>
                {dashboard.name}
              </span>
              ! 👋
            </h1>

            <p style={styles.subtitle}>
              Here's your health summary for today
            </p>
          </div>

          <div style={styles.profileTags}>
            <span style={styles.tag}>
              🎯 {formatText(dashboard.goal)}
            </span>

            <span style={styles.tag}>
              🥗 {formatText(dashboard.dietType)}
            </span>
          </div>

        </div>


        {/* FUTURE METRICS */}
        <div style={styles.futureMetrics}>

          <FutureMetric
            icon="🔥"
            label="Streak"
          />

          <FutureMetric
            icon="🏆"
            label="Nutrition Score"
          />

          <FutureMetric
            icon="🎯"
            label="Goals Met"
          />

        </div>


        {/* MAIN CARDS */}
        <div style={styles.cardGrid}>

          <MetricCard
            icon="🔥"
            title="Calories Consumed"
            current={dashboard.caloriesConsumed}
            target={dashboard.calorieTarget}
            unit="kcal"
            percent={caloriePercent}
          />

          <MetricCard
            icon="💪"
            title="Protein Intake"
            current={dashboard.proteinConsumed}
            target={dashboard.proteinTarget}
            unit="g"
            percent={proteinPercent}
          />

          <MetricCard
            icon="💧"
            title="Water Hydration"
            current={dashboard.waterConsumed}
            target={dashboard.waterTarget}
            unit="L"
            percent={waterPercent}
          />

          <div style={styles.card}>
            <div style={styles.metricHeader}>
              <div style={styles.metricIcon}>
                ⚖️
              </div>

              <span>
                Body Mass Index (BMI)
              </span>
            </div>

            <div style={styles.bmiValue}>
              {dashboard.bmi || "--"}
            </div>

            <div style={styles.bmiBadge}>
              {dashboard.bmiCategory}
            </div>

            <div style={styles.bmiInfo}>
              Healthy Range: 18.5 - 24.9
            </div>
          </div>

        </div>


        {/* CHART + MACROS */}
        <div style={styles.middleGrid}>

          {/* WEEKLY CHART */}
          <div style={styles.largeCard}>

            <div style={styles.cardTitle}>
              Calorie Intake — Last 7 Days
            </div>

            <div style={styles.chart}>

              {weeklyEntries.map(
                ([day, calories]) => {

                  const height =
                    maxWeeklyCalories > 0
                      ? Math.max(
                          5,
                          (calories /
                            maxWeeklyCalories) *
                            150
                        )
                      : 5;

                  return (
                    <div
                      style={styles.barGroup}
                      key={day}
                    >
                      <div style={styles.barValue}>
                        {calories}
                      </div>

                      <div
                        style={{
                          ...styles.bar,
                          height: `${height}px`
                        }}
                      />

                      <div style={styles.barDay}>
                        {day}
                      </div>
                    </div>
                  );
                }
              )}

            </div>
          </div>


          {/* MACROS */}
          <div style={styles.largeCard}>

            <div style={styles.cardTitle}>
              Macronutrient Distribution
            </div>

            <div style={styles.macroTotal}>
              {dashboard.caloriesConsumed}
              <span style={styles.smallText}>
                {" "}kcal today
              </span>
            </div>

            <MacroRow
              label="Carbohydrates"
              value={dashboard.carbsConsumed}
              unit="g"
              icon="🟢"
            />

            <MacroRow
              label="Protein"
              value={dashboard.proteinConsumed}
              unit="g"
              icon="🔵"
            />

            <MacroRow
              label="Fats"
              value={dashboard.fatConsumed}
              unit="g"
              icon="🟠"
            />

          </div>
        </div>


        {/* BOTTOM */}
        <div style={styles.bottomGrid}>

          {/* TODAY MEALS */}
          <div style={styles.largeCard}>

            <div style={styles.cardTitle}>
              Today's Meals
            </div>

            {dashboard.todayMeals?.length === 0 ? (
              <div style={styles.empty}>
                No meals logged today.
              </div>
            ) : (
              dashboard.todayMeals.map((meal) => (
                <div
                  key={meal.id}
                  style={styles.mealRow}
                >
                  <div>

                    <strong>
                      {formatText(meal.mealType)}
                    </strong>

                    <div style={styles.mealName}>
                      {meal.foodName}
                    </div>

                  </div>

                  <div style={styles.mealMacros}>
                    <strong>
                      {meal.calories} kcal
                    </strong>

                    <span>
                      P {meal.protein}g
                    </span>

                    <span>
                      C {meal.carbs}g
                    </span>

                    <span>
                      F {meal.fat}g
                    </span>
                  </div>

                </div>
              ))
            )}
          </div>


          {/* QUICK ACTIONS */}
          <div style={styles.largeCard}>

            <div style={styles.cardTitle}>
              Quick Actions
            </div>

            <div style={styles.quickGrid}>

              <QuickAction
                icon="📷"
                label="Scan Meal"
              />

              <QuickAction
                icon="➕"
                label="Add Food"
              />

              <QuickAction
                icon="💧"
                label="Log Water"
              />

              <QuickAction
                icon="🤖"
                label="AI Assistant"
                onClick={() =>
                  navigate("/chat")
                }
              />

              <QuickAction
                icon="📅"
                label="Meal Planner"
              />

              <QuickAction
                icon="📈"
                label="View Progress"
              />

            </div>
          </div>


          {/* RECENT ACTIVITY */}
          <div style={styles.largeCard}>

            <div style={styles.cardTitle}>
              Recent Activity
            </div>

            <div style={styles.empty}>
              Recent activity tracking
              will be added next.
            </div>

          </div>

        </div>

      </main>
    </div>
  );
}


/* =========================================================
   SMALL COMPONENTS
========================================================= */

function SidebarItem({
  icon,
  label,
  active,
  onClick
}) {
  return (
    <div
      onClick={onClick}
      style={{
        ...styles.sidebarItem,
        ...(active
          ? styles.sidebarActive
          : {})
      }}
    >
      <span>{icon}</span>
      <span>{label}</span>
    </div>
  );
}


function MetricCard({
  icon,
  title,
  current,
  target,
  unit,
  percent
}) {
  return (
    <div style={styles.card}>

      <div style={styles.metricHeader}>
        <div style={styles.metricIcon}>
          {icon}
        </div>

        <span>{title}</span>
      </div>

      <div style={styles.metricValue}>
        {current}
        <span style={styles.target}>
          {" "}/ {target ?? "--"} {unit}
        </span>
      </div>

      <div style={styles.progressTrack}>
        <div
          style={{
            ...styles.progressBar,
            width: `${percent}%`
          }}
        />
      </div>

      <div style={styles.percentText}>
        {percent}% of daily goal
      </div>

    </div>
  );
}


function MacroRow({
  label,
  value,
  unit,
  icon
}) {
  return (
    <div style={styles.macroRow}>
      <span>
        {icon} {label}
      </span>

      <strong>
        {value} {unit}
      </strong>
    </div>
  );
}


function QuickAction({
  icon,
  label,
  onClick
}) {
  return (
    <button
      onClick={onClick}
      style={styles.quickAction}
    >
      <div style={styles.quickIcon}>
        {icon}
      </div>

      {label}
    </button>
  );
}


function FutureMetric({
  icon,
  label
}) {
  return (
    <div style={styles.futureMetric}>
      <span style={styles.futureIcon}>
        {icon}
      </span>

      <div>
        <strong>{label}</strong>

        <div style={styles.futureText}>
          Coming next
        </div>
      </div>
    </div>
  );
}


function formatText(value) {
  if (!value) {
    return "Not set";
  }

  return value
    .replaceAll("_", " ")
    .toLowerCase()
    .replace(/\b\w/g, (c) =>
      c.toUpperCase()
    );
}


/* =========================================================
   STYLES
========================================================= */

const styles = {

  page: {
    minHeight: "100vh",
    background: "#f5f8f5",
    display: "flex",
    fontFamily:
      "Inter, Arial, sans-serif",
    color: "#18201c"
  },

  center: {
    minHeight: "100vh",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    fontFamily:
      "Inter, Arial, sans-serif",
    background: "#f5f8f5"
  },

  sidebar: {
    width: "230px",
    minHeight: "100vh",
    padding: "24px 16px",
    boxSizing: "border-box",
    background:
      "linear-gradient(180deg, #054b33 0%, #003827 100%)",
    color: "white",
    position: "fixed",
    left: 0,
    top: 0,
    overflowY: "auto"
  },

  logo: {
    display: "flex",
    alignItems: "center",
    gap: "10px",
    marginBottom: "30px"
  },

  logoIcon: {
    fontSize: "35px"
  },

  logoTitle: {
    fontSize: "24px",
    fontWeight: "700"
  },

  logoSubtitle: {
    fontSize: "11px",
    color: "#83d99f"
  },

  sidebarItem: {
    display: "flex",
    alignItems: "center",
    gap: "13px",
    padding: "13px 15px",
    borderRadius: "9px",
    marginBottom: "5px",
    cursor: "pointer",
    fontSize: "14px"
  },

  sidebarActive: {
    background:
      "linear-gradient(90deg, #138b45, #169f4d)"
  },

  sidebarBottom: {
    marginTop: "45px"
  },

  userCard: {
    background:
      "rgba(255,255,255,0.08)",
    borderRadius: "12px",
    padding: "15px",
    display: "flex",
    gap: "10px",
    alignItems: "center",
    marginBottom: "15px"
  },

  avatar: {
    width: "42px",
    height: "42px",
    borderRadius: "50%",
    background: "#43b96c",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    fontWeight: "700",
    fontSize: "18px"
  },

  userName: {
    fontWeight: "700"
  },

  userGoal: {
    fontSize: "11px",
    color: "#a8e0ba",
    marginTop: "4px"
  },

  helpCard: {
    background:
      "rgba(255,255,255,0.07)",
    padding: "15px",
    borderRadius: "12px"
  },

  helpText: {
    fontSize: "12px",
    color: "#d3e9db",
    lineHeight: "1.5"
  },

  chatButton: {
    width: "100%",
    background: "#43c05e",
    color: "white",
    border: "none",
    padding: "10px",
    borderRadius: "8px",
    cursor: "pointer",
    fontWeight: "600"
  },

  main: {
    marginLeft: "230px",
    width: "calc(100% - 230px)",
    padding: "30px",
    boxSizing: "border-box"
  },

  topBar: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: "22px",
    gap: "20px"
  },

  heading: {
    margin: 0,
    fontSize: "27px"
  },

  green: {
    color: "#168d42"
  },

  subtitle: {
    color: "#69736d",
    marginTop: "6px",
    marginBottom: 0
  },

  profileTags: {
    display: "flex",
    gap: "10px",
    flexWrap: "wrap"
  },

  tag: {
    background: "white",
    border: "1px solid #e0e7e1",
    padding: "9px 13px",
    borderRadius: "10px",
    fontSize: "12px"
  },

  futureMetrics: {
    display: "flex",
    justifyContent: "flex-end",
    gap: "10px",
    marginBottom: "18px"
  },

  futureMetric: {
    background: "white",
    border: "1px solid #e1e8e2",
    borderRadius: "12px",
    padding: "10px 16px",
    display: "flex",
    gap: "8px",
    alignItems: "center",
    minWidth: "145px"
  },

  futureIcon: {
    fontSize: "22px"
  },

  futureText: {
    fontSize: "11px",
    color: "#89938c",
    marginTop: "3px"
  },

  cardGrid: {
    display: "grid",
    gridTemplateColumns:
      "repeat(4, minmax(180px, 1fr))",
    gap: "16px"
  },

  card: {
    background: "white",
    border: "1px solid #e3e9e4",
    borderRadius: "14px",
    padding: "20px",
    boxShadow:
      "0 4px 14px rgba(0,0,0,0.04)"
  },

  metricHeader: {
    display: "flex",
    alignItems: "center",
    gap: "10px",
    fontSize: "13px"
  },

  metricIcon: {
    width: "38px",
    height: "38px",
    borderRadius: "10px",
    background: "#ecf8ef",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    fontSize: "20px"
  },

  metricValue: {
    marginTop: "14px",
    fontSize: "24px",
    fontWeight: "700"
  },

  target: {
    fontSize: "12px",
    fontWeight: "500",
    color: "#68716c"
  },

  progressTrack: {
    width: "100%",
    height: "6px",
    background: "#e5ece7",
    borderRadius: "20px",
    marginTop: "18px",
    overflow: "hidden"
  },

  progressBar: {
    height: "100%",
    background: "#1cad55",
    borderRadius: "20px"
  },

  percentText: {
    marginTop: "8px",
    fontSize: "11px",
    color: "#68716c"
  },

  bmiValue: {
    fontSize: "28px",
    fontWeight: "700",
    marginTop: "14px"
  },

  bmiBadge: {
    display: "inline-block",
    marginTop: "7px",
    padding: "5px 10px",
    borderRadius: "7px",
    background: "#e7f7eb",
    color: "#16893d",
    fontSize: "12px",
    fontWeight: "600"
  },

  bmiInfo: {
    borderTop: "1px solid #edf0ed",
    paddingTop: "12px",
    marginTop: "18px",
    fontSize: "11px",
    color: "#78817b"
  },

  middleGrid: {
    display: "grid",
    gridTemplateColumns: "2fr 1fr",
    gap: "16px",
    marginTop: "16px"
  },

  bottomGrid: {
    display: "grid",
    gridTemplateColumns:
      "1.4fr 1fr 0.8fr",
    gap: "16px",
    marginTop: "16px"
  },

  largeCard: {
    background: "white",
    border: "1px solid #e3e9e4",
    borderRadius: "14px",
    padding: "20px",
    boxShadow:
      "0 4px 14px rgba(0,0,0,0.04)"
  },

  cardTitle: {
    fontWeight: "700",
    fontSize: "15px",
    marginBottom: "20px"
  },

  chart: {
    height: "200px",
    display: "flex",
    alignItems: "flex-end",
    justifyContent: "space-around",
    borderBottom: "1px solid #e8ece9",
    paddingBottom: "8px"
  },

  barGroup: {
    width: "11%",
    textAlign: "center"
  },

  bar: {
    width: "36px",
    maxWidth: "100%",
    background:
      "linear-gradient(180deg, #3eb95b, #85d98c)",
    margin: "4px auto 8px",
    borderRadius: "5px 5px 0 0"
  },

  barValue: {
    fontSize: "10px",
    fontWeight: "600"
  },

  barDay: {
    fontSize: "11px",
    color: "#68726c"
  },

  macroTotal: {
    fontSize: "27px",
    fontWeight: "700",
    marginBottom: "20px"
  },

  smallText: {
    fontSize: "12px",
    fontWeight: "400",
    color: "#747d77"
  },

  macroRow: {
    display: "flex",
    justifyContent: "space-between",
    padding: "11px 0",
    borderBottom: "1px solid #eef1ef",
    fontSize: "13px"
  },

  mealRow: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    borderBottom: "1px solid #edf0ee",
    padding: "12px 0",
    gap: "10px"
  },

  mealName: {
    fontSize: "12px",
    color: "#78817b",
    marginTop: "4px"
  },

  mealMacros: {
    display: "flex",
    gap: "12px",
    alignItems: "center",
    fontSize: "11px"
  },

  quickGrid: {
    display: "grid",
    gridTemplateColumns: "repeat(3, 1fr)",
    gap: "10px"
  },

  quickAction: {
    border: "1px solid #e1e7e2",
    background: "#fbfdfb",
    padding: "14px 8px",
    borderRadius: "12px",
    cursor: "pointer",
    fontSize: "11px"
  },

  quickIcon: {
    fontSize: "22px",
    marginBottom: "7px"
  },

  empty: {
    color: "#89928c",
    fontSize: "13px",
    padding: "20px 0"
  }
};