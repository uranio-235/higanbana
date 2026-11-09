package local.higanbana;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
public class WebController {
    private final Workflow workflow;
    private final DemoImageProvider demo;

    public WebController(Workflow workflow, DemoImageProvider demo) {
        this.workflow = workflow;
        this.demo = demo;
    }

    @ModelAttribute public void common(Model model) {
        model.addAttribute("textProvenance", workflow.textProvenance());
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
        var novel = workflow.createNovel(input.title(), source);
        flash.addFlashAttribute("notice", "Novela guardada.");
        return redirect(novel.id, "chunks");
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
        if (view.equals("characters") || view.equals("studio")) {
            model.addAttribute("characters", workflow.characters(novelId));
        }
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

    @PostMapping("/novels/{novelId}/characters") public String addCharacter(@PathVariable long novelId,
                    @Valid @ModelAttribute CharacterInput input) {
        workflow.addCharacter(novelId, input.name(), input.description());
        return redirect(novelId, "characters");
    }

    @PostMapping("/novels/{novelId}/characters/{characterId}/save") public String saveCharacter(@PathVariable long novelId,
                    @PathVariable long characterId, @Valid @ModelAttribute CharacterInput input) {
        workflow.updateCharacter(novelId, characterId, input.name(), input.description());
        return redirect(novelId, "characters");
    }

    @PostMapping("/novels/{novelId}/characters/{characterId}/delete") public String deleteCharacter(@PathVariable long novelId,
                    @PathVariable long characterId) {
        workflow.deleteCharacter(novelId, characterId);
        return redirect(novelId, "characters");
    }

    @PostMapping("/novels/{novelId}/chunks/propose") public String proposeChunks(@PathVariable long novelId,
                    @RequestParam int limit, @RequestParam(defaultValue = "") String revision) {
        workflow.proposeChunks(novelId, limit, revision);
        return redirect(novelId, "chunks");
    }

    @PostMapping("/novels/{novelId}/chunks/confirm") public String confirmChunks(@PathVariable long novelId) {
        workflow.confirmChunks(novelId);
        return redirect(novelId, "panels");
    }

    @PostMapping("/novels/{novelId}/chunks/{chunkId}/panels/propose") public String proposePanels(@PathVariable long novelId,
                   @PathVariable long chunkId, @RequestParam int count, @RequestParam(defaultValue = "") String revision) {
        workflow.proposePanels(novelId, chunkId, count, revision);
        return redirect(novelId, "panels?chunkId=" + chunkId);
    }

    @PostMapping("/novels/{novelId}/chunks/{chunkId}/panels/save") public String savePanels(@PathVariable long novelId,
                   @PathVariable long chunkId, @RequestParam org.springframework.util.MultiValueMap<String, String> form) {
        workflow.savePanels(novelId, chunkId, form.get("descriptions"));
        return redirect(novelId, "panels?chunkId=" + chunkId);
    }

    @PostMapping("/novels/{novelId}/chunks/{chunkId}/panels/confirm") public String confirmPanels(@PathVariable long novelId,
                   @PathVariable long chunkId) {
        workflow.confirmPanels(novelId, chunkId);
        return redirect(novelId, "studio");
    }

    @PostMapping("/novels/{novelId}/studio") public String studio(@PathVariable long novelId,
                  @RequestParam(required = false) List<Long> panelIds, @RequestParam(defaultValue = "") String direction,
                  @RequestParam(required = false) List<Long> characterIds) {
        workflow.createDraft(novelId, panelIds, direction, characterIds);
        return redirect(novelId, "studio");
    }

    @PostMapping("/novels/{novelId}/studio/{draftId}/save") public String saveDraft(@PathVariable long novelId,
                   @PathVariable long draftId, @RequestParam String prompt, RedirectAttributes flash) {
        workflow.saveDraft(novelId, draftId, prompt);
        flash.addFlashAttribute("notice", "Descripcion guardada. La generacion usara esta version.");
        return redirect(novelId, "studio");
    }

    @PostMapping("/novels/{novelId}/studio/{draftId}/generate") public String generate(@PathVariable long novelId, @PathVariable long draftId) {
        workflow.generate(novelId, draftId);
        return redirect(novelId, "generation");
    }

    @PostMapping("/novels/{novelId}/candidates/{candidateId}/approve") public String approve(@PathVariable long novelId,
                   @PathVariable long candidateId, RedirectAttributes flash) {
        workflow.approve(novelId, candidateId);
        flash.addFlashAttribute("notice", "Imagen aprobada y guardada en el manga local.");
        return redirect(novelId, "generation");
    }

    @PostMapping("/novels/{novelId}/candidates/{candidateId}/reject") public String reject(@PathVariable long novelId, @PathVariable long candidateId) {
        workflow.reject(novelId, candidateId);
        return redirect(novelId, "generation");
    }

    @GetMapping("/novels/{novelId}/candidates/{candidateId}/image") @ResponseBody
    public ResponseEntity<byte[]> candidateImage(@PathVariable long novelId, @PathVariable long candidateId) {
        return image(workflow.candidateImage(novelId, candidateId));
    }

    @GetMapping("/novels/{novelId}/entries/{entryId}/image") @ResponseBody
    public ResponseEntity<byte[]> entryImage(@PathVariable long novelId, @PathVariable long entryId) {
        return image(workflow.entryImage(novelId, entryId));
    }

    @GetMapping("/demo.svg") @ResponseBody public ResponseEntity<byte[]> demo() { return image(demo.generate("Higanbana")); }
    @GetMapping("/health") @ResponseBody public String health() { return "ok"; }

    private ResponseEntity<byte[]> image(ImageProvider.ImageResult image) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.mediaType()))
                .header("Cache-Control", "no-store").body(image.bytes());
    }

    private String redirect(long novelId, String view) { return "redirect:/novels/" + novelId + "/" + view; }

    public record NovelInput(@NotBlank @Size(max = 160) String title, @Size(max = 1_000_000) String source) {}
    public record CharacterInput(@NotBlank @Size(max = 120) String name, @NotBlank @Size(max = 2000) String description) {}

    @ExceptionHandler({IllegalArgumentException.class, BindException.class, MaxUploadSizeExceededException.class})
    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public String invalid(Exception exception, Model model) {
        String message = exception instanceof BindException ? "Revisa los campos obligatorios y sus longitudes."
                : exception instanceof MaxUploadSizeExceededException ? "El archivo supera el limite de 2 MB."
                : exception.getMessage();
        model.addAttribute("message", message);
        return "problem";
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.CONFLICT)
    public String conflict(IllegalStateException exception, Model model) {
        model.addAttribute("message", "No se pudo completar la operacion. Comprueba el almacenamiento local y vuelve a intentar.");
        return "problem";
    }
}