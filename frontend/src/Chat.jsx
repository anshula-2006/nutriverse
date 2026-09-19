import AppNav from "./AppNav.jsx";
import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";

import chatWelcome from "./assets/images/chat-welcome.jpg";
import "./Chat.css";
import { API_URL, readUser, handleUnauthorized } from "./api.js";

const TEMPORARY_ERROR_MESSAGE =
  "Nutri is having trouble responding right now. Please try again in a moment.";

function getRetryAfterSeconds(retryAfterHeader) {

  if (!retryAfterHeader) {
    return null;
  }

  const delayInSeconds =
    Number(retryAfterHeader);

  if (
    Number.isFinite(delayInSeconds) &&
    delayInSeconds >= 0
  ) {
    return Math.ceil(delayInSeconds);
  }

  const retryTime =
    Date.parse(retryAfterHeader);

  if (Number.isNaN(retryTime)) {
    return null;
  }

  return Math.max(
    0,
    Math.ceil((retryTime - Date.now()) / 1000)
  );
}

function getFriendlyErrorMessage(response) {

  if (response.status === 429) {

    const retryAfterSeconds =
      getRetryAfterSeconds(
        response.headers.get("Retry-After")
      );

    const rateLimitMessage =
      "Nutri is receiving a lot of requests right now. Please try again in a few seconds.";

    if (retryAfterSeconds > 0) {
      const unit =
        retryAfterSeconds === 1
          ? "second"
          : "seconds";

      return `${rateLimitMessage} You can try again in about ${retryAfterSeconds} ${unit}.`;
    }

    return rateLimitMessage;
  }

  if (response.status === 403) {
    return "Nutri is not available for this account right now. Please contact support if you believe this is a mistake.";
  }

  return TEMPORARY_ERROR_MESSAGE;
}

const suggestions = [
  [
    "🥗",
    "Plan my meals",
    "Help me plan my meals for today"
  ],
  [
    "🍲",
    "Suggest 3 recipes",
    "Suggest 3 healthy recipes for my next meal"
  ],
  [
    "💪",
    "Protein goal",
    "How can I reach my protein goal today?"
  ],
  [
    "🔄",
    "Healthy swap",
    "Suggest a healthier alternative for a food I like"
  ]
];

