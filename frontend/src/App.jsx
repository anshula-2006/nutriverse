import { Routes, Route } from "react-router-dom";

import Home from "./Home.jsx";
import Register from "./Register.jsx";
import Login from "./Login.jsx";
import Chat from "./Chat.jsx";
import Dashboard from "./Dashboard.jsx";

function App() {
  return (
    <Routes>

      <Route path="/" element={<Home />} />

      <Route path="/register" element={<Register />} />
      <Route path="/login" element={<Login />} />

      <Route path="/chat" element={<Chat />} />
      <Route path="/dashboard" element={<Dashboard />} />

    </Routes>
  );
}

export default App;