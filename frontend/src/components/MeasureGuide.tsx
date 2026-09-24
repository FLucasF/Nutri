import { X } from "lucide-react";

/**
 * Onde medir: a figura do corpo com o ponto da dobra ou a linha da fita, e a
 * instrução de cada local.
 *
 * "Imagens ilustrando cada medida." A figura é esquemática, desenhada em SVG
 * com as cores do tema, e o ponto aceso segue o campo em que o cursor está:
 * quem digita a dobra subescapular vê onde ela fica sem procurar numa lista.
 * As instruções seguem a padronização usual de antropometria; a figura dá o
 * lugar, o texto dá o detalhe que o desenho não mostra — a direção da dobra,
 * a posição do paciente.
 */

type View = "front" | "back";

type Site = {
  key: string;
  label: string;
  view: View;
  instruction: string;
  /** Um ponto (dobra) ou uma linha horizontal (circunferência). */
  point?: [number, number];
  line?: [number, number, number];
};

export const SKINFOLD_SITES: Site[] = [
  {
    key: "TRICEPS",
    label: "Tricipital",
    view: "back",
    point: [24, 82],
    instruction:
      "Face posterior do braço, no ponto médio entre o acrômio e o olécrano. Dobra vertical, com o braço relaxado ao lado do corpo.",
  },
  {
    key: "BICEPS",
    label: "Bicipital",
    view: "front",
    point: [24, 82],
    instruction: "Face anterior do braço, na mesma altura da tricipital, sobre o ventre do bíceps. Dobra vertical.",
  },
  {
    key: "SUBSCAPULAR",
    label: "Subescapular",
    view: "back",
    point: [44, 86],
    instruction:
      "Logo abaixo do ângulo inferior da escápula. Dobra oblíqua, a cerca de 45° para baixo e para fora.",
  },
  {
    key: "SUPRAILIAC",
    label: "Supra-ilíaca",
    view: "front",
    point: [36, 134],
    instruction:
      "Logo acima da crista ilíaca, na linha axilar. Dobra oblíqua, seguindo a linha natural da pele.",
  },
  {
    key: "ABDOMINAL",
    label: "Abdominal",
    view: "front",
    point: [67, 120],
    instruction: "Cerca de 2 cm ao lado da cicatriz umbilical. Dobra vertical.",
  },
  {
    key: "CHEST",
    label: "Peitoral",
    view: "front",
    point: [44, 62],
    instruction:
      "Diagonal, entre a linha axilar anterior e o mamilo: na metade da distância nos homens, a um terço nas mulheres.",
  },
  {
    key: "MEAN_AXILLARY",
    label: "Axilar média",
    view: "front",
    point: [31, 92],
    instruction: "Na linha axilar média, na altura do processo xifoide. Dobra vertical.",
  },
  {
    key: "THIGH",
    label: "Coxa",
    view: "front",
    point: [46, 200],
    instruction:
      "Face anterior da coxa, no ponto médio entre a prega inguinal e a borda superior da patela. Dobra vertical, com o peso na outra perna.",
  },
  {
    key: "CALF",
    label: "Panturrilha medial",
    view: "front",
    point: [51, 250],
    instruction:
      "Face medial da perna, na altura da maior circunferência da panturrilha. Dobra vertical, com o pé apoiado e o joelho a 90°.",
  },
];

export const CIRCUMFERENCE_SITES: Site[] = [
  {
    key: "NECK",
    label: "Pescoço",
    view: "front",
    line: [52, 68, 40],
    instruction: "Logo abaixo da proeminência laríngea, com a fita perpendicular ao eixo do pescoço.",
  },
  {
    key: "SHOULDER",
    label: "Ombro",
    view: "front",
    line: [20, 100, 52],
    instruction:
      "Sobre a maior proeminência dos deltoides, com os braços relaxados ao lado do corpo, ao fim de uma expiração normal.",
  },
  {
    key: "CHEST",
    label: "Tórax",
    view: "front",
    line: [30, 90, 68],
    instruction: "Na altura do mesoesterno (linha dos mamilos nos homens), ao fim de uma expiração normal.",
  },
  {
    key: "WAIST",
    label: "Cintura",
    view: "front",
    line: [34, 86, 110],
    instruction:
      "No ponto mais estreito entre a última costela e a crista ilíaca; sem um ponto estreito, no meio entre as duas.",
  },
  {
    key: "ABDOMEN",
    label: "Abdômen",
    view: "front",
    line: [33, 87, 122],
    instruction: "Na altura da cicatriz umbilical, ao fim de uma expiração normal.",
  },
  {
    key: "HIP",
    label: "Quadril",
    view: "back",
    line: [30, 90, 156],
    instruction: "Na maior protuberância dos glúteos, com os pés juntos.",
  },
  {
    key: "ARM_RELAXED",
    label: "Braço relaxado",
    view: "front",
    line: [16, 32, 82],
    instruction: "No ponto médio entre o acrômio e o olécrano, com o braço relaxado ao lado do corpo.",
  },
  {
    key: "ARM_CONTRACTED",
    label: "Braço contraído",
    view: "front",
    line: [15, 33, 90],
    instruction: "Na maior circunferência do braço, com o cotovelo flexionado a 90° e o bíceps contraído.",
  },
  {
    key: "FOREARM",
    label: "Antebraço",
    view: "front",
    line: [12, 26, 124],
    instruction: "Na maior circunferência do antebraço, com o braço estendido e a palma para cima.",
  },
  {
    key: "THIGH_PROXIMAL",
    label: "Coxa proximal",
    view: "front",
    line: [35, 57, 178],
    instruction: "Logo abaixo da prega glútea, com o peso distribuído nas duas pernas.",
  },
  {
    key: "THIGH_MEDIAL",
    label: "Coxa medial",
    view: "front",
    line: [35, 56, 200],
    instruction: "No ponto médio entre a prega inguinal e a borda superior da patela.",
  },
  {
    key: "THIGH_DISTAL",
    label: "Coxa distal",
    view: "front",
    line: [35, 54, 222],
    instruction: "Cerca de 5 cm acima da patela, com a perna relaxada.",
  },
  {
    key: "CALF",
    label: "Panturrilha",
    view: "front",
    line: [34, 54, 250],
    instruction: "Na maior circunferência da panturrilha, em pé, com o peso distribuído nas duas pernas.",
  },
];

