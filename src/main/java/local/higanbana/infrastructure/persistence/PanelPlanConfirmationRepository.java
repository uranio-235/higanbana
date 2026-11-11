package local.higanbana.infrastructure.persistence;

import local.higanbana.domain.entity.PanelPlanConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PanelPlanConfirmationRepository extends JpaRepository<PanelPlanConfirmation, Long> {}
