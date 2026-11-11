package local.higanbana.infrastructure.persistence;

import java.util.Optional;
import local.higanbana.domain.entity.PanelPlanConfirmation;
import org.springframework.stereotype.Component;

@Component
class PanelPlanConfirmationJPA {
    private final PanelPlanConfirmationRepository repository;

    PanelPlanConfirmationJPA(PanelPlanConfirmationRepository repository) { this.repository = repository; }

    Optional<PanelPlanConfirmation> findById(long id) { return repository.findById(id); }
    void save(PanelPlanConfirmation confirmation) { repository.save(confirmation); }
}
