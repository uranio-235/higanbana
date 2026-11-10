package local.higanbana.web.controller;

import local.higanbana.application.Workflow;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import static local.higanbana.web.WebRoutes.novel;

@Controller
public class PanelController {
    private final Workflow workflow;

    public PanelController(Workflow workflow) { this.workflow = workflow; }

    @PostMapping("/novels/{novelId}/chunks/{chunkId}/panels/propose") public String propose(
            @PathVariable long novelId, @PathVariable long chunkId, @RequestParam int count,
            @RequestParam(defaultValue = "") String revision) {
        workflow.proposePanels(novelId, chunkId, count, revision);
        return novel(novelId, "panels?chunkId=" + chunkId);
    }

    @PostMapping("/novels/{novelId}/chunks/{chunkId}/panels/save") public String save(
            @PathVariable long novelId, @PathVariable long chunkId,
            @RequestParam MultiValueMap<String, String> form) {
        workflow.savePanelImagePrompts(novelId, chunkId, form.get("imagePrompts"));
        return novel(novelId, "panels?chunkId=" + chunkId);
    }

    @PostMapping("/novels/{novelId}/chunks/{chunkId}/panels/confirm") public String confirm(
            @PathVariable long novelId, @PathVariable long chunkId) {
        workflow.confirmPanels(novelId, chunkId);
        return novel(novelId, "studio");
    }
}