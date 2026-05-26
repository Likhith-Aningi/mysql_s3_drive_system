package com.drive.dto;

import lombok.Data;

import java.util.List;

@Data
public class CompleteMultipartRequest {
    private String uploadId;
    private List<PartInfo> parts;

    @Data
    public static class PartInfo {
        private int partNumber;
        private String eTag;
    }
}
