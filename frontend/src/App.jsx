import { Routes, Route, Navigate } from "react-router-dom";

import Register from "./Register.jsx";
import Login from "./Login.jsx";

function App() {
    return (
        <Routes>
            <Route path="/" element={<Navigate to="/register" />} />
            <Route path="/register" element={<Register />} />
            <Route path="/login" element={<Login />} />
        </Routes>
    );
}

export default App;