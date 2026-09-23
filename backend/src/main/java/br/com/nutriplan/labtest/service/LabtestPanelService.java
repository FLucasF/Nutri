package br.com.nutriplan.labtest.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import br.com.nutriplan.auth.service.CurrentContext;
import br.com.nutriplan.labtest.domain.LabtestPanel;
import br.com.nutriplan.labtest.domain.LabtestPanelParameter;
import br.com.nutriplan.labtest.domain.LabtestParameter;
import br.com.nutriplan.labtest.dto.LabtestDtos;
import br.com.nutriplan.labtest.repository.LabtestPanelRepository;
import br.com.nutriplan.labtest.repository.LabtestParameterRepository;
import br.com.nutriplan.shared.error.BusinessRuleException;
import br.com.nutriplan.shared.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Os painéis de biomarcadores — os botões que preenchem o pedido de exame.
 *
 * O sistema traz os 25 das páginas 6 a 13 do documento. O consultório cria os
 * seus a partir do que usa, que é o que ele descreve: "caso ele queira
 * selecionar alguns como eu fiz nos de cima e formular um seu, mais genérico
 * para primeiras consultas, ele pode apertando em um botão que favorita".
 *
 * Os do sistema não são editáveis, pela mesma razão que as tabelas de
 * alimentos não são: são acervo compartilhado, e alterá-los em nome de todos
 * seria decidir pelo consultório dos outros. Quem quer mudar um, copia.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LabtestPanelService {

    private final LabtestPanelRepository panelRepository;
    private final LabtestParameterRepository parameterRepository;
    private final CurrentContext currentContext;

    @Transactional(readOnly = true)
    public List<LabtestDtos.PanelResponse> list() {
        return panelRepository.visibleTo(currentContext.accountId()).stream()
                .map(LabtestDtos.PanelResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public LabtestDtos.PanelResponse detail(Long id) {
        return LabtestDtos.PanelResponse.from(require(id));
    }

    /** Cria um painel do consultório a partir dos parâmetros escolhidos. */
    @Transactional
    public LabtestDtos.PanelResponse create(LabtestDtos.PanelRequest request) {
        Long accountId = currentContext.accountId();
        if (!StringUtils.hasText(request.name())) {
            throw new BusinessRuleException("Dê um nome ao painel.");
        }
        if (request.parameterIds() == null || request.parameterIds().isEmpty()) {
            throw new BusinessRuleException(
                    "Um painel vazio não preenche pedido nenhum. Escolha ao menos um parâmetro.");
        }

        var panel = new LabtestPanel(accountId, request.name().trim(), 0);
        fill(panel, request.parameterIds(), accountId);
        panelRepository.save(panel);
        log.info("Painel de exames criado: id={} conta={}", panel.getId(), accountId);
        return LabtestDtos.PanelResponse.from(panel);
    }

    @Transactional
    public LabtestDtos.PanelResponse update(Long id, LabtestDtos.PanelRequest request) {
        LabtestPanel panel = require(id);
        requireOwn(panel);
        panel.setName(request.name().trim());
        panel.getParameters().clear();
        fill(panel, request.parameterIds(), panel.getAccountId());
        return LabtestDtos.PanelResponse.from(panelRepository.save(panel));
    }

    /**
     * Duplica um painel para a lista do consultório, já editável.
     *
     * É o caminho para adaptar um painel do sistema sem alterar o original —
     * o mesmo que a biblioteca de orientações oferece.
     */
    @Transactional
    public LabtestDtos.PanelResponse duplicate(Long id) {
        LabtestPanel origin = require(id);
        Long accountId = currentContext.accountId();

        var copy = new LabtestPanel(accountId, shorten(origin.getName() + " (cópia)"), 0);
        for (LabtestPanelParameter source : origin.getParameters()) {
            copy.add(new LabtestPanelParameter(source.getParameter(), source.getOrder()));
        }
        panelRepository.save(copy);
        return LabtestDtos.PanelResponse.from(copy);
    }

    @Transactional
    public void remove(Long id) {
        LabtestPanel panel = require(id);
        requireOwn(panel);
        panelRepository.delete(panel);
    }

    // ------------------------------------------------------------------ apoio

    private void fill(LabtestPanel panel, List<Long> parameterIds, Long accountId) {
        var seen = new ArrayList<Long>();
        int order = 1;
        for (Long parameterId : parameterIds) {
            if (seen.contains(parameterId)) {
                continue;
            }
            seen.add(parameterId);
            LabtestParameter parameter = parameterRepository.findById(parameterId)
                    .filter(p -> p.getAccountId() == null || p.getAccountId().equals(accountId))
                    .orElseThrow(() -> new NotFoundException("Parâmetro", parameterId));
            panel.add(new LabtestPanelParameter(parameter, order));
            order++;
        }
    }

    private LabtestPanel require(Long id) {
        return panelRepository.visibleFind(id, currentContext.accountId())
                .orElseThrow(() -> new NotFoundException("Painel de exames", id));
    }

    private void requireOwn(LabtestPanel panel) {
        if (panel.isSystemPanel()) {
            throw new BusinessRuleException(
                    "Painel do sistema não é editável. Duplique-o para ter a sua versão.");
        }
    }

    private static String shorten(String name) {
        return name.length() <= 150 ? name : name.substring(0, 150);
    }
}
