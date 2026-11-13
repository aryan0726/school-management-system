
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import AdminSidebar from "../../components/layout/AdminSidebar";

const BASE_URL = "http://localhost:8080/api";

const STATUSES = ["PRESENT", "ABSENT", "LATE", "EXCUSED"];

function localDate() {
  const date = new Date();

  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");

  return `${year}-${month}-${day}`;
}

async function request(path, options = {}) {
  const token = sessionStorage.getItem("token");

  if (!token) {
    throw new Error("Session missing. Please log in again.");
  }

  const response = await fetch(`${BASE_URL}${path}`, {
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
        "Access denied or session expired. Please check your login."
      );
    }

    throw new Error(
      data?.message ||
      data?.detail ||
      `Request failed (HTTP ${response.status})`
    );
  }

  return data;
}

export default function Attendance() {
  const navigate = useNavigate();

  const [user] = useState(() => {
    try {
      return JSON.parse(
        sessionStorage.getItem("user") || "null"
      );
    } catch {
      return null;
    }
  });

  const isTeacher = user?.role === "TEACHER";
  const isAdmin = user?.role === "ADMIN";

  const [sessions, setSessions] = useState([]);
  const [assignedSections, setAssignedSections] = useState([]);
  const [sectionNames, setSectionNames] = useState({});
  const [teacherNames, setTeacherNames] = useState({});

  const [selectedSection, setSelectedSection] = useState("");
  const [attendanceDate, setAttendanceDate] = useState(localDate());

  const [roster, setRoster] = useState([]);
  const [marks, setMarks] = useState({});
  const [rosterLoading, setRosterLoading] = useState(false);
  const [rosterError, setRosterError] = useState("");

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [saving, setSaving] = useState(false);

  const [search, setSearch] = useState("");
  const [expandedId, setExpandedId] = useState(null);
  const [recordEdits, setRecordEdits] = useState({});
  const [savingRecordId, setSavingRecordId] = useState(null);

  // LOAD ATTENDANCE AND RELATED DATA
  const loadPage = async () => {
    setLoading(true);
    setError("");

    try {
      if (isTeacher) {
        const [sections, attendance] = await Promise.all([
          request("/attendance/my-sections"),
          request("/attendance/sessions"),
        ]);

        setAssignedSections(sections);
        setSessions(attendance);

        setSelectedSection((previous) => {
          if (previous) return previous;

          if (sections.length === 0) return "";

          const first = sections[0];

          return `${first.sectionId}|${first.academicYear}`;
        });
      } else if (isAdmin) {
        const [attendance, classes, teachers] =
          await Promise.all([
            request("/attendance/sessions"),
            request("/admin/classes"),
            request("/admin/teachers"),
          ]);

        setSessions(attendance);

        const classMap = Object.fromEntries(
          classes.map((c) => [c.id, c.name])
        );

        const sectionLists = await Promise.all(
          classes.map((c) =>
            request(`/admin/classes/${c.id}/sections`)
          )
        );

        const allSections = sectionLists.flat();

        setSectionNames(
          Object.fromEntries(
            allSections.map((s) => [
              s.id,
              `${classMap[s.classId] || "Class"} - ${s.name}`,
            ])
          )
        );

        setTeacherNames(
          Object.fromEntries(
            teachers.map((t) => [t.id, t.fullName])
          )
        );
      } else {
        throw new Error("Please log in as Admin or Teacher.");
      }
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadPage();
  }, [isAdmin, isTeacher]);

  const selectedAssignment = assignedSections.find(
    (item) =>
      `${item.sectionId}|${item.academicYear}` === selectedSection
  );

  // GET STUDENTS ELIGIBLE FOR ATTENDANCE
  useEffect(() => {
    if (!isTeacher || !selectedAssignment || !attendanceDate) {
      setRoster([]);
      setMarks({});
      return;
    }

    let cancelled = false;

    const loadRoster = async () => {
      setRosterLoading(true);
      setRosterError("");

      try {
        const query = new URLSearchParams({
          academicYear: selectedAssignment.academicYear,
          attendanceDate,
        });

        const students = await request(
          `/attendance/sections/${selectedAssignment.sectionId}/roster?${query}`
        );

        if (cancelled) return;

        setRoster(students);

        setMarks(
          Object.fromEntries(
            students.map((student) => [
              student.id,
              {
                status: "PRESENT",
                remarks: "",
              },
            ])
          )
        );
      } catch (err) {
        if (!cancelled) {
          setRoster([]);
          setRosterError(err.message);
        }
      } finally {
        if (!cancelled) {
          setRosterLoading(false);
        }
      }
    };

    loadRoster();

    return () => {
      cancelled = true;
    };
  }, [isTeacher, selectedSection, attendanceDate]);

  const refreshSessions = async () => {
    const attendance = await request("/attendance/sessions");
    setSessions(attendance);
  };

  const updateMark = (studentId, field, value) => {
    setMarks((previous) => ({
      ...previous,
      [studentId]: {
        ...previous[studentId],
        [field]: value,
      },
    }));
  };

  const alreadyMarked = Boolean(
    selectedAssignment &&
      sessions.some(
        (session) =>
          session.sectionId === selectedAssignment.sectionId &&
          session.attendanceDate === attendanceDate &&
          session.sessionType === "DAILY"
      )
  );

  // SUBMIT DAILY ATTENDANCE
  const markAttendance = async (event) => {
    event.preventDefault();

    if (
      !selectedAssignment ||
      roster.length === 0 ||
      alreadyMarked ||
      saving
    ) {
      return;
    }

    setSaving(true);
    setError("");
    setNotice("");

    try {
      const payload = {
        sectionId: selectedAssignment.sectionId,
        academicYear: selectedAssignment.academicYear,
        attendanceDate,
        records: roster.map((student) => ({
          studentId: student.id,
          status: marks[student.id]?.status || "PRESENT",
          remarks: marks[student.id]?.remarks || "",
        })),
      };

      const result = await request("/attendance/sessions", {
        method: "POST",
        body: JSON.stringify(payload),
      });

      setNotice(
        `${result.message}. ${result.totalStudents} students saved.`
      );

      await refreshSessions();
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  };

  // EXPAND AN EXISTING ATTENDANCE SESSION
  const toggleSession = (session) => {
    if (expandedId === session.id) {
      setExpandedId(null);
      return;
    }

    setExpandedId(session.id);

    setRecordEdits(
      Object.fromEntries(
        session.records.map((record) => [
          record.studentId,
          {
            status: record.status,
            remarks: record.remarks || "",
          },
        ])
      )
    );
  };

  const updateCorrection = (studentId, field, value) => {
    setRecordEdits((previous) => ({
      ...previous,
      [studentId]: {
        ...previous[studentId],
        [field]: value,
      },
    }));
  };

  // CORRECT A SAVED ATTENDANCE RECORD
  const saveCorrection = async (sessionId, record) => {
    const edit = recordEdits[record.studentId];

    if (!edit) return;

    setSavingRecordId(record.id);
    setError("");
    setNotice("");

    try {
      await request(
        `/attendance/sessions/${sessionId}/records/${record.studentId}`,
        {
          method: "PUT",
          body: JSON.stringify(edit),
        }
      );

      setNotice("Attendance record updated successfully.");
      await refreshSessions();
    } catch (err) {
      setError(err.message);
    } finally {
      setSavingRecordId(null);
    }
  };

  const getSectionName = (sectionId) => {
    if (sectionNames[sectionId]) {
      return sectionNames[sectionId];
    }

    const item = assignedSections.find(
      (section) => section.sectionId === sectionId
    );

    if (item) {
      return `${item.className} - ${item.sectionName}`;
    }

    return `Section #${sectionId}`;
  };

  const logout = () => {
    sessionStorage.removeItem("token");
    sessionStorage.removeItem("user");

    navigate("/login", { replace: true });
  };

  const totalPresent = sessions.reduce(
    (total, session) =>
      total +
      session.records.filter((r) => r.status === "PRESENT").length,
    0
  );

  const totalAbsent = sessions.reduce(
    (total, session) =>
      total +
      session.records.filter((r) => r.status === "ABSENT").length,
    0
  );

  const filteredSessions = sessions.filter((session) => {
    const query = search.toLowerCase().trim();

    const text = [
      session.attendanceDate,
      getSectionName(session.sectionId),
      session.sessionType,
      teacherNames[session.markedBy] || "",
      ...session.records.map((r) => r.studentName),
    ].join(" ");

    return text.toLowerCase().includes(query);
  });

  const inputClass =
    "w-full rounded-lg border border-gray-300 bg-white px-3 py-2.5 outline-none focus:border-indigo-500";

  const stats = [
    {
      title: "Attendance Sessions",
      value: sessions.length,
      color: "text-indigo-700",
    },
    {
      title: "Present Records",
      value: totalPresent,
      color: "text-green-700",
    },
    {
      title: "Absent Records",
      value: totalAbsent,
      color: "text-red-600",
    },
  ];

  return (
    <div className="flex min-h-screen bg-slate-50">
      {isAdmin && <AdminSidebar />}

      <div className="min-w-0 flex-1">
        {isTeacher && (
          <header className="flex flex-wrap items-center justify-between gap-4 border-b border-indigo-100 bg-white px-6 py-5">
            <div>
              <h2 className="text-xl font-bold text-indigo-800">
                🎓 EduSphere Teacher Portal
              </h2>

              <p className="mt-1 text-sm text-gray-500">
                Welcome, {user?.fullName || "Teacher"}
              </p>
            </div>

            <button
              type="button"
              onClick={logout}
              className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium hover:bg-gray-50"
            >
              Logout
            </button>
          </header>
        )}

        <main className="p-6 md:p-8">
          {/* PAGE HEADER */}
          <div className="mb-7 flex flex-wrap items-center justify-between gap-3">
            <div>
              <h1 className="text-3xl font-bold text-gray-900">
                Attendance Management
              </h1>

              <p className="mt-2 text-gray-500">
                {isTeacher
                  ? "Mark and manage attendance for your assigned sections."
                  : "Monitor attendance across the school."}
              </p>
            </div>

            <button
              type="button"
              onClick={loadPage}
              disabled={loading}
              className="rounded-lg border border-indigo-200 bg-white px-4 py-2.5 font-medium text-indigo-700 hover:bg-indigo-50 disabled:opacity-50"
            >
              Refresh
            </button>
          </div>

          {/* SUMMARY */}
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

          {/* NOTICES */}
          {notice && (
            <div
              role="status"
              className="mb-5 rounded-lg bg-green-50 p-4 text-sm text-green-700"
            >
              {notice}
            </div>
          )}

          {error && (
            <div
              role="alert"
              className="mb-5 rounded-lg bg-red-50 p-4 text-sm text-red-700"
            >
              {error}
            </div>
          )}

          {/* TEACHER ATTENDANCE FORM */}
          {isTeacher && (
            <section className="mb-9 rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
              <h2 className="mb-5 text-xl font-bold text-gray-900">
                Mark Daily Attendance
              </h2>

              {assignedSections.length === 0 && !loading ? (
                <p className="text-sm text-amber-700">
                  You have no active section assignments.
                  Ask the Admin to assign you a section.
                </p>
              ) : (
                <form onSubmit={markAttendance}>
                  <div className="grid gap-4 md:grid-cols-2">
                    <label className="text-sm font-medium text-gray-700">
                      Assigned Class & Section
                      <select
                        className={`${inputClass} mt-2`}
                        value={selectedSection}
                        onChange={(e) =>
                          setSelectedSection(e.target.value)
                        }
                        required
                      >
                        <option value="">
                          Select class and section
                        </option>

                        {assignedSections.map((item) => (
                          <option
                            key={`${item.sectionId}|${item.academicYear}`}
                            value={`${item.sectionId}|${item.academicYear}`}
                          >
                            {item.className} - {item.sectionName}
                            {" "}({item.academicYear})
                          </option>
                        ))}
                      </select>
                    </label>

                    <label className="text-sm font-medium text-gray-700">
                      Attendance Date
                      <input
                        type="date"
                        className={`${inputClass} mt-2`}
                        value={attendanceDate}
                        max={localDate()}
                        onChange={(e) =>
                          setAttendanceDate(e.target.value)
                        }
                        required
                      />
                    </label>
                  </div>

                  {rosterError && (
                    <p
                      role="alert"
                      className="mt-4 text-sm text-red-600"
                    >
                      {rosterError}
                    </p>
                  )}

                  {alreadyMarked && (
                    <div className="mt-5 rounded-lg bg-amber-50 p-4 text-sm text-amber-800">
                      Daily attendance already exists for this
                      section and date. You can correct your saved
                      records below.
                    </div>
                  )}

                  <div className="mt-6 overflow-x-auto rounded-xl border border-gray-200">
                    <table className="w-full min-w-[650px] text-left">
                      <thead className="bg-indigo-50 text-sm">
                        <tr>
                          <th className="p-4">Admission No.</th>
                          <th className="p-4">Student</th>
                          <th className="p-4">Status</th>
                          <th className="p-4">Remarks</th>
                        </tr>
                      </thead>

                      <tbody>
                        {rosterLoading ? (
                          <tr>
                            <td
                              colSpan={4}
                              className="p-6 text-center text-gray-500"
                            >
                              Loading eligible students...
                            </td>
                          </tr>
                        ) : roster.length === 0 ? (
                          <tr>
                            <td
                              colSpan={4}
                              className="p-6 text-center text-gray-500"
                            >
                              No eligible students found.
                            </td>
                          </tr>
                        ) : (
                          roster.map((student) => (
                            <tr
                              key={student.id}
                              className="border-t border-gray-100"
                            >
                              <td className="p-3">
                                {student.admissionNumber}
                              </td>

                              <td className="p-3 font-medium">
                                {student.fullName}
                              </td>

                              <td className="p-3">
                                <select
                                  value={
                                    marks[student.id]?.status ||
                                    "PRESENT"
                                  }
                                  onChange={(e) =>
                                    updateMark(
                                      student.id,
                                      "status",
                                      e.target.value
                                    )
                                  }
                                  disabled={alreadyMarked}
                                  className={inputClass}
                                >
                                  {STATUSES.map((status) => (
                                    <option
                                      key={status}
                                      value={status}
                                    >
                                      {status}
                                    </option>
                                  ))}
                                </select>
                              </td>

                              <td className="p-3">
                                <input
                                  type="text"
                                  maxLength={255}
                                  value={
                                    marks[student.id]?.remarks || ""
                                  }
                                  onChange={(e) =>
                                    updateMark(
                                      student.id,
                                      "remarks",
                                      e.target.value
                                    )
                                  }
                                  disabled={alreadyMarked}
                                  placeholder="Optional"
                                  className={inputClass}
                                />
                              </td>
                            </tr>
                          ))
                        )}
                      </tbody>
                    </table>
                  </div>

                  <div className="mt-5 flex justify-end">
                    <button
                      type="submit"
                      disabled={
                        saving ||
                        rosterLoading ||
                        !!rosterError ||
                        roster.length === 0 ||
                        alreadyMarked
                      }
                      className="rounded-lg bg-indigo-600 px-6 py-3 font-semibold text-white hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      {saving
                        ? "Saving Attendance..."
                        : "Submit Attendance"}
                    </button>
                  </div>
                </form>
              )}
            </section>
          )}

          {/* SESSION HISTORY */}
          <section>
            <div className="mb-5 flex flex-wrap items-center justify-between gap-4">
              <div>
                <h2 className="text-xl font-bold text-gray-900">
                  {isTeacher
                    ? "My Attendance History"
                    : "School Attendance Records"}
                </h2>

                <p className="mt-1 text-sm text-gray-500">
                  Select a session to view student attendance.
                </p>
              </div>

              <input
                type="search"
                value={search}
                onChange={(e) =>
                  setSearch(e.target.value)
                }
                placeholder="Search date, section or student..."
                className={`${inputClass} max-w-sm`}
              />
            </div>

            {loading ? (
              <div className="rounded-xl bg-white p-8 text-center text-gray-500">
                Loading attendance...
              </div>
            ) : filteredSessions.length === 0 ? (
              <div className="rounded-xl border border-gray-200 bg-white p-8 text-center text-gray-500">
                No attendance sessions found.
              </div>
            ) : (
              <div className="space-y-4">
                {filteredSessions.map((session) => {
                  const expanded = expandedId === session.id;

                  const present = session.records.filter(
                    (r) => r.status === "PRESENT"
                  ).length;

                  return (
                    <div
                      key={session.id}
                      className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm"
                    >
                      <div className="flex flex-wrap items-center justify-between gap-4 p-5">
                        <div>
                          <h3 className="font-bold text-gray-900">
                            {getSectionName(session.sectionId)}
                          </h3>

                          <p className="mt-1 text-sm text-gray-500">
                            {session.attendanceDate} •{" "}
                            {session.sessionType}
                          </p>

                          <p className="mt-2 text-sm text-gray-600">
                            {session.records.length} students •{" "}
                            {present} present
                          </p>

                          {isAdmin && (
                            <p className="mt-1 text-xs text-gray-500">
                              Marked by:{" "}
                              {teacherNames[session.markedBy] ||
                                `Teacher #${session.markedBy}`}
                            </p>
                          )}
                        </div>

                        <button
                          type="button"
                          onClick={() =>
                            toggleSession(session)
                          }
                          className="rounded-lg bg-indigo-100 px-4 py-2 text-sm font-medium text-indigo-700 hover:bg-indigo-200"
                        >
                          {expanded
                            ? "Hide Records"
                            : "View Records"}
                        </button>
                      </div>

                      {expanded && (
                        <div className="overflow-x-auto border-t border-gray-200">
                          <table className="w-full min-w-[650px] text-left">
                            <thead className="bg-indigo-50 text-sm">
                              <tr>
                                <th className="p-4">Student</th>
                                <th className="p-4">Status</th>
                                <th className="p-4">Remarks</th>
                                {isTeacher && (
                                  <th className="p-4">Action</th>
                                )}
                              </tr>
                            </thead>

                            <tbody>
                              {session.records.map((record) => (
                                <tr
                                  key={record.id}
                                  className="border-t border-gray-100"
                                >
                                  <td className="p-4 font-medium">
                                    {record.studentName}
                                  </td>

                                  <td className="p-3">
                                    {isTeacher ? (
                                      <select
                                        value={
                                          recordEdits[
                                            record.studentId
                                          ]?.status ||
                                          record.status
                                        }
                                        onChange={(e) =>
                                          updateCorrection(
                                            record.studentId,
                                            "status",
                                            e.target.value
                                          )
                                        }
                                        className={inputClass}
                                      >
                                        {STATUSES.map((status) => (
                                          <option
                                            key={status}
                                            value={status}
                                          >
                                            {status}
                                          </option>
                                        ))}
                                      </select>
                                    ) : (
                                      <span className="font-medium">
                                        {record.status}
                                      </span>
                                    )}
                                  </td>

                                  <td className="p-3">
                                    {isTeacher ? (
                                      <input
                                        value={
                                          recordEdits[
                                            record.studentId
                                          ]?.remarks ??
                                          record.remarks ??
                                          ""
                                        }
                                        maxLength={255}
                                        onChange={(e) =>
                                          updateCorrection(
                                            record.studentId,
                                            "remarks",
                                            e.target.value
                                          )
                                        }
                                        className={inputClass}
                                      />
                                    ) : (
                                      record.remarks || "—"
                                    )}
                                  </td>

                                  {isTeacher && (
                                    <td className="p-3">
                                      <button
                                        type="button"
                                        disabled={
                                          savingRecordId !== null
                                        }
                                        onClick={() =>
                                          saveCorrection(
                                            session.id,
                                            record
                                          )
                                        }
                                        className="rounded-lg bg-indigo-600 px-4 py-2 text-sm text-white hover:bg-indigo-700 disabled:opacity-50"
                                      >
                                        {savingRecordId === record.id
                                          ? "Saving..."
                                          : "Save"}
                                      </button>
                                    </td>
                                  )}
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </section>
        </main>
      </div>
    </div>
  );
}
