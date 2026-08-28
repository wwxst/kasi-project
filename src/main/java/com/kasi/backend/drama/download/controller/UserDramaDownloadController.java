package com.kasi.backend.drama.download.controller;

import com.kasi.backend.common.response.ApiResponse;
import com.kasi.backend.drama.download.dto.CreateDramaDownloadTaskDTO;
import com.kasi.backend.drama.download.service.DramaDownloadTaskService;
import com.kasi.backend.drama.download.vo.DramaDownloadTaskVO;
import com.kasi.backend.security.context.AuthContextHolder;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

@RestController
@RequestMapping("/api/user/promotion")
public class UserDramaDownloadController {
    private final DramaDownloadTaskService service;

    public UserDramaDownloadController(DramaDownloadTaskService service) {
        this.service = service;
    }

    @PostMapping("/dramas/{dramaId}/downloads")
    public ApiResponse<DramaDownloadTaskVO> create(@PathVariable Long dramaId,
                                                    @Valid @RequestBody CreateDramaDownloadTaskDTO request) {
        return ApiResponse.success(service.create(AuthContextHolder.getUserId(), dramaId, request));
    }

    @GetMapping("/downloads/{taskId}")
    public ApiResponse<DramaDownloadTaskVO> get(@PathVariable Long taskId) {
        return ApiResponse.success(service.get(AuthContextHolder.getUserId(), taskId));
    }

    @GetMapping("/downloads/{taskId}/file")
    public ResponseEntity<Resource> file(@PathVariable Long taskId) {
        Path path = service.getFile(AuthContextHolder.getUserId(), taskId);
        Resource resource = new FileSystemResource(path);
        String filename = path.getFileName().toString();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(resource);
    }
}
