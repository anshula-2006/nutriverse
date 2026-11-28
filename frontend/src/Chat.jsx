import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import AppNav from "./AppNav.jsx";
import { API_URL, handleUnauthorized, readUser } from "./api.js";
import "./Chat.css";
const TEMP_ERROR = "Nutri is having trouble responding right now. Please try again in a moment.";
const DEFAULT_ETA_MS = {
  chat: 2200,
  recipe: 4500,
  recommendation: 7000
};
const ETA_ALPHA = 0.30;
const ETA_MIN_MS = 1000;
const ETA_MAX_MS = 30000;

function etaStorageKey(type) {
  return `nutriverse_eta_${type}`;
}

function getEstimatedTime(type) {
  try {
    const saved = Number(localStorage.getItem(etaStorageKey(type)));
    if (Number.isFinite(saved) && saved >= ETA_MIN_MS && saved <= ETA_MAX_MS) {
      return saved;
    }
  } catch {
    // Use the default when browser storage is unavailable.
  }
  return DEFAULT_ETA_MS[type] ?? DEFAULT_ETA_MS.chat;
}

function updateEstimatedTime(type, actualMs) {
  if (!Number.isFinite(actualMs) || actualMs <= 0) return;
  const oldEstimate = getEstimatedTime(type);
  const learned = oldEstimate * (1 - ETA_ALPHA) + actualMs * ETA_ALPHA;
  const safeEstimate = Math.min(ETA_MAX_MS, Math.max(ETA_MIN_MS, learned));
  try {
    localStorage.setItem(etaStorageKey(type), String(Math.round(safeEstimate)));
  } catch {
    // The ETA still works for this request even if storage is unavailable.
  }
}

