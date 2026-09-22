import AppNav from "./AppNav.jsx";
import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import chatWelcome from "./assets/images/chat-welcome.jpg";
import "./Chat.css";
import { API_URL, readUser, handleUnauthorized } from "./api.js";

const TEMP_ERROR =
  "Nutri is having trouble responding right now. Please try again in a moment.";

const suggestions = [
  ["🥗", "Plan my meals", "Help me plan my meals for today"],
  ["🍲", "Suggest 3 recipes", "Suggest 3 recipes for my next meal"],
  ["💪", "Protein breakfast", "Suggest a high-protein breakfast"],
  ["🌱", "Fiber meal", "Suggest a high-fiber meal"]
];

const hasAny = (text, words) => words.some(word => text.includes(word));

const FOLLOW_UP = /\b(?:other|another|more|different|else)\b/;
const MEAL_IDEAS = /\b(?:meals|dishes|ideas)\b/;

const PROFILE_UPDATE =
  /\b(?:i (?:don't|dont|do not) (?:eat|consume|like)|i (?:can't|cant|cannot) (?:eat|have)|i dislike|i hate|avoid|exclude)\b/i;

const REQUEST_WORDS =
  /\b(?:recommend|suggest|give me|what should i eat|what can i eat|another|more|alternative|instead)\b/i;

function isProfileUpdatePrompt(text) {
  return PROFILE_UPDATE.test(text) && !REQUEST_WORDS.test(text);
}

function isDailyProgressPrompt(text) {
  const value = text.toLowerCase();

  const nutrient = hasAny(value, [
    "protein",
    "calorie",
    "calories",
    "macro",
    "macros"
  ]);

  const progress = hasAny(value, [
    "goal",
    "target",
    "remaining",
    "left",
    "consumed",
    "consume",
    "eaten",
    "ate",
    "logged",
    "hit my"
  ]);

  return nutrient && progress;
}

function isRecommendationPrompt(text, previousWasChatReply = false) {
  const value = text.toLowerCase();

  // These belong to /api/chat because the backend needs to update/read
  // the user's saved profile or today's logged totals.
  if (isProfileUpdatePrompt(value) || isDailyProgressPrompt(value)) {
    return false;
  }

  if (
    hasAny(value, [
      "don't have",
      "dont have",
      "do not have",
      "not available",
      "without ",
      "exclude ",
      "avoid ",
      "instead of ",
      "alternative",
      "swap"
    ])
  ) {
    return true;
  }

  // After a generated meal/recipe conversation, "other meals" should
  // remain in the normal chat instead of becoming single-food results.
  if (
    FOLLOW_UP.test(value) &&
    (previousWasChatReply || MEAL_IDEAS.test(value))
  ) {
    return false;
  }

  if (hasAny(value, ["recipe", "plan my meal", "plan my meals"])) {
    return false;
  }

  return (
    hasAny(value, [
      "recommend",
      "suggest",
      "give me",
      "what should i eat",
      "what can i eat",
      "i need",
      "i want"
    ]) &&
    hasAny(value, [
      "breakfast",
      "lunch",
      "dinner",
      "snack",
      "protein",
      "fiber",
      "fibre",
      "vegetarian",
      "vegan",
      "food",
      "meal",
      "post workout",
      "post-workout"
    ])
  );
}

function historyItems(data) {
  if (!Array.isArray(data)) return [];

  return data.flatMap(item => {
    if (!item || !["user", "assistant"].includes(item.role)) return [];

    if (item.role === "assistant" && item.recommendation) {
      const items = recommendationItems(item.recommendation);

      return [
        items.length
          ? {
              role: "assistant",
              recommendation: {
                ...item.recommendation,
                recommendations: items
              }
            }
          : {
              role: "assistant",
              content:
                "No additional verified options matched your constraints."
            }
      ];
    }

    return typeof item.content === "string" && item.content.trim()
      ? [{ role: item.role, content: item.content }]
      : [];
  });
}

function friendlyError(response) {
  if (response.status === 429) {
    const retry = Number(response.headers.get("Retry-After"));

    return Number.isFinite(retry) && retry > 0
      ? `Nutri is receiving a lot of requests right now. Try again in about ${Math.ceil(
          retry
        )} seconds.`
      : "Nutri is receiving a lot of requests right now. Please try again in a few seconds.";
  }

  if (response.status === 403)
    return "Nutri is not available for this account right now.";

  if (response.status === 503)
    return "Verified nutrition evidence is temporarily unavailable. Please try again shortly.";

  return TEMP_ERROR;
}

