-- Nomes do esquema em inglês.
--
-- O código passou a ser escrito em inglês, e o mapeamento das entidades deixou
-- de casar com o banco: uma entidade `Account` procura a tabela `account`, e o
-- que existia era `conta`. Sem esta migração a aplicação nem sobe — a validação
-- do mapeamento acontece no start.
--
-- Três blocos, nesta ordem obrigatória:
--   1. tabelas, porque os comandos de coluna abaixo já usam o nome novo;
--   2. colunas, com índice e chave estrangeira acompanhando sozinhos;
--   3. valores de enum gravados como texto — `AGENDADO` virou `SCHEDULED`, e
--      sem o UPDATE as linhas existentes ficariam com um valor que o enum já
--      não reconhece.
--
-- Migração aplicada não se edita: por isso uma nova, e não uma alteração das
-- anteriores. Só usa o subconjunto comum a H2 e PostgreSQL (AD-11).

-- ----------------------------------------------------- tabelas
ALTER TABLE agendamento RENAME TO appointment;
ALTER TABLE alimento RENAME TO food;
ALTER TABLE avaliacao_antropometrica RENAME TO anthropometric_assessment;
ALTER TABLE conta RENAME TO account;
ALTER TABLE curva_crescimento RENAME TO growth_chart;
ALTER TABLE equivalente_item RENAME TO item_substitution;
ALTER TABLE exame RENAME TO labtest;
ALTER TABLE faixa_referencia RENAME TO reference_range;
ALTER TABLE imagem_de_orientacao RENAME TO handout_image;
ALTER TABLE imagem_do_plano RENAME TO plan_image;
ALTER TABLE ingrediente_receita RENAME TO recipe_ingredient;
ALTER TABLE item_refeicao RENAME TO meal_item;
ALTER TABLE lancamento_financeiro RENAME TO finance_transaction;
ALTER TABLE laudo_exame RENAME TO labtest_report;
ALTER TABLE medida_caseira RENAME TO household_measure;
ALTER TABLE orientacao RENAME TO handout;
ALTER TABLE orientacao_do_plano RENAME TO plan_handout;
ALTER TABLE paciente RENAME TO patient;
ALTER TABLE parametro_exame RENAME TO labtest_parameter;
ALTER TABLE parametro_solicitado RENAME TO ordered_parameter;
ALTER TABLE pergunta RENAME TO question;
ALTER TABLE plano_alimentar RENAME TO meal_plan;
ALTER TABLE questionario RENAME TO questionnaire;
ALTER TABLE refeicao RENAME TO meal;
ALTER TABLE resposta_item RENAME TO item_answer;
ALTER TABLE resposta_questionario RENAME TO questionnaire_answer;
ALTER TABLE solicitacao_exame RENAME TO labtest_order;
ALTER TABLE token_de_recuperacao RENAME TO recovery_token;
ALTER TABLE usuario RENAME TO app_user;

