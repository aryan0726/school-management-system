
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import AdminSidebar from "../../components/layout/AdminSidebar";

const API_BASE = "http://localhost:8080/api/admin";

function AdminDashboard() {
  const navigate = useNavigate();

  const [counts, setCounts] = useState({
    students: null,
    teachers: null,
    classes: null,
  });

  const [loading, setLoading] = useState(true);
  const [errors, setErrors] = useState({});

  const [user] = useState(() => {
    try {
      return JSON.parse(
        sessionStorage.getItem("user") || "null"
      );
    } catch {
      return null;
    }
  });

  // Fetch dashboard statistics
  useEffect(() => {
    const controller = new AbortController();

    const fetchDashboardStats = async () => {
      setLoading(true);
      setErrors({});

      const token = sessionStorage.getItem("token");

      if (!token) {
        setErrors({
          auth: "Session missing. Please log in again.",
        });
        setLoading(false);
        return;
      }

      const endpoints = [
        "students",
        "teachers",
        "classes",
      ];

      const results = await Promise.allSettled(
        endpoints.map(async (endpoint) => {
          const response = await fetch(
            `${API_BASE}/${endpoint}`,
            {
              method: "GET",
              headers: {
                Authorization: `Bearer ${token}`,
              },
              signal: controller.signal,
            }
          );

          if (!response.ok) {
            if (
              response.status === 401 ||
              response.status === 403
            ) {
              throw new Error(
                "Session expired or access denied."
              );
            }

            throw new Error(
              `Failed to fetch ${endpoint}.`
            );
          }

          const data = await response.json();

          if (!Array.isArray(data)) {
            throw new Error(
              `Invalid ${endpoint} data received.`
            );
          }

          return data.length;
        })
      );

      if (controller.signal.aborted) return;

      const updatedCounts = {
        students: null,
        teachers: null,
        classes: null,
      };

      const updatedErrors = {};

      results.forEach((result, index) => {
        const endpoint = endpoints[index];

        if (result.status === "fulfilled") {
          updatedCounts[endpoint] = result.value;
        } else if (
          result.reason?.name !== "AbortError"
        ) {
          updatedErrors[endpoint] =
            result.reason?.message ||
            "Unable to load data.";
        }
      });

      setCounts(updatedCounts);
      setErrors(updatedErrors);
      setLoading(false);
    };

    fetchDashboardStats();

    return () => controller.abort();
  }, []);

  // Dashboard cards
  const stats = [
    {
      title: "Total Students",
      value: counts.students,
      icon: "🎓",
      color: "bg-blue-100",
    },
    {
      title: "Total Teachers",
      value: counts.teachers,
      icon: "👨‍🏫",
      color: "bg-purple-100",
    },
    {
      title: "Total Classes",
      value: counts.classes,
      icon: "🏫",
      color: "bg-indigo-100",
    },
    {
  icon: "📚",
  title: "Academic Management",
  desc: "Subjects, enrollments and teacher assignments",
  path: "/admin/academic",
  buttonLabel: "Open Academics",
  available: true,
},
    {
      title: "Attendance",
      value: null,
      icon: "📅",
      color: "bg-green-100",
    },
    {
      title: "Pending Fees",
      value: null,
      icon: "💳",
      color: "bg-orange-100",
    },
  ];

  // Dashboard quick actions
  const quickActions = [
    {
      icon: "➕",
      title: "Add Student",
      desc: "Register a new student",
      path: "/admin/students",
      buttonLabel: "Open Students",
      available: true,
    },
    {
      icon: "👨‍🏫",
      title: "Manage Teachers",
      desc: "View and manage faculty",
      path: "/admin/teachers",
      buttonLabel: "Open Teachers",
      available: true,
    },
    {
      icon: "🏫",
      title: "Manage Classes",
      desc: "Organize classes and sections",
      path: "/admin/classes",
      buttonLabel: "Open Classes",
      available: true,
    },
  ];

  const formatValue = (value) => {
    if (loading) return "...";
    return value ?? "—";
  };

  return (
    <div className="flex min-h-screen bg-slate-50">

      {/* SIDEBAR */}
      <AdminSidebar />

      <div className="min-w-0 flex-1">

        {/* HEADER */}
        <header className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-5 md:px-8">

          <div>
            <h1 className="text-2xl font-bold text-slate-900">
              Dashboard Overview
            </h1>

            <p className="mt-1 text-sm text-slate-500">
              Welcome back,{" "}
              {user?.fullName || "Administrator"}!
            </p>
          </div>

          <div className="flex h-11 w-11 items-center justify-center rounded-full bg-indigo-100 font-bold text-indigo-700">
            {user?.fullName?.charAt(0)?.toUpperCase() || "A"}
          </div>

        </header>

        {/* MAIN CONTENT */}
        <main className="p-6 md:p-8">

          {/* WELCOME BANNER */}
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

          {/* STATISTICS */}
          <div className="mt-8 grid gap-5 sm:grid-cols-2 xl:grid-cols-5">

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
                  {formatValue(stat.value)}
                </h3>

              </div>
            ))}

          </div>

          {/* API ERRORS */}
          {Object.keys(errors).length > 0 && (
            <div
              role="alert"
              className="mt-5 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700"
            >

              {Object.entries(errors).map(([key, message]) => (
                <p key={key} className="mb-1 last:mb-0">
                  <span className="font-semibold capitalize">
                    {key}:
                  </span>{" "}
                  {message}
                </p>
              ))}

            </div>
          )}

          {/* QUICK ACTIONS */}
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
                      {action.buttonLabel}
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

          {/* RECENT ACTIVITY */}
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
