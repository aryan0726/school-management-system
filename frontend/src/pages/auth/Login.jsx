
import { useState } from "react";
import { useNavigate } from "react-router-dom";

const API_URL = "http://localhost:8080";

const roles = [
  { value: "ADMIN", label: "Administrator" },
  { value: "TEACHER", label: "Teacher" },
  { value: "GUARDIAN", label: "Guardian / Parent" },
];

function Login() {
  const navigate = useNavigate();

  const [role, setRole] = useState("ADMIN");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleLogin = async (e) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      const res = await fetch(`${API_URL}/api/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          email: email.trim(),
          password,
        }),
      });

      if (!res.ok) {
        throw new Error(
          res.status === 401
            ? "Incorrect email or password."
            : "Unable to sign in. Please try again."
        );
      }

      const data = await res.json();

      if (!data.token) {
        throw new Error("Authentication token missing.");
      }

      // Backend role is the source of truth.
      if (data.role !== role) {
        throw new Error(
          "This account does not belong to the selected role."
        );
      }

      const routes = {
        ADMIN: "/admin",
        TEACHER: "/teacher",
        GUARDIAN: "/guardian",
      };

      const destination = routes[data.role];

      if (!destination) {
        throw new Error("Unsupported account role.");
      }

      sessionStorage.setItem("token", data.token);
      sessionStorage.setItem(
        "user",
        JSON.stringify({
          id: data.id,
          fullName: data.fullName,
          email: data.email,
          role: data.role,
        })
      );

      navigate(destination, { replace: true });
    } catch (err) {
      setError(
        err instanceof TypeError
          ? "Cannot connect to server. Check Spring Boot."
          : err.message
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#f8f9fd] lg:grid lg:grid-cols-2">

      {/* LEFT PANEL */}
      <section className="relative hidden min-h-screen overflow-hidden bg-gradient-to-br from-indigo-950 via-indigo-800 to-violet-700 p-12 text-white lg:flex lg:flex-col lg:justify-between">

        {/* Decorative shapes */}
        <div className="absolute -right-36 -top-36 h-[450px] w-[450px] rounded-full bg-white/10 blur-3xl" />
        <div className="absolute -bottom-32 -left-28 h-[400px] w-[400px] rounded-full bg-purple-400/20 blur-3xl" />

        {/* Logo */}
        <div className="relative z-10 flex items-center gap-3">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-white/20 text-2xl">
            🎓
          </div>

          <div>
            <h2 className="text-xl font-bold tracking-tight">
              EduSphere
            </h2>
            <p className="text-xs tracking-widest text-indigo-200">
              SCHOOL MANAGEMENT SYSTEM
            </p>
          </div>
        </div>

        {/* Main content */}
        <div className="relative z-10 mx-auto w-full max-w-lg">

          <span className="mb-6 inline-block rounded-full border border-white/20 bg-white/10 px-4 py-2 text-sm text-indigo-100">
            ✨ Modern Education Platform
          </span>

          <h1 className="text-5xl font-bold leading-tight tracking-tight">
            Empowering
            <br />
            Education.
            <br />
            <span className="text-indigo-200">
              Simplifying
              <br />
              Management.
            </span>
          </h1>

          <p className="mt-6 max-w-md text-base leading-8 text-indigo-100/85">
            One secure platform to manage students, faculty,
            attendance, examinations and school finances.
          </p>

          {/* Glass feature card */}
          <div className="mt-12 rounded-3xl border border-white/20 bg-white/10 p-7 shadow-2xl backdrop-blur-lg">

            <div className="mb-6 flex items-center gap-4">
              <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-white/20 text-3xl">
                🏫
              </div>

              <div>
                <h3 className="text-xl font-bold">
                  Your School, Connected
                </h3>
                <p className="text-sm text-indigo-200">
                  Everything in one place
                </p>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              {[
                ["📚", "Student Records"],
                ["📅", "Attendance"],
                ["📊", "Exams & Results"],
                ["💳", "Fee Management"],
              ].map(([icon, title]) => (
                <div
                  key={title}
                  className="flex items-center gap-3 rounded-xl border border-white/10 bg-white/10 p-4"
                >
                  <span className="text-xl">{icon}</span>
                  <span className="text-sm font-medium">
                    {title}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>

        <p className="relative z-10 text-sm text-indigo-200">
          © {new Date().getFullYear()} EduSphere.
          Building smarter schools.
        </p>
      </section>

      {/* RIGHT LOGIN PANEL */}
      <main className="flex min-h-screen items-center justify-center px-5 py-12 sm:px-10">

        <div className="w-full max-w-[440px]">

          {/* Mobile logo */}
          <div className="mb-10 flex items-center gap-3 lg:hidden">
            <div className="rounded-xl bg-indigo-600 p-3 text-xl text-white">
              🎓
            </div>
            <span className="text-xl font-bold text-slate-900">
              EduSphere
            </span>
          </div>

          {/* Heading */}
          <div className="mb-9">

            <div className="mb-5 inline-flex h-14 w-14 items-center justify-center rounded-2xl bg-indigo-100 text-2xl text-indigo-700">
              🔐
            </div>

            <h2 className="text-3xl font-extrabold tracking-tight text-slate-900">
              Welcome back!
            </h2>

            <p className="mt-3 text-sm leading-6 text-slate-500">
              Sign in to access your school dashboard
              and continue where you left off.
            </p>
          </div>

          {error && (
            <div
              role="alert"
              className="mb-6 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
            >
              {error}
            </div>
          )}

          <form onSubmit={handleLogin} className="space-y-5">

            {/* ROLE */}
            <div>
              <label
                htmlFor="role"
                className="mb-2 block text-sm font-semibold text-slate-700"
              >
                Login as
              </label>

              <select
                id="role"
                value={role}
                onChange={(e) => setRole(e.target.value)}
                className="w-full rounded-xl border border-slate-200 bg-white px-4 py-3.5 text-sm text-slate-800 outline-none transition focus:border-indigo-500 focus:ring-4 focus:ring-indigo-100"
              >
                {roles.map((item) => (
                  <option key={item.value} value={item.value}>
                    {item.label}
                  </option>
                ))}
              </select>
            </div>

            {/* EMAIL */}
            <div>
              <label
                htmlFor="email"
                className="mb-2 block text-sm font-semibold text-slate-700"
              >
                Email address
              </label>

              <input
                id="email"
                type="email"
                autoComplete="username"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="Enter your school email"
                className="w-full rounded-xl border border-slate-200 bg-white px-4 py-3.5 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-indigo-500 focus:ring-4 focus:ring-indigo-100"
              />
            </div>

            {/* PASSWORD */}
            <div>
              <label
                htmlFor="password"
                className="mb-2 block text-sm font-semibold text-slate-700"
              >
                Password
              </label>

              <div className="relative">
                <input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  autoComplete="current-password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Enter your password"
                  className="w-full rounded-xl border border-slate-200 bg-white px-4 py-3.5 pr-20 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-indigo-500 focus:ring-4 focus:ring-indigo-100"
                />

                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute inset-y-0 right-4 text-sm font-semibold text-indigo-600 hover:text-indigo-800"
                >
                  {showPassword ? "Hide" : "Show"}
                </button>
              </div>
            </div>

            {/* SUBMIT */}
            <button
              type="submit"
              disabled={loading}
              className="flex w-full items-center justify-center gap-3 rounded-xl bg-gradient-to-r from-indigo-600 to-violet-600 px-5 py-4 text-sm font-semibold text-white shadow-lg shadow-indigo-200 transition hover:from-indigo-700 hover:to-violet-700 focus:outline-none focus:ring-4 focus:ring-indigo-200 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {loading ? (
                <>
                  <span className="h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white" />
                  Signing in...
                </>
              ) : (
                <>
                  Sign in to Dashboard
                  <span className="text-lg">→</span>
                </>
              )}
            </button>
          </form>

          {/* Security footer */}
          <div className="mt-8 rounded-xl border border-indigo-100 bg-indigo-50 p-4">
            <div className="flex items-start gap-3">
              <span className="text-lg">🛡️</span>
              <p className="text-xs leading-6 text-slate-600">
                <span className="font-semibold text-slate-800">
                  Secure Portal
                </span>
                <br />
                This system is intended for authorized
                school staff and guardians only.
              </p>
            </div>
          </div>

          <p className="mt-9 text-center text-xs text-slate-400">
            © {new Date().getFullYear()} EduSphere.
            All rights reserved.
          </p>
        </div>
      </main>
    </div>
  );
}

export default Login;
