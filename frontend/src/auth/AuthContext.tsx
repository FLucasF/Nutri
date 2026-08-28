import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { api, definirTratamentoDeSessaoExpirada, gravarToken, lerToken } from "../api/client";
import type { UsuarioResumo } from "../api/types";

interface Contexto {
  usuario: UsuarioResumo | null;
  carregando: boolean;
  entrar: (email: string, senha: string) => Promise<void>;
  cadastrar: (dados: {
    nome: string;
    email: string;
    senha: string;
    crn?: string;
    telefone?: string;
  }) => Promise<void>;
  sair: () => void;
}

const AuthContext = createContext<Contexto | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<UsuarioResumo | null>(null);
  const [carregando, setCarregando] = useState(true);

  const sair = useCallback(() => {
    gravarToken(null);
    setUsuario(null);
  }, []);

  /**
   * Restaura a sessão a partir do token guardado.
   *
   * O usuário é reconsultado no servidor em vez de lido do token: se a conta
   * foi desativada ou o perfil mudou desde o último acesso, a interface precisa
   * refletir isso já na abertura, e não só na primeira ação bloqueada.
   */
  useEffect(() => {
    definirTratamentoDeSessaoExpirada(sair);

    if (!lerToken()) {
      setCarregando(false);
      return;
    }
    api
      .eu()
      .then(setUsuario)
      .catch(() => gravarToken(null))
      .finally(() => setCarregando(false));
  }, [sair]);

  const entrar = useCallback(async (email: string, senha: string) => {
    const resposta = await api.login(email, senha);
    gravarToken(resposta.token);
    setUsuario(resposta.usuario);
  }, []);

  const cadastrar = useCallback<Contexto["cadastrar"]>(async (dados) => {
    const resposta = await api.cadastrar(dados);
    gravarToken(resposta.token);
    setUsuario(resposta.usuario);
  }, []);

  const valor = useMemo(
    () => ({ usuario, carregando, entrar, cadastrar, sair }),
    [usuario, carregando, entrar, cadastrar, sair],
  );

  return <AuthContext.Provider value={valor}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const contexto = useContext(AuthContext);
  if (!contexto) {
    throw new Error("useAuth precisa estar dentro de AuthProvider");
  }
  return contexto;
}
