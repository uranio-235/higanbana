package local.higanbana.web.controller;

import local.higanbana.application.Workflow;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import static local.higanbana.web.WebRoutes.novel;

@Controller
public class ChunkController {
    private final Workflow workflow;

    public ChunkController(Workflow workflow) { this.workflow = workflow; }

    @PostMapping("/novels/{novelId}/chunks/propose") public String propose(@PathVariable long novelId,
            @RequestParam(defaultValue = "") String revision) {
        workflow.proposeChunks(novelId, revision);
        return novel(novelId, "chunks");
    }

    @PostMapping("/novels/{novelId}/chunks/confirm") public String confirm(@PathVariable long novelId) {
        workflow.confirmChunks(novelId);
        return novel(novelId, "panels");
    }
}