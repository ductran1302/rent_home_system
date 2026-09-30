package com.ruinhome.contract;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/contracts/{contractId}/photos")
public class ContractPhotoController {

    private final ContractPhotoService contractPhotoService;

    public ContractPhotoController(ContractPhotoService contractPhotoService) {
        this.contractPhotoService = contractPhotoService;
    }

    @GetMapping
    public List<ContractPhotoService.ContractPhotoResponse> list(@PathVariable Long contractId) {
        return contractPhotoService.list(contractId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ContractPhotoService.ContractPhotoResponse upload(
            @PathVariable Long contractId,
            @RequestParam("file") MultipartFile file) {
        return contractPhotoService.upload(contractId, file);
    }

    @GetMapping("/{photoId}/content")
    public ResponseEntity<byte[]> content(@PathVariable Long contractId,
                                          @PathVariable Long photoId) {
        ContractPhotoService.Content content = contractPhotoService.findContent(contractId, photoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(content.bytes());
    }

    @DeleteMapping("/{photoId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public void delete(@PathVariable Long contractId, @PathVariable Long photoId) {
        contractPhotoService.delete(contractId, photoId);
    }
}