function Chat() {
  const navigate = useNavigate();
  const location = useLocation();

  const token = localStorage.getItem("token");
  const user = readUser();

  const [foodContext, setFoodContext] = useState(
    location.state?.food || null
  );

  const [message, setMessage] = useState("");
  const [messages, setMessages] = useState([]);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [isSending, setIsSending] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [typingText, setTypingText] = useState("Nutri is typing...");

  const endRef = useRef(null);
  const sendingRef = useRef(false);
  const timerRef = useRef(null);

  useEffect(() => {
    if (!token) navigate("/login", { replace: true });
  }, [navigate, token]);

  useEffect(() => {
    if (!token) {
      setHistoryLoading(false);
      return undefined;
    }

    const controller = new AbortController();

    async function loadHistory() {
      try {
        const response = await fetch(`${API_URL}/api/chat`, {
          headers: {
            Authorization: `Bearer ${token}`
          },
          signal: controller.signal
        });

        if (handleUnauthorized(response, navigate)) return;
        if (!response.ok) return;

        const data = await response.json();

        if (!controller.signal.aborted) {
          setMessages(historyItems(data));
        }
      } catch (error) {
        if (error.name !== "AbortError") {
          setMessages([]);
        }
      } finally {
        if (!controller.signal.aborted) {
          setHistoryLoading(false);
        }
      }
    }

    loadHistory();

    return () => controller.abort();
  }, [navigate, token]);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, isSending]);

  useEffect(
    () => () => window.clearTimeout(timerRef.current),
    []
  );

  async function sendMessage(text = message) {
    const userMessage = text.trim();

    if (!userMessage || sendingRef.current || historyLoading) return;
    if (!token) return navigate("/login");

    sendingRef.current = true;

    setMessages(prev => [
      ...prev,
      { role: "user", content: userMessage }
    ]);

    setMessage("");
    setIsSending(true);
    setTypingText("Nutri is typing...");

    timerRef.current = window.setTimeout(
      () => setTypingText("Nutri is thinking..."),
      5000
    );

    try {
      const lastReply = [...messages]
        .reverse()
        .find(item => item.role === "assistant");

      const previousWasChatReply = Boolean(
        lastReply && !lastReply.recommendation
      );

      const recommendation =
        !foodContext &&
        isRecommendationPrompt(
          userMessage,
          previousWasChatReply
        );

      const endpoint = recommendation
        ? "/api/recommendations"
        : "/api/chat";

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
        setMessages(prev => [
          ...prev,
          {
            role: "assistant",
            content: friendlyError(response)
          }
        ]);

        return;
      }

      const data = await response.json();
      const structured = data?.recommendation ?? data;

      if (
        recommendation ||
        data?.recommendation ||
        Array.isArray(data?.recommendations)
      ) {
        const items = recommendationItems(structured);

        const reply = items.length
          ? {
              role: "assistant",
              recommendation: {
                ...structured,
                recommendations: items
              }
            }
          : {
              role: "assistant",
              content:
                "I couldn't find a verified USDA-backed recommendation for that request."
            };

        setMessages(prev => [...prev, reply]);
        return;
      }

      setMessages(prev => [
        ...prev,
        {
          role: "assistant",
          content:
            typeof data?.reply === "string" && data.reply
              ? data.reply
              : "I couldn't generate a response."
        }
      ]);
    } catch {
      setMessages(prev => [
        ...prev,
        {
          role: "assistant",
          content: TEMP_ERROR
        }
      ]);
    } finally {
      window.clearTimeout(timerRef.current);
      timerRef.current = null;

      sendingRef.current = false;
      setIsSending(false);
    }
  }

  async function deleteChat() {
    if (
      isDeleting ||
      isSending ||
      historyLoading ||
      messages.length === 0
    ) {
      return;
    }

    if (
      !window.confirm(
        "Delete your entire chat history? This cannot be undone."
      )
    ) {
      return;
    }

    setIsDeleting(true);

    try {
      const response = await fetch(`${API_URL}/api/chat`, {
        method: "DELETE",
        headers: {
          Authorization: `Bearer ${token}`
        }
      });

      if (handleUnauthorized(response, navigate)) return;

      if (!response.ok) {
        window.alert(
          "Could not delete your chat history. Please try again."
        );
        return;
      }

      setMessages([]);
      setFoodContext(null);
      setMessage("");
    } catch {
      window.alert(
        "Could not connect to the backend. Please try again."
      );
    } finally {
      setIsDeleting(false);
    }
  }

  function handleKey(event) {
    if (
      event.key === "Enter" &&
      !event.shiftKey &&
      !event.nativeEvent.isComposing
    ) {
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
            <h2>Nutri AI Assistant 🌱</h2>
            <p>Evidence-aware personalized nutrition guidance</p>
          </div>

          <div className="chat-header-actions">
            <span className="chat-online">● Online</span>

            <button
              className="chat-delete"
              type="button"
              onClick={deleteChat}
              disabled={
                isDeleting ||
                isSending ||
                historyLoading ||
                messages.length === 0
              }
            >
              {isDeleting ? "Deleting..." : "Delete chat"}
            </button>
          </div>
        </header>

        {foodContext && (
          <div className="chat-food-context">
            <span>
              Selected food: {foodContext.foodName}. Nutrition facts
              will be retrieved from its exact source record.
            </span>

            <button
              type="button"
              disabled={isSending}
              onClick={() =>
                sendMessage(
                  "How much protein does this food contain?"
                )
              }
            >
              Ask about protein
            </button>

            <button
              type="button"
              disabled={isSending}
              onClick={() => setFoodContext(null)}
            >
              Clear food
            </button>
          </div>
        )}

        <section className="chat-messages">
          {!historyLoading && messages.length === 0 && (
            <div className="chat-welcome">
              <div className="chat-welcome-text">
                <span className="chat-label">
                  YOUR NUTRITION COMPANION
                </span>

                <h1>Hey {user.name || "there"} 👋</h1>

                <p>
                  Ask for recommendations, nutrition information or
                  practical meal ideas.
                </p>

                <div className="chat-suggestions">
                  {suggestions.map(([icon, title, prompt]) => (
                    <button
                      type="button"
                      key={title}
                      disabled={isSending || historyLoading}
                      onClick={() => sendMessage(prompt)}
                    >
                      <span>{icon}</span>
                      {title}
                    </button>
                  ))}
                </div>
              </div>

              <img
                src={chatWelcome}
                alt="Healthy balanced meal"
              />
            </div>
          )}

          {messages.map((item, index) => (
            <div
              key={index}
              className={`chat-message ${item.role}`}
            >
              {item.role === "assistant" && (
                <div className="chat-bot-avatar">🌿</div>
              )}

              <div className="chat-message-content">
                <small>
                  {item.role === "assistant" ? "Nutri" : "You"}
                </small>

                <div className="chat-bubble">
                  {item.role === "assistant"
                    ? item.recommendation
                      ? (
                        <RecommendationCards
                          data={item.recommendation}
                        />
                      )
                      : (
                        <FormatText text={item.content} />
                      )
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

                <div
                  className="chat-bubble chat-typing"
                  role="status"
                  aria-live="polite"
                >
                  <span>{typingText}</span>

                  <span
                    className="chat-typing-dots"
                    aria-hidden="true"
                  >
                    ● ● ●
                  </span>
                </div>
              </div>
            </div>
          )}

          <div ref={endRef} />
        </section>

        <div className="chat-input-area">
          <textarea
            aria-label="Message to Nutri"
            rows="1"
            placeholder="Ask Nutri about food, meals or nutrition..."
            value={message}
            disabled={isSending || historyLoading}
            onChange={event => setMessage(event.target.value)}
            onKeyDown={handleKey}
          />

          <button
            className="chat-send"
            type="button"
            aria-label="Send message"
            disabled={
              !message.trim() ||
              isSending ||
              historyLoading
            }
            onClick={() => sendMessage()}
          >
            ➤
          </button>
        </div>
      </main>
    </div>
  );
}

function recommendationItems(data) {
  return Array.isArray(data?.recommendations)
    ? data.recommendations.filter(
        item =>
          item &&
          typeof item.what === "string" &&
          item.what.trim()
      )
    : [];
}

function RecommendationCards({ data }) {
  return (
    <div className="recommendation-results">
      <div className="recommendation-heading">
        <div>
          <strong>Evidence-aware recommendations</strong>
          <p>
            Personalized using your request and saved profile.
          </p>
        </div>

        <span className="recommendation-method">
          Structured Evidence
        </span>
      </div>

      {recommendationItems(data).map((item, index) => (
        <div
          className="recommendation-card"
          key={`${item.evidence?.source || "food"}:${item.evidence?.sourceId || index}:${index}`}
        >
          <div className="recommendation-title">
            <span className="recommendation-number">
              {index + 1}
            </span>

            <div>
              <small>RECOMMENDATION</small>
              <h3>{item.what}</h3>
            </div>
          </div>

          <div className="recommendation-section">
            <strong>Why?</strong>

            {(Array.isArray(item.why) ? item.why : [])
              .filter(
                reason =>
                  typeof reason === "string" &&
                  reason.trim()
              )
              .map((reason, i) => (
                <p key={i} className="recommendation-why">
                  ✓ {reason}
                </p>
              ))}
          </div>

          <div className="recommendation-section">
            <strong>Evidence</strong>

            <div className="recommendation-evidence">
              <EvidenceValue
                label="Protein"
                value={item.evidence?.protein}
                unit="g"
              />

              <EvidenceValue
                label="Calories"
                value={item.evidence?.calories}
                unit="kcal"
              />

              <EvidenceValue
                label="Fiber"
                value={item.evidence?.fiber}
                unit="g"
              />

              <EvidenceValue
                label="Reference"
                value={item.evidence?.servingSize}
                unit={item.evidence?.servingUnit}
              />
            </div>
          </div>

          {typeof item.reason === "string" &&
            item.reason.trim() && (
              <div className="recommendation-section">
                <strong>Reason</strong>
                <p>{item.reason}</p>
              </div>
            )}

          <div className="recommendation-source">
            {item.evidence?.verified === true &&
            item.evidence?.source &&
            item.evidence?.sourceId
              ? (
                <span>✓ Verified source</span>
              )
              : (
                <span>Source verification unavailable</span>
              )}

            <span>{item.evidence?.source}</span>

            {item.evidence?.sourceId && (
              <span>
                FDC ID: {item.evidence.sourceId}
              </span>
            )}

            {item.evidence?.dataType && (
              <span>{item.evidence.dataType}</span>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}

function EvidenceValue({ label, value, unit }) {
  const number = Number(value);

  const available =
    value != null &&
    value !== "" &&
    Number.isFinite(number);

  const shown = available
    ? Math.round(number * 100) / 100
    : "N/A";

  return (
    <div className="recommendation-evidence-value">
      <small>{label}</small>
      <strong>
        {shown} {available ? unit : ""}
      </strong>
    </div>
  );
}

function FormatText({ text = "" }) {
  return (
    <div>
      {text.split("\n").map((line, index) => {
        const value = line.trim();

        if (!value)
          return (
            <div
              className="chat-space"
              key={index}
            />
          );

        if (/^\|?[-:\s|]+\|?$/.test(value))
          return null;

        if (
          value.startsWith("|") &&
          value.endsWith("|")
        ) {
          return (
            <div
              className="chat-table-row"
              key={index}
            >
              {value
                .split("|")
                .filter(Boolean)
                .map((cell, i) => (
                  <span key={i}>
                    <BoldText text={cell.trim()} />
                  </span>
                ))}
            </div>
          );
        }

        if (
          value.startsWith("- ") ||
          value.startsWith("• ")
        ) {
          return (
            <div
              className="chat-list-line"
              key={index}
            >
              <span>•</span>
              <BoldText text={value.slice(2)} />
            </div>
          );
        }

        const step = value.match(/^(\d+)\.\s+(.*)/);

        if (step) {
          return (
            <div
              className="chat-step"
              key={index}
            >
              <span>{step[1]}</span>
              <BoldText text={step[2]} />
            </div>
          );
        }

        return (
          <div
            className="chat-line"
            key={index}
          >
            <BoldText text={value} />
          </div>
        );
      })}
    </div>
  );
}

function BoldText({ text }) {
  return text
    .split(/(\*\*.*?\*\*)/g)
    .map((part, index) =>
      part.startsWith("**") &&
      part.endsWith("**")
        ? (
          <strong key={index}>
            {part.slice(2, -2)}
          </strong>
        )
        : part
    );
}

export default Chat;