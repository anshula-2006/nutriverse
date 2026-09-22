import AppNav from "./AppNav.jsx";
import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import chatWelcome from "./assets/images/chat-welcome.jpg";
import { API_URL, readUser, handleUnauthorized } from "./api.js";
import "./Chat.css";

const TEMP_ERROR = "Nutri is having trouble responding right now. Please try again in a moment.";

const suggestions = [
  ["🥗", "Plan my meals", "Help me plan my meals for today"],
  ["🍲", "Suggest recipes", "Suggest 3 recipes for my next meal"],
  ["💪", "Protein breakfast", "Suggest a high-protein breakfast"],
  ["🌱", "Fiber meal", "Suggest a high-fiber meal"]
];

const hasAny = (t, words) => words.some(w => t.includes(w));
const PROFILE_UPDATE = /\b(?:i (?:don't|dont|do not) (?:eat|consume|like)|i (?:can't|cant|cannot) (?:eat|have)|i dislike|i hate|avoid|exclude)\b/i;
const REQUEST_WORDS = /\b(?:recommend|suggest|give me|what should i eat|what can i eat|another|more|alternative|instead)\b/i;
const FOLLOW_UP = /\b(?:other|another|more|different|else)\b/;

function isRecommendationPrompt(text, previousChat = false) {
  const t = text.toLowerCase();

  const profileUpdate = PROFILE_UPDATE.test(t) && !REQUEST_WORDS.test(t);
  const dailyProgress =
    hasAny(t, ["protein", "calorie", "calories", "macro", "macros"]) &&
    hasAny(t, ["goal", "target", "remaining", "left", "consumed", "consume", "eaten", "ate", "logged", "hit my"]);

  if (profileUpdate || dailyProgress) return false;
  if (hasAny(t, ["recipe", "plan my meal", "plan my meals"])) return false;
  if (FOLLOW_UP.test(t) && previousChat) return false;

  if (hasAny(t, ["don't have", "dont have", "do not have", "not available", "without ", "exclude ", "avoid ", "instead of ", "alternative", "swap"]))
    return true;

  return hasAny(t, ["recommend", "suggest", "give me", "what should i eat", "what can i eat", "i need", "i want"]) &&
    hasAny(t, ["breakfast", "lunch", "dinner", "snack", "protein", "fiber", "fibre", "vegetarian", "vegan", "food", "meal", "post workout", "post-workout"]);
}

