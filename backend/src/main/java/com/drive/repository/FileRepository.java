package com.drive.repository;

import com.drive.entity.FileMetadata;
import com.drive.entity.UploadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileRepository extends JpaRepository<FileMetadata, Long> {
    List<FileMetadata> findByOwnerIdAndUploadStatusOrderByUploadedAtDesc(Long ownerId, UploadStatus status);
    Optional<FileMetadata> findByIdAndOwnerId(Long id, Long ownerId);
    boolean existsByIdAndOwnerId(Long id, Long ownerId);
    List<FileMetadata> findByUploadStatusAndUploadedAtBefore(UploadStatus status, LocalDateTime cutoff);

    List<FileMetadata> findByUploadStatusAndMultipartUploadIdIsNullAndUploadedAtBefore(
            UploadStatus status, LocalDateTime cutoff);

    List<FileMetadata> findByUploadStatusAndMultipartUploadIdIsNotNullAndUploadedAtBefore(
            UploadStatus status, LocalDateTime cutoff);
}