function Chat() {

  const navigate = useNavigate();
  const location = useLocation();
  const [foodContext, setFoodContext] = useState(location.state?.food || null);
  const endRef = useRef(null);
  const isSendingRef = useRef(false);
  const thinkingTimerRef = useRef(null);

  const user = readUser();

  const token = localStorage.getItem("token");

  const [message, setMessage] = useState("");
  const [messages, setMessages] = useState([]);
  const [isSending, setIsSending] = useState(false);
  const [typingText, setTypingText] =
    useState("Nutri is typing...");


  // =========================================================
  // AUTH CHECK
  // =========================================================

  useEffect(() => {

    if (!token) {
      navigate("/login");
    }

  }, [navigate, token]);


  // =========================================================
  // AUTO SCROLL
  // =========================================================

  useEffect(() => {

    endRef.current?.scrollIntoView({
      behavior: "smooth"
    });

  }, [messages, isSending]);


  // =========================================================
  // TIMER CLEANUP
  // =========================================================

  useEffect(() => {

    return () => {
      window.clearTimeout(
        thinkingTimerRef.current
      );
    };

  }, []);


  // =========================================================
  // HANDLE EXPIRED / INVALID JWT
  // =========================================================

  // =========================================================
  // SEND MESSAGE
  // =========================================================

  async function sendMessage(text = message) {

    const userMessage = text.trim();

    if (
      !userMessage ||
      isSendingRef.current
    ) {
      return;
    }

    if (!token) {
      navigate("/login");
      return;
    }

    isSendingRef.current = true;

    setMessages(previous => [
      ...previous,
      {
        role: "user",
        content: userMessage
      }
    ]);

    setMessage("");
    setIsSending(true);
    setTypingText("Nutri is typing...");

    thinkingTimerRef.current =
      window.setTimeout(() => {
        setTypingText("Nutri is thinking...");
      }, 5000);


    try {

      const response = await fetch(
        `${API_URL}/api/chat`,
        {
          method: "POST",

          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${token}`
          },

          body: JSON.stringify({
            message: userMessage,
            ...(foodContext ? { source: foodContext.source, sourceId: foodContext.sourceId } : {})
          })
        }
      );


      if (handleUnauthorized(response, navigate)) {
        return;
      }


      if (!response.ok) {
        setMessages(previous => [
          ...previous,
          {
            role: "assistant",
            content:
              getFriendlyErrorMessage(response)
          }
        ]);

        return;
      }


      const data =
        await response.json();


      setMessages(previous => [
        ...previous,
        {
          role: "assistant",
          content:
            (typeof data?.reply === "string" && data.reply) ||
            "I couldn't generate a response."
        }
      ]);


    } catch {
      setMessages(previous => [
        ...previous,
        {
          role: "assistant",
          content: TEMPORARY_ERROR_MESSAGE
        }
      ]);


    } finally {
      window.clearTimeout(
        thinkingTimerRef.current
      );

      thinkingTimerRef.current = null;
      isSendingRef.current = false;
      setIsSending(false);
    }
  }


  // =========================================================
  // ENTER KEY
  // =========================================================

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


  // =========================================================
  // UI
  // =========================================================

  return (

    <div className="chat-page">


      {/* SIDEBAR */}

      <AppNav />


      {/* MAIN */}

      <main className="chat-main">


        {/* HEADER */}

        <header className="chat-header">


          <div>

            <h2>
              Nutri AI Assistant 🌱
            </h2>

            <p>
              Your personalized nutrition companion
            </p>

          </div>


          <span className="chat-online">
            ● Online
          </span>


        </header>


        {foodContext && (
          <div className="chat-food-context">
            <span>Selected food: {foodContext.foodName}. Nutrition facts will be fetched from its source.</span>
            <button type="button" onClick={() => sendMessage("How much protein does this food contain?")} disabled={isSending}>Ask about protein</button>
            <button type="button" onClick={() => setFoodContext(null)} disabled={isSending}>Clear food</button>
          </div>
        )}

        {/* MESSAGES */}

        <section className="chat-messages">


          {messages.length === 0 && (

            <div className="chat-welcome">


              <div className="chat-welcome-text">


                <span className="chat-label">
                  YOUR NUTRITION COMPANION
                </span>


                <h1>
                  Hey {user.name || "there"} 👋
                </h1>


                <p>
                  What would you like to work on today?
                </p>


                <div className="chat-suggestions">


                  {suggestions.map(
                    ([icon, title, prompt]) => (

                      <button

                        key={title}

                        onClick={() =>
                          sendMessage(prompt)
                        }

                      >

                        <span>
                          {icon}
                        </span>

                        {title}

                      </button>

                    )
                  )}


                </div>


              </div>


              <img
                src={chatWelcome}
                alt="Healthy balanced meal"
              />


            </div>

          )}


          {messages.map(
            (chatMessage, index) => (

              <div

                key={index}

                className={
                  `chat-message ${chatMessage.role}`
                }

              >


                {chatMessage.role ===
                  "assistant" && (

                  <div className="chat-bot-avatar">
                    🌿
                  </div>

                )}


                <div className="chat-message-content">


                  <small>

                    {chatMessage.role ===
                    "assistant"
                      ? "Nutri"
                      : "You"}

                  </small>


                  <div className="chat-bubble">


                    {chatMessage.role ===
                    "assistant"
                      ? (
                        <FormatText
                          text={
                            chatMessage.content
                          }
                        />
                      )
                      : chatMessage.content
                    }


                  </div>


                </div>


              </div>

            )
          )}


          {/* TYPING */}

          {isSending && (

            <div className="chat-message assistant">


              <div className="chat-bot-avatar">
                🌿
              </div>


              <div className="chat-message-content">

                <small>
                  Nutri
                </small>

                <div
                  className="chat-bubble chat-typing"
                  role="status"
                  aria-live="polite"
                >
                  <span>
                    {typingText}
                  </span>

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


        {/* INPUT */}

        <div className="chat-input-area">


          <textarea
            aria-label="Message to Nutri"

            rows="1"

            placeholder=
              "Ask Nutri anything about food or nutrition..."

            value={message}

            onChange={event =>
              setMessage(
                event.target.value
              )
            }

            onKeyDown={handleKey}

            disabled={isSending}

          />


          <button

            className="chat-send"
            aria-label="Send message"

            onClick={() =>
              sendMessage()
            }

            disabled={
              !message.trim() ||
              isSending
            }

          >
            ➤
          </button>


        </div>


      </main>


    </div>
  );
}


// =========================================================
// FORMAT AI TEXT
// =========================================================

function FormatText({
  text = ""
}) {

  const lines =
    text.split("\n");


  return (

    <div>

      {lines.map(
        (line, index) => {

          const value =
            line.trim();


          if (!value) {

            return (
              <div
                className="chat-space"
                key={index}
              />
            );
          }


          // Ignore markdown table separator

          if (
            /^\|?[-:\s|]+\|?$/.test(
              value
            )
          ) {
            return null;
          }


          // Markdown table row

          if (
            value.startsWith("|") &&
            value.endsWith("|")
          ) {

            const cells = value
              .split("|")
              .filter(Boolean)
              .map(
                cell =>
                  cell.trim()
              );


            return (

              <div
                className="chat-table-row"
                key={index}
              >

                {cells.map(
                  (cell, cellIndex) => (

                    <span
                      key={cellIndex}
                    >

                      <BoldText
                        text={cell}
                      />

                    </span>

                  )
                )}

              </div>
            );
          }


          // Bullet

          if (
            value.startsWith("- ") ||
            value.startsWith("• ")
          ) {

            return (

              <div
                className="chat-list-line"
                key={index}
              >

                <span>
                  •
                </span>

                <BoldText
                  text={
                    value.slice(2)
                  }
                />

              </div>
            );
          }


          // Numbered step

          const step =
            value.match(
              /^(\d+)\.\s+(.*)/
            );


          if (step) {

            return (

              <div
                className="chat-step"
                key={index}
              >

                <span>
                  {step[1]}
                </span>

                <BoldText
                  text={step[2]}
                />

              </div>
            );
          }


          return (

            <div
              className="chat-line"
              key={index}
            >

              <BoldText
                text={value}
              />

            </div>
          );
        }
      )}

    </div>
  );
}


// =========================================================
// BOLD MARKDOWN
// =========================================================

function BoldText({
  text
}) {

  return text
    .split(
      /(\*\*.*?\*\*)/g
    )
    .map(
      (part, index) =>

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
