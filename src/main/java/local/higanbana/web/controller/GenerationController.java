package local.higanbana.web.controller;

import local.higanbana.application.Workflow;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import static local.higanbana.web.WebRoutes.novel;

@Controller
public class GenerationController {
    private final Workflow workflow;

    public GenerationController(Workflow workflow) { this.workflow = workflow; }

    @PostMapping("/novels/{novelId}/candidates/{candidateId}/approve") public String approve(
            @PathVariable long novelId, @PathVariable long candidateId) {
        workflow.approve(novelId, candidateId);
        return novel(novelId, "generation");
    }

    @PostMapping("/novels/{novelId}/candidates/{candidateId}/reject") public String reject(
            @PathVariable long novelId, @PathVariable long candidateId) {
        workflow.reject(novelId, candidateId);
        return novel(novelId, "generation");
    }
}