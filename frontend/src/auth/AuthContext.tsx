import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { api, defineSessionExpiredHandling, storeToken, readToken } from "../api/client";
import type { UserSummary } from "../api/types";

interface Context {
  user: UserSummary | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (data: {
    name: string;
    email: string;
    password: string;
    crn?: string;
    phone?: string;
  }) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<Context | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserSummary | null>(null);
  const [loading, setLoading] = useState(true);

  const logout = useCallback(() => {
    storeToken(null);
    setUser(null);
  }, []);

  /**
   * Restores the session from the stored token.
   *
   * The user is queried again on the server instead of being read from the
   * token: if the account was deactivated or the role changed since the last
   * visit, the interface has to reflect that already at opening, and not only
   * at the first blocked action.
   */
  useEffect(() => {
    defineSessionExpiredHandling(logout);

    if (!readToken()) {
      setLoading(false);
      return;
    }
    api
      .eu()
      .then(setUser)
      .catch(() => storeToken(null))
      .finally(() => setLoading(false));
  }, [logout]);

  const login = useCallback(async (email: string, password: string) => {
    const answer = await api.login(email, password);
    storeToken(answer.token);
    setUser(answer.user);
  }, []);

  const register = useCallback<Context["register"]>(async (data) => {
    const answer = await api.register(data);
    storeToken(answer.token);
    setUser(answer.user);
  }, []);

  const value = useMemo(
    () => ({ user, loading, login, register, logout }),
    [user, loading, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth precisa estar dentro de AuthProvider");
  }
  return context;
}
