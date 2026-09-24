import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { Link, NavLink, Navigate, Route, Routes, matchPath, useLocation } from "react-router-dom";
import {
  Apple,
  BarChart3,
  BookOpen,
  CalendarDays,
  Handshake,
  ClipboardList,
  ListChecks,
  LogOut,
  Menu,
  Monitor,
  Moon,
  Package,
  Sun,
  UserCog,
  Users,
  Wallet,
  type LucideIcon,
} from "lucide-react";
import { useAuth } from "./auth/AuthContext";
import type { UserSummary } from "./api/types";
import { THEMES, type Theme, applyTheme, themeGuardado, watchTheme } from "./theme";
import { useIsCompact, useIsPhone, useMediaQuery } from "./hooks/useMediaQuery";
import { useScrolled } from "./hooks/useScrolled";
import { Avatar } from "./components/Avatar";
import { MacroRing } from "./components/MacroRing";
import { Sheet } from "./components/Sheet";
import { FeedbackProvider } from "./components/Feedback";
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
import Anamneses from "./pages/Anamneses";
import EnergyPlans from "./pages/EnergyPlans";
import Schedule from "./pages/Schedule";
import Team from "./pages/Team";
import Labtests from "./pages/Labtests";
import Finance from "./pages/Finance";
import Handouts from "./pages/Handouts";
import Partners from "./pages/Partners";
import Packages from "./pages/Packages";
import Statistics from "./pages/Statistics";

