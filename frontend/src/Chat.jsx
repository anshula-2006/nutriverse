import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import AppNav from "./AppNav.jsx";
import { API_URL, handleUnauthorized, readUser } from "./api.js";
import "./Chat.css";
const TEMP_ERROR = "Nutri is having trouble responding right now. Please try again in a moment.";
const SUGGESTIONS = [
  ["Plan my meals", "Help me plan my meals for today"],
  ["Suggest recipes", "Suggest 3 recipes for my next meal"],
  ["Protein breakfast", "Suggest a high-protein breakfast"],
  ["Fiber meal", "Suggest a high-fiber meal"]
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
  if (hasAny(value, ["recipe", "plan my meal", "plan my meals"])) return false;
  if (FOLLOW_UP.test(value) && previousPlainReply) return false;
  const substitution = hasAny(value, SUBSTITUTION_WORDS);
  if (substitution && previousPlainReply && !REQUEST_WORDS.test(value)) return false;
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
  const [elapsed, setElapsed] = useState(0);
  const endRef = useRef(null);
  const sendingRef = useRef(false);
  const thinkingTimer = useRef(null);
  const elapsedTimer = useRef(null);
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
    clearTimeout(thinkingTimer.current);
    clearInterval(elapsedTimer.current);
  }, []);
  function startTimer() {
    setElapsed(0);
    setStatusText("Preparing response...");
    thinkingTimer.current = setTimeout(() => setStatusText("Checking context and evidence..."), 5000);
    elapsedTimer.current = setInterval(() => setElapsed(value => value + 1), 1000);
  }
  function stopTimer() {
    clearTimeout(thinkingTimer.current);
    clearInterval(elapsedTimer.current);
    thinkingTimer.current = null;
    elapsedTimer.current = null;
    setElapsed(0);
  }
  async function sendMessage(text = message) {
    const userMessage = text.trim();
    if (!userMessage || sendingRef.current || historyLoading) return;
    if (!token) return navigate("/login");
    const lastAssistant = [...messages].reverse().find(item => item.role === "assistant");
    const previousPlainReply = Boolean(lastAssistant && !lastAssistant.recommendation);
    sendingRef.current = true;
    setMessages(current => [...current, { role: "user", content: userMessage }]);
    setMessage("");
    setIsSending(true);
    startTimer();
    try {
      const recommendation = !foodContext && isRecommendationPrompt(userMessage, previousPlainReply);
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
              <h2>Ask about a food, a meal, your daily progress or what you ate today.</h2>
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
                    : <FormatText text={item.content} />
                  : item.content}
              </div>
            </article>
          ))}
          {isSending && (
            <article className="chat-message assistant">
              <header>Nutri</header>
              <div className="chat-bubble chat-status">
                {statusText}{elapsed > 0 ? ` ${elapsed}s` : ""}
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
