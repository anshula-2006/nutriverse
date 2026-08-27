import { useEffect, useRef, useState } from "react";

function Chat() {
  const [message, setMessage] = useState("");
  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(false);

  const messagesEndRef = useRef(null);

  const user = JSON.parse(localStorage.getItem("user"));

  // Auto-scroll whenever a new message appears
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({
      behavior: "smooth"
    });
  }, [messages, loading]);


  const sendMessage = async () => {
    if (!message.trim() || loading) return;

    if (!user?.id) {
      setMessages((prev) => [
        ...prev,
        {
          role: "assistant",
          content: "Please login first."
        }
      ]);

      return;
    }

    const userMessage = message.trim();

    // Show user's message immediately
    setMessages((prev) => [
      ...prev,
      {
        role: "user",
        content: userMessage
      }
    ]);

    setMessage("");
    setLoading(true);

    try {
      const response = await fetch(
        "http://localhost:8080/api/chat",
        {
          method: "POST",

          headers: {
            "Content-Type": "application/json"
          },

          body: JSON.stringify({
            conversationId: user.id,
            message: userMessage
          })
        }
      );

      const data = await response.json();

      if (response.ok) {
        // Show Nutri reply
        setMessages((prev) => [
          ...prev,
          {
            role: "assistant",
            content: data.reply
          }
        ]);
      } else {
        setMessages((prev) => [
          ...prev,
          {
            role: "assistant",
            content:
              data.message ||
              "Something went wrong."
          }
        ]);
      }

    } catch (error) {
      console.error(error);

      setMessages((prev) => [
        ...prev,
        {
          role: "assistant",
          content:
            "Could not connect to the backend."
        }
      ]);

    } finally {
      setLoading(false);
    }
  };


  const handleKeyDown = (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      sendMessage();
    }
  };


  return (
    <div style={styles.page}>

      <div style={styles.chatContainer}>

        {/* HEADER */}
        <div style={styles.header}>

          <div style={styles.botInfo}>

            <div style={styles.avatar}>
              🥗
            </div>

            <div>
              <h2 style={styles.botName}>
                Nutri
              </h2>

              <p style={styles.subtitle}>
                Your AI Nutrition Companion
              </p>
            </div>

          </div>


          <div style={styles.online}>
            <span style={styles.onlineDot}></span>
            Online
          </div>

        </div>


        {/* CHAT MESSAGES */}
        <div style={styles.messagesArea}>

          {messages.length === 0 && (
            <div style={styles.welcome}>

              <div style={styles.welcomeIcon}>
                🥗
              </div>

              <h2>
                Hey {user?.name || "there"} 👋
              </h2>

              <p style={styles.welcomeText}>
                I'm Nutri, your personal AI
                nutrition companion.
              </p>

              <p style={styles.welcomeText}>
                What would you like help with
                today?
              </p>

            </div>
          )}


          {messages.map((msg, index) => (

            <div
              key={index}
              style={{
                ...styles.messageRow,

                justifyContent:
                  msg.role === "user"
                    ? "flex-end"
                    : "flex-start"
              }}
            >

              <div
                style={{
                  ...styles.messageBubble,

                  ...(msg.role === "user"
                    ? styles.userBubble
                    : styles.botBubble)
                }}
              >

                <div style={styles.sender}>

                  {msg.role === "user"
                    ? "You"
                    : "Nutri"}

                </div>

                <div style={styles.messageText}>
                  {msg.content}
                </div>

              </div>

            </div>
          ))}


          {/* LOADING BUBBLE */}
          {loading && (

            <div
              style={{
                ...styles.messageRow,
                justifyContent: "flex-start"
              }}
            >

              <div
                style={{
                  ...styles.messageBubble,
                  ...styles.botBubble
                }}
              >

                <div style={styles.sender}>
                  Nutri
                </div>

                <div style={styles.thinking}>
                  <span>●</span>
                  <span>●</span>
                  <span>●</span>
                </div>

              </div>

            </div>
          )}


          <div ref={messagesEndRef} />

        </div>


        {/* INPUT AREA */}
        <div style={styles.inputArea}>

          <input
            type="text"
            placeholder="Ask Nutri something..."
            value={message}
            onChange={(e) =>
              setMessage(e.target.value)
            }
            onKeyDown={handleKeyDown}
            disabled={loading}
            style={styles.input}
          />


          <button
            onClick={sendMessage}
            disabled={
              loading || !message.trim()
            }
            style={{
              ...styles.sendButton,

              opacity:
                loading || !message.trim()
                  ? 0.55
                  : 1,

              cursor:
                loading || !message.trim()
                  ? "not-allowed"
                  : "pointer"
            }}
          >
            ➤
          </button>

        </div>

      </div>

    </div>
  );
}


