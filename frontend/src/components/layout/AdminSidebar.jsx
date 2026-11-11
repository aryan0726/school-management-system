
import { NavLink, useNavigate } from "react-router-dom";

const menuItems = [
  { icon: "▦", label: "Dashboard", path: "/admin" },
  { icon: "🎓", label: "Students", path: "/admin/students" },
  { icon: "👨‍🏫", label: "Teachers", path: "/admin/teachers" },
  { icon: "🏫", label: "Classes", path: "/admin/classes" },
  { icon: "📅", label: "Attendance", path: "/admin/attendance" },
  { icon: "📝", label: "Exams & Results", path: "/admin/exams" },
  { icon: "💳", label: "Fee Management", path: "/admin/fees" },
  { icon: "📊", label: "Reports", path: "/admin/reports" },
];

function AdminSidebar() {
  const navigate = useNavigate();

  const handleLogout = () => {
    sessionStorage.removeItem("token");
    sessionStorage.removeItem("user");
    navigate("/login", { replace: true });
  };

  return (
    <aside className="flex h-screen w-64 shrink-0 flex-col bg-gradient-to-b from-indigo-950 via-indigo-900 to-violet-950 text-white">

      {/* Logo */}
      <div className="flex items-center gap-3 border-b border-white/10 px-6 py-6">
        <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-white/15 text-2xl">
          🎓
        </div>
        <div>
          <h1 className="text-xl font-bold">EduSphere</h1>
          <p className="text-[10px] tracking-widest text-indigo-200">
            ADMIN PORTAL
          </p>
        </div>
      </div>

      {/* Navigation */}
      <nav className="flex-1 space-y-1 overflow-y-auto px-3 py-6">
        <p className="mb-3 px-4 text-[11px] font-semibold uppercase tracking-widest text-indigo-300">
          Main Menu
        </p>

        {menuItems.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            end={item.path === "/admin"}
            className={({ isActive }) =>
              `flex items-center gap-3 rounded-xl px-4 py-3 text-sm font-medium transition ${
                isActive
                  ? "bg-white/15 text-white shadow-sm ring-1 ring-white/10"
                  : "text-indigo-200 hover:bg-white/10 hover:text-white"
              }`
            }
          >
            <span className="w-6 text-center text-lg">
              {item.icon}
            </span>
            <span>{item.label}</span>
          </NavLink>
        ))}
      </nav>

      {/* Bottom profile */}
      <div className="border-t border-white/10 p-4">
        <div className="mb-3 flex items-center gap-3 rounded-xl bg-white/10 p-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-indigo-400 font-bold">
            A
          </div>
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold">
              School Admin
            </p>
            <p className="text-xs text-indigo-200">
              Administrator
            </p>
          </div>
        </div>

        <button
          onClick={handleLogout}
          className="flex w-full items-center gap-3 rounded-xl px-4 py-3 text-sm font-medium text-indigo-200 transition hover:bg-red-500/20 hover:text-red-200"
        >
          <span className="text-lg">↪</span>
          Logout
        </button>
      </div>
    </aside>
  );
}

export default AdminSidebar;
