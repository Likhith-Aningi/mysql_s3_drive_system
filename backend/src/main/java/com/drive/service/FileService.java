package com.drive.service;

import com.drive.dto.FileMetadataDto;
import com.drive.dto.PresignedUploadRequest;
import com.drive.dto.PresignedUploadResponse;
import com.drive.entity.FileMetadata;
import com.drive.entity.UploadStatus;
import com.drive.entity.User;
import com.drive.repository.FileRepository;
import com.drive.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileService {

    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;

    @Value("${cloudfront.url:}")
    private String cloudfrontBaseUrl;

    public PresignedUploadResponse initiateUpload(String userEmail, PresignedUploadRequest request) {
        User owner = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        String s3Key = "users/" + owner.getId() + "/" + UUID.randomUUID() + "/" + request.getFileName();
        String uploadUrl = s3Service.generateUploadPresignedUrl(s3Key, request.getContentType());

        String cloudfrontUrl = cloudfrontBaseUrl.isBlank() ? null
                : cloudfrontBaseUrl.stripTrailing() + "/" + s3Key;

        FileMetadata metadata = FileMetadata.builder()
                .originalName(request.getFileName())
                .s3Key(s3Key)
                .contentType(request.getContentType())
                .fileSize(request.getFileSize())
                .cloudfrontUrl(cloudfrontUrl)
                .owner(owner)
                .build();
        FileMetadata saved = fileRepository.save(metadata);

        return new PresignedUploadResponse(uploadUrl, s3Key, saved.getId());
    }

    @Transactional
    public void confirmUpload(String userEmail, Long fileId) {
        User owner = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        FileMetadata file = fileRepository.findByIdAndOwnerId(fileId, owner.getId())
                .orElseThrow(() -> new NoSuchElementException("File not found"));
        if (file.getUploadStatus() == UploadStatus.COMPLETED) {
            throw new IllegalStateException("Upload already confirmed");
        }
        if (!s3Service.objectExists(file.getS3Key())) {
            throw new IllegalStateException("File not found in S3 — upload may have failed");
        }
        file.setUploadStatus(UploadStatus.COMPLETED);
        fileRepository.save(file);
    }

    public List<FileMetadataDto> listFiles(String userEmail) {
        User owner = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        return fileRepository.findByOwnerIdAndUploadStatusOrderByUploadedAtDesc(owner.getId(), UploadStatus.COMPLETED)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public String getDownloadUrl(String userEmail, Long fileId) {
        User owner = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        FileMetadata file = fileRepository.findByIdAndOwnerId(fileId, owner.getId())
                .orElseThrow(() -> new NoSuchElementException("File not found"));
        if (file.getUploadStatus() != UploadStatus.COMPLETED) {
            throw new IllegalStateException("File upload has not been confirmed");
        }
        if (file.getCloudfrontUrl() != null && !file.getCloudfrontUrl().isBlank()) {
            return file.getCloudfrontUrl();
        }
        return s3Service.generateDownloadPresignedUrl(file.getS3Key());
    }

    // linkType: "expiring" (7-day presigned S3) | "permanent" (CloudFront URL)
    public String getShareUrl(String userEmail, Long fileId, String linkType) {
        User owner = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        FileMetadata file = fileRepository.findByIdAndOwnerId(fileId, owner.getId())
                .orElseThrow(() -> new NoSuchElementException("File not found"));
        if (file.getUploadStatus() != UploadStatus.COMPLETED) {
            throw new IllegalStateException("File upload has not been confirmed");
        }
        if ("permanent".equalsIgnoreCase(linkType)) {
            if (file.getCloudfrontUrl() == null || file.getCloudfrontUrl().isBlank()) {
                throw new IllegalStateException("No CloudFront URL available for this file");
            }
            return file.getCloudfrontUrl();
        }
        return s3Service.generateSharePresignedUrl(file.getS3Key());
    }

    @Transactional
    public void deleteFile(String userEmail, Long fileId) {
        User owner = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        FileMetadata file = fileRepository.findByIdAndOwnerId(fileId, owner.getId())
                .orElseThrow(() -> new NoSuchElementException("File not found"));
        s3Service.deleteObject(file.getS3Key());
        fileRepository.delete(file);
    }

    private FileMetadataDto toDto(FileMetadata f) {
        return FileMetadataDto.builder()
                .id(f.getId())
                .originalName(f.getOriginalName())
                .contentType(f.getContentType())
                .fileSize(f.getFileSize())
                .uploadedAt(f.getUploadedAt())
                .cloudfrontUrl(f.getCloudfrontUrl())
                .build();
    }
}
