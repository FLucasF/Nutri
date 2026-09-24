import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { FlaskConical, Star, Trash2, UtensilsCrossed } from "lucide-react";
import { api } from "../api/client";
import { explainError } from "../api/errors";
import { useFeedback } from "../components/Feedback";
import { count } from "../text";
import type { FavoriteMeal, LabtestPanel } from "../api/types";

/**
 * Os favoritos do consultório, num lugar só.
 *
 * "Biblioteca de favoritos (refeições, pedidos de exame)": as refeições
 * salvas nascem no editor do cardápio, e os painéis de exame no pedido de
 * exames — cada um onde é usado. O que faltava era ver e arrumar todos de
 * uma vez, sem abrir um plano ou um paciente para isso.
 */
export default function Favorites() {
  const feedback = useFeedback();
  const [meals, setMeals] = useState<FavoriteMeal[]>([]);
  const [panels, setPanels] = useState<LabtestPanel[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [savedMeals, allPanels] = await Promise.all([
        api.mealFavorites.list(),
        api.labtests.panels(),
      ]);
      setMeals(savedMeals);
      setPanels(allPanels.filter((p) => p.own && !p.systemPanel));
    } catch (e) {
      setError(explainError(e, "abrir os favoritos"));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function removeMeal(meal: FavoriteMeal) {
    if (!confirm(`Remover a refeição salva "${meal.name}"? Os planos que já a usaram não mudam.`)) return;
    try {
      await api.mealFavorites.remove(meal.id);
      feedback.confirm(`"${meal.name}" saiu dos favoritos.`);
      await load();
    } catch (e) {
      setError(explainError(e, "remover a refeição salva"));
    }
  }

  async function removePanel(panel: LabtestPanel) {
    if (!confirm(`Remover o painel "${panel.name}"? Os pedidos que já o usaram não mudam.`)) return;
    try {
      await api.labtests.removePanel(panel.id);
      feedback.confirm(`Painel "${panel.name}" removido.`);
      await load();
    } catch (e) {
      setError(explainError(e, "remover o painel"));
    }
  }

  return (
    <div className="favorites-page">
      <div className="header-page">
        <div>
          <h1>Favoritos</h1>
          <p>
            {loading
              ? "Carregando…"
              : `${count(meals.length, "refeição salva", "refeições salvas")} · ${count(
                  panels.length,
                  "painel de exames",
                  "painéis de exames",
                )}`}
          </p>
        </div>
      </div>

      {error && (
        <div className="warning error mb-3" role="alert">
          {error}
        </div>
      )}

      <section className="card mb-3">
        <div className="card-head">
          <div>
            <h2 className="card-title">Refeições salvas</h2>
            <p className="card-sub">
              No editor do cardápio, a estrela da refeição a guarda aqui; em outro plano, ela volta pelo
              botão “Refeições salvas”.
            </p>
          </div>
        </div>
        {!loading && meals.length === 0 ? (
          <div className="empty">
            <span className="empty-icon">
              <UtensilsCrossed aria-hidden="true" />
            </span>
            <span className="empty-title">Nenhuma refeição salva.</span>
            <span className="empty-hint">
              Abra um plano em <Link to="/prescriptions">Prescrições</Link> e salve a refeição que
              você repete.
            </span>
          </div>
        ) : (
          <ul className="favorites-list">
            {meals.map((meal) => (
              <li key={meal.id} className="favorite-row">
                <span className="favorite-icon" aria-hidden="true">
                  <Star />
                </span>
                <span className="favorite-body">
                  <strong>{meal.name}</strong>
                  <span className="favorite-meta">
                    {meal.mealName} · {count(meal.itemsTotal, "item", "itens")}
                    {meal.energyKcal !== undefined && meal.energyKcal !== null
                      ? ` · ${Math.round(meal.energyKcal)} kcal`
                      : ""}
                  </span>
                  {meal.items.length > 0 && (
                    <span className="favorite-items">
                      {meal.items
                        .map((item) => item.description)
                        .filter(Boolean)
                        .slice(0, 5)
                        .join(", ")}
                      {meal.items.length > 5 ? "…" : ""}
                    </span>
                  )}
                </span>
                <button
                  type="button"
                  className="button perigo pequeno"
                  onClick={() => void removeMeal(meal)}
                  aria-label={`Remover a refeição salva ${meal.name}`}
                >
                  <Trash2 aria-hidden="true" />
                  Remover
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="card">
        <div className="card-head">
          <div>
            <h2 className="card-title">Painéis de exames</h2>
            <p className="card-sub">
              Os seus pedidos de exame favoritos. Monte-os no pedido de exames do paciente com
              “Salvar como painel”; os 25 painéis do sistema ficam lá, prontos para usar.
            </p>
          </div>
        </div>
        {!loading && panels.length === 0 ? (
          <div className="empty">
            <span className="empty-icon">
              <FlaskConical aria-hidden="true" />
            </span>
            <span className="empty-title">Nenhum painel seu ainda.</span>
            <span className="empty-hint">
              No pedido de exames de um paciente, escolha os exames e use “Salvar como painel”.
            </span>
          </div>
        ) : (
          <ul className="favorites-list">
            {panels.map((panel) => (
              <li key={panel.id} className="favorite-row">
                <span className="favorite-icon" aria-hidden="true">
                  <FlaskConical />
                </span>
                <span className="favorite-body">
                  <strong>{panel.name}</strong>
                  <span className="favorite-meta">{count(panel.parameters.length, "exame", "exames")}</span>
                  <span className="favorite-items">
                    {panel.parameters
                      .slice(0, 8)
                      .map((p) => p.name)
                      .join(", ")}
                    {panel.parameters.length > 8 ? "…" : ""}
                  </span>
                </span>
                <button
                  type="button"
                  className="button perigo pequeno"
                  onClick={() => void removePanel(panel)}
                  aria-label={`Remover o painel ${panel.name}`}
                >
                  <Trash2 aria-hidden="true" />
                  Remover
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
