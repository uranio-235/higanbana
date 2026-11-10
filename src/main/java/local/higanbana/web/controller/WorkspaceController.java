package local.higanbana.web.controller;

import jakarta.validation.Valid;
import local.higanbana.application.Workflow;
import local.higanbana.web.dto.NovelInput;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static local.higanbana.web.WebRoutes.novel;

@Controller
public class WorkspaceController {
    private final Workflow workflow;

    public WorkspaceController(Workflow workflow) { this.workflow = workflow; }

    @ModelAttribute public void common(Model model) {
        model.addAttribute("textProvenance", workflow.textProvenance());
        model.addAttribute("chunkProvenance", workflow.chunkProvenance());
        model.addAttribute("chunkingAvailable", workflow.chunkingAvailable());
    }

    @GetMapping("/") public String home(Model model) {
        model.addAttribute("view", "novels");
        model.addAttribute("novels", workflow.novels());
        return "workspace";
    }

    @PostMapping("/novels") public String create(@Valid @ModelAttribute NovelInput input,
            @RequestParam(required = false) MultipartFile file, RedirectAttributes flash) throws IOException {
        String source = input.source();
        if (file != null && !file.isEmpty()) {
            if (source != null && !source.isBlank()) { throw new IllegalArgumentException("Pega texto o sube un archivo, no ambos."); }
            if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(java.util.Locale.ROOT).endsWith(".txt")) {
                throw new IllegalArgumentException("Solo se admiten archivos .txt UTF-8.");
            }
            try {
                source = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(file.getBytes())).toString();
            } catch (CharacterCodingException exception) {
                throw new IllegalArgumentException("El archivo no contiene texto UTF-8 valido.");
            }
        }
        var saved = workflow.createNovel(input.title(), source);
        flash.addFlashAttribute("notice", "Novela guardada.");
        return novel(saved.id, "chunks");
    }

    @GetMapping("/novels/{novelId}/{view}") public String page(@PathVariable long novelId, @PathVariable String view,
            @RequestParam(required = false) Long chunkId, Model model) {
        if (!List.of("characters", "chunks", "panels", "studio", "generation", "reader").contains(view)) {
            throw new IllegalArgumentException("Vista no encontrada.");
        }
        model.addAttribute("novel", workflow.novel(novelId));
        model.addAttribute("view", view);
        var chunks = workflow.chunks(novelId);
        model.addAttribute("chunks", chunks);
        if (view.equals("characters") || view.equals("studio")) { model.addAttribute("characters", workflow.characters(novelId)); }
        if (view.equals("chunks")) { model.addAttribute("plan", workflow.chunkPlan(novelId)); }
        if (view.equals("panels") && !chunks.isEmpty()) {
            var chunk = workflow.chunk(novelId, chunkId == null ? chunks.getFirst().id : chunkId);
            model.addAttribute("selectedChunk", chunk);
            model.addAttribute("panelPlan", workflow.panelPlan(novelId, chunk.id));
        }
        if (view.equals("studio")) {
            model.addAttribute("panels", workflow.panels(novelId));
            model.addAttribute("drafts", workflow.drafts(novelId));
        }
        if (view.equals("generation")) {
            model.addAttribute("drafts", workflow.drafts(novelId));
            model.addAttribute("candidates", workflow.candidates(novelId));
        }
        if (view.equals("reader")) { model.addAttribute("entries", workflow.reader(novelId)); }
        return "workspace";
    }
}