package local.higanbana.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NovelInput(@NotBlank @Size(max = 160) String title,
                         @Size(max = 1_000_000) String source) {}