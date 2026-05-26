package com.drive.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MultipartInitiateResponse {
    private Long fileMetadataId;
    private String uploadId;
    private String s3Key;
    private long partSize;
}