/* =========================================================
   STYLES
   ========================================================= */

const styles = {

  page: {
    minHeight: "100vh",
    width: "100%",
    background:
      "linear-gradient(135deg, #eef7ef, #f7faf7)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    padding: "20px",
    boxSizing: "border-box",
    fontFamily:
      "'Segoe UI', Arial, sans-serif"
  },


  chatContainer: {
    width: "100%",
    maxWidth: "950px",
    height: "88vh",
    background: "#ffffff",
    borderRadius: "22px",
    overflow: "hidden",
    display: "flex",
    flexDirection: "column",
    boxShadow:
      "0 18px 45px rgba(24, 70, 35, 0.14)"
  },


  header: {
    padding: "17px 24px",
    background:
      "linear-gradient(135deg, #174d2c, #2e7d45)",
    color: "white",
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between"
  },


  botInfo: {
    display: "flex",
    alignItems: "center",
    gap: "12px"
  },


  avatar: {
    width: "46px",
    height: "46px",
    background:
      "rgba(255,255,255,0.17)",
    borderRadius: "50%",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    fontSize: "23px"
  },


  botName: {
    margin: 0,
    fontSize: "21px",
    fontWeight: "650"
  },


  subtitle: {
    margin: "3px 0 0",
    fontSize: "12px",
    opacity: 0.78
  },


  online: {
    display: "flex",
    alignItems: "center",
    gap: "7px",
    fontSize: "13px",
    opacity: 0.9
  },


  onlineDot: {
    width: "8px",
    height: "8px",
    background: "#8ef0a5",
    borderRadius: "50%"
  },


  messagesArea: {
    flex: 1,
    overflowY: "auto",
    padding: "25px",
    background: "#f8faf8"
  },


  welcome: {
    textAlign: "center",
    maxWidth: "420px",
    margin: "100px auto 0",
    color: "#31463a"
  },


  welcomeIcon: {
    fontSize: "42px",
    marginBottom: "12px"
  },


  welcomeText: {
    margin: "7px 0",
    color: "#6b7b70",
    fontSize: "14px",
    lineHeight: "1.6"
  },


  messageRow: {
    width: "100%",
    display: "flex",
    marginBottom: "17px"
  },


  messageBubble: {
    maxWidth: "70%",
    padding: "11px 15px",
    borderRadius: "18px",
    fontSize: "14px",
    lineHeight: "1.55"
  },


  userBubble: {
    background:
      "linear-gradient(135deg, #2e7d45, #388e52)",
    color: "white",
    borderBottomRightRadius: "5px"
  },


  botBubble: {
    background: "#ffffff",
    color: "#26372d",
    border: "1px solid #e2e9e3",
    borderBottomLeftRadius: "5px",
    boxShadow:
      "0 2px 8px rgba(0,0,0,0.04)"
  },


  sender: {
    fontSize: "11px",
    fontWeight: "700",
    marginBottom: "4px",
    opacity: 0.72
  },


  messageText: {
    whiteSpace: "pre-wrap",
    wordBreak: "break-word"
  },


  thinking: {
    display: "flex",
    gap: "4px",
    fontSize: "8px",
    opacity: 0.55,
    padding: "3px 0"
  },


  inputArea: {
    display: "flex",
    alignItems: "center",
    gap: "10px",
    padding: "15px 20px",
    borderTop: "1px solid #e6ebe7",
    background: "#ffffff"
  },


  input: {
    flex: 1,
    padding: "13px 18px",
    borderRadius: "25px",
    border: "1px solid #cad7cd",
    background: "#f8faf8",
    fontSize: "14px",
    outline: "none",
    boxSizing: "border-box"
  },


  sendButton: {
    width: "47px",
    height: "47px",
    flexShrink: 0,
    border: "none",
    borderRadius: "50%",
    background:
      "linear-gradient(135deg, #246b39, #3c9858)",
    color: "white",
    fontSize: "19px"
  }
};


export default Chat;