function recommendationItems(data) {
  return Array.isArray(data?.recommendations)
    ? data.recommendations.filter(x => x?.what?.trim())
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
  if (response.status === 429) return "Nutri is receiving a lot of requests. Please try again shortly.";
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
  const [typingText, setTypingText] = useState("Nutri is typing...");
  const [elapsed, setElapsed] = useState(0);

  const endRef = useRef(null);
  const sendingRef = useRef(false);
  const thinkingTimer = useRef(null);
  const elapsedTimer = useRef(null);

  useEffect(() => {
    if (!token) navigate("/login", { replace: true });
  }, [token, navigate]);

  useEffect(() => {
    if (!token) return setHistoryLoading(false);

    const controller = new AbortController();

    fetch(`${API_URL}/api/chat`, {
      headers: { Authorization: `Bearer ${token}` },
      signal: controller.signal
    })
      .then(r => {
        if (handleUnauthorized(r, navigate) || !r.ok) return [];
        return r.json();
      })
      .then(data => !controller.signal.aborted && setMessages(historyItems(data)))
      .catch(e => e.name !== "AbortError" && setMessages([]))
      .finally(() => !controller.signal.aborted && setHistoryLoading(false));

    return () => controller.abort();
  }, [token, navigate]);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, isSending]);

  useEffect(() => () => {
    clearTimeout(thinkingTimer.current);
    clearInterval(elapsedTimer.current);
  }, []);

  function startTimer() {
    setElapsed(0);
    setTypingText("Nutri is typing...");

    thinkingTimer.current = setTimeout(() => setTypingText("Nutri is thinking..."), 5000);
    elapsedTimer.current = setInterval(() => setElapsed(x => x + 1), 1000);
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

    sendingRef.current = true;
    setMessages(m => [...m, { role: "user", content: userMessage }]);
    setMessage("");
    setIsSending(true);
    startTimer();

    try {
      const lastReply = [...messages].reverse().find(x => x.role === "assistant");
      const recommendation =
        !foodContext &&
        isRecommendationPrompt(userMessage, Boolean(lastReply && !lastReply.recommendation));

      const endpoint = recommendation ? "/api/recommendations" : "/api/chat";
      const body = recommendation
        ? { request: userMessage }
        : {
            message: userMessage,
            ...(foodContext && {
              source: foodContext.source,
              sourceId: foodContext.sourceId
            })
          };

      const response = await fetch(`${API_URL}${endpoint}`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`
        },
        body: JSON.stringify(body)
      });

      if (handleUnauthorized(response, navigate)) return;

      if (!response.ok) {
        setMessages(m => [...m, { role: "assistant", content: friendlyError(response) }]);
        return;
      }

      const data = await response.json();
      const structured = data?.recommendation ?? data;

      if (recommendation || data?.recommendation || Array.isArray(data?.recommendations)) {
        const items = recommendationItems(structured);

        setMessages(m => [...m, {
          role: "assistant",
          ...(items.length
            ? { recommendation: { ...structured, recommendations: items } }
            : { content: "I couldn't find a verified USDA-backed recommendation for that request." })
        }]);
      } else {
        setMessages(m => [...m, {
          role: "assistant",
          content: data?.reply || "I couldn't generate a response."
        }]);
      }
    } catch {
      setMessages(m => [...m, { role: "assistant", content: TEMP_ERROR }]);
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

  function handleKey(e) {
    if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      sendMessage();
    }
  }

  return (
    <div className="chat-page">
      <AppNav />

      <main className="chat-main">
        <header className="chat-header">
          <div>
            <h2>Nutri AI Assistant 🌱</h2>
            <p>Evidence-aware personalized nutrition guidance</p>
          </div>

          <div className="chat-header-actions">
            <span className="chat-online">● Online</span>
            <button
              className="chat-delete"
              onClick={deleteChat}
              disabled={isDeleting || isSending || historyLoading || !messages.length}
            >
              {isDeleting ? "Deleting..." : "Delete chat"}
            </button>
          </div>
        </header>

        {foodContext && (
          <div className="chat-food-context">
            <span>Selected food: {foodContext.foodName}. Exact source record will be used.</span>
            <button disabled={isSending} onClick={() => sendMessage("How much protein does this food contain?")}>
              Ask about protein
            </button>
            <button disabled={isSending} onClick={() => setFoodContext(null)}>Clear food</button>
          </div>
        )}

        <section className="chat-messages">
          {!historyLoading && !messages.length && (
            <div className="chat-welcome">
              <div className="chat-welcome-text">
                <span className="chat-label">YOUR NUTRITION COMPANION</span>
                <h1>Hey {user?.name || "there"} 👋</h1>
                <p>Ask for recommendations, nutrition information or practical meal ideas.</p>

                <div className="chat-suggestions">
                  {suggestions.map(([icon, title, prompt]) => (
                    <button key={title} disabled={isSending} onClick={() => sendMessage(prompt)}>
                      <span>{icon}</span>{title}
                    </button>
                  ))}
                </div>
              </div>

              <img src={chatWelcome} alt="Healthy balanced meal" />
            </div>
          )}

          {messages.map((item, i) => (
            <div key={i} className={`chat-message ${item.role}`}>
              {item.role === "assistant" && <div className="chat-bot-avatar">🌿</div>}

              <div className="chat-message-content">
                <small>{item.role === "assistant" ? "Nutri" : "You"}</small>

                <div className="chat-bubble">
                  {item.role === "assistant"
                    ? item.recommendation
                      ? <RecommendationCards data={item.recommendation} />
                      : <FormatText text={item.content} />
                    : item.content}
                </div>
              </div>
            </div>
          ))}

          {isSending && (
            <div className="chat-message assistant">
              <div className="chat-bot-avatar">🌿</div>
              <div className="chat-message-content">
                <small>Nutri</small>
                <div className="chat-bubble chat-typing">
                  <span>{typingText} {elapsed > 0 && `${elapsed}s`}</span>
                  <span className="chat-typing-dots">● ● ●</span>
                </div>
              </div>
            </div>
          )}

          <div ref={endRef} />
        </section>

        <div className="chat-input-area">
          <textarea
            rows="1"
            placeholder="Ask Nutri about food, meals or nutrition..."
            value={message}
            disabled={isSending || historyLoading}
            onChange={e => setMessage(e.target.value)}
            onKeyDown={handleKey}
          />

          <button
            className="chat-send"
            disabled={!message.trim() || isSending || historyLoading}
            onClick={() => sendMessage()}
          >
            ➤
          </button>
        </div>
      </main>
    </div>
  );
}

function RecommendationCards({ data }) {
  return (
    <div className="recommendation-results">
      <div className="recommendation-heading">
        <div>
          <strong>Evidence-aware recommendations</strong>
          <p>Personalized using your request and saved profile.</p>
        </div>
        <span className="recommendation-method">Structured Evidence</span>
      </div>

      {recommendationItems(data).map((item, i) => (
        <div className="recommendation-card" key={`${item.evidence?.sourceId || i}-${i}`}>
          <div className="recommendation-title">
            <span className="recommendation-number">{i + 1}</span>
            <div>
              <small>RECOMMENDATION</small>
              <h3>{item.what}</h3>
            </div>
          </div>

          {!!item.why?.length && (
            <div className="recommendation-section">
              <strong>Why?</strong>
              {item.why.map((reason, j) => (
                <p key={j} className="recommendation-why">✓ {reason}</p>
              ))}
            </div>
          )}

          <div className="recommendation-section">
            <strong>Evidence</strong>
            <div className="recommendation-evidence">
              <Evidence label="Protein" value={item.evidence?.protein} unit="g" />
              <Evidence label="Calories" value={item.evidence?.calories} unit="kcal" />
              <Evidence label="Fiber" value={item.evidence?.fiber} unit="g" />
              <Evidence label="Reference" value={item.evidence?.servingSize} unit={item.evidence?.servingUnit} />
            </div>
          </div>

          {item.reason && (
            <div className="recommendation-section">
              <strong>Reason</strong>
              <p>{item.reason}</p>
            </div>
          )}

          <div className="recommendation-source">
            <span>{item.evidence?.verified ? "✓ Verified source" : "Source verification unavailable"}</span>
            <span>{item.evidence?.source}</span>
            {item.evidence?.sourceId && <span>FDC ID: {item.evidence.sourceId}</span>}
            {item.evidence?.dataType && <span>{item.evidence.dataType}</span>}
          </div>
        </div>
      ))}
    </div>
  );
}

function Evidence({ label, value, unit }) {
  const n = Number(value);
  const ok = value != null && value !== "" && Number.isFinite(n);

  return (
    <div className="recommendation-evidence-value">
      <small>{label}</small>
      <strong>{ok ? Math.round(n * 100) / 100 : "N/A"} {ok ? unit : ""}</strong>
    </div>
  );
}

function FormatText({ text = "" }) {
  return (
    <div>
      {text.split("\n").map((line, i) => {
        const v = line.trim();

        if (!v) return <div className="chat-space" key={i} />;
        if (/^\|?[-:\s|]+\|?$/.test(v)) return null;

        if (v.startsWith("|") && v.endsWith("|"))
          return (
            <div className="chat-table-row" key={i}>
              {v.split("|").filter(Boolean).map((cell, j) => (
                <span key={j}><Bold text={cell.trim()} /></span>
              ))}
            </div>
          );

        if (/^[-•]\s/.test(v))
          return <div className="chat-list-line" key={i}><span>•</span><Bold text={v.slice(2)} /></div>;

        const step = v.match(/^(\d+)\.\s+(.*)/);
        if (step)
          return <div className="chat-step" key={i}><span>{step[1]}</span><Bold text={step[2]} /></div>;

        return <div className="chat-line" key={i}><Bold text={v} /></div>;
      })}
    </div>
  );
}

function Bold({ text = "" }) {
  return text.split(/(\*\*.*?\*\*)/g).map((part, i) =>
    part.startsWith("**") && part.endsWith("**")
      ? <strong key={i}>{part.slice(2, -2)}</strong>
      : part
  );
}