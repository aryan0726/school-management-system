
import { useEffect, useState } from "react";
import AdminSidebar from "../../components/layout/AdminSidebar";

const API_URL = "http://localhost:8080/api/admin/students";

const emptyForm = {
  admissionNumber: "",
  fullName: "",
  dateOfBirth: "",
  gender: "",
};

export default function Students() {
  const [students, setStudents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");

  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState("");
  const [actionId, setActionId] = useState(null);

  const getHeaders = () => ({
    Authorization: `Bearer ${sessionStorage.getItem("token")}`,
    "Content-Type": "application/json",
  });

  const fetchStudents = async () => {
    setLoading(true);
    setError("");

    try {
      const response = await fetch(API_URL, {
        headers: getHeaders(),
      });

      if (!response.ok) {
        throw new Error(
          response.status === 401 || response.status === 403
            ? "Session expired or access denied. Please log in again."
            : "Failed to load students."
        );
      }

      setStudents(await response.json());
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchStudents();
  }, []);

  const openAddForm = () => {
    setEditingId(null);
    setForm(emptyForm);
    setFormError("");
    setShowForm(true);
  };

  const openEditForm = (student) => {
    setEditingId(student.id);
    setForm({
      admissionNumber: student.admissionNumber,
      fullName: student.fullName,
      dateOfBirth: student.dateOfBirth || "",
      gender: student.gender || "",
    });
    setFormError("");
    setShowForm(true);
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    setForm((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSaving(true);
    setFormError("");

    try {
      const url = editingId
        ? `${API_URL}/${editingId}`
        : API_URL;

      const response = await fetch(url, {
        method: editingId ? "PUT" : "POST",
        headers: getHeaders(),
        body: JSON.stringify({
          admissionNumber: form.admissionNumber.trim(),
          fullName: form.fullName.trim(),
          dateOfBirth: form.dateOfBirth || null,
          gender: form.gender || null,
        }),
      });

      if (!response.ok) {
        const data = await response.json().catch(() => ({}));

        throw new Error(
          response.status === 401 || response.status === 403
            ? "Session expired or access denied. Please log in again."
            : data.message ||
                `Unable to save student (HTTP ${response.status})`
        );
      }

      setShowForm(false);
      setEditingId(null);
      setForm(emptyForm);
      await fetchStudents();
    } catch (err) {
      setFormError(err.message);
    } finally {
      setSaving(false);
    }
  };

  const deactivateStudent = async (student) => {
    if (!window.confirm(
      `Deactivate ${student.fullName}? Their record will be kept.`
    )) {
      return;
    }

    setActionId(student.id);
    setError("");

    try {
      const response = await fetch(
        `${API_URL}/${student.id}/deactivate`,
        {
          method: "PATCH",
          headers: getHeaders(),
        }
      );

      if (!response.ok) {
        throw new Error(
          response.status === 401 || response.status === 403
            ? "Session expired or access denied. Please log in again."
            : "Failed to deactivate student."
        );
      }

      await fetchStudents();
    } catch (err) {
      setError(err.message);
    } finally {
      setActionId(null);
    }
  };

  const filteredStudents = students.filter((student) => {
    const query = search.toLowerCase().trim();

    return (
      student.fullName.toLowerCase().includes(query) ||
      student.admissionNumber.toLowerCase().includes(query)
    );
  });

  const activeCount = students.filter((s) => s.active).length;

  return (
    <div className="flex min-h-screen bg-gray-50">
      <AdminSidebar />

      <main className="min-w-0 flex-1 p-6 md:p-8">
        {/* Header */}
        <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
          <div>
            <h1 className="text-3xl font-bold text-gray-900">
              Students Management
            </h1>
            <p className="mt-2 text-gray-500">
              Manage student records and admissions
            </p>
          </div>

          <button
            onClick={openAddForm}
            className="rounded-lg bg-indigo-600 px-5 py-3 font-semibold text-white transition hover:bg-indigo-700"
          >
            + Add Student
          </button>
        </div>

        {/* Summary Cards */}
        <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
          {[
            {
              title: "Total Students",
              value: students.length,
              color: "text-indigo-700",
            },
            {
              title: "Active Students",
              value: activeCount,
              color: "text-green-700",
            },
            {
              title: "Inactive Students",
              value: students.length - activeCount,
              color: "text-red-600",
            },
          ].map((item) => (
            <div
              key={item.title}
              className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm"
            >
              <p className="text-sm text-gray-500">{item.title}</p>
              <p className={`mt-2 text-3xl font-bold ${item.color}`}>
                {loading || error ? "—" : item.value}
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
            placeholder="Search by student name or admission number..."
            className="w-full max-w-md rounded-lg border border-gray-300 bg-white px-4 py-3 outline-none focus:border-indigo-500"
          />
        </div>

        {error && (
          <div
            role="alert"
            className="mb-4 rounded-lg bg-red-50 p-4 text-sm text-red-700"
          >
            {error}
            <button
              onClick={fetchStudents}
              className="ml-3 font-semibold underline"
            >
              Retry
            </button>
          </div>
        )}

        {/* Students Table */}
        <div className="overflow-x-auto rounded-xl border border-gray-200 bg-white shadow-sm">
          <table className="w-full min-w-[900px] text-left">
            <thead className="bg-indigo-50 text-sm text-gray-700">
              <tr>
                <th className="p-4">Admission No.</th>
                <th className="p-4">Student Name</th>
                <th className="p-4">Date of Birth</th>
                <th className="p-4">Gender</th>
                <th className="p-4">Status</th>
                <th className="p-4">Actions</th>
              </tr>
            </thead>

            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={6} className="p-8 text-center text-gray-500">
                    Loading students...
                  </td>
                </tr>
              ) : error ? (
                <tr>
                  <td colSpan={6} className="p-8 text-center text-red-500">
                    Unable to display students.
                  </td>
                </tr>
              ) : filteredStudents.length === 0 ? (
                <tr>
                  <td colSpan={6} className="p-8 text-center text-gray-500">
                    {search
                      ? "No matching students found."
                      : "No students found."}
                  </td>
                </tr>
              ) : (
                filteredStudents.map((student) => (
                  <tr
                    key={student.id}
                    className="border-t border-gray-100 hover:bg-gray-50"
                  >
                    <td className="p-4">
                      {student.admissionNumber}
                    </td>

                    <td className="p-4 font-medium text-gray-900">
                      {student.fullName}
                    </td>

                    <td className="p-4">
                      {student.dateOfBirth || "—"}
                    </td>

                    <td className="p-4">
                      {student.gender || "—"}
                    </td>

                    <td className="p-4">
                      <span
                        className={`rounded-full px-3 py-1 text-xs font-semibold ${
                          student.active
                            ? "bg-green-100 text-green-700"
                            : "bg-red-100 text-red-700"
                        }`}
                      >
                        {student.active ? "Active" : "Inactive"}
                      </span>
                    </td>

                    <td className="p-4">
                      <div className="flex gap-2">
                        <button
                          onClick={() => openEditForm(student)}
                          className="rounded-lg bg-indigo-100 px-3 py-2 text-sm font-medium text-indigo-700 hover:bg-indigo-200"
                        >
                          Edit
                        </button>

                        {student.active && (
                          <button
                            onClick={() => deactivateStudent(student)}
                            disabled={actionId === student.id}
                            className="rounded-lg bg-red-100 px-3 py-2 text-sm font-medium text-red-700 hover:bg-red-200 disabled:opacity-50"
                          >
                            {actionId === student.id
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
          <div className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-2xl bg-white p-7 shadow-xl">
            <div className="mb-6 flex items-center justify-between">
              <h2 className="text-2xl font-bold text-gray-800">
                {editingId ? "Edit Student" : "Add New Student"}
              </h2>

              <button
                type="button"
                onClick={() => setShowForm(false)}
                disabled={saving}
                aria-label="Close form"
                className="text-2xl text-gray-400 hover:text-gray-700 disabled:opacity-50"
              >
                ×
              </button>
            </div>

            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="mb-1 block text-sm font-medium">
                  Admission Number *
                </label>
                <input
                  name="admissionNumber"
                  value={form.admissionNumber}
                  onChange={handleChange}
                  required
                  maxLength={50}
                  className="w-full rounded-lg border border-gray-300 p-3 outline-none focus:border-indigo-500"
                  placeholder="STU003"
                />
              </div>

              <div>
                <label className="mb-1 block text-sm font-medium">
                  Full Name *
                </label>
                <input
                  name="fullName"
                  value={form.fullName}
                  onChange={handleChange}
                  required
                  maxLength={100}
                  className="w-full rounded-lg border border-gray-300 p-3 outline-none focus:border-indigo-500"
                  placeholder="Student full name"
                />
              </div>

              <div>
                <label className="mb-1 block text-sm font-medium">
                  Date of Birth
                </label>
                <input
                  type="date"
                  name="dateOfBirth"
                  value={form.dateOfBirth}
                  onChange={handleChange}
                  className="w-full rounded-lg border border-gray-300 p-3 outline-none focus:border-indigo-500"
                />
              </div>

              <div>
                <label className="mb-1 block text-sm font-medium">
                  Gender
                </label>
                <select
                  name="gender"
                  value={form.gender}
                  onChange={handleChange}
                  className="w-full rounded-lg border border-gray-300 p-3 outline-none focus:border-indigo-500"
                >
                  <option value="">Select gender</option>
                  <option value="MALE">Male</option>
                  <option value="FEMALE">Female</option>
                  <option value="OTHER">Other</option>
                </select>
              </div>

              {formError && (
                <p role="alert" className="text-sm text-red-600">
                  {formError}
                </p>
              )}

              <div className="flex justify-end gap-3 pt-4">
                <button
                  type="button"
                  onClick={() => setShowForm(false)}
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
                    ? "Update Student"
                    : "Save Student"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
