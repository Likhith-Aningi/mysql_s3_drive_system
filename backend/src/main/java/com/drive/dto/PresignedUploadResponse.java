package com.drive.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PresignedUploadResponse {
    private String uploadUrl;
    private String s3Key;
    private Long fileMetadataId;
}