function timingTypeFor(message, recommendation) {
  if (recommendation) return "recommendation";
  const value = message.toLowerCase();
  if (hasAny(value, ["recipe", "how do i make", "how to make", "ingredients", "cook"])) {
    return "recipe";
  }
  return "chat";
}
const SUGGESTIONS = [
  ["Nutrition in an apple", "Tell me about apple"],
  ["High-fiber foods", "Suggest high-fiber vegetarian foods"],
  ["Protein-rich breakfast", "Suggest a high-protein breakfast"],
  ["Today's calorie intake", "How many calories have I consumed today?"]
];
const PROFILE_UPDATE = /\b(?:i (?:don't|dont|do not) (?:eat|consume|like)|i (?:can't|cant|cannot) (?:eat|have)|i dislike|i hate|avoid|exclude)\b/i;
const REQUEST_WORDS = /\b(?:recommend|suggest|give me|what should i eat|what can i eat|another|more|alternative|instead)\b/i;
const FOLLOW_UP = /\b(?:other|another|more|different|else)\b/i;
const SUBSTITUTION_WORDS = ["don't have", "dont have", "do not have", "not available", "without ", "instead of ", "replace ", "replacement", "substitute", "swap"];
const hasAny = (text, words) => words.some(word => text.includes(word));
function isRecommendationPrompt(text, previousPlainReply = false) {
  const value = text.toLowerCase();
  const profileUpdate = PROFILE_UPDATE.test(value) && !REQUEST_WORDS.test(value);
  const dailyProgress = hasAny(value, ["protein", "calorie", "calories", "macro", "macros"])
    && hasAny(value, ["goal", "target", "remaining", "left", "consumed", "consume", "eaten", "ate", "logged", "hit my"]);
  if (profileUpdate || dailyProgress) return false;
  if (hasAny(value, ["recipe", "plan my meal", "plan my meals", "meal plan", "meal plans", "plan my diet"])) return false;
  // A generic singular-meal request needs a clarification, not evidence cards.
  if (/\b(?:give me|suggest|recommend|i want|i need)\b.*\bmeal\b/.test(value)
      && !/\b(?:breakfast|lunch|dinner|snacks?)\b/.test(value)) return false;
  if (FOLLOW_UP.test(value) && previousPlainReply) return false;
  const substitution = hasAny(value, SUBSTITUTION_WORDS);
  if (substitution && previousPlainReply) return false;
  if (substitution) return true;
  return hasAny(value, ["recommend", "suggest", "give me", "what should i eat", "what can i eat", "i need", "i want"])
    && hasAny(value, ["breakfast", "lunch", "dinner", "snack", "protein", "fiber", "fibre", "vegetarian", "vegan", "food", "meal", "post workout", "post-workout"]);
}
function recommendationItems(data) {
  return Array.isArray(data?.recommendations)
    ? data.recommendations.filter(item => item?.what?.trim())
    : [];
}
function historyItems(data) {
  if (!Array.isArray(data)) return [];
  return data.flatMap(item => {
    if (!["user", "assistant"].includes(item?.role)) return [];
    if (item.role === "assistant" && item.recommendation) {
      const items = recommendationItems(item.recommendation);
      return [{
        role: "assistant",
        ...(items.length
          ? { recommendation: { ...item.recommendation, recommendations: items } }
          : { content: "No additional verified options matched your constraints." })
      }];
    }
    return item.content?.trim() ? [{ role: item.role, content: item.content }] : [];
  });
}
function friendlyError(response) {
  if (response.status === 429) return "Nutri is receiving many requests. Please try again shortly.";
  if (response.status === 403) return "Nutri is not available for this account right now.";
  if (response.status === 503) return "Verified nutrition evidence is temporarily unavailable.";
  return TEMP_ERROR;
}
export default function Chat() {
  const navigate = useNavigate();
  const location = useLocation();
  const token = localStorage.getItem("token");
  const user = readUser();
  const [messages, setMessages] = useState([]);
  const [message, setMessage] = useState("");
  const [foodContext, setFoodContext] = useState(location.state?.food || null);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [isSending, setIsSending] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [statusText, setStatusText] = useState("Preparing response...");
  const [remainingSeconds, setRemainingSeconds] = useState(null);
  const endRef = useRef(null);
  const sendingRef = useRef(false);
  const countdownTimer = useRef(null);
  useEffect(() => {
    if (!token) navigate("/login", { replace: true });
  }, [token, navigate]);
  useEffect(() => {
    if (!token) {
      setHistoryLoading(false);
      return;
    }
    const controller = new AbortController();
    fetch(`${API_URL}/api/chat`, {
      headers: { Authorization: `Bearer ${token}` },
      signal: controller.signal
    })
      .then(response => {
        if (handleUnauthorized(response, navigate) || !response.ok) return [];
        return response.json();
      })
      .then(data => !controller.signal.aborted && setMessages(historyItems(data)))
      .catch(error => error.name !== "AbortError" && setMessages([]))
      .finally(() => !controller.signal.aborted && setHistoryLoading(false));
    return () => controller.abort();
  }, [token, navigate]);
  useEffect(() => {
    endRef.current?.scrollIntoView({ block: "end" });
  }, [messages, isSending]);
  useEffect(() => () => {
    clearInterval(countdownTimer.current);
  }, []);
  function startTimer(requestType, startedAt) {
    clearInterval(countdownTimer.current);

    const estimateMs = getEstimatedTime(requestType);
    setRemainingSeconds(Math.max(1, Math.ceil(estimateMs / 1000)));
    setStatusText(
      requestType === "recommendation"
        ? "Checking nutrition evidence..."
        : requestType === "recipe"
          ? "Preparing recipe..."
          : "Preparing response..."
    );

    countdownTimer.current = setInterval(() => {
      const elapsedMs = performance.now() - startedAt;
      const remainingMs = estimateMs - elapsedMs;

      if (remainingMs > 0) {
        setRemainingSeconds(Math.max(1, Math.ceil(remainingMs / 1000)));

        if (requestType === "recommendation" && elapsedMs > estimateMs * 0.45) {
          setStatusText("Ranking verified options...");
        } else if (requestType === "recipe" && elapsedMs > estimateMs * 0.50) {
          setStatusText("Finishing recipe...");
        } else if (requestType === "chat" && elapsedMs > estimateMs * 0.50) {
          setStatusText("Preparing your answer...");
        }
      } else {
        setRemainingSeconds(null);
        setStatusText("Taking a little longer than usual...");
      }
    }, 250);
  }

  function stopTimer() {
    clearInterval(countdownTimer.current);
    countdownTimer.current = null;
    setRemainingSeconds(null);
  }
  async function sendMessage(text = message) {
    const userMessage = text.trim();
    if (!userMessage || sendingRef.current || historyLoading) return;
    if (!token) return navigate("/login");
    const lastAssistant = [...messages].reverse().find(item => item.role === "assistant");
    const previousPlainReply = Boolean(lastAssistant && !lastAssistant.recommendation);
    const recommendation = !foodContext && isRecommendationPrompt(userMessage, previousPlainReply);
    const requestType = timingTypeFor(userMessage, recommendation);
    const startedAt = performance.now();

    sendingRef.current = true;
    setMessages(current => [...current, { role: "user", content: userMessage }]);
    setMessage("");
    setIsSending(true);
    startTimer(requestType, startedAt);

    try {
      const endpoint = recommendation ? "/api/recommendations" : "/api/chat";
      const body = recommendation
        ? { request: userMessage }
        : { message: userMessage, ...(foodContext && { source: foodContext.source, sourceId: foodContext.sourceId }) };
      const response = await fetch(`${API_URL}${endpoint}`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
        body: JSON.stringify(body)
      });
      if (handleUnauthorized(response, navigate)) return;
      if (!response.ok) {
        setMessages(current => [...current, { role: "assistant", content: friendlyError(response) }]);
        return;
      }
      const data = await response.json();
      updateEstimatedTime(requestType, performance.now() - startedAt);
      const structured = data?.recommendation ?? data;
      if (recommendation || data?.recommendation || Array.isArray(data?.recommendations)) {
        const items = recommendationItems(structured);
        setMessages(current => [...current, {
          role: "assistant",
          ...(items.length
            ? { recommendation: { ...structured, recommendations: items } }
            : { content: "I could not find a verified recommendation for that request." })
        }]);
      } else {
        setMessages(current => [...current, {
          role: "assistant",
          content: data?.reply || "I could not generate a response."
        }]);
      }
    } catch {
      setMessages(current => [...current, { role: "assistant", content: TEMP_ERROR }]);
    } finally {
      stopTimer();
      sendingRef.current = false;
      setIsSending(false);
    }
  }
  async function deleteChat() {
    if (isDeleting || isSending || historyLoading || !messages.length) return;
    if (!confirm("Delete your entire chat history? This cannot be undone.")) return;
    setIsDeleting(true);
    try {
      const response = await fetch(`${API_URL}/api/chat`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${token}` }
      });
      if (handleUnauthorized(response, navigate)) return;
      if (!response.ok) return alert("Could not delete your chat history.");
      setMessages([]);
      setFoodContext(null);
      setMessage("");
    } catch {
      alert("Could not connect to the backend.");
    } finally {
      setIsDeleting(false);
    }
  }
  function handleKey(event) {
    if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      sendMessage();
    }
  }
  return (
    <div className="chat-page">
      <AppNav />
      <main className="chat-main">
        <header className="chat-header">
          <div>
            <p>NutriVerse assistant</p>
            <h1>Nutrition conversation</h1>
          </div>
          <button onClick={deleteChat}
            disabled={isDeleting || isSending || historyLoading || !messages.length}>
            {isDeleting ? "Deleting..." : "Delete chat"}
          </button>
        </header>
        {foodContext && (
          <div className="chat-food-context">
            <div>
              <strong>{foodContext.foodName}</strong>
              <span>Using the selected source record for this food.</span>
            </div>
            <button disabled={isSending}
              onClick={() => sendMessage("How much protein does this food contain?")}>
              Ask about protein
            </button>
            <button disabled={isSending} onClick={() => setFoodContext(null)}>Clear</button>
          </div>
        )}
        <section className="chat-messages">
          {historyLoading && <p className="chat-loading">Loading conversation...</p>}
          {!historyLoading && !messages.length && (
            <section className="chat-intro">
              <p>Start a conversation</p>
              <h2>How can Nutri help you?</h2>
              <div className="chat-suggestions">
                {SUGGESTIONS.map(([title, prompt]) => (
                  <button key={title} disabled={isSending} onClick={() => sendMessage(prompt)}>
                    {title}
                  </button>
                ))}
              </div>
              <small>Signed in as {user?.name || "User"}.</small>
            </section>
          )}
          {messages.map((item, index) => (
            <article key={index} className={`chat-message ${item.role}`}>
              <header>{item.role === "assistant" ? "Nutri" : "You"}</header>
              <div className="chat-bubble">
                {item.role === "assistant"
                  ? item.recommendation
                    ? <RecommendationCards data={item.recommendation} />
                    : <>
    <FormatText text={item.content} />
    <GraphEvidence text={item.content} />
  </>
                  : item.content}
              </div>
            </article>
          ))}
          {isSending && (
            <article className="chat-message assistant">
              <header>Nutri</header>
              <div className="chat-bubble chat-status">
                {statusText}
                {remainingSeconds != null && (
                  <span className="chat-countdown">
                    About {remainingSeconds}s remaining
                  </span>
                )}
              </div>
            </article>
          )}
          <div ref={endRef} />
        </section>
        <div className="chat-input-wrap">
          <textarea rows="2" placeholder="Ask Nutri about food, meals or nutrition"
            value={message} disabled={isSending || historyLoading}
            onChange={event => setMessage(event.target.value)} onKeyDown={handleKey} />
          <button disabled={!message.trim() || isSending || historyLoading}
            onClick={() => sendMessage()}>
            Send
          </button>
        </div>
      </main>
    </div>
  );
}
function RecommendationCards({ data }) {
  return <div className="recommendation-results">
    <div className="recommendation-heading">
      <div><strong>Evidence-aware recommendations</strong>
        <p>Personalized using your request and saved profile.</p></div>
      <span className="recommendation-method">Structured evidence</span>
    </div>
    {recommendationItems(data).map((item, index) => <div className="recommendation-card"
      key={`${item.evidence?.sourceId || index}-${index}`}>
      <div className="recommendation-title">
        <span className="recommendation-number">{index + 1}</span>
        <div><small>Recommendation</small><h3>{item.what}</h3></div>
      </div>
      {!!item.why?.length && <div className="recommendation-section">
        <strong>Why?</strong>
        <ol className="recommendation-reasons">
          {item.why.map((reason, i) => <li key={i}>{reason}</li>)}
        </ol>
      </div>}
      <div className="recommendation-section">
        <strong>Evidence</strong>
        <div className="recommendation-evidence">
          <Evidence label="Protein" value={item.evidence?.protein} unit="g" />
          <Evidence label="Calories" value={item.evidence?.calories} unit="kcal" />
          <Evidence label="Fiber" value={item.evidence?.fiber} unit="g" />
          <Evidence label="Reference" value={item.evidence?.servingSize}
            unit={item.evidence?.servingUnit} />
        </div>
      </div>
      {item.reason && <div className="recommendation-section">
        <strong>Reason</strong><p>{item.reason}</p>
      </div>}
      <div className="recommendation-source">
        <span>{item.evidence?.verified ? "Verified source" : "Source verification unavailable"}</span>
        {item.evidence?.source && <span>{item.evidence.source}</span>}
        {item.evidence?.sourceId && <span>ID {item.evidence.sourceId}</span>}
        {item.evidence?.dataType && <span>{item.evidence.dataType}</span>}
      </div>
    </div>)}
  </div>;
}
function Evidence({ label, value, unit }) {
  const n = Number(value), valid = value != null && value !== "" && Number.isFinite(n);
  return <div className="recommendation-evidence-value">
    <small>{label}</small><strong>{valid ? Math.round(n * 100) / 100 : "N/A"} {valid ? unit : ""}</strong>
  </div>;
}
function FormatText({ text = "" }) {
  return <div>{text.split("\n").map((line, index) => {
    const value = line.trim();
    if (!value) return <div className="chat-space" key={index} />;
    if (/^\|?[-:\s|]+\|?$/.test(value)) return null;
    if (value.startsWith("|") && value.endsWith("|")) return <div className="chat-table-row" key={index}>
      {value.split("|").filter(Boolean).map((cell, i) => <span key={i}><Bold text={cell.trim()} /></span>)}
    </div>;
    if (/^[-•]\s/.test(value)) return <div className="chat-list-line" key={index}>
      <span>•</span><Bold text={value.slice(2)} />
    </div>;
    const step = value.match(/^(\d+)\.\s+(.*)/);
    if (step) return <div className="chat-step" key={index}>
      <span>{step[1]}.</span><Bold text={step[2]} />
    </div>;
    return <div className="chat-line" key={index}><Bold text={value} /></div>;
  })}</div>;
}
function Bold({ text = "" }) {
  return text.split(/(\*\*.*?\*\*)/g).map((part, index) =>
    part.startsWith("**") && part.endsWith("**")
      ? <strong key={index}>{part.slice(2, -2)}</strong> : part);
}

function GraphEvidence({ text = "", evidence }) {
  const navigate = useNavigate();

  const [graph, setGraph] = useState(null);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState(null);

  const source = evidence?.source ||
    text.match(/Source:\s*([^|\r\n]+)/i)?.[1]?.trim();

  const sourceId = evidence?.sourceId ||
    text.match(/Source ID:\s*([A-Z]\d{3})\b/i)?.[1];

  if (source !== "ICMR-NIN IFCT 2017" || !sourceId) {
    return null;
  }

  async function toggleGraph() {
    if (open) {
      setOpen(false);
      return;
    }

    setOpen(true);

    if (graph) return;

    setLoading(true);
    setError("");

    try {
      const params = new URLSearchParams({
        source,
        sourceId
      });

      const response = await fetch(
        `${API_URL}/api/kg/evidence?${params}`,
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("token")}`
          }
        }
      );

      if (handleUnauthorized(response, navigate)) return;

      if (!response.ok) {
        throw new Error("Graph evidence is unavailable.");
      }

      const data = await response.json();
      setGraph(data);

    } catch (err) {
      setError(err.message);

    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="kg-panel">
      <button
        type="button"
        className="kg-toggle"
        onClick={toggleGraph}
        disabled={loading}
        aria-expanded={open}
      >
        {open ? "Hide Knowledge Graph" : "View Knowledge Graph"}
      </button>

      {open && (
        <div className="kg-content">
          {loading && <p>Retrieving verified graph evidence...</p>}

          {error && <p className="kg-error">{error}</p>}

          {graph && (
            <>
              <h3>{graph.foodName}</h3>

              <p className="kg-verified">
                {graph.verified
                  ? "Verified source record"
                  : "Source verification unavailable"}
              </p>

              <svg
                viewBox="0 0 600 480"
                className="kg-svg"
                aria-label={`Knowledge graph for ${graph.foodName}`}
              >
                {graph.nutrients.map((nutrient, index) => {
                  const angle =
                    (index / graph.nutrients.length) *
                    2 * Math.PI - Math.PI / 2;

                  const x = 300 + 210 * Math.cos(angle);
                  const y = 200 + 145 * Math.sin(angle);

                  return (
                    <g key={nutrient.name}>
                      <line
                        x1="300"
                        y1="200"
                        x2={x}
                        y2={y}
                        className="kg-edge"
                      />

                      <g
                        className="kg-nutrient"
                        role="button"
                        tabIndex={0}
                        aria-label={`${nutrient.name}: ${nutrient.amount} ${nutrient.unit}`}
                        onClick={() => setSelected(nutrient)}
                        onKeyDown={event => {
                          if (event.key === "Enter" || event.key === " ") {
                            event.preventDefault();
                            setSelected(nutrient);
                          }
                        }}
                      >
                        <circle
                          cx={x}
                          cy={y}
                          r="43"
                          className="kg-nutrient-node"
                        />

                        <text x={x} y={y - 5}>
                          {nutrient.name}
                        </text>

                        <text x={x} y={y + 13}>
                          {nutrient.amount} {nutrient.unit}
                        </text>
                      </g>
                    </g>
                  );
                })}

                <line
                  x1="300"
                  y1="200"
                  x2="300"
                  y2="420"
                  className="kg-edge"
                />

                <circle
                  cx="300"
                  cy="200"
                  r="57"
                  className="kg-food-node"
                />

                <text x="300" y="196">Food</text>
                <text x="300" y="215">{graph.sourceId}</text>

                <rect
                  x="190"
                  y="395"
                  width="220"
                  height="50"
                  rx="12"
                  className="kg-source-node"
                />

                <text x="300" y="424">{graph.source}</text>
              </svg>

              <p className="kg-hint">
                Select a nutrient to inspect its evidence.
              </p>

              {selected && (
                <div className="kg-detail">
                  <strong>{selected.name}</strong>
                  <p>
                    {selected.amount} {selected.unit} per{" "}
                    {graph.servingSize} {graph.servingUnit}
                  </p>
                  <small>Relationship: CONTAINS</small>
                </div>
              )}

              <div className="kg-values">
                {graph.nutrients.map(nutrient => (
                  <div key={nutrient.name}>
                    <span>{nutrient.name}</span>
                    <strong>
                      {nutrient.amount} {nutrient.unit}
                    </strong>
                  </div>
                ))}
              </div>

              <p className="kg-source">
                Source: {graph.source} | ID: {graph.sourceId}
              </p>

              <small>
                Values are based on {graph.servingSize}{" "}
                {graph.servingUnit} of food. Source verification
                does not establish clinical effectiveness.
              </small>
            </>
          )}
        </div>
      )}
    </div>
  );
}