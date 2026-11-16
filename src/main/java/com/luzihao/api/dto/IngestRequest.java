package com.luzihao.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IngestRequest(@NotBlank @Size(max = 255) String title,
                            @NotBlank String content,
                            @Size(max = 64) String permissionTag,
                            @Size(max = 500) String sourceUri) {
}
