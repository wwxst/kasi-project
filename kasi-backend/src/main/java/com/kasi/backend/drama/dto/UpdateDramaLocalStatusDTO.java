package com.kasi.backend.drama.dto;

import com.kasi.backend.drama.enums.DramaLocalStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateDramaLocalStatusDTO {
    @NotNull
    private DramaLocalStatus localStatus;
}
