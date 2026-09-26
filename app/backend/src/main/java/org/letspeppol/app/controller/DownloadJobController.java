package org.letspeppol.app.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.letspeppol.app.dto.CreateDownloadJobRequest;
import org.letspeppol.app.dto.DownloadJobDto;
import org.letspeppol.app.service.DownloadJobService;
import org.letspeppol.app.util.JwtUtil;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

@RestController
@RequestMapping("/sapi/download-jobs")
@RequiredArgsConstructor
public class DownloadJobController {

    private final DownloadJobService downloadJobService;

    @PostMapping
    public ResponseEntity<DownloadJobDto> create(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody CreateDownloadJobRequest request) {
        DownloadJobDto job = downloadJobService.create(JwtUtil.getPeppolId(jwt), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    @GetMapping
    public List<DownloadJobDto> findAll(@AuthenticationPrincipal Jwt jwt) {
        return downloadJobService.findAll(JwtUtil.getPeppolId(jwt));
    }

    @PostMapping("{id}/retry")
    public ResponseEntity<DownloadJobDto> retry(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        DownloadJobDto job = downloadJobService.retry(JwtUtil.getPeppolId(jwt), id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    @DeleteMapping("{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        downloadJobService.delete(JwtUtil.getPeppolId(jwt), id);
    }

    @GetMapping("{id}/file")
    public ResponseEntity<StreamingResponseBody> download(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        DownloadJobService.DownloadReservation reservation =
                downloadJobService.reserveDownload(JwtUtil.getPeppolId(jwt), id);
        StreamingResponseBody body = outputStream -> {
            try (InputStream inputStream = Files.newInputStream(reservation.file())) {
                inputStream.transferTo(outputStream);
                outputStream.flush();
            } finally {
                downloadJobService.completeTerminalDownload(reservation);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(fileSize(reservation))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(reservation.filename(), StandardCharsets.UTF_8)
                        .build().toString())
                .body(body);
    }

    private long fileSize(DownloadJobService.DownloadReservation reservation) {
        try {
            return Files.size(reservation.file());
        } catch (Exception e) {
            throw new IllegalStateException("Could not read archive file", e);
        }
    }
}
