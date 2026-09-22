import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import AppNav from "./AppNav.jsx";
import { API_URL, apiFetch, handleUnauthorized, readUser } from "./api.js";
import chatWelcome from "./assets/images/chat-welcome.jpg";
import "./Chat.css";

const ERROR = "Nutri is having trouble responding right now. Please try again.";

const SUGGESTIONS = [
  ["Plan today's meals", "Help me plan my meals for today"],
  ["Meal ideas", "Suggest 3 healthy recipes for my next meal"],
  ["Protein breakfast", "Suggest a high-protein breakfast"],
  ["High-fibre meal", "Suggest a high-fiber meal"]
];

export default function Chat() {
  const navigate = useNavigate();
  const location = useLocation();
  const token = localStorage.getItem("token");
  const user = readUser();

  const [food, setFood] = useState(location.state?.food || null);
  const [message, setMessage] = useState("");
  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const sendingRef = useRef(false);
  const endRef = useRef(null);

  useEffect(() => {
    if (!token) navigate("/login", { replace: true });
  }, [navigate, token]);

  useEffect(() => {
    if (!token) return;

    const controller = new AbortController();

    fetch(`${API_URL}/api/chat`, {
      headers: { Authorization: `Bearer ${token}` },
      signal: controller.signal
    })
      .then(async response => {
        if (handleUnauthorized(response, navigate) || !response.ok) return [];
        return response.json();
      })
      .then(data => {
        if (!controller.signal.aborted) setMessages(readHistory(data));
      })
      .catch(() => {})
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [navigate, token]);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, sending]);

  async function send(text = message) {
    const value = text.trim();
    if (!value || sendingRef.current || loading) return;

    sendingRef.current = true;
    setSending(true);
    setMessage("");
    setMessages(old => [...old, { role: "user", content: value }]);

    try {
      const recommendation = !food && isRecommendation(value, messages);
      const endpoint = recommendation ? "/api/recommendations" : "/api/chat";

      const body = recommendation
        ? { request: value }
        : {
            message: value,
            ...(food && { source: food.source, sourceId: food.sourceId })
          };

      const response = await apiFetch(`${API_URL}${endpoint}`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`
        },
        body: JSON.stringify(body)
      });

      if (handleUnauthorized(response, navigate)) return;

      if (!response.ok) {
        addAssistant(friendlyError(response));
        return;
      }

      const data = await response.json();

      if (data?.compositeMeal) {
        setMessages(old => [...old, {
          role: "assistant",
          content: data.reply || "",
          compositeMeal: data.compositeMeal
        }]);
        return;
      }

      const structured = data?.recommendation ?? data;
      if (recommendation || data?.recommendation || Array.isArray(data?.recommendations)) {
        const items = recommendations(structured);
        setMessages(old => [...old, items.length
          ? { role: "assistant", recommendation: { ...structured, recommendations: items } }
          : { role: "assistant", content: "No verified recommendation matched that request." }
        ]);
        return;
      }

      addAssistant(data?.reply || "I could not generate a response.");
    } catch (e) {
      addAssistant(e?.message || ERROR);
    } finally {
      sendingRef.current = false;
      setSending(false);
    }
  }

  function addAssistant(content) {
    setMessages(old => [...old, { role: "assistant", content }]);
  }

  async function clearChat() {
    if (deleting || sending || !messages.length) return;
    if (!window.confirm("Delete your chat history?")) return;

    setDeleting(true);
    try {
      const response = await apiFetch(`${API_URL}/api/chat`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${token}` }
      });

      if (handleUnauthorized(response, navigate)) return;
      if (!response.ok) throw new Error();

      setMessages([]);
      setFood(null);
    } catch {
      window.alert("Could not delete chat history.");
    } finally {
      setDeleting(false);
    }
  }

  function keyDown(e) {
    if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      send();
    }
  }

  return (
    <div className="chat-page">
      <AppNav />

      <main className="chat-main">
        <header className="chat-header">
          <div>
            <small>NUTRIVERSE</small>
            <h1>Nutri assistant</h1>
            <p>Evidence-aware personalized nutrition guidance</p>
          </div>

          <button onClick={clearChat} disabled={deleting || sending || !messages.length}>
            {deleting ? "Deleting..." : "Delete chat"}
          </button>
        </header>

        {food && (
          <section className="chat-food">
            <div>
              <small>SELECTED FOOD</small>
              <strong>{food.foodName}</strong>
            </div>
            <span>Questions use its exact source record.</span>
            <button onClick={() => setFood(null)}>Clear</button>
          </section>
        )}

        <section className="chat-messages">
          {loading && <ChatLoader />}

          {!loading && !messages.length && (
            <div className="chat-welcome">
              <div>
                <small>PERSONAL NUTRITION ASSISTANT</small>
                <h2>What would you like to understand{user.name ? `, ${user.name}` : ""}?</h2>
                <p>Ask about foods, recipes, nutrients or meal recommendations.</p>

                <div className="chat-suggestions">
                  {SUGGESTIONS.map(([title, prompt]) => (
                    <button key={title} onClick={() => send(prompt)}>{title}</button>
                  ))}
                </div>
              </div>

              <img src={chatWelcome} alt="Balanced meal ingredients" />
            </div>
          )}

          {messages.map((item, index) => (
            <article key={index} className={`chat-message ${item.role}`}>
              <div>
                <small>{item.role === "assistant" ? "Nutri" : "You"}</small>

                <div className="chat-bubble">
                  {item.recommendation
                    ? <Recommendations data={item.recommendation} />
                    : item.compositeMeal
                      ? <><Text text={item.content} /><Composite data={item.compositeMeal} /></>
                      : item.role === "assistant"
                        ? <Text text={item.content} />
                        : item.content}
                </div>
              </div>
            </article>
          ))}

          {sending && (
            <article className="chat-message assistant">
              <div>
                <small>Nutri</small>
                <div className="chat-bubble">Checking nutrition evidence...</div>
              </div>
            </article>
          )}

          <div ref={endRef} />
        </section>

        <div className="chat-input">
          <textarea
            rows="1"
            value={message}
            placeholder="Ask about food, meals or nutrition"
            disabled={sending || loading}
            onChange={e => setMessage(e.target.value)}
            onKeyDown={keyDown}
          />
          <button disabled={!message.trim() || sending || loading} onClick={() => send()}>
            Send
          </button>
        </div>
      </main>
    </div>
  );
}

