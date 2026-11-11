package local.higanbana.web.controller;

import local.higanbana.application.Workflow;
import local.higanbana.domain.ai.TextProviderException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.io.IOException;
import java.util.List;
import static local.higanbana.web.WebRoutes.novel;

@Controller
public class StudioController {
    private final Workflow workflow;

    public StudioController(Workflow workflow) { this.workflow = workflow; }

    @PostMapping("/novels/{novelId}/studio") public String createDraft(@PathVariable long novelId,
            @RequestParam(required = false) List<Long> panelIds,
            @RequestParam(defaultValue = "") String direction,
            @RequestParam(required = false) List<Long> characterIds, RedirectAttributes flash) {
        try {
            workflow.createDraft(novelId, panelIds, direction, characterIds);
        } catch (TextProviderException exception) {
            flash.addFlashAttribute("studioError", exception.getMessage());
            flash.addFlashAttribute("selectedPanelIds", panelIds);
            flash.addFlashAttribute("selectedCharacterIds", characterIds);
            flash.addFlashAttribute("studioDirection", direction);
        }
        return novel(novelId, "studio");
    }

    @PostMapping("/novels/{novelId}/studio/{draftId}/save") public String save(@PathVariable long novelId,
            @PathVariable long draftId, @RequestParam String prompt) {
        workflow.saveDraft(novelId, draftId, prompt);
        return novel(novelId, "studio");
    }

    @PostMapping("/novels/{novelId}/studio/{draftId}/delete") public String delete(@PathVariable long novelId,
            @PathVariable long draftId) {
        workflow.deleteDraft(novelId, draftId);
        return novel(novelId, "studio");
    }

    @Transactional
    @PostMapping("/novels/{novelId}/studio/{draftId}/upload") public String upload(@PathVariable long novelId,
            @PathVariable long draftId, @RequestParam String prompt, @RequestParam MultipartFile image) throws IOException {
        byte[] imageBytes = image.getBytes();
        workflow.saveDraft(novelId, draftId, prompt);
        workflow.uploadImage(novelId, draftId, imageBytes);
        return novel(novelId, "generation");
    }
}