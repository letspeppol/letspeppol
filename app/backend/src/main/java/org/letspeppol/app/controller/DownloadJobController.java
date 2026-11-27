package org.letspeppol.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.security.access.prepost.PreAuthorize;
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
@Tag(name = "App Download Jobs", description = "Bulk export endpoints that build, list, and deliver ZIP archives of the authenticated company's documents.")
@SecurityRequirement(name = "oauth2", scopes = "openid")
public class DownloadJobController {

    private final DownloadJobService downloadJobService;

    @PostMapping
    @PreAuthorize("hasAuthority('INVOICE_EXPORT')")
    @Operation(summary = "Request a bulk download", description = "Queues a job that collects the documents matching the given filter into a ZIP archive.")
    public ResponseEntity<DownloadJobDto> create(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody CreateDownloadJobRequest request) {
        DownloadJobDto job = downloadJobService.create(JwtUtil.getPeppolId(jwt), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INVOICE_EXPORT')")
    @Operation(summary = "List download jobs", description = "Returns the authenticated company's download jobs with their progress and state.")
    public List<DownloadJobDto> findAll(@AuthenticationPrincipal Jwt jwt) {
        return downloadJobService.findAll(JwtUtil.getPeppolId(jwt));
    }

    @PostMapping("{id}/retry")
    @PreAuthorize("hasAuthority('INVOICE_EXPORT')")
    @Operation(summary = "Retry a download job", description = "Queues a failed download job again with its original filter.")
    public ResponseEntity<DownloadJobDto> retry(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        DownloadJobDto job = downloadJobService.retry(JwtUtil.getPeppolId(jwt), id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    @DeleteMapping("{id}")
    @PreAuthorize("hasAuthority('INVOICE_EXPORT')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a download job", description = "Removes a download job and its archive file.")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        downloadJobService.delete(JwtUtil.getPeppolId(jwt), id);
    }

    @GetMapping("{id}/file")
    @PreAuthorize("hasAuthority('INVOICE_EXPORT')")
    @Operation(summary = "Download the archive", description = "Streams the ZIP archive produced by a finished download job.")
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