function Recommendations({ data }) {
  return (
    <div className="recs">
      <header>
        <div><strong>Evidence-aware recommendations</strong><small>Personalized using your saved profile.</small></div>
        <span>STRUCTURED EVIDENCE</span>
      </header>

      {recommendations(data).map((item, i) => (
        <article key={`${item.evidence?.sourceId || i}-${i}`}>
          <h3><span>{i + 1}</span>{item.what}</h3>

          {item.why?.length > 0 && (
            <ul>{item.why.filter(Boolean).map((reason, j) => <li key={j}>{reason}</li>)}</ul>
          )}

          <div className="evidence">
            <Fact label="Protein" value={item.evidence?.protein} unit="g" />
            <Fact label="Calories" value={item.evidence?.calories} unit="kcal" />
            <Fact label="Fibre" value={item.evidence?.fiber} unit="g" />
            <Fact label="Reference" value={item.evidence?.servingSize} unit={item.evidence?.servingUnit} />
          </div>

          {item.reason && <p>{item.reason}</p>}

          <footer>
            <span>{item.evidence?.verified ? "Verified source" : "Verification unavailable"}</span>
            {item.evidence?.source && <span>{item.evidence.source}</span>}
            {item.evidence?.sourceId && <span>ID: {item.evidence.sourceId}</span>}
          </footer>
        </article>
      ))}
    </div>
  );
}

