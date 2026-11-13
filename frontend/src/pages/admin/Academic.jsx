
import { useEffect, useState } from "react";
import AdminSidebar from "../../components/layout/AdminSidebar";

const BASE = "http://localhost:8080/api/admin";

const initialData = {
  subjects: [],
  enrollments: [],
  assignments: [],
  students: [],
  teachers: [],
  classes: [],
  sections: [],
};

const emptyForm = () => ({
  name: "",
  code: "",
  studentId: "",
  teacherId: "",
  sectionId: "",
  subjectId: "",
  academicYear: "2026-2027",
  enrollmentDate: "",
});

const inputStyle =
  "w-full rounded-lg border border-gray-300 bg-white px-4 py-3 outline-none focus:border-indigo-500";

async function api(path, options = {}) {
  const token = sessionStorage.getItem("token");

  if (!token) {
    throw new Error("Session missing. Please login again.");
  }

  const response = await fetch(`${BASE}${path}`, {
    ...options,
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      ...options.headers,
    },
  });

  const data = await response.json().catch(() => null);

  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      throw new Error(
        "Session expired or access denied. Please login again."
      );
    }

    throw new Error(
      data?.message || `Request failed (HTTP ${response.status})`
    );
  }

  return data;
}

export default function Academic() {
  const [db, setDb] = useState(initialData);
  const [tab, setTab] = useState("subjects");
  const [search, setSearch] = useState("");

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const [modal, setModal] = useState(null);
  const [editingId, setEditingId] = useState(null);
  const [form, setForm] = useState(emptyForm());
  const [formError, setFormError] = useState("");
  const [saving, setSaving] = useState(false);
  const [actionId, setActionId] = useState(null);

  // Load academic data and related records
  const loadData = async () => {
    setLoading(true);
    setError("");

    try {
      const [
        subjects,
        enrollments,
        assignments,
        students,
        teachers,
        classes,
      ] = await Promise.all([
        api("/academic/subjects"),
        api("/academic/enrollments"),
        api("/academic/assignments"),
        api("/students"),
        api("/teachers"),
        api("/classes"),
      ]);

      const sectionLists = await Promise.all(
        classes.map((item) =>
          api(`/classes/${item.id}/sections`)
        )
      );

      setDb({
        subjects,
        enrollments,
        assignments,
        students,
        teachers,
        classes,
        sections: sectionLists.flat(),
      });
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  // Lookup helpers
  const studentName = (id) =>
    db.students.find((s) => s.id === id)?.fullName ||
    `Student #${id}`;

  const teacherName = (id) =>
    db.teachers.find((t) => t.id === id)?.fullName ||
    `Teacher #${id}`;

  const subjectName = (id) =>
    db.subjects.find((s) => s.id === id)?.name ||
    `Subject #${id}`;

  const sectionName = (id) => {
    const section = db.sections.find((s) => s.id === id);

    if (!section) return `Section #${id}`;

    const schoolClass = db.classes.find(
      (c) => c.id === section.classId
    );

    return `${schoolClass?.name || "Class"} - ${section.name}`;
  };

  const activeStudents = db.students.filter((s) => s.active);
  const activeTeachers = db.teachers.filter((t) => t.active);
  const activeSubjects = db.subjects.filter((s) => s.active);
  const activeClassIds = new Set(
    db.classes.filter((c) => c.active).map((c) => c.id)
  );

  const activeSections = db.sections.filter((s) =>
    activeClassIds.has(s.classId)
  );

  // Open add form
  const openAdd = (kind) => {
    setModal(kind);
    setEditingId(null);
    setForm(emptyForm());
    setFormError("");
  };

  // Open edit form
  const openEdit = (kind, item) => {
    setModal(kind);
    setEditingId(item.id);
    setFormError("");

    setForm({
      ...emptyForm(),
      name: item.name || "",
      code: item.code || "",
      studentId: String(item.studentId || ""),
      teacherId: String(item.teacherId || ""),
      sectionId: String(item.sectionId || ""),
      subjectId: String(item.subjectId || ""),
      academicYear: item.academicYear || "2026-2027",
      enrollmentDate: item.enrollmentDate || "",
    });
  };

  const closeModal = () => {
    if (saving) return;

    setModal(null);
    setEditingId(null);
    setFormError("");
  };

  const changeForm = (e) => {
    const { name, value } = e.target;

    setForm((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  // Create or edit records
  const saveRecord = async (e) => {
    e.preventDefault();
    if (saving) return;

    setSaving(true);
    setFormError("");

    try {
      let payload;

      if (modal === "subjects") {
        payload = {
          name: form.name.trim(),
          code: form.code.trim(),
        };
      } else if (modal === "enrollments") {
        payload = {
          studentId: Number(form.studentId),
          sectionId: Number(form.sectionId),
          academicYear: form.academicYear.trim(),
          enrollmentDate: form.enrollmentDate,
        };
      } else {
        payload = {
          teacherId: Number(form.teacherId),
          sectionId: Number(form.sectionId),
          subjectId: Number(form.subjectId),
          academicYear: form.academicYear.trim(),
        };
      }

      const editing = editingId !== null;

      await api(
        `/academic/${modal}${editing ? `/${editingId}` : ""}`,
        {
          method: editing ? "PUT" : "POST",
          body: JSON.stringify(payload),
        }
      );

      setModal(null);
      setEditingId(null);
      setNotice(
        editing
          ? "Record updated successfully."
          : "Record created successfully."
      );

      await loadData();
    } catch (err) {
      setFormError(err.message);
    } finally {
      setSaving(false);
    }
  };

  // Deactivate subject or assignment;
  // complete or withdraw enrollment
  const changeStatus = async (kind, item, status) => {
    const message =
      kind === "enrollments"
        ? `Mark this enrollment as ${status}?`
        : `Deactivate this ${
            kind === "subjects" ? "subject" : "assignment"
          }?`;

    if (!window.confirm(message)) return;

    setActionId(`${kind}-${item.id}`);
    setError("");
    setNotice("");

    try {
      const path =
        kind === "enrollments"
          ? `/academic/enrollments/${item.id}/status`
          : `/academic/${kind}/${item.id}/deactivate`;

      await api(path, {
        method: "PATCH",
        ...(kind === "enrollments"
          ? { body: JSON.stringify({ status }) }
          : {}),
      });

      setNotice("Status updated successfully.");
      await loadData();
    } catch (err) {
      setError(err.message);
    } finally {
      setActionId(null);
    }
  };

  const tabs = [
    {
      id: "subjects",
      label: "Subjects",
      count: db.subjects.length,
    },
    {
      id: "enrollments",
      label: "Enrollments",
      count: db.enrollments.length,
    },
    {
      id: "assignments",
      label: "Teacher Assignments",
      count: db.assignments.length,
    },
  ];

  const headers = {
    subjects: ["Code", "Subject", "Status", "Actions"],
    enrollments: [
      "Student",
      "Section",
      "Academic Year",
      "Enrollment Date",
      "Status",
      "Actions",
    ],
    assignments: [
      "Teacher",
      "Section",
      "Subject",
      "Academic Year",
      "Status",
      "Actions",
    ],
  };

  const query = search.trim().toLowerCase();

  const filtered = db[tab].filter((item) => {
    let text = "";

    if (tab === "subjects") {
      text = `${item.name} ${item.code}`;
    } else if (tab === "enrollments") {
      text = `${studentName(item.studentId)}
              ${sectionName(item.sectionId)}
              ${item.academicYear} ${item.status}`;
    } else {
      text = `${teacherName(item.teacherId)}
              ${sectionName(item.sectionId)}
              ${subjectName(item.subjectId)}
              ${item.academicYear}`;
    }

    return text.toLowerCase().includes(query);
  });

  const statusBadge = (status) => (
    <span
      className={`rounded-full px-3 py-1 text-xs font-semibold ${
        status === "ACTIVE"
          ? "bg-green-100 text-green-700"
          : status === "COMPLETED"
          ? "bg-blue-100 text-blue-700"
          : "bg-red-100 text-red-700"
      }`}
    >
      {status}
    </span>
  );

  // Reusable form fields
  const textInput = (
    name,
    label,
    required = true,
    type = "text",
    maxLength = 100
  ) => (
    <label className="block text-sm font-medium text-gray-700">
      {label}
      <input
        type={type}
        name={name}
        value={form[name]}
        onChange={changeForm}
        required={required}
        maxLength={type === "date" ? undefined : maxLength}
        className={`${inputStyle} mt-2`}
      />
    </label>
  );

  const selectInput = (name, label, options) => (
    <label className="block text-sm font-medium text-gray-700">
      {label}
      <select
        name={name}
        value={form[name]}
        onChange={changeForm}
        required
        className={`${inputStyle} mt-2`}
      >
        <option value="">Select {label}</option>

        {options.map((option) => (
          <option key={option.id} value={option.id}>
            {option.label}
          </option>
        ))}
      </select>
    </label>
  );

  const studentOptions = activeStudents.map((s) => ({
    id: s.id,
    label: `${s.fullName} (${s.admissionNumber})`,
  }));

  const teacherOptions = activeTeachers.map((t) => ({
    id: t.id,
    label: `${t.fullName} (${t.employeeNumber})`,
  }));

  const subjectOptions = activeSubjects.map((s) => ({
    id: s.id,
    label: `${s.name} (${s.code})`,
  }));

  const sectionOptions = activeSections.map((s) => ({
    id: s.id,
    label: sectionName(s.id),
  }));

  const addButtonLabel = {
    subjects: "+ Add Subject",
    enrollments: "+ Enroll Student",
    assignments: "+ Assign Teacher",
  };

  const editButton = (kind, item) => (
    <button
      type="button"
      onClick={() => openEdit(kind, item)}
      className="rounded-lg bg-indigo-100 px-3 py-2 text-sm font-medium text-indigo-700 hover:bg-indigo-200"
    >
      Edit
    </button>
  );

  const deactivateButton = (kind, item) => (
    <button
      type="button"
      disabled={actionId !== null}
      onClick={() => changeStatus(kind, item)}
      className="rounded-lg bg-red-100 px-3 py-2 text-sm font-medium text-red-700 hover:bg-red-200 disabled:opacity-50"
    >
      Deactivate
    </button>
  );

  return (
    <div className="flex min-h-screen bg-gray-50">
      <AdminSidebar />

      <main className="min-w-0 flex-1 p-6 md:p-8">
        {/* HEADER */}
        <div className="mb-7 flex flex-wrap items-center justify-between gap-4">
          <div>
            <h1 className="text-3xl font-bold text-gray-900">
              Academic Management
            </h1>

            <p className="mt-2 text-gray-500">
              Manage subjects, student enrollments and faculty assignments
            </p>
          </div>

          <button
            type="button"
            onClick={() => openAdd(tab)}
            className="rounded-lg bg-indigo-600 px-5 py-3 font-semibold text-white hover:bg-indigo-700"
          >
            {addButtonLabel[tab]}
          </button>
        </div>

        {/* SUMMARY CARDS / TABS */}
        <div className="mb-7 grid grid-cols-1 gap-4 sm:grid-cols-3">
          {tabs.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => {
                setTab(item.id);
                setSearch("");
                setNotice("");
              }}
              className={`rounded-xl border p-5 text-left shadow-sm transition ${
                tab === item.id
                  ? "border-indigo-500 bg-indigo-50"
                  : "border-gray-200 bg-white hover:border-indigo-300"
              }`}
            >
              <p className="text-sm font-medium text-gray-600">
                {item.label}
              </p>

              <p className="mt-2 text-3xl font-bold text-indigo-700">
                {loading || error ? "—" : item.count}
              </p>
            </button>
          ))}
        </div>

        {/* SEARCH */}
        <div className="mb-5">
          <input
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder={`Search ${tab}...`}
            aria-label={`Search ${tab}`}
            className={`${inputStyle} max-w-md`}
          />
        </div>

        {/* SUCCESS */}
        {notice && (
          <div
            role="status"
            className="mb-5 rounded-lg bg-green-50 p-4 text-sm text-green-700"
          >
            {notice}
          </div>
        )}

        {/* ERROR */}
        {error && (
          <div
            role="alert"
            className="mb-5 rounded-lg bg-red-50 p-4 text-sm text-red-700"
          >
            {error}

            <button
              type="button"
              onClick={loadData}
              className="ml-3 font-semibold underline"
            >
              Retry
            </button>
          </div>
        )}

        {/* DATA TABLE */}
        <div className="overflow-x-auto rounded-xl border border-gray-200 bg-white shadow-sm">
          <table className="w-full min-w-[850px] text-left">
            <thead className="bg-indigo-50 text-sm text-gray-700">
              <tr>
                {headers[tab].map((header) => (
                  <th key={header} className="p-4">
                    {header}
                  </th>
                ))}
              </tr>
            </thead>

            <tbody>
              {loading || error || filtered.length === 0 ? (
                <tr>
                  <td
                    colSpan={headers[tab].length}
                    className="p-8 text-center text-gray-500"
                  >
                    {loading
                      ? "Loading academic records..."
                      : error
                      ? "Unable to display records."
                      : query
                      ? "No matching records."
                      : "No records found."}
                  </td>
                </tr>
              ) : (
                filtered.map((item) => (
                  <tr
                    key={item.id}
                    className="border-t border-gray-100 hover:bg-gray-50"
                  >
                    {tab === "subjects" && (
                      <>
                        <td className="p-4 font-medium">
                          {item.code}
                        </td>

                        <td className="p-4">{item.name}</td>

                        <td className="p-4">
                          {statusBadge(
                            item.active ? "ACTIVE" : "INACTIVE"
                          )}
                        </td>

                        <td className="p-4">
                          <div className="flex gap-2">
                            {editButton("subjects", item)}

                            {item.active &&
                              deactivateButton("subjects", item)}
                          </div>
                        </td>
                      </>
                    )}

                    {tab === "enrollments" && (
                      <>
                        <td className="p-4 font-medium">
                          {studentName(item.studentId)}
                        </td>

                        <td className="p-4">
                          {sectionName(item.sectionId)}
                        </td>

                        <td className="p-4">
                          {item.academicYear}
                        </td>

                        <td className="p-4">
                          {item.enrollmentDate}
                        </td>

                        <td className="p-4">
                          {statusBadge(item.status)}
                        </td>

                        <td className="p-4">
                          {item.status === "ACTIVE" && (
                            <div className="flex flex-wrap gap-2">
                              {editButton("enrollments", item)}

                              <button
                                type="button"
                                disabled={actionId !== null}
                                onClick={() =>
                                  changeStatus(
                                    "enrollments",
                                    item,
                                    "COMPLETED"
                                  )
                                }
                                className="rounded-lg bg-blue-100 px-3 py-2 text-sm font-medium text-blue-700 hover:bg-blue-200 disabled:opacity-50"
                              >
                                Complete
                              </button>

                              <button
                                type="button"
                                disabled={actionId !== null}
                                onClick={() =>
                                  changeStatus(
                                    "enrollments",
                                    item,
                                    "WITHDRAWN"
                                  )
                                }
                                className="rounded-lg bg-red-100 px-3 py-2 text-sm font-medium text-red-700 hover:bg-red-200 disabled:opacity-50"
                              >
                                Withdraw
                              </button>
                            </div>
                          )}
                        </td>
                      </>
                    )}

                    {tab === "assignments" && (
                      <>
                        <td className="p-4 font-medium">
                          {teacherName(item.teacherId)}
                        </td>

                        <td className="p-4">
                          {sectionName(item.sectionId)}
                        </td>

                        <td className="p-4">
                          {subjectName(item.subjectId)}
                        </td>

                        <td className="p-4">
                          {item.academicYear}
                        </td>

                        <td className="p-4">
                          {statusBadge(
                            item.active ? "ACTIVE" : "INACTIVE"
                          )}
                        </td>

                        <td className="p-4">
                          {item.active && (
                            <div className="flex gap-2">
                              {editButton("assignments", item)}
                              {deactivateButton("assignments", item)}
                            </div>
                          )}
                        </td>
                      </>
                    )}
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </main>

      {/* ADD / EDIT FORM */}
      {modal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="academic-form-title"
            className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-2xl bg-white p-7 shadow-xl"
          >
            <div className="mb-6 flex items-center justify-between">
              <h2
                id="academic-form-title"
                className="text-2xl font-bold text-gray-900"
              >
                {editingId !== null ? "Edit" : "Add"}{" "}
                {modal === "subjects"
                  ? "Subject"
                  : modal === "enrollments"
                  ? "Enrollment"
                  : "Teacher Assignment"}
              </h2>

              <button
                type="button"
                onClick={closeModal}
                disabled={saving}
                aria-label="Close form"
                className="text-2xl text-gray-400 hover:text-gray-700 disabled:opacity-50"
              >
                ×
              </button>
            </div>

            <form onSubmit={saveRecord} className="space-y-5">
              {/* SUBJECT FIELDS */}
              {modal === "subjects" && (
                <>
                  {textInput("name", "Subject Name *")}
                  {textInput(
                    "code",
                    "Subject Code *",
                    true,
                    "text",
                    20
                  )}
                </>
              )}

              {/* ENROLLMENT FIELDS */}
              {modal === "enrollments" && (
                <>
                  {selectInput(
                    "studentId",
                    "Student *",
                    studentOptions
                  )}

                  {selectInput(
                    "sectionId",
                    "Section *",
                    sectionOptions
                  )}

                  {textInput(
                    "academicYear",
                    "Academic Year *",
                    true,
                    "text",
                    20
                  )}

                  {textInput(
                    "enrollmentDate",
                    "Enrollment Date *",
                    true,
                    "date"
                  )}
                </>
              )}

              {/* ASSIGNMENT FIELDS */}
              {modal === "assignments" && (
                <>
                  {selectInput(
                    "teacherId",
                    "Teacher *",
                    teacherOptions
                  )}

                  {selectInput(
                    "sectionId",
                    "Section *",
                    sectionOptions
                  )}

                  {selectInput(
                    "subjectId",
                    "Subject *",
                    subjectOptions
                  )}

                  {textInput(
                    "academicYear",
                    "Academic Year *",
                    true,
                    "text",
                    20
                  )}
                </>
              )}

              {formError && (
                <p role="alert" className="text-sm text-red-600">
                  {formError}
                </p>
              )}

              <div className="flex justify-end gap-3 pt-3">
                <button
                  type="button"
                  onClick={closeModal}
                  disabled={saving}
                  className="rounded-lg border border-gray-300 px-5 py-2.5 text-gray-700 hover:bg-gray-50 disabled:opacity-50"
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
                    : editingId !== null
                    ? "Update Record"
                    : "Save Record"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