/** A silhueta, igual nas duas vistas; o que muda é o que se marca nela. */
function Body({ view, sites, active, label }: { view: View; sites: Site[]; active?: string; label: string }) {
  const shown = sites.filter((s) => s.view === view);
  return (
    <svg className="guide-body" viewBox="0 0 120 300" role="img" aria-label={label}>
      <g className="guide-silhouette">
        <circle cx="60" cy="22" r="14" />
        <rect x="53" y="32" width="14" height="12" rx="4" />
        <path d="M34 46 Q60 40 86 46 L92 60 L86 118 Q84 132 88 150 L88 170 L32 170 L32 150 Q36 132 34 118 L28 60 Z" />
        <path className="guide-limb" d="M28 54 L20 110 L16 158" />
        <path className="guide-limb" d="M92 54 L100 110 L104 158" />
        <path className="guide-limb leg" d="M46 166 L44 230 L44 290" />
        <path className="guide-limb leg" d="M74 166 L76 230 L76 290" />
      </g>
      {view === "back" && <path className="guide-spine" d="M60 46 L60 160" />}
      {view === "front" && <circle className="guide-navel" cx="60" cy="120" r="1.6" />}
      {shown.map((site) => {
        const on = site.key === active;
        if (site.line) {
          const [x1, x2, y] = site.line;
          return (
            <line
              key={site.key}
              className={on ? "guide-mark tape active" : "guide-mark tape"}
              x1={x1}
              x2={x2}
              y1={y}
              y2={y}
            />
          );
        }
        const [cx, cy] = site.point as [number, number];
        return (
          <circle
            key={site.key}
            className={on ? "guide-mark fold active" : "guide-mark fold"}
            cx={cx}
            cy={cy}
            r={on ? 4.5 : 3}
          />
        );
      })}
    </svg>
  );
}

export function MeasureGuide({
  kind,
  active,
  onChoose,
  onClose,
}: {
  kind: "skinfold" | "circumference";
  /** A chave do local aceso: a dobra, ou o local da circunferência sem o lado. */
  active?: string;
  onChoose: (key: string) => void;
  onClose: () => void;
}) {
  const sites = kind === "skinfold" ? SKINFOLD_SITES : CIRCUMFERENCE_SITES;
  const current = sites.find((s) => s.key === active) ?? sites[0]!;
  const noun = kind === "skinfold" ? "a dobra" : "a circunferência";

  return (
    <div className="measure-guide" role="region" aria-label={kind === "skinfold" ? "Onde medir as dobras" : "Onde medir as circunferências"}>
      <div className="measure-guide-figure">
        <figure>
          <Body view="front" sites={sites} active={current.key} label={`Vista de frente, marcando ${noun} ${current.label.toLowerCase()}`} />
          <figcaption>frente</figcaption>
        </figure>
        <figure>
          <Body view="back" sites={sites} active={current.key} label={`Vista de costas, marcando ${noun} ${current.label.toLowerCase()}`} />
          <figcaption>costas</figcaption>
        </figure>
      </div>
      <div className="measure-guide-text">
        <div className="measure-guide-head">
          <h4>{current.label}</h4>
          <button type="button" className="button ghost pequeno" onClick={onClose}>
            <X aria-hidden="true" />
            Fechar guia
          </button>
        </div>
        <p className="measure-guide-instruction">{current.instruction}</p>
        <div className="measure-guide-sites" role="group" aria-label="Locais">
          {sites.map((site) => (
            <button
              key={site.key}
              type="button"
              className="measure-guide-site"
              aria-pressed={site.key === current.key}
              onClick={() => onChoose(site.key)}
            >
              {site.label}
            </button>
          ))}
        </div>
        <p className="minusculo">
          Figura esquemática. Ao clicar num campo do formulário, o local dele acende aqui.
        </p>
      </div>
    </div>
  );
}
