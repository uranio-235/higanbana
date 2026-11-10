package local.higanbana.web.controller;

import local.higanbana.application.Workflow;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;
import static local.higanbana.web.WebRoutes.novel;

@Controller
public class StudioController {
    private final Workflow workflow;

    public StudioController(Workflow workflow) { this.workflow = workflow; }

    @PostMapping("/novels/{novelId}/studio") public String createDraft(@PathVariable long novelId,
            @RequestParam(required = false) List<Long> panelIds,
            @RequestParam(defaultValue = "") String direction,
            @RequestParam(required = false) List<Long> characterIds) {
        workflow.createDraft(novelId, panelIds, direction, characterIds);
        return novel(novelId, "studio");
    }

    @PostMapping("/novels/{novelId}/studio/{draftId}/save") public String save(@PathVariable long novelId,
            @PathVariable long draftId, @RequestParam String prompt) {
        workflow.saveDraft(novelId, draftId, prompt);
        return novel(novelId, "studio");
    }

    @PostMapping("/novels/{novelId}/studio/{draftId}/generate") public String generate(@PathVariable long novelId,
            @PathVariable long draftId) {
        workflow.generate(novelId, draftId);
        return novel(novelId, "generation");
    }
}