-- ----------------------------------------------------- colunas
ALTER TABLE account RENAME COLUMN nome TO name;
ALTER TABLE account RENAME COLUMN plano TO plan;
ALTER TABLE account RENAME COLUMN plano_expira_em TO plan_expires_at;
ALTER TABLE account RENAME COLUMN cor_primaria TO primary_color;
ALTER TABLE account RENAME COLUMN ativa TO active;
ALTER TABLE account RENAME COLUMN token_agenda TO schedule_token;
ALTER TABLE account RENAME COLUMN criado_em TO created_at;
ALTER TABLE account RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE account RENAME COLUMN criado_por TO created_by;
ALTER TABLE account RENAME COLUMN atualizado_por TO updated_by;
ALTER TABLE anthropometric_assessment RENAME COLUMN paciente_id TO patient_id;
ALTER TABLE anthropometric_assessment RENAME COLUMN data TO date;
ALTER TABLE anthropometric_assessment RENAME COLUMN peso_kg TO weight_kg;
ALTER TABLE anthropometric_assessment RENAME COLUMN altura_cm TO height_cm;
ALTER TABLE anthropometric_assessment RENAME COLUMN dobra_tricipital TO triceps_skinfold;
ALTER TABLE anthropometric_assessment RENAME COLUMN dobra_bicipital TO biceps_skinfold;
ALTER TABLE anthropometric_assessment RENAME COLUMN dobra_subescapular TO subscapular_skinfold;
ALTER TABLE anthropometric_assessment RENAME COLUMN dobra_suprailiaca TO suprailiac_skinfold;
ALTER TABLE anthropometric_assessment RENAME COLUMN dobra_abdominal TO abdominal_skinfold;
ALTER TABLE anthropometric_assessment RENAME COLUMN dobra_peitoral TO chest_skinfold;
ALTER TABLE anthropometric_assessment RENAME COLUMN dobra_coxa TO thigh_skinfold;
ALTER TABLE anthropometric_assessment RENAME COLUMN dobra_panturrilha TO calf_skinfold;
ALTER TABLE anthropometric_assessment RENAME COLUMN dobra_axilar_media TO skinfold_mean_axillary;
ALTER TABLE anthropometric_assessment RENAME COLUMN circ_cintura TO waist_circumference;
ALTER TABLE anthropometric_assessment RENAME COLUMN circ_quadril TO hip_circumference;
ALTER TABLE anthropometric_assessment RENAME COLUMN circ_abdomen TO abdomen_circumference;
ALTER TABLE anthropometric_assessment RENAME COLUMN circ_braco TO arm_circumference;
ALTER TABLE anthropometric_assessment RENAME COLUMN circ_antebraco TO forearm_circumference;
ALTER TABLE anthropometric_assessment RENAME COLUMN circ_coxa TO thigh_circumference;
ALTER TABLE anthropometric_assessment RENAME COLUMN circ_panturrilha TO calf_circumference;
ALTER TABLE anthropometric_assessment RENAME COLUMN circ_torax TO chest_circumference;
ALTER TABLE anthropometric_assessment RENAME COLUMN protocolo_composicao TO composition_protocol;
ALTER TABLE anthropometric_assessment RENAME COLUMN percentual_gordura TO fat_percentage;
ALTER TABLE anthropometric_assessment RENAME COLUMN massa_gorda_kg TO mass_fat_kg;
ALTER TABLE anthropometric_assessment RENAME COLUMN massa_magra_kg TO mass_lean_kg;
ALTER TABLE anthropometric_assessment RENAME COLUMN equacao_gasto TO expenditure_equation;
ALTER TABLE anthropometric_assessment RENAME COLUMN fator_atividade TO activity_factor;
ALTER TABLE anthropometric_assessment RENAME COLUMN gasto_basal_kcal TO basal_expenditure_kcal;
ALTER TABLE anthropometric_assessment RENAME COLUMN gasto_total_kcal TO total_expenditure_kcal;
ALTER TABLE anthropometric_assessment RENAME COLUMN observacoes TO notes;
ALTER TABLE anthropometric_assessment RENAME COLUMN semana_gestacional TO gestational_week;
ALTER TABLE anthropometric_assessment RENAME COLUMN peso_pre_gestacional_kg TO weight_gestational_pre_kg;
ALTER TABLE anthropometric_assessment RENAME COLUMN conta_id TO account_id;
ALTER TABLE anthropometric_assessment RENAME COLUMN criado_em TO created_at;
ALTER TABLE anthropometric_assessment RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE anthropometric_assessment RENAME COLUMN criado_por TO created_by;
ALTER TABLE anthropometric_assessment RENAME COLUMN atualizado_por TO updated_by;
ALTER TABLE app_user RENAME COLUMN nome TO name;
ALTER TABLE app_user RENAME COLUMN senha_hash TO password_hash;
ALTER TABLE app_user RENAME COLUMN perfil TO user_role;
ALTER TABLE app_user RENAME COLUMN conta_id TO account_id;
ALTER TABLE app_user RENAME COLUMN telefone TO phone;
ALTER TABLE app_user RENAME COLUMN ativo TO active;
ALTER TABLE app_user RENAME COLUMN senha_versao TO password_version;
ALTER TABLE app_user RENAME COLUMN senha_alterada_em TO password_changed_at;
ALTER TABLE app_user RENAME COLUMN criado_em TO created_at;
ALTER TABLE app_user RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE app_user RENAME COLUMN criado_por TO created_by;
ALTER TABLE app_user RENAME COLUMN atualizado_por TO updated_by;
ALTER TABLE appointment RENAME COLUMN paciente_id TO patient_id;
ALTER TABLE appointment RENAME COLUMN inicio TO start_at;
ALTER TABLE appointment RENAME COLUMN duracao_minutos TO minutes_duration;
ALTER TABLE appointment RENAME COLUMN tipo TO type;
ALTER TABLE appointment RENAME COLUMN situacao TO status;
ALTER TABLE appointment RENAME COLUMN observacao TO notes;
ALTER TABLE appointment RENAME COLUMN motivo_desfecho TO outcome_reason;
ALTER TABLE appointment RENAME COLUMN conta_id TO account_id;
ALTER TABLE appointment RENAME COLUMN criado_em TO created_at;
ALTER TABLE appointment RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE appointment RENAME COLUMN criado_por TO created_by;
ALTER TABLE appointment RENAME COLUMN atualizado_por TO updated_by;
ALTER TABLE finance_transaction RENAME COLUMN tipo TO type;
ALTER TABLE finance_transaction RENAME COLUMN situacao TO status;
ALTER TABLE finance_transaction RENAME COLUMN valor TO amount;
ALTER TABLE finance_transaction RENAME COLUMN competencia TO accrual;
ALTER TABLE finance_transaction RENAME COLUMN vencimento TO due;
ALTER TABLE finance_transaction RENAME COLUMN data_pagamento TO payment_date;
ALTER TABLE finance_transaction RENAME COLUMN categoria TO category;
ALTER TABLE finance_transaction RENAME COLUMN forma_pagamento TO payment_form;
ALTER TABLE finance_transaction RENAME COLUMN descricao TO description;
ALTER TABLE finance_transaction RENAME COLUMN paciente_id TO patient_id;
ALTER TABLE finance_transaction RENAME COLUMN agendamento_id TO appointment_id;
ALTER TABLE finance_transaction RENAME COLUMN conta_id TO account_id;
ALTER TABLE finance_transaction RENAME COLUMN criado_em TO created_at;
ALTER TABLE finance_transaction RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE finance_transaction RENAME COLUMN criado_por TO created_by;
ALTER TABLE finance_transaction RENAME COLUMN atualizado_por TO updated_by;
ALTER TABLE food RENAME COLUMN conta_id TO account_id;
ALTER TABLE food RENAME COLUMN descricao TO description;
ALTER TABLE food RENAME COLUMN descricao_busca TO search_description;
ALTER TABLE food RENAME COLUMN grupo TO group_name;
ALTER TABLE food RENAME COLUMN fonte TO source;
ALTER TABLE food RENAME COLUMN codigo_fonte TO source_code;
ALTER TABLE food RENAME COLUMN marca TO brand;
ALTER TABLE food RENAME COLUMN codigo_barras TO barcode;
ALTER TABLE food RENAME COLUMN modo_preparo TO instructions_mode;
ALTER TABLE food RENAME COLUMN rendimento_gramas TO grams_yield;
ALTER TABLE food RENAME COLUMN porcoes TO servings;
ALTER TABLE food RENAME COLUMN ativo TO active;
ALTER TABLE food RENAME COLUMN energia_kcal TO energy_kcal;
ALTER TABLE food RENAME COLUMN energia_kj TO kj_energy;
ALTER TABLE food RENAME COLUMN proteina_g TO protein_g;
ALTER TABLE food RENAME COLUMN carboidrato_g TO carbohydrate_g;
ALTER TABLE food RENAME COLUMN acucares_g TO sugars_g;
ALTER TABLE food RENAME COLUMN acucares_adicionados_g TO sugars_added_g;
ALTER TABLE food RENAME COLUMN fibra_g TO fiber_g;
ALTER TABLE food RENAME COLUMN lipideos_g TO fat_g;
ALTER TABLE food RENAME COLUMN gorduras_saturadas_g TO fat_saturated_g;
ALTER TABLE food RENAME COLUMN gorduras_trans_g TO fat_trans_g;
ALTER TABLE food RENAME COLUMN gorduras_monoinsaturadas_g TO fat_monounsaturated_g;
ALTER TABLE food RENAME COLUMN gorduras_poliinsaturadas_g TO fat_polyunsaturated_g;
ALTER TABLE food RENAME COLUMN colesterol_mg TO cholesterol_mg;
ALTER TABLE food RENAME COLUMN sodio_mg TO sodium_mg;
ALTER TABLE food RENAME COLUMN calcio_mg TO calcium_mg;
ALTER TABLE food RENAME COLUMN ferro_mg TO iron_mg;
ALTER TABLE food RENAME COLUMN magnesio_mg TO magnesium_mg;
ALTER TABLE food RENAME COLUMN fosforo_mg TO phosphorus_mg;
ALTER TABLE food RENAME COLUMN potassio_mg TO potassium_mg;
ALTER TABLE food RENAME COLUMN zinco_mg TO zinc_mg;
ALTER TABLE food RENAME COLUMN cobre_mg TO copper_mg;
ALTER TABLE food RENAME COLUMN manganes_mg TO manganese_mg;
ALTER TABLE food RENAME COLUMN selenio_mcg TO selenium_mcg;
ALTER TABLE food RENAME COLUMN vitamina_c_mg TO vitamin_c_mg;
ALTER TABLE food RENAME COLUMN tiamina_mg TO thiamin_mg;
ALTER TABLE food RENAME COLUMN riboflavina_mg TO riboflavin_mg;
ALTER TABLE food RENAME COLUMN niacina_mg TO niacin_mg;
ALTER TABLE food RENAME COLUMN piridoxina_mg TO pyridoxine_mg;
ALTER TABLE food RENAME COLUMN vitamina_b12_mcg TO vitamin_b12_mcg;
ALTER TABLE food RENAME COLUMN folato_mcg TO folate_mcg;
ALTER TABLE food RENAME COLUMN vitamina_d_mcg TO vitamin_d_mcg;
ALTER TABLE food RENAME COLUMN vitamina_e_mg TO vitamin_e_mg;
ALTER TABLE food RENAME COLUMN umidade_pct TO moisture_pct;
ALTER TABLE food RENAME COLUMN cinzas_g TO ash_g;
ALTER TABLE food RENAME COLUMN criado_em TO created_at;
ALTER TABLE food RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE food RENAME COLUMN criado_por TO created_by;
ALTER TABLE food RENAME COLUMN atualizado_por TO updated_by;
ALTER TABLE growth_chart RENAME COLUMN indicador TO indicator;
ALTER TABLE growth_chart RENAME COLUMN sexo TO sex;
ALTER TABLE growth_chart RENAME COLUMN mes TO month_number;
ALTER TABLE growth_chart RENAME COLUMN criado_em TO created_at;
ALTER TABLE growth_chart RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE growth_chart RENAME COLUMN criado_por TO created_by;
ALTER TABLE growth_chart RENAME COLUMN atualizado_por TO updated_by;
ALTER TABLE handout RENAME COLUMN conta_id TO account_id;
ALTER TABLE handout RENAME COLUMN titulo TO title;
ALTER TABLE handout RENAME COLUMN corpo TO body;
ALTER TABLE handout RENAME COLUMN imagem_nome TO name_image;
ALTER TABLE handout RENAME COLUMN imagem_tipo TO image_type;
ALTER TABLE handout RENAME COLUMN ativo TO active;
ALTER TABLE handout RENAME COLUMN criado_em TO created_at;
ALTER TABLE handout RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE handout RENAME COLUMN criado_por TO created_by;
ALTER TABLE handout RENAME COLUMN atualizado_por TO updated_by;
ALTER TABLE handout_image RENAME COLUMN orientacao_id TO handout_id;
ALTER TABLE handout_image RENAME COLUMN conteudo TO content;
ALTER TABLE handout_image RENAME COLUMN criado_em TO created_at;
ALTER TABLE handout_image RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE handout_image RENAME COLUMN criado_por TO created_by;
ALTER TABLE handout_image RENAME COLUMN atualizado_por TO updated_by;
ALTER TABLE household_measure RENAME COLUMN alimento_id TO food_id;
ALTER TABLE household_measure RENAME COLUMN conta_id TO account_id;
ALTER TABLE household_measure RENAME COLUMN descricao TO description;
ALTER TABLE household_measure RENAME COLUMN gramas TO grams;
ALTER TABLE household_measure RENAME COLUMN padrao TO standard;
ALTER TABLE household_measure RENAME COLUMN criado_em TO created_at;
ALTER TABLE household_measure RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE household_measure RENAME COLUMN criado_por TO created_by;
ALTER TABLE household_measure RENAME COLUMN atualizado_por TO updated_by;
ALTER TABLE item_answer RENAME COLUMN resposta_id TO answer_id;
ALTER TABLE item_answer RENAME COLUMN pergunta_id TO question_id;
ALTER TABLE item_answer RENAME COLUMN pergunta_texto TO text_question;
ALTER TABLE item_answer RENAME COLUMN valor TO amount;
ALTER TABLE item_answer RENAME COLUMN pontos TO points;
ALTER TABLE item_answer RENAME COLUMN ordem TO sort_order;
ALTER TABLE item_answer RENAME COLUMN criado_em TO created_at;
ALTER TABLE item_answer RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE item_answer RENAME COLUMN criado_por TO created_by;
ALTER TABLE item_answer RENAME COLUMN atualizado_por TO updated_by;
ALTER TABLE item_substitution RENAME COLUMN alimento_id TO food_id;
ALTER TABLE item_substitution RENAME COLUMN medida_id TO measure_id;
ALTER TABLE item_substitution RENAME COLUMN descricao TO description;
ALTER TABLE item_substitution RENAME COLUMN descricao_medida TO measure_description;
ALTER TABLE item_substitution RENAME COLUMN quantidade TO quantity;
ALTER TABLE item_substitution RENAME COLUMN gramas TO grams;
ALTER TABLE item_substitution RENAME COLUMN criado_em TO created_at;
ALTER TABLE item_substitution RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE item_substitution RENAME COLUMN criado_por TO created_by;
ALTER TABLE item_substitution RENAME COLUMN atualizado_por TO updated_by;
ALTER TABLE labtest RENAME COLUMN conta_id TO account_id;
ALTER TABLE labtest RENAME COLUMN paciente_id TO patient_id;
ALTER TABLE labtest RENAME COLUMN parametro_id TO parameter_id;
ALTER TABLE labtest RENAME COLUMN data_coleta TO collection_date;
ALTER TABLE labtest RENAME COLUMN valor TO amount;
ALTER TABLE labtest RENAME COLUMN unidade TO unit;
ALTER TABLE labtest RENAME COLUMN classificacao TO classification;
ALTER TABLE labtest RENAME COLUMN referencia_min TO reference_min;
ALTER TABLE labtest RENAME COLUMN referencia_max TO reference_max;
ALTER TABLE labtest RENAME COLUMN observacao TO notes;
ALTER TABLE labtest RENAME COLUMN laudo_nome TO name_report;
ALTER TABLE labtest RENAME COLUMN laudo_tipo TO report_type;
ALTER TABLE labtest RENAME COLUMN criado_em TO created_at;
ALTER TABLE labtest RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE labtest RENAME COLUMN criado_por TO created_by;
ALTER TABLE labtest RENAME COLUMN atualizado_por TO updated_by;
ALTER TABLE labtest_order RENAME COLUMN conta_id TO account_id;
ALTER TABLE labtest_order RENAME COLUMN paciente_id TO patient_id;
ALTER TABLE labtest_order RENAME COLUMN data TO date;
ALTER TABLE labtest_order RENAME COLUMN observacao TO notes;
ALTER TABLE labtest_order RENAME COLUMN criado_em TO created_at;
ALTER TABLE labtest_order RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE labtest_order RENAME COLUMN criado_por TO created_by;
ALTER TABLE labtest_order RENAME COLUMN atualizado_por TO updated_by;
ALTER TABLE labtest_parameter RENAME COLUMN conta_id TO account_id;
ALTER TABLE labtest_parameter RENAME COLUMN nome TO name;
ALTER TABLE labtest_parameter RENAME COLUMN unidade_padrao TO standard_unit;
ALTER TABLE labtest_parameter RENAME COLUMN grupo TO group_name;
ALTER TABLE labtest_parameter RENAME COLUMN ativo TO active;
ALTER TABLE labtest_parameter RENAME COLUMN criado_em TO created_at;
ALTER TABLE labtest_parameter RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE labtest_parameter RENAME COLUMN criado_por TO created_by;
ALTER TABLE labtest_parameter RENAME COLUMN atualizado_por TO updated_by;
ALTER TABLE labtest_report RENAME COLUMN exame_id TO labtest_id;
ALTER TABLE labtest_report RENAME COLUMN conteudo TO content;
ALTER TABLE labtest_report RENAME COLUMN criado_em TO created_at;
ALTER TABLE labtest_report RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE labtest_report RENAME COLUMN criado_por TO created_by;
ALTER TABLE labtest_report RENAME COLUMN atualizado_por TO updated_by;
ALTER TABLE meal RENAME COLUMN plano_id TO plan_id;
ALTER TABLE meal RENAME COLUMN nome TO name;
ALTER TABLE meal RENAME COLUMN horario TO time;
ALTER TABLE meal RENAME COLUMN ordem TO sort_order;
ALTER TABLE meal RENAME COLUMN observacao TO notes;
ALTER TABLE meal RENAME COLUMN criado_em TO created_at;
ALTER TABLE meal RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE meal RENAME COLUMN criado_por TO created_by;
ALTER TABLE meal RENAME COLUMN atualizado_por TO updated_by;
ALTER TABLE meal_item RENAME COLUMN refeicao_id TO meal_id;
ALTER TABLE meal_item RENAME COLUMN alimento_id TO food_id;
ALTER TABLE meal_item RENAME COLUMN medida_id TO measure_id;
ALTER TABLE meal_item RENAME COLUMN descricao TO description;
ALTER TABLE meal_item RENAME COLUMN descricao_medida TO measure_description;
ALTER TABLE meal_item RENAME COLUMN quantidade TO quantity;
ALTER TABLE meal_item RENAME COLUMN gramas TO grams;
ALTER TABLE meal_item RENAME COLUMN ordem TO sort_order;
ALTER TABLE meal_item RENAME COLUMN observacao TO notes;
ALTER TABLE meal_item RENAME COLUMN criado_em TO created_at;
ALTER TABLE meal_item RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE meal_item RENAME COLUMN criado_por TO created_by;
ALTER TABLE meal_item RENAME COLUMN atualizado_por TO updated_by;
ALTER TABLE meal_plan RENAME COLUMN paciente_id TO patient_id;
ALTER TABLE meal_plan RENAME COLUMN titulo TO title;
ALTER TABLE meal_plan RENAME COLUMN metodo TO method;
ALTER TABLE meal_plan RENAME COLUMN identificador_publico TO public_identifier;
ALTER TABLE meal_plan RENAME COLUMN vigencia_inicio TO start_validity;
ALTER TABLE meal_plan RENAME COLUMN vigencia_fim TO end_validity;
ALTER TABLE meal_plan RENAME COLUMN orientacoes TO handouts;
ALTER TABLE meal_plan RENAME COLUMN observacoes_internas TO internal_notes;
ALTER TABLE meal_plan RENAME COLUMN modelo TO template;
ALTER TABLE meal_plan RENAME COLUMN meta_energia_kcal TO target_energy_kcal;
ALTER TABLE meal_plan RENAME COLUMN conta_id TO account_id;
ALTER TABLE meal_plan RENAME COLUMN criado_em TO created_at;
ALTER TABLE meal_plan RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE meal_plan RENAME COLUMN criado_por TO created_by;
ALTER TABLE meal_plan RENAME COLUMN atualizado_por TO updated_by;
ALTER TABLE ordered_parameter RENAME COLUMN solicitacao_id TO order_id;
ALTER TABLE ordered_parameter RENAME COLUMN parametro_id TO parameter_id;
ALTER TABLE ordered_parameter RENAME COLUMN criado_em TO created_at;
ALTER TABLE ordered_parameter RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE ordered_parameter RENAME COLUMN criado_por TO created_by;
ALTER TABLE ordered_parameter RENAME COLUMN atualizado_por TO updated_by;
ALTER TABLE patient RENAME COLUMN nome TO name;
ALTER TABLE patient RENAME COLUMN telefone TO phone;
ALTER TABLE patient RENAME COLUMN data_nascimento TO birth_date;
ALTER TABLE patient RENAME COLUMN sexo TO sex;
ALTER TABLE patient RENAME COLUMN profissao TO occupation;
ALTER TABLE patient RENAME COLUMN objetivo TO goal;
ALTER TABLE patient RENAME COLUMN observacoes TO notes;
ALTER TABLE patient RENAME COLUMN usuario_id TO user_id;
ALTER TABLE patient RENAME COLUMN ativo TO active;
ALTER TABLE patient RENAME COLUMN conta_id TO account_id;
ALTER TABLE patient RENAME COLUMN criado_em TO created_at;
ALTER TABLE patient RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE patient RENAME COLUMN criado_por TO created_by;
ALTER TABLE patient RENAME COLUMN atualizado_por TO updated_by;
ALTER TABLE plan_handout RENAME COLUMN plano_id TO plan_id;
ALTER TABLE plan_handout RENAME COLUMN orientacao_id TO handout_id;
ALTER TABLE plan_handout RENAME COLUMN titulo TO title;
ALTER TABLE plan_handout RENAME COLUMN corpo TO body;
ALTER TABLE plan_handout RENAME COLUMN imagem_nome TO name_image;
ALTER TABLE plan_handout RENAME COLUMN imagem_tipo TO image_type;
ALTER TABLE plan_handout RENAME COLUMN ordem TO sort_order;
ALTER TABLE plan_handout RENAME COLUMN criado_em TO created_at;
ALTER TABLE plan_handout RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE plan_handout RENAME COLUMN criado_por TO created_by;
ALTER TABLE plan_handout RENAME COLUMN atualizado_por TO updated_by;
ALTER TABLE plan_image RENAME COLUMN orientacao_do_plano_id TO plan_id_handout;
ALTER TABLE plan_image RENAME COLUMN conteudo TO content;
ALTER TABLE plan_image RENAME COLUMN criado_em TO created_at;
ALTER TABLE plan_image RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE plan_image RENAME COLUMN criado_por TO created_by;
ALTER TABLE plan_image RENAME COLUMN atualizado_por TO updated_by;
ALTER TABLE question RENAME COLUMN questionario_id TO questionnaire_id;
ALTER TABLE question RENAME COLUMN enunciado TO statement;
ALTER TABLE question RENAME COLUMN tipo TO type;
ALTER TABLE question RENAME COLUMN obrigatoria TO required;
ALTER TABLE question RENAME COLUMN ordem TO sort_order;
ALTER TABLE question RENAME COLUMN opcoes TO options;
ALTER TABLE question RENAME COLUMN criado_em TO created_at;
ALTER TABLE question RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE question RENAME COLUMN criado_por TO created_by;
ALTER TABLE question RENAME COLUMN atualizado_por TO updated_by;
ALTER TABLE questionnaire RENAME COLUMN conta_id TO account_id;
ALTER TABLE questionnaire RENAME COLUMN nome TO name;
ALTER TABLE questionnaire RENAME COLUMN descricao TO description;
ALTER TABLE questionnaire RENAME COLUMN instrumento TO instrument;
ALTER TABLE questionnaire RENAME COLUMN versao TO version;
ALTER TABLE questionnaire RENAME COLUMN pontuavel TO scorable;
ALTER TABLE questionnaire RENAME COLUMN faixa_de_corte TO cutoff_range;
ALTER TABLE questionnaire RENAME COLUMN versao_modelo TO version_template;
ALTER TABLE questionnaire RENAME COLUMN ativo TO active;
ALTER TABLE questionnaire RENAME COLUMN criado_em TO created_at;
ALTER TABLE questionnaire RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE questionnaire RENAME COLUMN criado_por TO created_by;
ALTER TABLE questionnaire RENAME COLUMN atualizado_por TO updated_by;
ALTER TABLE questionnaire_answer RENAME COLUMN conta_id TO account_id;
ALTER TABLE questionnaire_answer RENAME COLUMN paciente_id TO patient_id;
ALTER TABLE questionnaire_answer RENAME COLUMN agendamento_id TO appointment_id;
ALTER TABLE questionnaire_answer RENAME COLUMN questionario_id TO questionnaire_id;
ALTER TABLE questionnaire_answer RENAME COLUMN versao_modelo TO version_template;
ALTER TABLE questionnaire_answer RENAME COLUMN identificador_publico TO public_identifier;
ALTER TABLE questionnaire_answer RENAME COLUMN enviado_em TO sent_at;
ALTER TABLE questionnaire_answer RENAME COLUMN respondido_em TO answered_at;
ALTER TABLE questionnaire_answer RENAME COLUMN escore TO score;
ALTER TABLE questionnaire_answer RENAME COLUMN classificacao TO classification;
ALTER TABLE questionnaire_answer RENAME COLUMN faixa_de_corte TO cutoff_range;
ALTER TABLE questionnaire_answer RENAME COLUMN criado_em TO created_at;
ALTER TABLE questionnaire_answer RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE questionnaire_answer RENAME COLUMN criado_por TO created_by;
ALTER TABLE questionnaire_answer RENAME COLUMN atualizado_por TO updated_by;
ALTER TABLE recipe_ingredient RENAME COLUMN receita_id TO recipe_id;
ALTER TABLE recipe_ingredient RENAME COLUMN alimento_id TO food_id;
ALTER TABLE recipe_ingredient RENAME COLUMN medida_id TO measure_id;
ALTER TABLE recipe_ingredient RENAME COLUMN descricao_medida TO measure_description;
ALTER TABLE recipe_ingredient RENAME COLUMN quantidade TO quantity;
ALTER TABLE recipe_ingredient RENAME COLUMN gramas TO grams;
ALTER TABLE recipe_ingredient RENAME COLUMN ordem TO sort_order;
ALTER TABLE recipe_ingredient RENAME COLUMN criado_em TO created_at;
ALTER TABLE recipe_ingredient RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE recipe_ingredient RENAME COLUMN criado_por TO created_by;
ALTER TABLE recipe_ingredient RENAME COLUMN atualizado_por TO updated_by;
ALTER TABLE recovery_token RENAME COLUMN usuario_id TO user_id;
ALTER TABLE recovery_token RENAME COLUMN expira_em TO expires_at;
ALTER TABLE recovery_token RENAME COLUMN usado_em TO used_at;
ALTER TABLE recovery_token RENAME COLUMN criado_em TO created_at;
ALTER TABLE recovery_token RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE recovery_token RENAME COLUMN criado_por TO created_by;
ALTER TABLE recovery_token RENAME COLUMN atualizado_por TO updated_by;
ALTER TABLE reference_range RENAME COLUMN parametro_id TO parameter_id;
ALTER TABLE reference_range RENAME COLUMN sexo TO sex;
ALTER TABLE reference_range RENAME COLUMN idade_min TO age_min;
ALTER TABLE reference_range RENAME COLUMN idade_max TO age_max;
ALTER TABLE reference_range RENAME COLUMN minimo TO minimum;
ALTER TABLE reference_range RENAME COLUMN maximo TO maximum;
ALTER TABLE reference_range RENAME COLUMN criado_em TO created_at;
ALTER TABLE reference_range RENAME COLUMN atualizado_em TO updated_at;
ALTER TABLE reference_range RENAME COLUMN criado_por TO created_by;
ALTER TABLE reference_range RENAME COLUMN atualizado_por TO updated_by;

