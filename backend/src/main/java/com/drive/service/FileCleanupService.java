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
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(20);
        List<FileMetadata> expired =
                fileRepository.findByUploadStatusAndUploadedAtBefore(UploadStatus.PENDING, cutoff);

        if (expired.isEmpty()) return;

        log.info("Cleaning up {} expired pending upload(s)", expired.size());
        for (FileMetadata file : expired) {
            try {
                s3Service.deleteObject(file.getS3Key());
            } catch (Exception e) {
                log.warn("Could not delete S3 object {} during cleanup: {}", file.getS3Key(), e.getMessage());
            }
        }
        fileRepository.deleteAll(expired);
        log.info("Deleted {} orphaned FileMetadata row(s)", expired.size());
    }
}
