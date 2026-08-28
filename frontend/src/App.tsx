import { useState } from "react";
import { NavLink, Navigate, Route, Routes, useLocation } from "react-router-dom";
import { useAuth } from "./auth/AuthContext";
import { TEMAS, type Tema, aplicarTema, temaGuardado } from "./tema";
import Acesso from "./pages/Acesso";
import Pacientes from "./pages/Pacientes";
import PacienteDetalhe from "./pages/PacienteDetalhe";
import Alimentos from "./pages/Alimentos";
import AlimentoDetalhe from "./pages/AlimentoDetalhe";
import FormularioDoPaciente from "./pages/FormularioDoPaciente";
import Prescricoes from "./pages/Prescricoes";
import Questionarios from "./pages/Questionarios";
import EditorDePlano from "./pages/EditorDePlano";
import EditorDeReceita from "./pages/EditorDeReceita";
import PlanoDoPaciente from "./pages/PlanoDoPaciente";
import Antropometria from "./pages/Antropometria";
import Agenda from "./pages/Agenda";
import Equipe from "./pages/Equipe";
import Exames from "./pages/Exames";
import Financeiro from "./pages/Financeiro";
import Orientacoes from "./pages/Orientacoes";
import { ProvedorDeRecado } from "./componentes/Recado";

function Protegida({ children }: { children: React.ReactNode }) {
  const { usuario, carregando } = useAuth();
  const local = useLocation();

  if (carregando) {
    return <p className="carregando">Carregando…</p>;
  }
  if (!usuario) {
    return <Navigate to="/acesso" replace state={{ de: local.pathname }} />;
  }
  return <>{children}</>;
}

function Estrutura({ children }: { children: React.ReactNode }) {
  const { usuario, sair } = useAuth();
  const ehSecretaria = usuario?.perfil === "SECRETARIA";

  return (
    <div className="app">
      <aside className="barra-lateral">
        <div className="marca">
          NutriPlan
          <small>consultório</small>
        </div>

        <nav>
          <NavLink to="/pacientes" className={({ isActive }) => (isActive ? "ativo" : "")}>
            Pacientes
          </NavLink>
          <NavLink to="/agenda" className={({ isActive }) => (isActive ? "ativo" : "")}>
            Agenda
          </NavLink>
          {/* A secretaria opera a recepcao. Mostrar o que ela nao pode abrir
              so produziria uma tela de erro no fim do clique. */}
          {!ehSecretaria && (
            <>
          <NavLink to="/prescricoes" className={({ isActive }) => (isActive ? "ativo" : "")}>
            Prescrições
          </NavLink>
          <NavLink to="/alimentos" className={({ isActive }) => (isActive ? "ativo" : "")}>
            Alimentos
          </NavLink>
          <NavLink to="/orientacoes" className={({ isActive }) => (isActive ? "ativo" : "")}>
            Orientações
          </NavLink>
          <NavLink to="/questionarios" className={({ isActive }) => (isActive ? "ativo" : "")}>
            Questionários
          </NavLink>
          <NavLink to="/financeiro" className={({ isActive }) => (isActive ? "ativo" : "")}>
            Financeiro
          </NavLink>
            </>
          )}
        </nav>

        <SeletorDeTema />

        <div className="rodape-lateral">
          <b>{usuario?.nome}</b>
          <span className="minusculo">Plano {usuario?.plano.toLowerCase()}</span>
          {!ehSecretaria && (
            <NavLink to="/equipe" className="minusculo" style={{ display: "block" }}>
              Equipe do consultório
            </NavLink>
          )}
          <button
            type="button"
            className="botao secundario pequeno"
            style={{ marginTop: "0.6rem", width: "100%", justifyContent: "center" }}
            onClick={sair}
          >
            Sair
          </button>
        </div>
      </aside>

      <main className="conteudo">{children}</main>
    </div>
  );
}

/**
 * Escolha do tema. Vive no trilho porque e ajuste de ambiente, e nao acao sobre
 * o paciente — nao disputa espaco com o conteudo da tela.
 */
function SeletorDeTema() {
  const [tema, setTema] = useState<Tema>(temaGuardado);

  function escolher(novo: Tema) {
    aplicarTema(novo);
    setTema(novo);
  }

  return (
    <div className="seletor-tema" role="group" aria-label="Tema da interface">
      {TEMAS.map((opcao) => (
        <button
          key={opcao.valor}
          type="button"
          aria-pressed={tema === opcao.valor}
          onClick={() => escolher(opcao.valor)}
        >
          {opcao.rotulo}
        </button>
      ))}
    </div>
  );
}

export default function App() {
  return (
    /* O recado envolve tudo, inclusive as telas abertas pelo paciente: um erro
       ao enviar o questionário precisa da mesma clareza que um erro interno. */
    <ProvedorDeRecado>
      <Routes>
        {/* Aberta pelo paciente, sem autenticação e fora da estrutura do consultório. */}
        <Route path="/plano/:identificador" element={<PlanoDoPaciente />} />
        {/* Questionário respondido pelo paciente, também sem autenticação. */}
        <Route path="/formulario/:identificador" element={<FormularioDoPaciente />} />

        <Route path="/acesso" element={<Acesso />} />

        <Route
          path="/*"
          element={
            <Protegida>
              <Estrutura>
                <Routes>
                  <Route path="/" element={<Navigate to="/pacientes" replace />} />
                  <Route path="/pacientes" element={<Pacientes />} />
                  <Route path="/pacientes/:id" element={<PacienteDetalhe />} />
                  <Route path="/pacientes/:id/antropometria" element={<Antropometria />} />
                  <Route path="/pacientes/:id/exames" element={<Exames />} />
                  <Route path="/agenda" element={<Agenda />} />
                  <Route path="/financeiro" element={<Financeiro />} />
                  <Route path="/alimentos" element={<Alimentos />} />
                  <Route path="/alimentos/:id" element={<AlimentoDetalhe />} />
                  <Route path="/receitas/novo" element={<EditorDeReceita />} />
                  <Route path="/receitas/:id" element={<EditorDeReceita />} />
                  <Route path="/equipe" element={<Equipe />} />
                  <Route path="/orientacoes" element={<Orientacoes />} />
                  <Route path="/questionarios" element={<Questionarios />} />
                  <Route path="/prescricoes" element={<Prescricoes />} />
                  <Route path="/prescricoes/novo" element={<EditorDePlano />} />
                  <Route path="/prescricoes/:id" element={<EditorDePlano />} />
                  <Route path="*" element={<p className="vazio">Página não encontrada.</p>} />
              </Routes>
            </Estrutura>
          </Protegida>
        }
        />
      </Routes>
    </ProvedorDeRecado>
  );
}
