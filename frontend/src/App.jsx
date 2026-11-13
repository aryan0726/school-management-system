
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import Login from "./pages/auth/Login";
import AdminDashboard from "./pages/admin/AdminDashboard";
import Students from "./pages/admin/Students";
import Teachers from "./pages/admin/Teachers";
import Classes from "./pages/admin/Classes";
import Academic from "./pages/admin/Academic";
import Attendance from "./pages/attendance/Attendance";

function App() {
  return (
    
<BrowserRouter>
  <Routes>
    <Route path="/" element={<Navigate to="/login" replace />} />
    <Route path="/login" element={<Login />} />

    {/* Admin Routes */}
    <Route path="/admin" element={<AdminDashboard />} />
    <Route path="/admin/students" element={<Students />} />
    <Route path="/admin/teachers" element={<Teachers />} />
    <Route path="/admin/classes" element={<Classes />} />
    <Route path="/admin/academic" element={<Academic />} />
    <Route path="/admin/attendance" element={<Attendance />} />

    {/* Teacher Routes */}
    <Route path="/teacher" element={<Attendance />} />
    <Route path="/teacher/attendance" element={<Attendance />} />
  </Routes>
</BrowserRouter>

  );
}

export default App;
