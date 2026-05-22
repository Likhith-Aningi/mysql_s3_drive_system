package com.drive.controller;

import com.drive.dto.FileMetadataDto;
import com.drive.dto.PresignedUploadRequest;
import com.drive.dto.PresignedUploadResponse;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
}