function Composite({ data }) {
  const n = data?.perServing || {};

  return (
    <div className="composite">
      <header>
        <div><small>COMPOSITE MEAL ESTIMATE</small><h3>{data.dishName || "Homemade meal"}</h3></div>
        <b>ESTIMATED</b>
      </header>

      <div className="evidence">
        <Fact label="Calories" value={n.calories} unit="kcal" />
        <Fact label="Protein" value={n.protein} unit="g" />
        <Fact label="Carbohydrate" value={n.carbohydrates ?? n.carbs} unit="g" />
        <Fact label="Fat" value={n.fat} unit="g" />
        <Fact label="Fibre" value={n.fiber} unit="g" />
        <Fact label="Servings" value={data.servings} />
      </div>

      {!!data.ingredients?.length && (
        <section>
          <h4>Ingredient evidence</h4>
          {data.ingredients.map((x, i) => (
            <div className="ingredient" key={`${x.sourceId || i}-${i}`}>
              <strong>{x.ingredient} <small>{x.quantityGrams} g</small></strong>
              <span>{x.matchedFood}<small>{x.source}{x.sourceId ? ` | ID: ${x.sourceId}` : ""}</small></span>
            </div>
          ))}
        </section>
      )}

      {!!data.warnings?.length && <ul>{data.warnings.map((w, i) => <li key={i}>{w}</li>)}</ul>}

      <footer>Estimated recipe result, not a direct prepared-food record.</footer>
    </div>
  );
}

function Fact({ label, value, unit = "" }) {
  const n = Number(value);
  const shown = value != null && value !== "" && Number.isFinite(n)
    ? `${Math.round(n * 100) / 100}${unit ? ` ${unit}` : ""}`
    : "N/A";

  return <div><small>{label}</small><strong>{shown}</strong></div>;
}

function Text({ text = "" }) {
  return (
    <div>
      {text.split("\n").map((line, i) => {
        const value = line.trim();
        if (!value) return <br key={i} />;
        if (value.startsWith("- ") || value.startsWith("• ")) return <p key={i}>• <Bold text={value.slice(2)} /></p>;
        return <p key={i}><Bold text={value} /></p>;
      })}
    </div>
  );
}

function Bold({ text }) {
  return text.split(/(\*\*.*?\*\*)/g).map((part, i) =>
    part.startsWith("**") && part.endsWith("**")
      ? <strong key={i}>{part.slice(2, -2)}</strong>
      : part
  );
}

function ChatLoader() {
  return <div className="chat-loader"><span /><span /><span /></div>;
}

function recommendations(data) {
  return Array.isArray(data?.recommendations)
    ? data.recommendations.filter(x => x?.what?.trim())
    : [];
}

function readHistory(data) {
  if (!Array.isArray(data)) return [];

  return data.flatMap(item => {
    if (!["user", "assistant"].includes(item?.role)) return [];
    if (item.compositeMeal) return [{ role: "assistant", content: item.content || "", compositeMeal: item.compositeMeal }];
    if (item.recommendation) return [{ role: "assistant", recommendation: item.recommendation }];
    return item.content?.trim() ? [{ role: item.role, content: item.content }] : [];
  });
}

function isRecommendation(text, history) {
  const v = text.toLowerCase();

  if (/(recipe|plan my meal|plan my meals)/.test(v)) return false;
  if (/\b(other|another|more|different)\b/.test(v) && history.some(x => x.role === "assistant")) return false;

  return /(recommend|suggest|give me|what should i eat|what can i eat|i need|i want)/.test(v)
    && /(breakfast|lunch|dinner|snack|protein|fiber|fibre|vegetarian|vegan|food|meal)/.test(v);
}

function friendlyError(response) {
  if (response.status === 429) return "Nutri is receiving many requests. Please try again shortly.";
  if (response.status === 503) return "Verified nutrition evidence is temporarily unavailable.";
  if (response.status === 403) return "Nutri is unavailable for this account.";
  return ERROR;
}