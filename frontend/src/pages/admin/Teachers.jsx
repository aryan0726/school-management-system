
import { useEffect, useState } from "react";
import AdminSidebar from "../../components/layout/AdminSidebar";

const API_URL = "http://localhost:8080/api/admin/teachers";

const emptyForm = {
  employeeNumber: "",
  fullName: "",
  email: "",
  password: "",
  qualification: "",
  joiningDate: "",
};

export default function Teachers() {
  const [teachers, setTeachers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");

  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [formError, setFormError] = useState("");
  const [saving, setSaving] = useState(false);
  const [actionId, setActionId] = useState(null);

  const getHeaders = (json = false) => {
    const headers = {
      Authorization: `Bearer ${sessionStorage.getItem("token")}`,
    };

    if (json) {
      headers["Content-Type"] = "application/json";
    }

    return headers;
  };

  const checkResponse = async (response) => {
    if (response.ok) return response.json();

    const data = await response.json().catch(() => ({}));

    if (response.status === 401 || response.status === 403) {
      throw new Error(
        "Session expired or access denied. Please log in again."
      );
    }

    throw new Error(
      data.message || `Request failed (HTTP ${response.status})`
    );
  };

  const fetchTeachers = async () => {
    setLoading(true);
    setError("");

    try {
      const response = await fetch(API_URL, {
        headers: getHeaders(),
      });

      const data = await checkResponse(response);

      if (!Array.isArray(data)) {
        throw new Error("Invalid teacher data received.");
      }

      setTeachers(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTeachers();
  }, []);

  const openAddForm = () => {
    setEditingId(null);
    setForm({ ...emptyForm });
    setFormError("");
    setShowForm(true);
  };

  const openEditForm = (teacher) => {
    setEditingId(teacher.id);

    setForm({
      employeeNumber: teacher.employeeNumber,
      fullName: teacher.fullName,
      email: teacher.email,
      password: "",
      qualification: teacher.qualification || "",
      joiningDate: teacher.joiningDate || "",
    });

    setFormError("");
    setShowForm(true);
  };

  const closeForm = () => {
    if (saving) return;

    setShowForm(false);
    setEditingId(null);
    setForm({ ...emptyForm });
    setFormError("");
  };

  const handleChange = (e) => {
    const { name, value } = e.target;

    setForm((previous) => ({
      ...previous,
      [name]: value,
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (saving) return;

    setSaving(true);
    setFormError("");

    try {
      const payload = {
        employeeNumber: form.employeeNumber.trim(),
        fullName: form.fullName.trim(),
        email: form.email.trim().toLowerCase(),
        qualification: form.qualification.trim() || null,
        joiningDate: form.joiningDate || null,
      };

      if (!editingId) {
        payload.password = form.password;
      }

      const response = await fetch(
        editingId ? `${API_URL}/${editingId}` : API_URL,
        {
          method: editingId ? "PUT" : "POST",
          headers: getHeaders(true),
          body: JSON.stringify(payload),
        }
      );

      await checkResponse(response);

      setShowForm(false);
      setEditingId(null);
      setForm({ ...emptyForm });

      await fetchTeachers();
    } catch (err) {
      setFormError(err.message);
    } finally {
      setSaving(false);
    }
  };

  const deactivateTeacher = async (teacher) => {
    if (
      !window.confirm(
        `Deactivate ${teacher.fullName}? This will also disable their login account.`
      )
    ) {
      return;
    }

    setActionId(teacher.id);
    setError("");

    try {
      const response = await fetch(
        `${API_URL}/${teacher.id}/deactivate`,
        {
          method: "PATCH",
          headers: getHeaders(),
        }
      );

      await checkResponse(response);
      await fetchTeachers();
    } catch (err) {
      setError(err.message);
    } finally {
      setActionId(null);
    }
  };

  const filteredTeachers = teachers.filter((teacher) => {
    const query = search.trim().toLowerCase();

    return (
      teacher.fullName.toLowerCase().includes(query) ||
      teacher.employeeNumber.toLowerCase().includes(query) ||
      teacher.email.toLowerCase().includes(query)
    );
  });

  const activeCount = teachers.filter(
    (teacher) => teacher.active
  ).length;

  const stats = [
    {
      title: "Total Teachers",
      value: teachers.length,
      color: "text-indigo-700",
    },
    {
      title: "Active Teachers",
      value: activeCount,
      color: "text-green-700",
    },
    {
      title: "Inactive Teachers",
      value: teachers.length - activeCount,
      color: "text-red-600",
    },
  ];

  return (
    <div className="flex min-h-screen bg-gray-50">
      <AdminSidebar />

      <main className="min-w-0 flex-1 p-6 md:p-8">
        {/* Header */}
        <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
          <div>
            <h1 className="text-3xl font-bold text-gray-900">
              Teachers Management
            </h1>

            <p className="mt-2 text-gray-500">
              Manage faculty records and teacher accounts
            </p>
          </div>

          <button
            type="button"
            onClick={openAddForm}
            className="rounded-lg bg-indigo-600 px-5 py-3 font-semibold text-white transition hover:bg-indigo-700"
          >
            + Add Teacher
          </button>
        </div>

        {/* Statistics */}
        <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
          {stats.map((stat) => (
            <div
              key={stat.title}
              className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm"
            >
              <p className="text-sm text-gray-500">
                {stat.title}
              </p>

              <p
                className={`mt-2 text-3xl font-bold ${stat.color}`}
              >
                {loading || error ? "—" : stat.value}
              </p>
            </div>
          ))}
        </div>

        {/* Search */}
        <div className="mb-5">
          <input
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by name, employee number or email..."
            aria-label="Search teachers"
            className="w-full max-w-lg rounded-lg border border-gray-300 bg-white px-4 py-3 outline-none focus:border-indigo-500"
          />
        </div>

        {/* Error Message */}
        {error && (
          <div
            role="alert"
            className="mb-5 rounded-lg bg-red-50 p-4 text-sm text-red-700"
          >
            {error}

            <button
              type="button"
              onClick={fetchTeachers}
              className="ml-3 font-semibold underline"
            >
              Retry
            </button>
          </div>
        )}

        {/* Teachers Table */}
        <div className="overflow-x-auto rounded-xl border border-gray-200 bg-white shadow-sm">
          <table className="w-full min-w-[1100px] text-left">
            <thead className="bg-indigo-50 text-sm text-gray-700">
              <tr>
                <th className="p-4">Employee No.</th>
                <th className="p-4">Teacher Name</th>
                <th className="p-4">Email</th>
                <th className="p-4">Qualification</th>
                <th className="p-4">Joining Date</th>
                <th className="p-4">Status</th>
                <th className="p-4">Actions</th>
              </tr>
            </thead>

            <tbody>
              {loading ? (
                <tr>
                  <td
                    colSpan={7}
                    className="p-8 text-center text-gray-500"
                  >
                    Loading teachers...
                  </td>
                </tr>
              ) : error ? (
                <tr>
                  <td
                    colSpan={7}
                    className="p-8 text-center text-red-500"
                  >
                    Unable to display teachers.
                  </td>
                </tr>
              ) : filteredTeachers.length === 0 ? (
                <tr>
                  <td
                    colSpan={7}
                    className="p-8 text-center text-gray-500"
                  >
                    {search
                      ? "No matching teachers found."
                      : "No teachers found."}
                  </td>
                </tr>
              ) : (
                filteredTeachers.map((teacher) => (
                  <tr
                    key={teacher.id}
                    className="border-t border-gray-100 hover:bg-gray-50"
                  >
                    <td className="p-4">
                      {teacher.employeeNumber}
                    </td>

                    <td className="p-4 font-medium text-gray-900">
                      {teacher.fullName}
                    </td>

                    <td className="p-4">
                      {teacher.email}
                    </td>

                    <td className="p-4">
                      {teacher.qualification || "—"}
                    </td>

                    <td className="p-4">
                      {teacher.joiningDate || "—"}
                    </td>

                    <td className="p-4">
                      <span
                        className={`rounded-full px-3 py-1 text-xs font-semibold ${
                          teacher.active
                            ? "bg-green-100 text-green-700"
                            : "bg-red-100 text-red-700"
                        }`}
                      >
                        {teacher.active ? "Active" : "Inactive"}
                      </span>
                    </td>

                    <td className="p-4">
                      <div className="flex gap-2">
                        <button
                          type="button"
                          onClick={() => openEditForm(teacher)}
                          className="rounded-lg bg-indigo-100 px-3 py-2 text-sm font-medium text-indigo-700 hover:bg-indigo-200"
                        >
                          Edit
                        </button>

                        {teacher.active && (
                          <button
                            type="button"
                            onClick={() =>
                              deactivateTeacher(teacher)
                            }
                            disabled={actionId !== null}
                            className="rounded-lg bg-red-100 px-3 py-2 text-sm font-medium text-red-700 hover:bg-red-200 disabled:opacity-50"
                          >
                            {actionId === teacher.id
                              ? "Working..."
                              : "Deactivate"}
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </main>

      {/* Add / Edit Modal */}
      {showForm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="teacher-form-title"
            className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-2xl bg-white p-7 shadow-xl"
          >
            <div className="mb-6 flex items-center justify-between">
              <h2
                id="teacher-form-title"
                className="text-2xl font-bold text-gray-800"
              >
                {editingId ? "Edit Teacher" : "Add New Teacher"}
              </h2>

              <button
                type="button"
                onClick={closeForm}
                disabled={saving}
                aria-label="Close form"
                className="text-2xl text-gray-400 hover:text-gray-700 disabled:opacity-50"
              >
                ×
              </button>
            </div>

            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label
                  htmlFor="employeeNumber"
                  className="mb-1 block text-sm font-medium"
                >
                  Employee Number *
                </label>

                <input
                  id="employeeNumber"
                  name="employeeNumber"
                  value={form.employeeNumber}
                  onChange={handleChange}
                  required
                  maxLength={50}
                  placeholder="TCH002"
                  className="w-full rounded-lg border border-gray-300 p-3 outline-none focus:border-indigo-500"
                />
              </div>

              <div>
                <label
                  htmlFor="fullName"
                  className="mb-1 block text-sm font-medium"
                >
                  Full Name *
                </label>

                <input
                  id="fullName"
                  name="fullName"
                  value={form.fullName}
                  onChange={handleChange}
                  required
                  maxLength={100}
                  placeholder="Teacher full name"
                  className="w-full rounded-lg border border-gray-300 p-3 outline-none focus:border-indigo-500"
                />
              </div>

              <div>
                <label
                  htmlFor="teacherEmail"
                  className="mb-1 block text-sm font-medium"
                >
                  Email *
                </label>

                <input
                  id="teacherEmail"
                  type="email"
                  name="email"
                  value={form.email}
                  onChange={handleChange}
                  required
                  maxLength={150}
                  placeholder="teacher@example.com"
                  className="w-full rounded-lg border border-gray-300 p-3 outline-none focus:border-indigo-500"
                />
              </div>

              {!editingId && (
                <div>
                  <label
                    htmlFor="teacherPassword"
                    className="mb-1 block text-sm font-medium"
                  >
                    Login Password *
                  </label>

                  <input
                    id="teacherPassword"
                    type="password"
                    name="password"
                    value={form.password}
                    onChange={handleChange}
                    required
                    minLength={8}
                    autoComplete="new-password"
                    placeholder="Minimum 8 characters"
                    className="w-full rounded-lg border border-gray-300 p-3 outline-none focus:border-indigo-500"
                  />
                </div>
              )}

              <div>
                <label
                  htmlFor="qualification"
                  className="mb-1 block text-sm font-medium"
                >
                  Qualification
                </label>

                <input
                  id="qualification"
                  name="qualification"
                  value={form.qualification}
                  onChange={handleChange}
                  maxLength={150}
                  placeholder="M.Sc, B.Ed"
                  className="w-full rounded-lg border border-gray-300 p-3 outline-none focus:border-indigo-500"
                />
              </div>

              <div>
                <label
                  htmlFor="joiningDate"
                  className="mb-1 block text-sm font-medium"
                >
                  Joining Date
                </label>

                <input
                  id="joiningDate"
                  type="date"
                  name="joiningDate"
                  value={form.joiningDate}
                  onChange={handleChange}
                  className="w-full rounded-lg border border-gray-300 p-3 outline-none focus:border-indigo-500"
                />
              </div>

              {formError && (
                <p
                  role="alert"
                  className="text-sm text-red-600"
                >
                  {formError}
                </p>
              )}

              <div className="flex justify-end gap-3 pt-4">
                <button
                  type="button"
                  onClick={closeForm}
                  disabled={saving}
                  className="rounded-lg border border-gray-300 px-5 py-2.5 hover:bg-gray-50 disabled:opacity-50"
                >
                  Cancel
                </button>

                <button
                  type="submit"
                  disabled={saving}
                  className="rounded-lg bg-indigo-600 px-5 py-2.5 font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
                >
                  {saving
                    ? "Saving..."
                    : editingId
                    ? "Update Teacher"
                    : "Save Teacher"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