-- --------------------------------- valores de enum gravados

-- account.plan (Plano)
UPDATE account SET plan = 'UNDERGRADUATE' WHERE plan = 'GRADUACAO';

-- app_user.user_role (Perfil)
UPDATE app_user SET user_role = 'NUTRITIONIST' WHERE user_role = 'NUTRICIONISTA';
UPDATE app_user SET user_role = 'ASSISTANT' WHERE user_role = 'SECRETARIA';
UPDATE app_user SET user_role = 'PATIENT' WHERE user_role = 'PACIENTE';

-- appointment.status (SituacaoAtendimento)
UPDATE appointment SET status = 'SCHEDULED' WHERE status = 'AGENDADO';
UPDATE appointment SET status = 'CONFIRMED' WHERE status = 'CONFIRMADO';
UPDATE appointment SET status = 'COMPLETED' WHERE status = 'REALIZADO';
UPDATE appointment SET status = 'NOSHOW' WHERE status = 'FALTOU';
UPDATE appointment SET status = 'CANCELED' WHERE status = 'CANCELADO';

-- appointment.type (TipoAtendimento)
UPDATE appointment SET type = 'FIRST_CONSULTATION' WHERE type = 'PRIMEIRA_CONSULTA';
UPDATE appointment SET type = 'FOLLOWUP' WHERE type = 'RETORNO';
UPDATE appointment SET type = 'OTHER' WHERE type = 'OUTRO';

