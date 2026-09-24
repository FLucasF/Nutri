package br.com.nutriplan.servicepackage.service;

import br.com.nutriplan.auth.service.CurrentContext;
import br.com.nutriplan.servicepackage.domain.ServicePackage;
import br.com.nutriplan.servicepackage.dto.PackageDtos;
import br.com.nutriplan.servicepackage.repository.ServicePackageRepository;
import br.com.nutriplan.shared.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServicePackageService {

    private final ServicePackageRepository repository;
    private final CurrentContext currentContext;

    @Transactional(readOnly = true)
    public List<PackageDtos.PackageResponse> list(boolean includeInactive) {
        return repository.findByAccountIdOrderByNameAsc(currentContext.accountId()).stream()
                .filter(p -> includeInactive || p.isActive())
                .map(PackageDtos.PackageResponse::from)
                .toList();
    }

    @Transactional
    public PackageDtos.PackageResponse create(PackageDtos.PackageRequest req) {
        var pack = new ServicePackage(currentContext.accountId(), req.name().trim(), req.amount());
        apply(req, pack);
        return PackageDtos.PackageResponse.from(repository.save(pack));
    }

    @Transactional
    public PackageDtos.PackageResponse update(Long id, PackageDtos.PackageRequest req) {
        ServicePackage pack = require(id);
        pack.setName(req.name().trim());
        pack.setAmount(req.amount());
        apply(req, pack);
        return PackageDtos.PackageResponse.from(pack);
    }

    /** Desativar preserva as consultas e os lançamentos que já apontam para o pacote. */
    @Transactional
    public PackageDtos.PackageResponse setActive(Long id, boolean active) {
        ServicePackage pack = require(id);
        pack.setActive(active);
        return PackageDtos.PackageResponse.from(pack);
    }

    @Transactional(readOnly = true)
    public ServicePackage require(Long id) {
        return repository.findByIdAndAccountId(id, currentContext.accountId())
                .orElseThrow(() -> new NotFoundException("Pacote", id));
    }

    private static void apply(PackageDtos.PackageRequest req, ServicePackage pack) {
        pack.setSessions(req.sessions());
        pack.setIntervalDays(req.intervalDays());
        pack.setNotes(req.notes());
    }
}
