package local.higanbana.web.controller;

import local.higanbana.application.Workflow;
import local.higanbana.infrastructure.image.ImageProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class ImageController {
    private final Workflow workflow;
    public ImageController(Workflow workflow) {
        this.workflow = workflow;
    }

    @GetMapping("/novels/{novelId}/candidates/{candidateId}/image") @ResponseBody
    public ResponseEntity<byte[]> candidate(@PathVariable long novelId, @PathVariable long candidateId) {
        return image(workflow.candidateImage(novelId, candidateId));
    }

    @GetMapping("/novels/{novelId}/entries/{entryId}/image") @ResponseBody
    public ResponseEntity<byte[]> entry(@PathVariable long novelId, @PathVariable long entryId) {
        return image(workflow.entryImage(novelId, entryId));
    }

    @GetMapping("/health") @ResponseBody public String health() { return "ok"; }

    private ResponseEntity<byte[]> image(ImageProvider.ImageResult image) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.mediaType()))
                .header("Cache-Control", "no-store").body(image.bytes());
    }
}