-- finance_transaction.status (SituacaoLancamento)
UPDATE finance_transaction SET status = 'PENDING' WHERE status = 'PENDENTE';
UPDATE finance_transaction SET status = 'PAID' WHERE status = 'PAGO';
UPDATE finance_transaction SET status = 'CANCELED' WHERE status = 'CANCELADO';

-- finance_transaction.type (TipoLancamento)
UPDATE finance_transaction SET type = 'INCOME' WHERE type = 'RECEITA';
UPDATE finance_transaction SET type = 'EXPENSE' WHERE type = 'DESPESA';

-- food.source (FonteDeDados)
UPDATE food SET source = 'MANUFACTURER' WHERE source = 'FABRICANTE';
UPDATE food SET source = 'CUSTOM' WHERE source = 'PERSONALIZADO';
UPDATE food SET source = 'RECIPE' WHERE source = 'RECEITA';

-- growth_chart.indicator (IndicadorDeCrescimento)
UPDATE growth_chart SET indicator = 'BMI_TO_AGE' WHERE indicator = 'IMC_PARA_IDADE';
UPDATE growth_chart SET indicator = 'HEIGHT_TO_AGE' WHERE indicator = 'ESTATURA_PARA_IDADE';

-- growth_chart.sex (Sexo)
UPDATE growth_chart SET sex = 'FEMALE' WHERE sex = 'FEMININO';
UPDATE growth_chart SET sex = 'MALE' WHERE sex = 'MASCULINO';

