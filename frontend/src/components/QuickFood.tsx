import { useState, type FormEvent } from "react";

import { api } from "../api/client";
import { explainError } from "../api/errors";
import type { FoodDetail, Measure } from "../api/types";

/** Uma linha de medida caseira em edição. */
interface MeasureRow {
  key: string;
  description: string;
  grams: string;
}

let sequence = 0;
const newRow = (): MeasureRow => ({
  key: `m${(sequence += 1)}`,
  description: "",
  grams: "",
});

/**
 * As linhas preenchidas, na forma que a API espera.
 *
 * A primeira vira a medida padrão: é a que o cardápio já traz escolhida ao
 * usar o alimento, e sem nenhuma padrão a porção volta a ser dita em gramas.
 */
function measuresToSend(rows: MeasureRow[]) {
  return rows
    .map((row) => ({
      description: row.description.trim(),
      grams: Number(row.grams.trim().replace(",", ".")),
    }))
    .filter((row) => row.description && Number.isFinite(row.grams) && row.grams > 0)
    .map((row, index) => ({ ...row, standard: index === 0 }));
}

/**
 * Cadastrar alimento e medida caseira sem sair do cardápio.
 *
 * O cliente chama isso de ponto principal (páginas 36 e 37): "preciso de uma
 * maneira de adicionar um alimento, dentro do momento em que estou fazendo um
 * cardápio, sem ter que ir ao menu principal. Da mesma forma, preciso que ao
 * selecionar um alimento eu possa adicionar nele uma medida caseira".
 *
 * A razão é de fluxo, não de tela: sair do cardápio para cadastrar um alimento
 * significa perder o que estava montando, ou salvar pela metade para não
 * perder. As duas saídas são ruins, e as duas acontecem no meio de uma
 * consulta.
 *
 * O formulário é curto de propósito. Um alimento cadastrado às pressas com
 * quatro números é útil; um formulário com trinta nutrientes no meio do
 * atendimento não é preenchido.
 */
