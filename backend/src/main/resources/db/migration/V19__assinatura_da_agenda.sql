-- Assinatura da agenda em calendario externo.
--
-- Nao e integracao com a API do Google. E um feed iCalendar, o formato que
-- Google Agenda, Apple Calendar e Outlook assinam nativamente: o calendario
-- busca o endereco de tempos em tempos e reflete o que mudou.
--
-- A escolha e deliberada. OAuth com o Google exigiria credencial de aplicativo,
-- tela de consentimento revisada e um segundo sistema de tokens para manter —
-- e daria, em troca, a mesma coisa que o usuario quer: ver os atendimentos no
-- calendario que ele ja usa. O que o feed nao da e o caminho de volta, criar
-- evento no app a partir do Google, e isso esta dito no requisito.
--
-- O endereco e um UUID, como o do plano publico, e vale a mesma ressalva: quem
-- recebe o link, ve. Por isso ele e regeravel, e so existe depois que o
-- nutricionista pede.
ALTER TABLE conta ADD COLUMN token_agenda VARCHAR(36);

CREATE INDEX ix_conta_token_agenda ON conta (token_agenda);