-- labtest.classification (ClassificacaoDoExame)
UPDATE labtest SET classification = 'BELOW' WHERE classification = 'ABAIXO';
UPDATE labtest SET classification = 'ABOVE' WHERE classification = 'ACIMA';

-- meal_plan.method (MetodoPrescricao)
UPDATE meal_plan SET method = 'FOODS' WHERE method = 'ALIMENTOS';
UPDATE meal_plan SET method = 'SUBSTITUTIONS' WHERE method = 'EQUIVALENTES';
UPDATE meal_plan SET method = 'QUALITATIVE' WHERE method = 'QUALITATIVO';

-- meal_plan.status (StatusPlano)
UPDATE meal_plan SET status = 'DRAFT' WHERE status = 'RASCUNHO';
UPDATE meal_plan SET status = 'ACTIVE' WHERE status = 'ATIVO';
UPDATE meal_plan SET status = 'CLOSED' WHERE status = 'ENCERRADO';

-- patient.sex (Sexo)
UPDATE patient SET sex = 'FEMALE' WHERE sex = 'FEMININO';
UPDATE patient SET sex = 'MALE' WHERE sex = 'MASCULINO';

-- question.type (TipoDePergunta)
UPDATE question SET type = 'TEXT' WHERE type = 'TEXTO';
UPDATE question SET type = 'NUMBER' WHERE type = 'NUMERO';
UPDATE question SET type = 'CHOICE_SINGLE' WHERE type = 'ESCOLHA_UNICA';
UPDATE question SET type = 'MULTIPLE' WHERE type = 'MULTIPLA';

-- reference_range.sex (Sexo)
UPDATE reference_range SET sex = 'FEMALE' WHERE sex = 'FEMININO';
UPDATE reference_range SET sex = 'MALE' WHERE sex = 'MASCULINO';


-- ------------------------------------- restrições que citam valor de enum
ALTER TABLE finance_transaction DROP CONSTRAINT IF EXISTS ck_lancamento_pagamento;
ALTER TABLE finance_transaction ADD CONSTRAINT ck_transaction_payment CHECK (
    (status = 'PAID'  AND payment_date IS NOT NULL) OR
    (status <> 'PAID' AND payment_date IS NULL)
);

-- appointment.type: duas constantes estavam acentuadas e em português
-- (`Avaliação`, `Orientação`) desde antes desta migração, e o valor vai
-- gravado como texto. Sem estes UPDATE as linhas existentes ficariam com
-- um valor que o enum já não reconhece.
UPDATE appointment SET type = 'ASSESSMENT' WHERE type = 'Avaliação';
UPDATE appointment SET type = 'COUNSELING' WHERE type = 'Orientação';
