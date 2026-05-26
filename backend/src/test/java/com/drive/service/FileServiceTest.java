package com.drive.service;

import com.drive.dto.*;
import com.drive.entity.FileMetadata;
import com.drive.entity.UploadStatus;
import com.drive.entity.User;
import com.drive.repository.FileRepository;
import com.drive.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock FileRepository fileRepository;
    @Mock UserRepository userRepository;
    @Mock S3Service s3Service;
    @InjectMocks FileService fileService;

    private User user;
    private FileMetadata file;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(fileService, "cloudfrontBaseUrl", "");
        user = User.builder().id(1L).email("alice@example.com").name("Alice").build();
        file = FileMetadata.builder()
                .id(10L)
                .originalName("test.pdf")
                .s3Key("users/1/uuid/test.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .uploadStatus(UploadStatus.COMPLETED)
                .owner(user)
                .uploadedAt(LocalDateTime.now())
                .build();
    }

    // ── simple upload ──────────────────────────────────────────────────────────

    @Test
    void initiateUpload_success() {
        PresignedUploadRequest req = new PresignedUploadRequest();
        req.setFileName("doc.pdf");
        req.setContentType("application/pdf");
        req.setFileSize(2048L);

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(s3Service.generateUploadPresignedUrl(anyString(), anyString())).thenReturn("https://s3.presigned.url");
        when(fileRepository.save(any())).thenReturn(file);

        PresignedUploadResponse response = fileService.initiateUpload("alice@example.com", req);

        assertThat(response.getUploadUrl()).isEqualTo("https://s3.presigned.url");
        assertThat(response.getFileMetadataId()).isEqualTo(10L);

        ArgumentCaptor<FileMetadata> captor = ArgumentCaptor.forClass(FileMetadata.class);
        verify(fileRepository).save(captor.capture());
        assertThat(captor.getValue().getUploadStatus()).isEqualTo(UploadStatus.PENDING);
    }

    @Test
    void confirmUpload_success() {
        FileMetadata pendingFile = FileMetadata.builder()
                .id(10L).originalName("test.pdf").s3Key("users/1/uuid/test.pdf")
                .contentType("application/pdf").fileSize(1024L)
                .uploadStatus(UploadStatus.PENDING).owner(user).build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(fileRepository.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(pendingFile));
        when(s3Service.objectExists("users/1/uuid/test.pdf")).thenReturn(true);
        when(fileRepository.save(any())).thenReturn(pendingFile);

        fileService.confirmUpload("alice@example.com", 10L);

        ArgumentCaptor<FileMetadata> captor = ArgumentCaptor.forClass(FileMetadata.class);
        verify(fileRepository).save(captor.capture());
        assertThat(captor.getValue().getUploadStatus()).isEqualTo(UploadStatus.COMPLETED);
    }

    @Test
    void confirmUpload_fileNotInS3_throws() {
        FileMetadata pendingFile = FileMetadata.builder()
                .id(10L).originalName("test.pdf").s3Key("users/1/uuid/test.pdf")
                .contentType("application/pdf").fileSize(1024L)
                .uploadStatus(UploadStatus.PENDING).owner(user).build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(fileRepository.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(pendingFile));
        when(s3Service.objectExists("users/1/uuid/test.pdf")).thenReturn(false);

        assertThatThrownBy(() -> fileService.confirmUpload("alice@example.com", 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("S3");
    }

    @Test
    void confirmUpload_fileNotFound_throws() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(fileRepository.findByIdAndOwnerId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.confirmUpload("alice@example.com", 99L))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── multipart initiate ─────────────────────────────────────────────────────

    @Test
    void initiateMultipartUpload_success() {
        PresignedUploadRequest req = new PresignedUploadRequest();
        req.setFileName("video.mp4");
        req.setContentType("video/mp4");
        req.setFileSize(50 * 1024 * 1024L);

        FileMetadata saved = FileMetadata.builder()
                .id(20L).s3Key("users/1/uuid/video.mp4").originalName("video.mp4")
                .contentType("video/mp4").fileSize(50 * 1024 * 1024L)
                .multipartUploadId("upload-id-123").uploadStatus(UploadStatus.PENDING)
                .owner(user).build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(s3Service.initiateMultipartUpload(anyString(), anyString())).thenReturn("upload-id-123");
        when(fileRepository.save(any())).thenReturn(saved);

        MultipartInitiateResponse resp = fileService.initiateMultipartUpload("alice@example.com", req);

        assertThat(resp.getFileMetadataId()).isEqualTo(20L);
        assertThat(resp.getUploadId()).isEqualTo("upload-id-123");
        assertThat(resp.getPartSize()).isEqualTo(5L * 1024 * 1024);

        ArgumentCaptor<FileMetadata> captor = ArgumentCaptor.forClass(FileMetadata.class);
        verify(fileRepository).save(captor.capture());
        assertThat(captor.getValue().getMultipartUploadId()).isEqualTo("upload-id-123");
        assertThat(captor.getValue().getUploadStatus()).isEqualTo(UploadStatus.PENDING);
    }

    @Test
    void initiateMultipartUpload_userNotFound_throws() {
        PresignedUploadRequest req = new PresignedUploadRequest();
        req.setFileName("video.mp4");
        req.setContentType("video/mp4");
        req.setFileSize(1024L);

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.initiateMultipartUpload("alice@example.com", req))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── multipart part URL ─────────────────────────────────────────────────────

    @Test
    void getPartPresignedUrl_success() {
        FileMetadata multipartFile = FileMetadata.builder()
                .id(10L).s3Key("users/1/uuid/video.mp4").originalName("video.mp4")
                .contentType("video/mp4").fileSize(50L)
                .multipartUploadId("upload-id-123").uploadStatus(UploadStatus.PENDING)
                .owner(user).build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(fileRepository.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(multipartFile));
        when(s3Service.generatePartPresignedUrl("users/1/uuid/video.mp4", "upload-id-123", 1))
                .thenReturn("https://s3.part.url");

        PartUrlResponse resp = fileService.getPartPresignedUrl("alice@example.com", 10L, 1);

        assertThat(resp.getUploadUrl()).isEqualTo("https://s3.part.url");
    }

    @Test
    void getPartPresignedUrl_notMultipartUpload_throws() {
        // file.multipartUploadId is null — it was a simple upload
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(fileRepository.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> fileService.getPartPresignedUrl("alice@example.com", 10L, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("multipart");
    }

    @Test
    void getPartPresignedUrl_fileNotFound_throws() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(fileRepository.findByIdAndOwnerId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.getPartPresignedUrl("alice@example.com", 99L, 1))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── multipart complete ─────────────────────────────────────────────────────

    @Test
    void completeMultipartUpload_success() {
        FileMetadata multipartFile = FileMetadata.builder()
                .id(10L).s3Key("users/1/uuid/video.mp4").originalName("video.mp4")
                .contentType("video/mp4").fileSize(50L)
                .multipartUploadId("upload-id-123").uploadStatus(UploadStatus.PENDING)
                .owner(user).build();

        CompleteMultipartRequest req = buildCompleteRequest("upload-id-123", 1, "\"abc123\"");

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(fileRepository.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(multipartFile));
        when(fileRepository.save(any())).thenReturn(multipartFile);

        fileService.completeMultipartUpload("alice@example.com", 10L, req);

        verify(s3Service).completeMultipartUpload(
                eq("users/1/uuid/video.mp4"), eq("upload-id-123"), any());

        ArgumentCaptor<FileMetadata> captor = ArgumentCaptor.forClass(FileMetadata.class);
        verify(fileRepository).save(captor.capture());
        assertThat(captor.getValue().getUploadStatus()).isEqualTo(UploadStatus.COMPLETED);
        assertThat(captor.getValue().getMultipartUploadId()).isNull();
    }

    @Test
    void completeMultipartUpload_nullETag_throws() {
        FileMetadata multipartFile = FileMetadata.builder()
                .id(10L).s3Key("users/1/uuid/video.mp4").originalName("video.mp4")
                .contentType("video/mp4").fileSize(50L)
                .multipartUploadId("upload-id-123").uploadStatus(UploadStatus.PENDING)
                .owner(user).build();

        CompleteMultipartRequest req = buildCompleteRequest("upload-id-123", 1, null);

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(fileRepository.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(multipartFile));

        assertThatThrownBy(() -> fileService.completeMultipartUpload("alice@example.com", 10L, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ETag");

        verifyNoInteractions(s3Service);
    }

    @Test
    void completeMultipartUpload_blankETag_throws() {
        FileMetadata multipartFile = FileMetadata.builder()
                .id(10L).s3Key("users/1/uuid/video.mp4").originalName("video.mp4")
                .contentType("video/mp4").fileSize(50L)
                .multipartUploadId("upload-id-123").uploadStatus(UploadStatus.PENDING)
                .owner(user).build();

        CompleteMultipartRequest req = buildCompleteRequest("upload-id-123", 1, "");

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(fileRepository.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(multipartFile));

        assertThatThrownBy(() -> fileService.completeMultipartUpload("alice@example.com", 10L, req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ETag");

        verifyNoInteractions(s3Service);
    }

    @Test
    void completeMultipartUpload_fileNotFound_throws() {
        CompleteMultipartRequest req = buildCompleteRequest("upload-id-123", 1, "\"abc\"");

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(fileRepository.findByIdAndOwnerId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.completeMultipartUpload("alice@example.com", 99L, req))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── list / download / share ────────────────────────────────────────────────

    @Test
    void listFiles_returnsFilesForOwner() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(fileRepository.findByOwnerIdAndUploadStatusOrderByUploadedAtDesc(1L, UploadStatus.COMPLETED))
                .thenReturn(List.of(file));

        List<FileMetadataDto> files = fileService.listFiles("alice@example.com");

        assertThat(files).hasSize(1);
        assertThat(files.get(0).getOriginalName()).isEqualTo("test.pdf");
    }

    @Test
    void getDownloadUrl_success() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(fileRepository.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(file));
        when(s3Service.generateDownloadPresignedUrl(anyString())).thenReturn("https://download.url");

        String url = fileService.getDownloadUrl("alice@example.com", 10L);

        assertThat(url).isEqualTo("https://download.url");
    }

    @Test
    void getDownloadUrl_pendingFile_throws() {
        file.setUploadStatus(UploadStatus.PENDING);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(fileRepository.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> fileService.getDownloadUrl("alice@example.com", 10L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getDownloadUrl_fileNotFound_throws() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(fileRepository.findByIdAndOwnerId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.getDownloadUrl("alice@example.com", 99L))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void getShareUrl_expiring_success() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(fileRepository.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(file));
        when(s3Service.generateSharePresignedUrl(anyString())).thenReturn("https://share.url");

        String url = fileService.getShareUrl("alice@example.com", 10L, "expiring");

        assertThat(url).isEqualTo("https://share.url");
    }

    @Test
    void getShareUrl_permanent_returnsCloudfrontUrl() {
        file.setCloudfrontUrl("https://cdn.example.com/users/1/uuid/test.pdf");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(fileRepository.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(file));

        String url = fileService.getShareUrl("alice@example.com", 10L, "permanent");

        assertThat(url).isEqualTo("https://cdn.example.com/users/1/uuid/test.pdf");
    }

    // ── delete ─────────────────────────────────────────────────────────────────

    @Test
    void deleteFile_success() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(fileRepository.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(file));

        fileService.deleteFile("alice@example.com", 10L);

        verify(s3Service).deleteObject(file.getS3Key());
        verify(fileRepository).delete(file);
    }

    @Test
    void deleteFile_pendingFile_succeeds() {
        file.setUploadStatus(UploadStatus.PENDING);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(fileRepository.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(file));

        fileService.deleteFile("alice@example.com", 10L);

        verify(s3Service).deleteObject(file.getS3Key());
        verify(fileRepository).delete(file);
    }

    @Test
    void deleteFile_multipart_abortsBeforeDelete() {
        FileMetadata multipartFile = FileMetadata.builder()
                .id(10L).s3Key("users/1/uuid/video.mp4").originalName("video.mp4")
                .contentType("video/mp4").fileSize(50L)
                .multipartUploadId("upload-id-123").uploadStatus(UploadStatus.PENDING)
                .owner(user).build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(fileRepository.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(multipartFile));

        fileService.deleteFile("alice@example.com", 10L);

        verify(s3Service).abortMultipartUpload("users/1/uuid/video.mp4", "upload-id-123");
        verify(s3Service).deleteObject("users/1/uuid/video.mp4");
        verify(fileRepository).delete(multipartFile);
    }

    @Test
    void deleteFile_multipartAbortFails_stillDeletes() {
        FileMetadata multipartFile = FileMetadata.builder()
                .id(10L).s3Key("users/1/uuid/video.mp4").originalName("video.mp4")
                .contentType("video/mp4").fileSize(50L)
                .multipartUploadId("upload-id-123").uploadStatus(UploadStatus.PENDING)
                .owner(user).build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(fileRepository.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(multipartFile));
        doThrow(new RuntimeException("S3 abort failed")).when(s3Service).abortMultipartUpload(any(), any());

        fileService.deleteFile("alice@example.com", 10L);

        verify(s3Service).deleteObject("users/1/uuid/video.mp4");
        verify(fileRepository).delete(multipartFile);
    }

    @Test
    void deleteFile_notOwner_throws() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(fileRepository.findByIdAndOwnerId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.deleteFile("alice@example.com", 99L))
                .isInstanceOf(NoSuchElementException.class);
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
