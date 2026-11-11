package local.higanbana.web.controller;

import java.nio.charset.StandardCharsets;
import local.higanbana.application.MangaPdfExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class MangaPdfController {
    private final MangaPdfExportService exportService;

    public MangaPdfController(MangaPdfExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/novels/{novelId}/reader.pdf")
    @ResponseBody
    public ResponseEntity<byte[]> download(@PathVariable long novelId) {
        byte[] pdf = exportService.export(novelId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("higanbana-" + novelId + ".pdf", StandardCharsets.UTF_8)
                        .build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(pdf);
    }
}
