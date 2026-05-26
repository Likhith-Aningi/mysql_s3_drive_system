package com.drive.service;

import com.drive.entity.FileMetadata;
import com.drive.entity.UploadStatus;
import com.drive.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FileCleanupService {

    private static final Logger log = LoggerFactory.getLogger(FileCleanupService.class);

    private final FileRepository fileRepository;
    private final S3Service s3Service;

    @Scheduled(fixedDelayString = "${upload.cleanup.interval-ms:300000}")
    @Transactional
    public void cleanupExpiredPendingUploads() {
        LocalDateTime simpleCutoff = LocalDateTime.now().minusMinutes(20);
        LocalDateTime multipartCutoff = LocalDateTime.now().minusHours(24);

        List<FileMetadata> staleSimple = fileRepository
                .findByUploadStatusAndMultipartUploadIdIsNullAndUploadedAtBefore(UploadStatus.PENDING, simpleCutoff);

        List<FileMetadata> staleMultipart = fileRepository
                .findByUploadStatusAndMultipartUploadIdIsNotNullAndUploadedAtBefore(UploadStatus.PENDING, multipartCutoff);

        for (FileMetadata file : staleSimple) {
            try {
                s3Service.deleteObject(file.getS3Key());
            } catch (Exception e) {
                log.warn("S3 delete failed for {}: {}", file.getS3Key(), e.getMessage());
            }
        }

        for (FileMetadata file : staleMultipart) {
            try {
                s3Service.abortMultipartUpload(file.getS3Key(), file.getMultipartUploadId());
            } catch (Exception e) {
                log.warn("Abort multipart failed for {}: {}", file.getS3Key(), e.getMessage());
            }
        }

        List<FileMetadata> all = new ArrayList<>(staleSimple);
        all.addAll(staleMultipart);
        if (!all.isEmpty()) {
            fileRepository.deleteAll(all);
            log.info("Cleaned up {} simple + {} multipart stale uploads",
                    staleSimple.size(), staleMultipart.size());
        }
    }
}