export function QuickFood({
  onCreated,
  onClose,
}: {
  onCreated: (food: FoodDetail) => void;
  onClose: () => void;
}) {
  const [description, setDescription] = useState("");
  const [energyKcal, setEnergyKcal] = useState("");
  const [proteinG, setProteinG] = useState("");
  const [carbohydrateG, setCarbohydrateG] = useState("");
  const [fatG, setFatG] = useState("");
  /**
   * As medidas caseiras do alimento novo.
   *
   * "Adicionar mais de uma medida caseira na hora de adicionar um alimento, só
   * tem como colocar uma." Começa com uma linha vazia para o caminho comum não
   * exigir um clique antes de digitar.
   */
  const [measures, setMeasures] = useState<MeasureRow[]>(() => [newRow()]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const number = (text: string) => {
    const clean = text.trim().replace(",", ".");
    return clean ? Number(clean) : undefined;
  };

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!description.trim()) {
      setError("O alimento precisa de um nome para você achá-lo depois.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const food = await api.foods.create({
        description: description.trim(),
        composition: {
          energyKcal: number(energyKcal),
          proteinG: number(proteinG),
          carbohydrateG: number(carbohydrateG),
          fatG: number(fatG),
        },
        // As medidas vão no mesmo pedido: o servidor sempre aceitou a lista
        // inteira. Criá-las depois, uma a uma, deixaria o alimento cadastrado
        // e as medidas pela metade se a rede caísse no meio.
        measures: measuresToSend(measures),
      });
      onCreated(food);
    } catch (e) {
      setError(explainError(e, "cadastrar o alimento"));
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className="painel quick-form" onSubmit={submit}>
      <h3 style={{ marginTop: 0 }}>Novo alimento</h3>
      <p className="minusculo" style={{ marginTop: 0 }}>
        Valores por 100 g. Você pode completar o resto depois, em Alimentos.
      </p>

      {error && (
        <div className="warning error" role="alert">
          {error}
        </div>
      )}

      <div className="field">
        <label htmlFor="qf-nome">Nome</label>
        <input
          id="qf-nome"
          type="text"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="Pão caseiro da paciente"
          maxLength={250}
          autoFocus
        />
      </div>

      <div className="grid two">
        <div className="field">
          <label htmlFor="qf-kcal">Energia (kcal)</label>
          <input
            id="qf-kcal"
            inputMode="decimal"
            value={energyKcal}
            onChange={(e) => setEnergyKcal(e.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="qf-ptn">Proteínas (g)</label>
          <input
            id="qf-ptn"
            inputMode="decimal"
            value={proteinG}
            onChange={(e) => setProteinG(e.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="qf-cho">Carboidratos (g)</label>
          <input
            id="qf-cho"
            inputMode="decimal"
            value={carbohydrateG}
            onChange={(e) => setCarbohydrateG(e.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="qf-lip">Gorduras (g)</label>
          <input
            id="qf-lip"
            inputMode="decimal"
            value={fatG}
            onChange={(e) => setFatG(e.target.value)}
          />
        </div>
      </div>

      <MeasureRows
        rows={measures}
        onChange={setMeasures}
        disabled={saving}
        help="A primeira vira a medida padrão do alimento."
      />

      <div className="row" style={{ marginTop: "0.8rem" }}>
        <button className="button" type="submit" disabled={saving}>
          {saving ? "Cadastrando…" : "Cadastrar e usar"}
        </button>
        <button className="button secundario" type="button" onClick={onClose} disabled={saving}>
          Cancelar
        </button>
      </div>
    </form>
  );
}

/**
 * As linhas de medida caseira, com o botão de acrescentar outra.
 *
 * Vive fora dos dois formulários porque os dois precisam dela igual — o do
 * alimento novo e o do alimento que já existe. Uma cópia em cada um divergiria
 * no primeiro ajuste feito de um lado só.
 */
function MeasureRows({
  rows,
  onChange,
  disabled,
  help,
}: {
  rows: MeasureRow[];
  onChange: (rows: MeasureRow[]) => void;
  disabled: boolean;
  help?: string;
}) {
  function patch(key: string, change: Partial<MeasureRow>) {
    onChange(rows.map((row) => (row.key === key ? { ...row, ...change } : row)));
  }

  return (
    <div style={{ marginTop: "0.8rem" }}>
      <span className="minusculo">Medidas caseiras</span>
      {help && <div className="minusculo">{help}</div>}

      {rows.map((row, index) => (
        <div className="row" key={row.key} style={{ gap: "0.4rem", marginTop: "0.3rem" }}>
          <input
            style={{ flex: 2, minWidth: 120 }}
            type="text"
            value={row.description}
            onChange={(e) => patch(row.key, { description: e.target.value })}
            placeholder="fatia pequena"
            maxLength={120}
            disabled={disabled}
            aria-label={`Medida ${index + 1}`}
          />
          <input
            style={{ width: 110 }}
            inputMode="decimal"
            value={row.grams}
            onChange={(e) => patch(row.key, { grams: e.target.value })}
            placeholder="gramas"
            disabled={disabled}
            aria-label={`Gramas da medida ${index + 1}`}
          />
          {rows.length > 1 && (
            <button
              type="button"
              className="button perigo pequeno"
              onClick={() => onChange(rows.filter((x) => x.key !== row.key))}
              disabled={disabled}
              aria-label={`Remover medida ${index + 1}`}
            >
              ×
            </button>
          )}
        </div>
      ))}

      <button
        type="button"
        className="button secundario pequeno"
        style={{ marginTop: "0.4rem" }}
        onClick={() => onChange([...rows, newRow()])}
        disabled={disabled}
      >
        + outra medida
      </button>
    </div>
  );
}

/**
 * Acrescenta uma medida caseira ao alimento já escolhido.
 *
 * "É bem simples, uma textbox para o nome da medida caseira e outro para a
 * quantidade que ela representa e aí eu confirmo." É literalmente isso —
 * resistir a acrescentar campos aqui é o que mantém a coisa usável no meio de
 * uma consulta.
 */
export function QuickMeasure({
  foodId,
  foodName,
  onCreated,
  onClose,
}: {
  foodId: number;
  foodName: string;
  /** Recebe cada medida criada, na ordem em que foram informadas. */
  onCreated: (measure: Measure) => void;
  onClose: () => void;
}) {
  const [rows, setRows] = useState<MeasureRow[]>(() => [newRow()]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const wanted = measuresToSend(rows);
    if (wanted.length === 0) {
      setError("Informe o nome da medida e quantos gramas ela representa.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      // Nenhuma delas entra como padrão: o alimento já existe e já tem a sua,
      // e trocá-la por um acréscimo mudaria a porção dos cardápios que o usam.
      for (const measure of wanted) {
        onCreated(await api.foods.addMeasure(foodId, { ...measure, standard: false }));
      }
    } catch (e) {
      setError(explainError(e, "cadastrar a medida caseira"));
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className="painel quick-form" onSubmit={submit}>
      <h3 style={{ marginTop: 0 }}>Novas medidas caseiras</h3>
      <p className="minusculo" style={{ marginTop: 0 }}>
        Para <strong>{foodName}</strong>. Ficam só no seu consultório.
      </p>

      {error && (
        <div className="warning error" role="alert">
          {error}
        </div>
      )}

      <MeasureRows rows={rows} onChange={setRows} disabled={saving} />

      <div className="row" style={{ marginTop: "0.8rem" }}>
        <button className="button" type="submit" disabled={saving}>
          {saving ? "Cadastrando…" : "Cadastrar e usar"}
        </button>
        <button className="button secundario" type="button" onClick={onClose} disabled={saving}>
          Cancelar
        </button>
      </div>
    </form>
  );
}
