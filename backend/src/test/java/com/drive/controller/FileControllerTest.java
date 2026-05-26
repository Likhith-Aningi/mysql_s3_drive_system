package com.drive.controller;

import com.drive.dto.*;
import com.drive.service.FileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FileController.class)
class FileControllerTest {

    @Autowired MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @MockitoBean FileService fileService;
    @MockitoBean com.drive.security.JwtUtil jwtUtil;
    @MockitoBean org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    // ── simple upload ──────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice@example.com")
    void initiateUpload_success() throws Exception {
        PresignedUploadRequest req = new PresignedUploadRequest();
        req.setFileName("doc.pdf");
        req.setContentType("application/pdf");
        req.setFileSize(1024L);

        when(fileService.initiateUpload(anyString(), any()))
                .thenReturn(new PresignedUploadResponse("https://s3.url", "s3Key", 1L));

        mockMvc.perform(post("/api/files/upload/initiate")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value("https://s3.url"))
                .andExpect(jsonPath("$.fileMetadataId").value(1));
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    void initiateUpload_missingFileName_returns400() throws Exception {
        PresignedUploadRequest req = new PresignedUploadRequest();
        // fileName missing
        req.setContentType("application/pdf");
        req.setFileSize(1024L);

        mockMvc.perform(post("/api/files/upload/initiate")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── multipart initiate ─────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice@example.com")
    void initiateMultipartUpload_success() throws Exception {
        PresignedUploadRequest req = new PresignedUploadRequest();
        req.setFileName("video.mp4");
        req.setContentType("video/mp4");
        req.setFileSize(50 * 1024 * 1024L);

        when(fileService.initiateMultipartUpload(anyString(), any()))
                .thenReturn(new MultipartInitiateResponse(1L, "upload-id-123",
                        "users/1/uuid/video.mp4", 5L * 1024 * 1024));

        mockMvc.perform(post("/api/files/upload/multipart/initiate")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileMetadataId").value(1))
                .andExpect(jsonPath("$.uploadId").value("upload-id-123"))
                .andExpect(jsonPath("$.partSize").value(5 * 1024 * 1024));
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    void initiateMultipartUpload_invalidFileSize_returns400() throws Exception {
        PresignedUploadRequest req = new PresignedUploadRequest();
        req.setFileName("video.mp4");
        req.setContentType("video/mp4");
        req.setFileSize(0L); // fileSize must be > 0

        mockMvc.perform(post("/api/files/upload/multipart/initiate")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── part URL ───────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice@example.com")
    void getPartUrl_success() throws Exception {
        when(fileService.getPartPresignedUrl("alice@example.com", 1L, 2))
                .thenReturn(new PartUrlResponse("https://s3.part.url"));

        mockMvc.perform(get("/api/files/1/part-url").param("partNumber", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value("https://s3.part.url"));
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    void getPartUrl_notMultipartUpload_returns400() throws Exception {
        when(fileService.getPartPresignedUrl(anyString(), eq(1L), anyInt()))
                .thenThrow(new IllegalStateException("Not a multipart upload"));

        mockMvc.perform(get("/api/files/1/part-url").param("partNumber", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    void getPartUrl_fileNotFound_returns404() throws Exception {
        when(fileService.getPartPresignedUrl(anyString(), eq(99L), anyInt()))
                .thenThrow(new NoSuchElementException("File not found"));

        mockMvc.perform(get("/api/files/99/part-url").param("partNumber", "1"))
                .andExpect(status().isNotFound());
    }

    // ── complete multipart ─────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice@example.com")
    void completeMultipart_success() throws Exception {
        CompleteMultipartRequest req = buildCompleteRequest("upload-id-123", 1, "\"abc123\"");
        doNothing().when(fileService).completeMultipartUpload(anyString(), eq(1L), any());

        mockMvc.perform(post("/api/files/1/complete-multipart")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    void completeMultipart_missingETag_returns400() throws Exception {
        CompleteMultipartRequest req = buildCompleteRequest("upload-id-123", 1, null);
        doThrow(new IllegalStateException("One or more parts have a missing ETag"))
                .when(fileService).completeMultipartUpload(anyString(), eq(1L), any());

        mockMvc.perform(post("/api/files/1/complete-multipart")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    void completeMultipart_fileNotFound_returns404() throws Exception {
        CompleteMultipartRequest req = buildCompleteRequest("upload-id-123", 1, "\"abc\"");
        doThrow(new NoSuchElementException("File not found"))
                .when(fileService).completeMultipartUpload(anyString(), eq(99L), any());

        mockMvc.perform(post("/api/files/99/complete-multipart")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // ── list / download / share ────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice@example.com")
    void listFiles_success() throws Exception {
        FileMetadataDto dto = FileMetadataDto.builder()
                .id(1L).originalName("doc.pdf").contentType("application/pdf")
                .fileSize(1024L).uploadedAt(LocalDateTime.now()).build();

        when(fileService.listFiles("alice@example.com")).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].originalName").value("doc.pdf"));
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    void getDownloadUrl_success() throws Exception {
        when(fileService.getDownloadUrl("alice@example.com", 1L)).thenReturn("https://download.url");

        mockMvc.perform(get("/api/files/1/download"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://download.url"));
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    void getShareUrl_expiring_success() throws Exception {
        when(fileService.getShareUrl("alice@example.com", 1L, "expiring")).thenReturn("https://share.url");

        mockMvc.perform(get("/api/files/1/share"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://share.url"))
                .andExpect(jsonPath("$.linkType").value("expiring"));
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    void getShareUrl_permanent_success() throws Exception {
        when(fileService.getShareUrl("alice@example.com", 1L, "permanent")).thenReturn("https://cdn.example.com/file");

        mockMvc.perform(get("/api/files/1/share").param("linkType", "permanent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://cdn.example.com/file"))
                .andExpect(jsonPath("$.linkType").value("permanent"));
    }

    // ── confirm / delete ───────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice@example.com")
    void deleteFile_success() throws Exception {
        doNothing().when(fileService).deleteFile("alice@example.com", 1L);

        mockMvc.perform(delete("/api/files/1").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    void deleteFile_notFound_returns404() throws Exception {
        doThrow(new NoSuchElementException("File not found"))
                .when(fileService).deleteFile("alice@example.com", 99L);

        mockMvc.perform(delete("/api/files/99").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    void confirmUpload_success() throws Exception {
        doNothing().when(fileService).confirmUpload("alice@example.com", 1L);

        mockMvc.perform(post("/api/files/1/confirm").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    void confirmUpload_fileNotFound_returns404() throws Exception {
        doThrow(new NoSuchElementException("File not found"))
                .when(fileService).confirmUpload("alice@example.com", 99L);

        mockMvc.perform(post("/api/files/99/confirm").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    void confirmUpload_notInS3_returns400() throws Exception {
        doThrow(new IllegalStateException("File not found in S3"))
                .when(fileService).confirmUpload("alice@example.com", 1L);

        mockMvc.perform(post("/api/files/1/confirm").with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listFiles_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/files"))
                .andExpect(status().isUnauthorized());
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private CompleteMultipartRequest buildCompleteRequest(String uploadId, int partNumber, String eTag) {
        CompleteMultipartRequest req = new CompleteMultipartRequest();
        req.setUploadId(uploadId);
        CompleteMultipartRequest.PartInfo part = new CompleteMultipartRequest.PartInfo();
        part.setPartNumber(partNumber);
        part.setETag(eTag);
        req.setParts(List.of(part));
        return req;
    }
}