function Protected({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  const local = useLocation();

  if (loading) {
    return <p className="loading">Carregando…</p>;
  }
  if (!user) {
    return <Navigate to="/access" replace state={{ from: local.pathname }} />;
  }
  return <>{children}</>;
}

/* ------------------------------------------------------------ navigation */

type NavEntry = { to: string; label: string; icon: LucideIcon };
type NavGroup = { label: string; items: NavEntry[] };

const PATIENTS: NavEntry = { to: "/patients", label: "Pacientes", icon: Users };
const SCHEDULE: NavEntry = { to: "/schedule", label: "Agenda", icon: CalendarDays };
const PRESCRIPTIONS: NavEntry = { to: "/prescriptions", label: "Prescrições", icon: ClipboardList };
const FOODS: NavEntry = { to: "/foods", label: "Alimentos", icon: Apple };
const HANDOUTS: NavEntry = { to: "/handouts", label: "Orientações", icon: BookOpen };
const QUESTIONNAIRES: NavEntry = { to: "/questionnaires", label: "Questionários", icon: ListChecks };
const FINANCE: NavEntry = { to: "/finance", label: "Financeiro", icon: Wallet };
const TEAM: NavEntry = { to: "/team", label: "Equipe", icon: UserCog };
const STATISTICS: NavEntry = { to: "/statistics", label: "Estatísticas", icon: BarChart3 };
const PARTNERS: NavEntry = { to: "/partners", label: "Parceiros", icon: Handshake };
const PACKAGES: NavEntry = { to: "/packages", label: "Pacotes", icon: Package };

/**
 * The receptionist runs the front desk. Showing what they cannot open would
 * only produce an error screen at the end of the click, so the assistant sees
 * the clinic group alone, without prescriptions.
 */
function groupsFor(isAssistant: boolean): NavGroup[] {
  if (isAssistant) {
    return [{ label: "Clínica", items: [PATIENTS, SCHEDULE] }];
  }
  return [
    { label: "Clínica", items: [PATIENTS, SCHEDULE, PRESCRIPTIONS] },
    { label: "Biblioteca", items: [FOODS, HANDOUTS, QUESTIONNAIRES] },
    { label: "Consultório", items: [FINANCE, STATISTICS, PARTNERS, PACKAGES, TEAM] },
  ];
}

/** The phone tab bar: four destinations plus "Mais", which opens the drawer. */
function tabsFor(isAssistant: boolean): NavEntry[] {
  return isAssistant ? [PATIENTS, SCHEDULE] : [PATIENTS, SCHEDULE, PRESCRIPTIONS, FOODS];
}

/**
 * Editors that take the whole screen on the phone and the tablet: no app bar,
 * no tab bar, and the page's own header becomes the sticky top. On a wider
 * screen nothing changes.
 */
const FOCUSED_ROUTES = ["/prescriptions/new", "/prescriptions/:id", "/recipes/new", "/recipes/:id"];

/** Between the full sidebar and the app bar: a 64px rail of icons. */
const RAIL_QUERY = "(min-width: 901px) and (max-width: 1180px)";

function isUnder(pathname: string, to: string): boolean {
  return pathname === to || pathname.startsWith(to + "/");
}

/* ----------------------------------------------------------------- theme */

const THEME_ICONS: Record<Theme, LucideIcon> = { system: Monitor, light: Sun, dark: Moon };
const NEXT_THEME: Record<Theme, Theme> = { system: "light", light: "dark", dark: "system" };

function themeLabel(theme: Theme): string {
  return THEMES.find((option) => option.value === theme)?.label ?? theme;
}

type ThemeProps = { theme: Theme; onChange: (theme: Theme) => void };

/**
 * Three states, not a two-way switch: "system" has to be choosable again after
 * somebody pins light or dark. Icon-only segments; the label is read, not seen.
 */
function ThemeSegmented({ theme, onChange }: ThemeProps) {
  return (
    <div className="segmented icons" role="group" aria-label="Tema da interface">
      {THEMES.map((option) => {
        const Icon = THEME_ICONS[option.value];
        return (
          <button
            key={option.value}
            type="button"
            aria-pressed={theme === option.value}
            title={option.label}
            onClick={() => onChange(option.value)}
          >
            <Icon aria-hidden="true" />
            <span className="visually-hidden">{option.label}</span>
          </button>
        );
      })}
    </div>
  );
}

/** Where three buttons do not fit (rail, app bar): one button that cycles. */
function ThemeCycleButton({ theme, onChange }: ThemeProps) {
  const next = NEXT_THEME[theme];
  const Icon = THEME_ICONS[theme];
  const label = `Tema: ${themeLabel(theme)}. Mudar para ${themeLabel(next).toLowerCase()}`;
  return (
    <button
      type="button"
      className="button icon ghost"
      aria-label={label}
      title={label}
      onClick={() => onChange(next)}
    >
      <Icon aria-hidden="true" />
    </button>
  );
}

/* --------------------------------------------------------------- sidebar */

function NavItem({ entry, rail }: { entry: NavEntry; rail: boolean }) {
  const Icon = entry.icon;
  return (
    <NavLink
      to={entry.to}
      className={({ isActive }) => (isActive ? "nav-item active" : "nav-item")}
      // On the rail the label is hidden from sight but not from the reader;
      // the title gives the pointer its tooltip.
      title={rail ? entry.label : undefined}
      aria-label={rail ? entry.label : undefined}
    >
      <Icon aria-hidden="true" />
      <span className="nav-label">{entry.label}</span>
    </NavLink>
  );
}

function UserCard({ user }: { user: UserSummary }) {
  return (
    <div className="user-card">
      <Avatar name={user.name} size={32} />
      <div className="user-body">
        <span className="user-name">{user.name}</span>
        <span className="user-plan">Plano {user.plan.toLowerCase()}</span>
      </div>
    </div>
  );
}

type AccountProps = { user: UserSummary; isAssistant: boolean; onLogout: () => void };

function AccountActions({ isAssistant, onLogout }: Omit<AccountProps, "user">) {
  return (
    <>
      {!isAssistant && (
        <NavLink to="/team" className="button ghost pequeno">
          <UserCog aria-hidden="true" />
          Equipe do consultório
        </NavLink>
      )}
      <button type="button" className="button ghost pequeno" onClick={onLogout}>
        <LogOut aria-hidden="true" />
        Sair
      </button>
    </>
  );
}

/**
 * The rail has no room for a user card: the avatar becomes a button and the
 * card, the team link and Sair open in a small menu beside it. It closes on
 * Escape, on a click outside and when the route changes.
 */
function RailUserMenu({ user, isAssistant, onLogout }: AccountProps) {
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const menuId = useId();
  const { pathname } = useLocation();

  useEffect(() => {
    setOpen(false);
  }, [pathname]);

  useEffect(() => {
    if (!open) return;
    function onPointerDown(event: PointerEvent) {
      if (root.current && !root.current.contains(event.target as Node)) setOpen(false);
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") setOpen(false);
    }
    document.addEventListener("pointerdown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("pointerdown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [open]);

  return (
    <div className="rail-user" ref={root}>
      <button
        type="button"
        className="rail-avatar"
        aria-label={`Conta de ${user.name}`}
        title={user.name}
        aria-expanded={open}
        aria-controls={menuId}
        onClick={() => setOpen((current) => !current)}
      >
        <Avatar name={user.name} size={32} />
      </button>
      {open && (
        <div className="rail-menu" id={menuId}>
          <UserCard user={user} />
          <AccountActions isAssistant={isAssistant} onLogout={onLogout} />
        </div>
      )}
    </div>
  );
}

type SidebarProps = AccountProps & ThemeProps & { rail: boolean };

/**
 * The same markup serves the 240px sidebar, the 64px rail (labels hidden by
 * the stylesheet, footer folded into two buttons) and the drawer on the phone.
 */
function Sidebar({ user, isAssistant, onLogout, theme, onChange, rail }: SidebarProps) {
  return (
    <aside className="sidebar">
      <Link to="/patients" className="sidebar-brand" title={rail ? "NutriPlan" : undefined}>
        <MacroRing size={28} className="brand-mark" />
        <span className="brand-name">
          NutriPlan
          <span className="brand-eyebrow">Consultório</span>
        </span>
      </Link>

      <nav className="sidebar-nav" aria-label="Principal">
        {groupsFor(isAssistant).map((group) => (
          <div className="nav-group" key={group.label}>
            <span className="nav-group-label">{group.label}</span>
            {group.items.map((entry) => (
              <NavItem key={entry.to} entry={entry} rail={rail} />
            ))}
          </div>
        ))}
      </nav>

      <div className="sidebar-footer">
        {rail ? (
          <>
            <ThemeCycleButton theme={theme} onChange={onChange} />
            <RailUserMenu user={user} isAssistant={isAssistant} onLogout={onLogout} />
          </>
        ) : (
          <>
            <ThemeSegmented theme={theme} onChange={onChange} />
            <UserCard user={user} />
            <AccountActions isAssistant={isAssistant} onLogout={onLogout} />
          </>
        )}
      </div>
    </aside>
  );
}

/* ------------------------------------------------------- app bar / tab bar */

function AppBar({ onMenu, theme, onChange }: ThemeProps & { onMenu: () => void }) {
  const scrolled = useScrolled();
  return (
    <header className="appbar" data-scrolled={scrolled ? "true" : "false"}>
      <button
        type="button"
        className="button icon ghost"
        aria-label="Abrir menu"
        title="Abrir menu"
        onClick={onMenu}
      >
        <Menu aria-hidden="true" />
      </button>
      <Link to="/patients" className="appbar-brand">
        <MacroRing size={24} className="brand-mark" />
        <span className="brand-name">NutriPlan</span>
      </Link>
      <ThemeCycleButton theme={theme} onChange={onChange} />
    </header>
  );
}

function TabBar({ isAssistant, onMore }: { isAssistant: boolean; onMore: () => void }) {
  const { pathname } = useLocation();
  const tabs = tabsFor(isAssistant);
  // Whatever is not a tab of its own lives behind "Mais", which then reads as current.
  const elsewhere = !tabs.some((entry) => isUnder(pathname, entry.to));

  return (
    <nav className="tabbar" aria-label="Atalhos">
      {tabs.map((entry) => {
        const Icon = entry.icon;
        return (
          <NavLink
            key={entry.to}
            to={entry.to}
            className={({ isActive }) => (isActive ? "tabbar-item active" : "tabbar-item")}
          >
            <span className="tabbar-icon">
              <Icon aria-hidden="true" />
            </span>
            {entry.label}
          </NavLink>
        );
      })}
      <button type="button" className={elsewhere ? "tabbar-item active" : "tabbar-item"} onClick={onMore}>
        <span className="tabbar-icon">
          <Menu aria-hidden="true" />
        </span>
        Mais
      </button>
    </nav>
  );
}

/* ---------------------------------------------------------------- layout */

function Layout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth();
  const { pathname } = useLocation();
  const compact = useIsCompact();
  const phone = useIsPhone();
  const rail = useMediaQuery(RAIL_QUERY);
  const [theme, setTheme] = useState<Theme>(themeGuardado);
  const [drawerOpen, setDrawerOpen] = useState(false);

  // Navigating from inside the drawer is the reason it was opened.
  useEffect(() => {
    setDrawerOpen(false);
  }, [pathname]);

  // Protected only renders the layout with a user; the check keeps the types honest.
  if (!user) return null;

  const isAssistant = user.role === "ASSISTANT";
  const focused = FOCUSED_ROUTES.some((pattern) => matchPath(pattern, pathname) !== null);

  function chooseTheme(next: Theme) {
    applyTheme(next);
    setTheme(next);
  }

  const sidebar = (
    <Sidebar
      user={user}
      isAssistant={isAssistant}
      onLogout={logout}
      theme={theme}
      onChange={chooseTheme}
      rail={rail && !compact}
    />
  );

  return (
    <div className={focused ? "app focused" : "app"}>
      {!compact && sidebar}
      {compact && !focused && (
        <AppBar onMenu={() => setDrawerOpen(true)} theme={theme} onChange={chooseTheme} />
      )}

      <main className="content">{children}</main>

      {phone && !focused && <TabBar isAssistant={isAssistant} onMore={() => setDrawerOpen(true)} />}

      {compact && (
        <Sheet
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          title="Menu"
          side="left"
          className="drawer"
          hideTitle
        >
          {sidebar}
        </Sheet>
      )}
    </div>
  );
}

export default function App() {
  // The browser chrome colour follows the app's theme from the first render on,
  // on every route — the patient's public pages included.
  useEffect(() => {
    watchTheme();
  }, []);

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
                  <Route path="/patients/:id/anamneses" element={<Anamneses />} />
                  <Route path="/patients/:id/energy" element={<EnergyPlans />} />
                  <Route path="/patients/:id/anthropometry" element={<Anthropometry />} />
                  <Route path="/patients/:id/labtests" element={<Labtests />} />
                  <Route path="/schedule" element={<Schedule />} />
                  <Route path="/finance" element={<Finance />} />
                  <Route path="/statistics" element={<Statistics />} />
                  <Route path="/partners" element={<Partners />} />
                  <Route path="/packages" element={<Packages />} />
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
