package com.luzihao.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DiagnoseRequest(@NotBlank @Size(max = 2000) String question,
                              @Size(max = 64) String serviceName) {
}
