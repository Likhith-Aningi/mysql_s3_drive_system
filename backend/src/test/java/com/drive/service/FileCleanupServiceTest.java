package com.drive.service;

import com.drive.entity.FileMetadata;
import com.drive.entity.UploadStatus;
import com.drive.entity.User;
import com.drive.repository.FileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileCleanupServiceTest {

    @Mock FileRepository fileRepository;
    @Mock S3Service s3Service;
    @InjectMocks FileCleanupService fileCleanupService;

    private FileMetadata pendingFile(Long id, String s3Key) {
        User owner = User.builder().id(1L).email("x@x.com").name("X").build();
        return FileMetadata.builder()
                .id(id).s3Key(s3Key).uploadStatus(UploadStatus.PENDING)
                .originalName("file.pdf").contentType("application/pdf").fileSize(100L)
                .owner(owner).build();
    }

    private FileMetadata pendingMultipartFile(Long id, String s3Key, String uploadId) {
        User owner = User.builder().id(1L).email("x@x.com").name("X").build();
        return FileMetadata.builder()
                .id(id).s3Key(s3Key).uploadStatus(UploadStatus.PENDING)
                .originalName("file.mp4").contentType("video/mp4").fileSize(100L)
                .multipartUploadId(uploadId)
                .owner(owner).build();
    }

    @Test
    void cleanup_deletesExpiredSimplePendingRows() {
        FileMetadata f1 = pendingFile(1L, "key1");
        FileMetadata f2 = pendingFile(2L, "key2");
        when(fileRepository.findByUploadStatusAndMultipartUploadIdIsNullAndUploadedAtBefore(any(), any()))
                .thenReturn(List.of(f1, f2));
        when(fileRepository.findByUploadStatusAndMultipartUploadIdIsNotNullAndUploadedAtBefore(any(), any()))
                .thenReturn(List.of());

        fileCleanupService.cleanupExpiredPendingUploads();

        verify(s3Service).deleteObject("key1");
        verify(s3Service).deleteObject("key2");
        verify(fileRepository).deleteAll(List.of(f1, f2));
    }

    @Test
    void cleanup_abortsExpiredMultipartPendingRows() {
        FileMetadata f1 = pendingMultipartFile(1L, "key1", "upload-id-1");
        when(fileRepository.findByUploadStatusAndMultipartUploadIdIsNullAndUploadedAtBefore(any(), any()))
                .thenReturn(List.of());
        when(fileRepository.findByUploadStatusAndMultipartUploadIdIsNotNullAndUploadedAtBefore(any(), any()))
                .thenReturn(List.of(f1));

        fileCleanupService.cleanupExpiredPendingUploads();

        verify(s3Service).abortMultipartUpload("key1", "upload-id-1");
        verifyNoMoreInteractions(s3Service);
        verify(fileRepository).deleteAll(List.of(f1));
    }

    @Test
    void cleanup_nothingExpired_noDeletes() {
        when(fileRepository.findByUploadStatusAndMultipartUploadIdIsNullAndUploadedAtBefore(any(), any()))
                .thenReturn(List.of());
        when(fileRepository.findByUploadStatusAndMultipartUploadIdIsNotNullAndUploadedAtBefore(any(), any()))
                .thenReturn(List.of());

        fileCleanupService.cleanupExpiredPendingUploads();

        verifyNoInteractions(s3Service);
        verify(fileRepository, never()).deleteAll(any(List.class));
    }

    @Test
    void cleanup_s3DeleteFails_stillDeletesDbRow() {
        FileMetadata f1 = pendingFile(1L, "missing-key");
        when(fileRepository.findByUploadStatusAndMultipartUploadIdIsNullAndUploadedAtBefore(any(), any()))
                .thenReturn(List.of(f1));
        when(fileRepository.findByUploadStatusAndMultipartUploadIdIsNotNullAndUploadedAtBefore(any(), any()))
                .thenReturn(List.of());
        doThrow(new RuntimeException("S3 error")).when(s3Service).deleteObject("missing-key");

        fileCleanupService.cleanupExpiredPendingUploads();

        verify(fileRepository).deleteAll(List.of(f1));
    }
}
