-- Massa magra no cálculo energético.
--
-- As fórmulas de Katch-McArdle, Cunningham e Tinsley (por massa magra) partem
-- da massa livre de gordura, e não do peso. Ela entra no cálculo copiada, como
-- o peso e a altura: vem da avaliação escolhida (composição corporal ou
-- bioimpedância) ou do formulário, e fica gravada para que o cálculo antigo
-- não mude quando a avaliação for corrigida.
--
-- Só usa o subconjunto comum a H2 e PostgreSQL (AD-11).

ALTER TABLE energy_plan ADD COLUMN lean_mass_kg DECIMAL(6, 2);
