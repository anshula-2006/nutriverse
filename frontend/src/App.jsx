import { Navigate, Route, Routes } from "react-router-dom";
import Home from "./Home.jsx";
import Login from "./Login.jsx";
import Register from "./Register.jsx";
import Dashboard from "./Dashboard.jsx";
import Chat from "./Chat.jsx";
import Profile from "./Profile.jsx";
import FoodScan from "./FoodScan.jsx";
import InfoPage from "./InfoPage.jsx";

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/dashboard" element={<Dashboard />} />
      <Route path="/chat" element={<Chat />} />
      <Route path="/food-scan" element={<FoodScan />} />
      <Route path="/profile" element={<Profile />} />
      <Route path="/:page" element={<InfoPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
