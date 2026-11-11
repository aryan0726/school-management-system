
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import AdminSidebar from "../../components/layout/AdminSidebar";

const STUDENTS_API = "http://localhost:8080/api/admin/students";

function AdminDashboard() {
  const navigate = useNavigate();

  const [totalStudents, setTotalStudents] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [user] = useState(() => {
    try {
      return JSON.parse(sessionStorage.getItem("user") || "null");
    } catch {
      return null;
    }
  });

  useEffect(() => {
    const controller = new AbortController();

    const fetchStudentCount = async () => {
      try {
        setLoading(true);
        setError("");

        const token = sessionStorage.getItem("token");

        if (!token) {
          throw new Error("Session missing. Please log in again.");
        }

        const response = await fetch(STUDENTS_API, {
          method: "GET",
          headers: {
            Authorization: `Bearer ${token}`,
          },
          signal: controller.signal,
        });

        if (!response.ok) {
          if (response.status === 401 ||
              response.status === 403) {
            throw new Error(
              "Session expired or access denied. Please log in again."
            );
          }

          throw new Error("Unable to fetch student count.");
        }

        const students = await response.json();

        if (!Array.isArray(students)) {
          throw new Error("Invalid student data received.");
        }

        setTotalStudents(students.length);
      } catch (err) {
        if (err.name !== "AbortError") {
          setTotalStudents(null);
          setError(err.message);
        }
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      }
    };

    fetchStudentCount();

    return () => controller.abort();
  }, []);

  const stats = [
    {
      title: "Total Students",
      value: loading ? "..." : totalStudents ?? "—",
      icon: "🎓",
      color: "bg-blue-100",
    },
    {
      title: "Total Teachers",
      value: "—",
      icon: "👨‍🏫",
      color: "bg-purple-100",
    },
    {
      title: "Attendance",
      value: "—",
      icon: "📅",
      color: "bg-green-100",
    },
    {
      title: "Pending Fees",
      value: "—",
      icon: "💳",
      color: "bg-orange-100",
    },
  ];

  const quickActions = [
    {
      icon: "➕",
      title: "Add Student",
      desc: "Register a new student",
      path: "/admin/students",
      available: true,
    },
    {
      icon: "👨‍🏫",
      title: "Manage Teachers",
      desc: "View and manage faculty",
      available: false,
    },
    {
      icon: "🏫",
      title: "Manage Classes",
      desc: "Organize classes and sections",
      available: false,
    },
  ];

  return (
    <div className="flex min-h-screen bg-slate-50">
      {/* Sidebar */}
      <AdminSidebar />

      <div className="min-w-0 flex-1">
        {/* Top Navbar */}
        <header className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-5 md:px-8">
          <div>
            <h1 className="text-2xl font-bold text-slate-900">
              Dashboard Overview
            </h1>

            <p className="mt-1 text-sm text-slate-500">
              Welcome back, {user?.fullName || "Administrator"}!
            </p>
          </div>

          <div className="flex h-11 w-11 items-center justify-center rounded-full bg-indigo-100 font-bold text-indigo-700">
            {user?.fullName?.charAt(0)?.toUpperCase() || "A"}
          </div>
        </header>

        {/* Main Dashboard */}
        <main className="p-6 md:p-8">
          {/* Welcome Banner */}
          <div className="rounded-2xl bg-gradient-to-r from-indigo-700 to-violet-600 p-8 text-white shadow-lg">
            <h2 className="text-2xl font-bold">
              Welcome to EduSphere 🎓
            </h2>

            <p className="mt-2 max-w-xl text-sm leading-6 text-indigo-100">
              Manage your school's students, teachers,
              academic activities and financial records
              from one centralized dashboard.
            </p>
          </div>

          {/* Statistics */}
          <div className="mt-8 grid gap-5 sm:grid-cols-2 xl:grid-cols-4">
            {stats.map((stat) => (
              <div
                key={stat.title}
                className="rounded-2xl border border-slate-100 bg-white p-6 shadow-sm"
              >
                <div
                  className={`mb-5 flex h-12 w-12 items-center justify-center rounded-xl text-2xl ${stat.color}`}
                >
                  {stat.icon}
                </div>

                <p className="text-sm font-medium text-slate-500">
                  {stat.title}
                </p>

                <h3 className="mt-2 text-3xl font-bold text-slate-900">
                  {stat.value}
                </h3>
              </div>
            ))}
          </div>

          {/* API Error */}
          {error && (
            <div
              role="alert"
              className="mt-5 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700"
            >
              {error}
            </div>
          )}

          {/* Quick Actions */}
          <section className="mt-10">
            <h2 className="mb-5 text-xl font-bold text-slate-900">
              Quick Actions
            </h2>

            <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {quickActions.map((action) => (
                <div
                  key={action.title}
                  className="rounded-2xl border border-slate-100 bg-white p-6 shadow-sm"
                >
                  <div className="mb-4 text-3xl">
                    {action.icon}
                  </div>

                  <h3 className="font-bold text-slate-900">
                    {action.title}
                  </h3>

                  <p className="mt-2 text-sm text-slate-500">
                    {action.desc}
                  </p>

                  {action.available ? (
                    <button
                      type="button"
                      onClick={() => navigate(action.path)}
                      className="mt-5 inline-flex items-center gap-2 text-sm font-semibold text-indigo-600 hover:text-indigo-800"
                    >
                      Open Students
                      <span aria-hidden="true">→</span>
                    </button>
                  ) : (
                    <span className="mt-5 inline-block text-sm font-semibold text-slate-400">
                      Coming soon
                    </span>
                  )}
                </div>
              ))}
            </div>
          </section>

          {/* Recent Activity */}
          <section className="mt-10 rounded-2xl border border-slate-100 bg-white p-6 shadow-sm">
            <h2 className="text-xl font-bold text-slate-900">
              Recent Activity
            </h2>

            <p className="mt-4 text-sm text-slate-500">
              Recent school activities will appear here
              once the backend APIs are connected.
            </p>
          </section>
        </main>
      </div>
    </div>
  );
}

export default AdminDashboard;
