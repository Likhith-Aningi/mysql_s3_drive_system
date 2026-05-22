package com.drive.controller;

import com.drive.dto.FileMetadataDto;
import com.drive.dto.PresignedUploadRequest;
import com.drive.dto.PresignedUploadResponse;
import com.drive.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "Files", description = "File management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload/initiate")
    @Operation(summary = "Get a presigned S3 URL to upload a file directly — file never touches this server")
    public ResponseEntity<PresignedUploadResponse> initiateUpload(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody PresignedUploadRequest request) {
        return ResponseEntity.ok(fileService.initiateUpload(userDetails.getUsername(), request));
    }

    @GetMapping
    @Operation(summary = "List all files for the authenticated user")
    public ResponseEntity<List<FileMetadataDto>> listFiles(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(fileService.listFiles(userDetails.getUsername()));
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Get a CloudFront URL for download (falls back to 1-hour presigned S3 URL if CloudFront not configured)")
    public ResponseEntity<Map<String, String>> getDownloadUrl(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        String url = fileService.getDownloadUrl(userDetails.getUsername(), id);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @GetMapping("/{id}/share")
    @Operation(summary = "Get a shareable link. linkType=expiring (default) returns a 7-day presigned S3 URL; linkType=permanent returns the CloudFront URL")
    public ResponseEntity<Map<String, String>> getShareUrl(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @RequestParam(defaultValue = "expiring") String linkType) {
        String url = fileService.getShareUrl(userDetails.getUsername(), id, linkType);
        return ResponseEntity.ok(Map.of("url", url, "linkType", linkType));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a file from S3 and the database")
    public ResponseEntity<Void> deleteFile(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        fileService.deleteFile(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();
    }
}
