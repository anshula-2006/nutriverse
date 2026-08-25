import { useState } from "react";

function Chat() {
  const [message, setMessage] = useState("");
  const [reply, setReply] = useState("");

  const sendMessage = async () => {
    if (!message.trim()) return;

    const user = JSON.parse(localStorage.getItem("user"));

    if (!user?.id) {
      setReply("Please login first.");
      return;
    }

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
            message: message
          })
        }
      );

      const data = await response.json();

      if (response.ok) {
        setReply(data.reply);
        setMessage("");
      } else {
        setReply("Something went wrong.");
      }

    } catch (error) {
      setReply("Could not connect to backend.");
    }
  };

  return (
    <div>
      <h1>NutriVerse Chat</h1>

      <input
        type="text"
        value={message}
        placeholder="Ask Nutri something..."
        onChange={(e) => setMessage(e.target.value)}
      />

      <button onClick={sendMessage}>
        Send
      </button>

      {reply && (
        <p>
          <strong>Nutri:</strong> {reply}
        </p>
      )}
    </div>
  );
}

export default Chat;