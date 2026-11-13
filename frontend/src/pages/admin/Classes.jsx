
import { useEffect, useState } from "react";
import AdminSidebar from "../../components/layout/AdminSidebar";

const API_URL = "http://localhost:8080/api/admin/classes";

async function apiRequest(url, options = {}) {
  const token = sessionStorage.getItem("token");

  const response = await fetch(url, {
    ...options,
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      ...options.headers,
    },
  });

  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      throw new Error(
        "Session expired or access denied. Please log in again."
      );
    }

    throw new Error(
      data.message || `Request failed (${response.status})`
    );
  }

  return data;
}

export default function Classes() {
  const [classes, setClasses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [search, setSearch] = useState("");

  const [expandedClassId, setExpandedClassId] = useState(null);
  const [sectionsByClass, setSectionsByClass] = useState({});
  const [sectionsLoading, setSectionsLoading] = useState(null);
  const [sectionError, setSectionError] = useState("");

  const [classModal, setClassModal] = useState(false);
  const [editingClassId, setEditingClassId] = useState(null);
  const [className, setClassName] = useState("");

  const [sectionModal, setSectionModal] = useState(false);
  const [sectionForm, setSectionForm] = useState({
    classId: null,
    sectionId: null,
    name: "",
  });

  const [formError, setFormError] = useState("");
  const [saving, setSaving] = useState(false);
  const [actionId, setActionId] = useState(null);

  // LOAD CLASSES
  const loadClasses = async () => {
    setLoading(true);
    setError("");

    try {
      const data = await apiRequest(API_URL);

      if (!Array.isArray(data)) {
        throw new Error("Invalid classes data received.");
      }

      setClasses(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadClasses();
  }, []);

  // LOAD SECTIONS
  const loadSections = async (classId) => {
    setSectionsLoading(classId);
    setSectionError("");

    try {
      const data = await apiRequest(
        `${API_URL}/${classId}/sections`
      );

      if (!Array.isArray(data)) {
        throw new Error("Invalid sections data received.");
      }

      setSectionsByClass((prev) => ({
        ...prev,
        [classId]: data,
      }));
    } catch (err) {
      setSectionError(err.message);
    } finally {
      setSectionsLoading(null);
    }
  };

  const toggleSections = async (classId) => {
    if (expandedClassId === classId) {
      setExpandedClassId(null);
      return;
    }

    setExpandedClassId(classId);
    await loadSections(classId);
  };

  // CLASS FORM
  const openAddClass = () => {
    setEditingClassId(null);
    setClassName("");
    setFormError("");
    setClassModal(true);
  };

  const openEditClass = (schoolClass) => {
    setEditingClassId(schoolClass.id);
    setClassName(schoolClass.name);
    setFormError("");
    setClassModal(true);
  };

  const saveClass = async (e) => {
    e.preventDefault();

    if (saving) return;

    const name = className.trim();

    if (!name) {
      setFormError("Class name is required.");
      return;
    }

    setSaving(true);
    setFormError("");

    try {
      const editing = editingClassId !== null;

      await apiRequest(
        editing ? `${API_URL}/${editingClassId}` : API_URL,
        {
          method: editing ? "PUT" : "POST",
          body: JSON.stringify({ name }),
        }
      );

      setClassModal(false);
      setNotice(
        editing
          ? "Class updated successfully."
          : "Class created successfully."
      );

      await loadClasses();
    } catch (err) {
      setFormError(err.message);
    } finally {
      setSaving(false);
    }
  };

  // DEACTIVATE CLASS
  const deactivateClass = async (schoolClass) => {
    const confirmed = window.confirm(
      `Deactivate ${schoolClass.name}? The class record will remain saved.`
    );

    if (!confirmed) return;

    setActionId(schoolClass.id);
    setError("");
    setNotice("");

    try {
      await apiRequest(
        `${API_URL}/${schoolClass.id}/deactivate`,
        { method: "PATCH" }
      );

      setNotice(`${schoolClass.name} deactivated successfully.`);
      await loadClasses();
    } catch (err) {
      setError(err.message);
    } finally {
      setActionId(null);
    }
  };

  // SECTION FORM
  const openAddSection = (schoolClass) => {
    setSectionForm({
      classId: schoolClass.id,
      sectionId: null,
      name: "",
    });

    setFormError("");
    setSectionModal(true);
  };

  const openEditSection = (classId, section) => {
    setSectionForm({
      classId,
      sectionId: section.id,
      name: section.name,
    });

    setFormError("");
    setSectionModal(true);
  };

  const saveSection = async (e) => {
    e.preventDefault();

    if (saving) return;

    const name = sectionForm.name.trim();

    if (!name) {
      setFormError("Section name is required.");
      return;
    }

    setSaving(true);
    setFormError("");

    try {
      const editing = sectionForm.sectionId !== null;
      const classId = sectionForm.classId;

      const url = editing
        ? `${API_URL}/${classId}/sections/${sectionForm.sectionId}`
        : `${API_URL}/${classId}/sections`;

      await apiRequest(url, {
        method: editing ? "PUT" : "POST",
        body: JSON.stringify({ name }),
      });

      setSectionModal(false);
      setExpandedClassId(classId);

      setNotice(
        editing
          ? "Section updated successfully."
          : "Section added successfully."
      );

      await loadSections(classId);
    } catch (err) {
      setFormError(err.message);
    } finally {
      setSaving(false);
    }
  };

  // SEARCH & STATISTICS
  const filteredClasses = classes.filter((schoolClass) =>
    schoolClass.name
      .toLowerCase()
      .includes(search.trim().toLowerCase())
  );

  const activeCount = classes.filter(
    (schoolClass) => schoolClass.active
  ).length;

  const stats = [
    {
      title: "Total Classes",
      value: classes.length,
      color: "text-indigo-700",
    },
    {
      title: "Active Classes",
      value: activeCount,
      color: "text-green-700",
    },
    {
      title: "Inactive Classes",
      value: classes.length - activeCount,
      color: "text-red-600",
    },
  ];

  return (
    <div className="flex min-h-screen bg-gray-50">
      <AdminSidebar />

      <main className="min-w-0 flex-1 p-6 md:p-8">
        {/* PAGE HEADER */}
        <div className="mb-7 flex flex-wrap items-center justify-between gap-4">
          <div>
            <h1 className="text-3xl font-bold text-gray-900">
              Classes & Sections
            </h1>

            <p className="mt-2 text-gray-500">
              Organize school classes and their sections
            </p>
          </div>

          <button
            onClick={openAddClass}
            className="rounded-lg bg-indigo-600 px-5 py-3 font-semibold text-white hover:bg-indigo-700"
          >
            + Add Class
          </button>
        </div>

        {/* STATISTICS */}
        <div className="mb-7 grid grid-cols-1 gap-4 sm:grid-cols-3">
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

        {/* SEARCH */}
        <div className="mb-6">
          <input
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search classes..."
            aria-label="Search classes"
            className="w-full max-w-md rounded-lg border border-gray-300 bg-white px-4 py-3 outline-none focus:border-indigo-500"
          />
        </div>

        {/* SUCCESS MESSAGE */}
        {notice && (
          <div
            role="status"
            className="mb-5 rounded-lg border border-green-200 bg-green-50 p-4 text-sm text-green-700"
          >
            {notice}
          </div>
        )}

        {/* ERROR MESSAGE */}
        {error && (
          <div
            role="alert"
            className="mb-5 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700"
          >
            {error}

            <button
              onClick={loadClasses}
              className="ml-3 font-semibold underline"
            >
              Retry
            </button>
          </div>
        )}

        {/* CLASS LIST */}
        <div className="space-y-4">
          {loading ? (
            <div className="rounded-xl bg-white p-8 text-center text-gray-500">
              Loading classes...
            </div>
          ) : error ? null : filteredClasses.length === 0 ? (
            <div className="rounded-xl bg-white p-8 text-center text-gray-500">
              {search
                ? "No matching classes found."
                : "No classes added yet."}
            </div>
          ) : (
            filteredClasses.map((schoolClass) => {
              const expanded =
                expandedClassId === schoolClass.id;

              const sections =
                sectionsByClass[schoolClass.id] || [];

              return (
                <div
                  key={schoolClass.id}
                  className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm"
                >
                  {/* CLASS DETAILS */}
                  <div className="flex flex-wrap items-center justify-between gap-4 p-5">
                    <div className="flex items-center gap-4">
                      <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-indigo-100 text-xl">
                        🏫
                      </div>

                      <div>
                        <h2 className="text-lg font-bold text-gray-900">
                          {schoolClass.name}
                        </h2>

                        <span
                          className={`mt-1 inline-block rounded-full px-3 py-1 text-xs font-semibold ${
                            schoolClass.active
                              ? "bg-green-100 text-green-700"
                              : "bg-red-100 text-red-700"
                          }`}
                        >
                          {schoolClass.active
                            ? "Active"
                            : "Inactive"}
                        </span>
                      </div>
                    </div>

                    <div className="flex flex-wrap gap-2">
                      <button
                        onClick={() =>
                          toggleSections(schoolClass.id)
                        }
                        className="rounded-lg bg-gray-100 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-200"
                      >
                        {expanded
                          ? "Hide Sections"
                          : "View Sections"}
                      </button>

                      <button
                        onClick={() =>
                          openEditClass(schoolClass)
                        }
                        className="rounded-lg bg-indigo-100 px-4 py-2 text-sm font-medium text-indigo-700 hover:bg-indigo-200"
                      >
                        Edit
                      </button>

                      {schoolClass.active && (
                        <button
                          onClick={() =>
                            deactivateClass(schoolClass)
                          }
                          disabled={actionId !== null}
                          className="rounded-lg bg-red-100 px-4 py-2 text-sm font-medium text-red-700 hover:bg-red-200 disabled:opacity-50"
                        >
                          {actionId === schoolClass.id
                            ? "Working..."
                            : "Deactivate"}
                        </button>
                      )}
                    </div>
                  </div>

                  {/* SECTIONS */}
                  {expanded && (
                    <div className="border-t border-gray-200 bg-gray-50 p-5">
                      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
                        <h3 className="font-bold text-gray-800">
                          Sections
                        </h3>

                        {schoolClass.active && (
                          <button
                            onClick={() =>
                              openAddSection(schoolClass)
                            }
                            className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700"
                          >
                            + Add Section
                          </button>
                        )}
                      </div>

                      {sectionsLoading === schoolClass.id ? (
                        <p className="text-sm text-gray-500">
                          Loading sections...
                        </p>
                      ) : sectionError ? (
                        <p
                          role="alert"
                          className="text-sm text-red-600"
                        >
                          {sectionError}
                        </p>
                      ) : sections.length === 0 ? (
                        <p className="text-sm text-gray-500">
                          No sections added yet.
                        </p>
                      ) : (
                        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                          {sections.map((section) => (
                            <div
                              key={section.id}
                              className="flex items-center justify-between gap-3 rounded-lg border border-gray-200 bg-white p-4"
                            >
                              <span className="font-semibold text-gray-800">
                                Section {section.name}
                              </span>

                              <button
                                onClick={() =>
                                  openEditSection(
                                    schoolClass.id,
                                    section
                                  )
                                }
                                className="text-sm font-semibold text-indigo-600 hover:text-indigo-800"
                              >
                                Rename
                              </button>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  )}
                </div>
              );
            })
          )}
        </div>
      </main>

      {/* ADD / EDIT CLASS MODAL */}
      {classModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="class-form-title"
            className="w-full max-w-md rounded-2xl bg-white p-7 shadow-xl"
          >
            <h2
              id="class-form-title"
              className="mb-6 text-2xl font-bold text-gray-900"
            >
              {editingClassId !== null
                ? "Edit Class"
                : "Add New Class"}
            </h2>

            <form onSubmit={saveClass} className="space-y-5">
              <div>
                <label
                  htmlFor="className"
                  className="mb-2 block text-sm font-medium"
                >
                  Class Name *
                </label>

                <input
                  id="className"
                  value={className}
                  onChange={(e) =>
                    setClassName(e.target.value)
                  }
                  placeholder="Class 9"
                  maxLength={50}
                  required
                  autoFocus
                  className="w-full rounded-lg border border-gray-300 p-3 outline-none focus:border-indigo-500"
                />
              </div>

              {formError && (
                <p role="alert" className="text-sm text-red-600">
                  {formError}
                </p>
              )}

              <div className="flex justify-end gap-3">
                <button
                  type="button"
                  onClick={() => setClassModal(false)}
                  disabled={saving}
                  className="rounded-lg border border-gray-300 px-5 py-2.5 disabled:opacity-50"
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
                    : editingClassId !== null
                    ? "Update Class"
                    : "Save Class"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ADD / RENAME SECTION MODAL */}
      {sectionModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="section-form-title"
            className="w-full max-w-md rounded-2xl bg-white p-7 shadow-xl"
          >
            <h2
              id="section-form-title"
              className="mb-6 text-2xl font-bold text-gray-900"
            >
              {sectionForm.sectionId !== null
                ? "Rename Section"
                : "Add New Section"}
            </h2>

            <form onSubmit={saveSection} className="space-y-5">
              <div>
                <label
                  htmlFor="sectionName"
                  className="mb-2 block text-sm font-medium"
                >
                  Section Name *
                </label>

                <input
                  id="sectionName"
                  value={sectionForm.name}
                  onChange={(e) =>
                    setSectionForm((prev) => ({
                      ...prev,
                      name: e.target.value,
                    }))
                  }
                  placeholder="A"
                  maxLength={20}
                  required
                  autoFocus
                  className="w-full rounded-lg border border-gray-300 p-3 outline-none focus:border-indigo-500"
                />
              </div>

              {formError && (
                <p role="alert" className="text-sm text-red-600">
                  {formError}
                </p>
              )}

              <div className="flex justify-end gap-3">
                <button
                  type="button"
                  onClick={() => setSectionModal(false)}
                  disabled={saving}
                  className="rounded-lg border border-gray-300 px-5 py-2.5 disabled:opacity-50"
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
                    : sectionForm.sectionId !== null
                    ? "Update Section"
                    : "Save Section"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
