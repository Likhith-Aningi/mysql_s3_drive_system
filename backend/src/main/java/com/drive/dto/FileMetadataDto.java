package com.drive.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadataDto {
    private Long id;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private LocalDateTime uploadedAt;
    private String cloudfrontUrl;
}
