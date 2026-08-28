import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import chatWelcome from "./assets/images/chat-welcome.jpg";
import "./Chat.css";

const API = "http://localhost:8080";

const suggestions = [
  ["🥗", "Plan my meals", "Help me plan my meals for today"],
  ["🍲", "Suggest 3 recipes", "Suggest 3 healthy recipes for my next meal"],
  ["💪", "Protein goal", "How can I reach my protein goal today?"],
  ["🔄", "Healthy swap", "Suggest a healthier alternative for a food I like"]
];

function Chat() {
  const navigate = useNavigate();
  const endRef = useRef(null);

  const user = JSON.parse(localStorage.getItem("user") || "{}");

  const [message, setMessage] = useState("");
  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, loading]);

  async function sendMessage(text = message) {
    text = text.trim();

    if (!text || loading) return;

    if (!user.id) {
      navigate("/login");
      return;
    }

    setMessages(prev => [
      ...prev,
      { role: "user", content: text }
    ]);

    setMessage("");
    setLoading(true);

    try {
      const response = await fetch(`${API}/api/chat`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          conversationId: user.id,
          message: text
        })
      });

      const data = await response.json();

      setMessages(prev => [
        ...prev,
        {
          role: "assistant",
          content: response.ok
            ? data.reply
            : data.message || "Something went wrong."
        }
      ]);

    } catch {
      setMessages(prev => [
        ...prev,
        {
          role: "assistant",
          content: "Could not connect to NutriVerse."
        }
      ]);

    } finally {
      setLoading(false);
    }
  }

  function handleKey(e) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      sendMessage();
    }
  }

  return (
    <div className="chat-page">

      <aside className="chat-sidebar">

        <div className="chat-brand">
          <span>🌿</span>

          <div>
            <h2>NutriVerse</h2>
            <small>Smart Nutrition</small>
          </div>
        </div>

        <nav className="chat-nav">

          <button onClick={() => navigate("/dashboard")}>
            🏠 Dashboard
          </button>

          <button disabled>🍽️ Meals</button>
          <button disabled>📷 Food Scanner</button>

          <button className="active">
            ✨ AI Assistant
          </button>

          <button disabled>📈 Progress</button>
          <button disabled>🛒 Grocery List</button>
          <button disabled>⚙️ Settings</button>

        </nav>

        <div className="chat-profile">

          <div className="chat-profile-icon">
            {user.name?.[0]?.toUpperCase() || "U"}
          </div>

          <div>
            <strong>{user.name || "User"}</strong>
            <small>NutriVerse Member</small>
          </div>

        </div>

      </aside>


      <main className="chat-main">

        <header className="chat-header">

          <div>
            <h2>Nutri AI Assistant 🌱</h2>
            <p>Your personalized nutrition companion</p>
          </div>

          <span className="chat-online">
            ● Online
          </span>

        </header>


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

                  {suggestions.map(([icon, title, prompt]) => (
                    <button
                      key={title}
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


          {messages.map((msg, index) => (
            <div
              key={index}
              className={`chat-message ${msg.role}`}
            >

              {msg.role === "assistant" && (
                <div className="chat-bot-avatar">
                  🌿
                </div>
              )}

              <div className="chat-message-content">

                <small>
                  {msg.role === "assistant" ? "Nutri" : "You"}
                </small>

                <div className="chat-bubble">

                  {msg.role === "assistant"
                    ? <FormatText text={msg.content} />
                    : msg.content}

                </div>

              </div>

            </div>
          ))}


          {loading && (
            <div className="chat-message assistant">

              <div className="chat-bot-avatar">
                🌿
              </div>

              <div className="chat-message-content">
                <small>Nutri</small>

                <div className="chat-bubble chat-typing">
                  ● ● ●
                </div>
              </div>

            </div>
          )}

          <div ref={endRef} />

        </section>


        <div className="chat-input-area">

          <textarea
            rows="1"
            placeholder="Ask Nutri anything about food or nutrition..."
            value={message}
            onChange={e => setMessage(e.target.value)}
            onKeyDown={handleKey}
            disabled={loading}
          />

          <button
            className="chat-send"
            onClick={() => sendMessage()}
            disabled={!message.trim() || loading}
          >
            ➤
          </button>

        </div>

      </main>

    </div>
  );
}


function FormatText({ text = "" }) {
  const lines = text.split("\n");

  return (
    <div>
      {lines.map((line, i) => {
        const value = line.trim();

        if (!value) {
          return <div className="chat-space" key={i} />;
        }

        // Ignore markdown table separator
        if (/^\|?[-:\s|]+\|?$/.test(value)) {
          return null;
        }

        // Markdown table row
        if (value.startsWith("|") && value.endsWith("|")) {
          const cells = value
            .split("|")
            .filter(Boolean)
            .map(cell => cell.trim());

          return (
            <div className="chat-table-row" key={i}>
              {cells.map((cell, j) => (
                <span key={j}>
                  <BoldText text={cell} />
                </span>
              ))}
            </div>
          );
        }

        // Bullet
        if (value.startsWith("- ") || value.startsWith("• ")) {
          return (
            <div className="chat-list-line" key={i}>
              <span>•</span>
              <BoldText text={value.slice(2)} />
            </div>
          );
        }

        // Numbered step
        const step = value.match(/^(\d+)\.\s+(.*)/);

        if (step) {
          return (
            <div className="chat-step" key={i}>
              <span>{step[1]}</span>
              <BoldText text={step[2]} />
            </div>
          );
        }

        return (
          <div className="chat-line" key={i}>
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
    .map((part, i) =>
      part.startsWith("**") && part.endsWith("**")
        ? <strong key={i}>{part.slice(2, -2)}</strong>
        : part
    );
}

export default Chat;