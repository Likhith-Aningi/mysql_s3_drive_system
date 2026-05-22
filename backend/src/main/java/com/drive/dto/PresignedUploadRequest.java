package com.drive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class PresignedUploadRequest {

    @NotBlank
    private String fileName;

    @NotBlank
    private String contentType;

    @Positive
    private Long fileSize;
}
