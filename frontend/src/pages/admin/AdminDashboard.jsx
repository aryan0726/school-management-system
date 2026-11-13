
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import AdminSidebar from "../../components/layout/AdminSidebar";

const API_BASE = "http://localhost:8080/api";

const initialCounts = {
  students: null,
  teachers: null,
  classes: null,
  attendance: null,
  present: null,
  absent: null,
};

function AdminDashboard() {
  const navigate = useNavigate();

  const [counts, setCounts] = useState(initialCounts);
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

  useEffect(() => {
    const controller = new AbortController();

    const loadDashboard = async () => {
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

      const sources = [
        {
          key: "students",
          url: `${API_BASE}/admin/students`,
        },
        {
          key: "teachers",
          url: `${API_BASE}/admin/teachers`,
        },
        {
          key: "classes",
          url: `${API_BASE}/admin/classes`,
        },
        {
          key: "attendance",
          url: `${API_BASE}/attendance/sessions`,
        },
      ];

      const results = await Promise.allSettled(
        sources.map(async (source) => {
          const response = await fetch(source.url, {
            method: "GET",
            headers: {
              Authorization: `Bearer ${token}`,
            },
            signal: controller.signal,
          });

          if (!response.ok) {
            if (
              response.status === 401 ||
              response.status === 403
            ) {
              throw new Error(
                "Session expired or access denied. Please log in again."
              );
            }

            throw new Error(
              `Unable to load ${source.key}.`
            );
          }

          const data = await response.json();

          if (!Array.isArray(data)) {
            throw new Error(
              `Invalid ${source.key} data received.`
            );
          }

          return data;
        })
      );

      if (controller.signal.aborted) return;

      const updatedCounts = { ...initialCounts };
      const updatedErrors = {};

      results.forEach((result, index) => {
        const key = sources[index].key;

        if (result.status === "rejected") {
          if (result.reason?.name !== "AbortError") {
            updatedErrors[key] =
              result.reason?.message ||
              "Unable to load data.";
          }

          return;
        }

        const data = result.value;

        if (key !== "attendance") {
          updatedCounts[key] = data.length;
          return;
        }

        // Calculate attendance statistics
        updatedCounts.attendance = data.length;

        const records = data.flatMap((session) =>
          Array.isArray(session.records)
            ? session.records
            : []
        );

        updatedCounts.present = records.filter(
          (record) => record.status === "PRESENT"
        ).length;

        updatedCounts.absent = records.filter(
          (record) => record.status === "ABSENT"
        ).length;
      });

      setCounts(updatedCounts);
      setErrors(updatedErrors);
      setLoading(false);
    };

    loadDashboard();

    return () => controller.abort();
  }, []);

  const getValue = (key) => {
    if (loading) return "...";

    return counts[key] ?? "—";
  };

  const stats = [
    {
      title: "Total Students",
      key: "students",
      icon: "🎓",
      color: "bg-blue-100",
    },
    {
      title: "Total Teachers",
      key: "teachers",
      icon: "👨‍🏫",
      color: "bg-purple-100",
    },
    {
      title: "Total Classes",
      key: "classes",
      icon: "🏫",
      color: "bg-indigo-100",
    },
    {
      title: "Attendance Sessions",
      key: "attendance",
      icon: "📅",
      color: "bg-green-100",
    },
    {
      title: "Pending Fees",
      key: null,
      icon: "💳",
      color: "bg-orange-100",
    },
  ];

  const quickActions = [
    {
      icon: "➕",
      title: "Add Student",
      desc: "Register and manage students",
      path: "/admin/students",
      label: "Open Students",
    },
    {
      icon: "👨‍🏫",
      title: "Manage Teachers",
      desc: "Manage faculty and teacher accounts",
      path: "/admin/teachers",
      label: "Open Teachers",
    },
    {
      icon: "🏫",
      title: "Manage Classes",
      desc: "Organize classes and sections",
      path: "/admin/classes",
      label: "Open Classes",
    },
    {
      icon: "📚",
      title: "Academic Management",
      desc: "Subjects, enrollments and assignments",
      path: "/admin/academic",
      label: "Open Academics",
    },
    {
      icon: "📅",
      title: "Attendance Management",
      desc: "View daily attendance and student records",
      path: "/admin/attendance",
      label: "Open Attendance",
    },
  ];

  return (
    <div className="flex min-h-screen bg-slate-50">
      <AdminSidebar />

      <div className="min-w-0 flex-1">
        {/* Header */}
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

          {/* Statistics Cards */}
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
                  {stat.key ? getValue(stat.key) : "—"}
                </h3>

                {stat.key === "attendance" &&
                  !loading &&
                  counts.attendance !== null && (
                    <p className="mt-3 text-xs text-slate-500">
                      <span className="font-semibold text-green-700">
                        {counts.present} Present
                      </span>

                      {" · "}

                      <span className="font-semibold text-red-600">
                        {counts.absent} Absent
                      </span>
                    </p>
                  )}
              </div>
            ))}
          </div>

          {/* API Errors */}
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

                  <button
                    type="button"
                    onClick={() => navigate(action.path)}
                    className="mt-5 inline-flex items-center gap-2 text-sm font-semibold text-indigo-600 hover:text-indigo-800"
                  >
                    {action.label}
                    <span aria-hidden="true">→</span>
                  </button>
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
              once activity reporting is implemented.
            </p>
          </section>
        </main>
      </div>
    </div>
  );
}

export default AdminDashboard;
