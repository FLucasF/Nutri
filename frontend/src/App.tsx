import { useState } from "react";
import { NavLink, Navigate, Route, Routes, useLocation } from "react-router-dom";
import { useAuth } from "./auth/AuthContext";
import { THEMES, type Theme, applyTheme, themeGuardado } from "./theme";
import Access from "./pages/Access";
import Patients from "./pages/Patients";
import PatientDetail from "./pages/PatientDetail";
import Foods from "./pages/Foods";
import FoodDetail from "./pages/FoodDetail";
import PatientForm from "./pages/PatientForm";
import Prescriptions from "./pages/Prescriptions";
import Questionnaires from "./pages/Questionnaires";
import PlanEditor from "./pages/PlanEditor";
import RecipeEditor from "./pages/RecipeEditor";
import PatientPlan from "./pages/PatientPlan";
import Anthropometry from "./pages/Anthropometry";
import Schedule from "./pages/Schedule";
import Team from "./pages/Team";
import Labtests from "./pages/Labtests";
import Finance from "./pages/Finance";
import Handouts from "./pages/Handouts";
import { FeedbackProvider } from "./componentes/Feedback";

function Protected({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth();
  const local = useLocation();

  if (loading) {
    return <p className="loading">Loading…</p>;
  }
  if (!user) {
    return <Navigate to="/access" replace state={{ from: local.pathname }} />;
  }
  return <>{children}</>;
}

function Layout({ children }: { children: React.ReactNode }) {
  const { user, logout } = useAuth();
  const isAssistant = user?.role === "ASSISTANT";

  return (
    <div className="app">
      <aside className="bar-side">
        <div className="brand">
          NutriPlan
          <small>consultório</small>
        </div>

        <nav>
          <NavLink to="/patients" className={({ isActive }) => (isActive ? "active" : "")}>
            Patients
          </NavLink>
          <NavLink to="/schedule" className={({ isActive }) => (isActive ? "active" : "")}>
            Schedule
          </NavLink>
          {/* The receptionist runs the front desk. Showing what they cannot
              open would only produce an error screen at the end of the click. */}
          {!isAssistant && (
            <>
          <NavLink to="/prescriptions" className={({ isActive }) => (isActive ? "active" : "")}>
            Prescrições
          </NavLink>
          <NavLink to="/foods" className={({ isActive }) => (isActive ? "active" : "")}>
            Foods
          </NavLink>
          <NavLink to="/handouts" className={({ isActive }) => (isActive ? "active" : "")}>
            Orientações
          </NavLink>
          <NavLink to="/questionnaires" className={({ isActive }) => (isActive ? "active" : "")}>
            Questionários
          </NavLink>
          <NavLink to="/finance" className={({ isActive }) => (isActive ? "active" : "")}>
            Finance
          </NavLink>
            </>
          )}
        </nav>

        <ThemePicker />

        <div className="footer-side">
          <b>{user?.name}</b>
          <span className="minusculo">Plan {user?.plan.toLowerCase()}</span>
          {!isAssistant && (
            <NavLink to="/team" className="minusculo" style={{ display: "block" }}>
              Equipe do consultório
            </NavLink>
          )}
          <button
            type="button"
            className="button secundario pequeno"
            style={{ marginTop: "0.6rem", width: "100%", justifyContent: "center" }}
            onClick={logout}
          >
            Logout
          </button>
        </div>
      </aside>

      <main className="content">{children}</main>
    </div>
  );
}

/**
 * Theme choice. It lives on the rail because it is an adjustment to the
 * surroundings, and not an action on the patient — it does not compete for
 * space with the content of the screen.
 */
function ThemePicker() {
  const [theme, setTheme] = useState<Theme>(themeGuardado);

  function choose(novo: Theme) {
    applyTheme(novo);
    setTheme(novo);
  }

  return (
    <div className="picker-theme" role="group" aria-label="Tema da interface">
      {THEMES.map((option) => (
        <button
          key={option.value}
          type="button"
          aria-pressed={theme === option.value}
          onClick={() => choose(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}

export default function App() {
  return (
    /* The feedback wraps everything, including the screens the patient opens: an
       error sending the questionnaire needs the same clarity as an internal
       one. */
    <FeedbackProvider>
      <Routes>
        {/* Opened by the patient, without authentication and outside the practice. */}
        <Route path="/plan/:identifier" element={<PatientPlan />} />
        {/* A questionnaire answered by the patient, also without authentication. */}
        <Route path="/form/:identifier" element={<PatientForm />} />

        <Route path="/access" element={<Access />} />

        <Route
          path="/*"
          element={
            <Protected>
              <Layout>
                <Routes>
                  <Route path="/" element={<Navigate to="/patients" replace />} />
                  <Route path="/patients" element={<Patients />} />
                  <Route path="/patients/:id" element={<PatientDetail />} />
                  <Route path="/patients/:id/anthropometry" element={<Anthropometry />} />
                  <Route path="/patients/:id/labtests" element={<Labtests />} />
                  <Route path="/schedule" element={<Schedule />} />
                  <Route path="/finance" element={<Finance />} />
                  <Route path="/foods" element={<Foods />} />
                  <Route path="/foods/:id" element={<FoodDetail />} />
                  <Route path="/recipes/new" element={<RecipeEditor />} />
                  <Route path="/recipes/:id" element={<RecipeEditor />} />
                  <Route path="/team" element={<Team />} />
                  <Route path="/handouts" element={<Handouts />} />
                  <Route path="/questionnaires" element={<Questionnaires />} />
                  <Route path="/prescriptions" element={<Prescriptions />} />
                  <Route path="/prescriptions/new" element={<PlanEditor />} />
                  <Route path="/prescriptions/:id" element={<PlanEditor />} />
                  <Route path="*" element={<p className="empty">Página não encontrada.</p>} />
              </Routes>
            </Layout>
          </Protected>
        }
        />
      </Routes>
    </FeedbackProvider>
  );
}
