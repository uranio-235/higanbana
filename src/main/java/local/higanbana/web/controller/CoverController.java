package local.higanbana.web.controller;

import java.io.IOException;
import java.util.List;
import local.higanbana.application.CoverManager;
import local.higanbana.domain.ai.TextProviderException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import static local.higanbana.web.WebRoutes.novel;

@Controller
public class CoverController {
    private final CoverManager covers;

    public CoverController(CoverManager covers) { this.covers = covers; }

    @PostMapping("/novels/{novelId}/cover/generate")
    public String generate(@PathVariable long novelId, @RequestParam(required = false) List<Long> chunkIds,
            RedirectAttributes flash) {
        try {
            covers.generate(novelId, chunkIds);
            flash.addFlashAttribute("notice", "Prompt de portada generado.");
        } catch (TextProviderException exception) {
            flash.addFlashAttribute("coverError", exception.getMessage());
        }
        return novel(novelId, "cover");
    }

    @PostMapping("/novels/{novelId}/cover/save")
    public String save(@PathVariable long novelId, @RequestParam String prompt, RedirectAttributes flash) {
        covers.savePrompt(novelId, prompt);
        flash.addFlashAttribute("notice", "Prompt de portada guardado.");
        return novel(novelId, "cover");
    }

    @PostMapping("/novels/{novelId}/cover/upload")
    public String upload(@PathVariable long novelId, @RequestParam String prompt,
            @RequestParam MultipartFile image, RedirectAttributes flash) throws IOException {
        covers.upload(novelId, prompt, image.getBytes());
        flash.addFlashAttribute("notice", "Portada guardada.");
        return novel(novelId, "cover");
    }
}
