package local.higanbana.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CharacterInput(@NotBlank @Size(max = 120) String name,
                             @NotBlank @Size(max = 2000) String description) {}