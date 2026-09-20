import AppNav from "./AppNav.jsx";
import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import chatWelcome from "./assets/images/chat-welcome.jpg";
import "./Chat.css";
import { API_URL, readUser, handleUnauthorized } from "./api.js";
const TEMP_ERROR = "Nutri is having trouble responding right now. Please try again in a moment.";
const suggestions = [
  ["🥗", "Plan my meals", "Help me plan my meals for today"],
  ["🍲", "Suggest 3 recipes", "Suggest 3 healthy recipes for my next meal"],
  ["💪", "Protein breakfast", "Suggest a high-protein breakfast"],
  ["🌱", "Fiber meal", "Suggest a high-fiber meal"]
];
const hasAny = (text, words) => words.some(word => text.includes(word));
const FOLLOW_UP = /\b(?:other|another|more|different|else)\b/;
const MEAL_IDEAS = /\b(?:meals|dishes|ideas)\b/;
function isRecommendationPrompt(text, previousWasChatReply = false) {
  const value = text.toLowerCase();
  if (hasAny(value, [
    "don't have", "dont have", "do not have", "not available", "without ",
    "exclude ", "avoid ", "instead of ", "alternative", "swap"
  ])) return true;
  // "suggest any other meals" after Nutri gave recipes is a request for more recipes, not for single foods.
  if (FOLLOW_UP.test(value) && (previousWasChatReply || MEAL_IDEAS.test(value))) return false;
  if (hasAny(value, ["recipe", "plan my meal", "plan my meals"])) return false;
  return hasAny(value, [
    "recommend", "suggest", "give me", "what should i eat", "what can i eat", "i need", "i want"
  ]) && hasAny(value, [
    "breakfast", "lunch", "dinner", "snack", "protein", "fiber", "fibre",
    "vegetarian", "vegan", "food", "meal", "post workout", "post-workout"
  ]);
}
function friendlyError(response) {
  if (response.status === 429) {
    const retry = Number(response.headers.get("Retry-After"));
    return Number.isFinite(retry) && retry > 0
      ? `Nutri is receiving a lot of requests right now. Try again in about ${Math.ceil(retry)} seconds.`
      : "Nutri is receiving a lot of requests right now. Please try again in a few seconds.";
  }
  if (response.status === 403) return "Nutri is not available for this account right now.";
  if (response.status === 503) return "Verified nutrition evidence is temporarily unavailable. Please try again shortly.";
  return TEMP_ERROR;
}
function Chat() {
  const navigate = useNavigate();
  const location = useLocation();
  const token = localStorage.getItem("token");
  const user = readUser();
  const [foodContext, setFoodContext] = useState(location.state?.food || null);
  const [message, setMessage] = useState("");
  const [messages, setMessages] = useState([]);
  const [isSending, setIsSending] = useState(false);
  const [typingText, setTypingText] = useState("Nutri is typing...");
  const endRef = useRef(null), sendingRef = useRef(false), timerRef = useRef(null);
  useEffect(() => { if (!token) navigate("/login", { replace: true }); }, [navigate, token]);
  useEffect(() => { endRef.current?.scrollIntoView({ behavior: "smooth" }); }, [messages, isSending]);
  useEffect(() => () => window.clearTimeout(timerRef.current), []);
  async function sendMessage(text = message) {
    const userMessage = text.trim();
    if (!userMessage || sendingRef.current) return;
    if (!token) return navigate("/login");
    sendingRef.current = true;
    setMessages(prev => [...prev, { role: "user", content: userMessage }]);
    setMessage("");
    setIsSending(true);
    setTypingText("Nutri is typing...");
    timerRef.current = window.setTimeout(() => setTypingText("Nutri is thinking..."), 5000);
    try {
      const lastReply = [...messages].reverse().find(item => item.role === "assistant");
      const previousWasChatReply = Boolean(lastReply && !lastReply.recommendation);
      const recommendation = !foodContext && isRecommendationPrompt(userMessage, previousWasChatReply);
      const endpoint = recommendation ? "/api/recommendations" : "/api/chat";
      const body = recommendation
        ? { request: userMessage }
        : { message: userMessage, ...(foodContext && {
            source: foodContext.source, sourceId: foodContext.sourceId
          }) };
      const response = await fetch(`${API_URL}${endpoint}`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
        body: JSON.stringify(body)
      });
      if (handleUnauthorized(response, navigate)) return;
      if (!response.ok) {
        setMessages(prev => [...prev, { role: "assistant", content: friendlyError(response) }]);
        return;
      }
      const data = await response.json();
      const structured = data?.recommendation ?? data;
      if (recommendation || data?.recommendation || Array.isArray(data?.recommendations)) {
        const items = recommendationItems(structured);
        const reply = items.length
          ? { role: "assistant", recommendation: { ...structured, recommendations: items } }
          : { role: "assistant", content: "I couldn't find a verified USDA-backed recommendation for that request." };
        setMessages(prev => [...prev, reply]);
        return;
      }
      setMessages(prev => [...prev, {
        role: "assistant",
        content: typeof data?.reply === "string" && data.reply ? data.reply : "I couldn't generate a response."
      }]);
    } catch {
      setMessages(prev => [...prev, { role: "assistant", content: TEMP_ERROR }]);
    } finally {
      window.clearTimeout(timerRef.current);
      timerRef.current = null;
      sendingRef.current = false;
      setIsSending(false);
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
          <div><h2>Nutri AI Assistant 🌱</h2><p>Evidence-aware personalized nutrition guidance</p></div>
          <span className="chat-online">● Online</span>
        </header>
        {foodContext && <div className="chat-food-context">
          <span>Selected food: {foodContext.foodName}. Nutrition facts will be retrieved from its exact source record.</span>
          <button type="button" disabled={isSending} onClick={() => sendMessage("How much protein does this food contain?")}>Ask about protein</button>
          <button type="button" disabled={isSending} onClick={() => setFoodContext(null)}>Clear food</button>
        </div>}
        <section className="chat-messages">
          {messages.length === 0 && <div className="chat-welcome">
            <div className="chat-welcome-text">
              <span className="chat-label">YOUR NUTRITION COMPANION</span>
              <h1>Hey {user.name || "there"} 👋</h1>
              <p>Ask for recommendations, nutrition information or practical meal ideas.</p>
              <div className="chat-suggestions">
                {suggestions.map(([icon, title, prompt]) =>
                  <button type="button" key={title} disabled={isSending} onClick={() => sendMessage(prompt)}>
                    <span>{icon}</span>{title}
                  </button>
                )}
              </div>
            </div>
            <img src={chatWelcome} alt="Healthy balanced meal" />
          </div>}
          {messages.map((item, index) =>
            <div key={index} className={`chat-message ${item.role}`}>
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
          )}
          {isSending && <div className="chat-message assistant">
            <div className="chat-bot-avatar">🌿</div>
            <div className="chat-message-content">
              <small>Nutri</small>
              <div className="chat-bubble chat-typing" role="status" aria-live="polite">
                <span>{typingText}</span><span className="chat-typing-dots" aria-hidden="true">● ● ●</span>
              </div>
            </div>
          </div>}
          <div ref={endRef} />
        </section>
        <div className="chat-input-area">
          <textarea aria-label="Message to Nutri" rows="1" placeholder="Ask Nutri about food, meals or nutrition..."
            value={message} disabled={isSending} onChange={e => setMessage(e.target.value)} onKeyDown={handleKey} />
          <button className="chat-send" type="button" aria-label="Send message"
            disabled={!message.trim() || isSending} onClick={() => sendMessage()}>➤</button>
        </div>
      </main>
    </div>
  );
}
function recommendationItems(data) {
  return Array.isArray(data?.recommendations)
    ? data.recommendations.filter(item => item && typeof item.what === "string" && item.what.trim())
    : [];
}
function RecommendationCards({ data }) {
  return <div className="recommendation-results">
    <div className="recommendation-heading">
      <div><strong>Evidence-aware recommendations</strong><p>Personalized using your request and saved profile.</p></div>
      <span className="recommendation-method">Structured Evidence</span>
    </div>
    {recommendationItems(data).map((item, index) =>
      <div className="recommendation-card" key={`${item.evidence?.source || "food"}:${item.evidence?.sourceId || index}:${index}`}>
        <div className="recommendation-title">
          <span className="recommendation-number">{index + 1}</span>
          <div><small>RECOMMENDATION</small><h3>{item.what}</h3></div>
        </div>
        <div className="recommendation-section">
          <strong>Why?</strong>
          {(Array.isArray(item.why) ? item.why : []).filter(reason => typeof reason === "string" && reason.trim())
            .map((reason, i) => <p key={i} className="recommendation-why">✓ {reason}</p>)}
        </div>
        <div className="recommendation-section">
          <strong>Evidence</strong>
          <div className="recommendation-evidence">
            <EvidenceValue label="Protein" value={item.evidence?.protein} unit="g" />
            <EvidenceValue label="Calories" value={item.evidence?.calories} unit="kcal" />
            <EvidenceValue label="Fiber" value={item.evidence?.fiber} unit="g" />
            <EvidenceValue label="Reference" value={item.evidence?.servingSize} unit={item.evidence?.servingUnit} />
          </div>
        </div>
        {typeof item.reason === "string" && item.reason.trim() &&
          <div className="recommendation-section"><strong>Reason</strong><p>{item.reason}</p></div>}
        <div className="recommendation-source">
          {item.evidence?.verified === true && item.evidence?.source && item.evidence?.sourceId
            ? <span>✓ Verified source</span> : <span>Source verification unavailable</span>}
          <span>{item.evidence?.source}</span>
          {item.evidence?.sourceId && <span>FDC ID: {item.evidence.sourceId}</span>}
          {item.evidence?.dataType && <span>{item.evidence.dataType}</span>}
        </div>
      </div>
    )}
  </div>;
}
function EvidenceValue({ label, value, unit }) {
  const number = Number(value);
  const available = value != null && value !== "" && Number.isFinite(number);
  const shown = available ? Math.round(number * 100) / 100 : "N/A";
  return <div className="recommendation-evidence-value">
    <small>{label}</small><strong>{shown} {available ? unit : ""}</strong>
  </div>;
}
function FormatText({ text = "" }) {
  return <div>{text.split("\n").map((line, index) => {
    const value = line.trim();
    if (!value) return <div className="chat-space" key={index} />;
    if (/^\|?[-:\s|]+\|?$/.test(value)) return null;
    if (value.startsWith("|") && value.endsWith("|"))
      return <div className="chat-table-row" key={index}>
        {value.split("|").filter(Boolean).map((cell, i) => <span key={i}><BoldText text={cell.trim()} /></span>)}
      </div>;
    if (value.startsWith("- ") || value.startsWith("• "))
      return <div className="chat-list-line" key={index}><span>•</span><BoldText text={value.slice(2)} /></div>;
    const step = value.match(/^(\d+)\.\s+(.*)/);
    if (step) return <div className="chat-step" key={index}><span>{step[1]}</span><BoldText text={step[2]} /></div>;
    return <div className="chat-line" key={index}><BoldText text={value} /></div>;
  })}</div>;
}
function BoldText({ text }) {
  return text.split(/(\*\*.*?\*\*)/g).map((part, index) =>
    part.startsWith("**") && part.endsWith("**") ? <strong key={index}>{part.slice(2, -2)}</strong> : part
  );
}
export default Chat;
