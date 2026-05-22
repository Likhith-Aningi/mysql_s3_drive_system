package com.drive.service;

import com.drive.dto.FileMetadataDto;
import com.drive.dto.PresignedUploadRequest;
import com.drive.dto.PresignedUploadResponse;
import com.drive.entity.FileMetadata;
import com.drive.entity.User;
import com.drive.repository.FileRepository;
import com.drive.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
        user = User.builder().id(1L).email("alice@example.com").name("Alice").build();
        file = FileMetadata.builder()
                .id(10L)
                .originalName("test.pdf")
                .s3Key("users/1/uuid/test.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .owner(user)
                .uploadedAt(LocalDateTime.now())
                .build();
    }

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
    }

    @Test
    void listFiles_returnsFilesForOwner() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(fileRepository.findByOwnerIdOrderByUploadedAtDesc(1L)).thenReturn(List.of(file));

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

    @Test
    void deleteFile_success() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(fileRepository.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(file));

        fileService.deleteFile("alice@example.com", 10L);

        verify(s3Service).deleteObject(file.getS3Key());
        verify(fileRepository).delete(file);
    }

    @Test
    void deleteFile_notOwner_throws() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(fileRepository.findByIdAndOwnerId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.deleteFile("alice@example.com", 99L))
                .isInstanceOf(NoSuchElementException.class);
    }
}
