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

    @Test
    void cleanup_deletesExpiredPendingRows() {
        FileMetadata f1 = pendingFile(1L, "key1");
        FileMetadata f2 = pendingFile(2L, "key2");
        when(fileRepository.findByUploadStatusAndUploadedAtBefore(eq(UploadStatus.PENDING), any()))
                .thenReturn(List.of(f1, f2));

        fileCleanupService.cleanupExpiredPendingUploads();

        verify(s3Service).deleteObject("key1");
        verify(s3Service).deleteObject("key2");
        verify(fileRepository).deleteAll(List.of(f1, f2));
    }

    @Test
    void cleanup_nothingExpired_noDeletes() {
        when(fileRepository.findByUploadStatusAndUploadedAtBefore(any(), any()))
                .thenReturn(List.of());

        fileCleanupService.cleanupExpiredPendingUploads();

        verifyNoInteractions(s3Service);
        verify(fileRepository, never()).deleteAll(any(List.class));
    }

    @Test
    void cleanup_s3DeleteFails_stillDeletesDbRow() {
        FileMetadata f1 = pendingFile(1L, "missing-key");
        when(fileRepository.findByUploadStatusAndUploadedAtBefore(any(), any()))
                .thenReturn(List.of(f1));
        doThrow(new RuntimeException("S3 error")).when(s3Service).deleteObject("missing-key");

        fileCleanupService.cleanupExpiredPendingUploads();

        verify(fileRepository).deleteAll(List.of(f1));
    }
}
