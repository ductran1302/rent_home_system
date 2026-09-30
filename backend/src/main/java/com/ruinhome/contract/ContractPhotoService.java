package com.ruinhome.contract;

import com.ruinhome.auth.CurrentUserService;
import com.ruinhome.file.FileStorageService;
import com.ruinhome.user.Role;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ContractPhotoService {

    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    private final ContractPhotoRepository contractPhotoRepository;
    private final ContractRepository contractRepository;
    private final FileStorageService fileStorageService;
    private final CurrentUserService currentUserService;

    public ContractPhotoService(ContractPhotoRepository contractPhotoRepository,
                                ContractRepository contractRepository,
                                FileStorageService fileStorageService,
                                CurrentUserService currentUserService) {
        this.contractPhotoRepository = contractPhotoRepository;
        this.contractRepository = contractRepository;
        this.fileStorageService = fileStorageService;
        this.currentUserService = currentUserService;
    }

    public record ContractPhotoResponse(Long id, String originalName, LocalDateTime uploadedAt,
                                        String contentUrl) {
    }

    @Transactional
    public ContractPhotoResponse upload(Long contractId, MultipartFile file) {
        Contract contract = findManageable(contractId);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn tệp ảnh");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ảnh tối đa 5MB");
        }
        String contentType = file.getContentType();
        if (!fileStorageService.isAllowedType(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Chỉ chấp nhận ảnh JPG, PNG hoặc WebP");
        }
        String relativePath = fileStorageService.store("contracts/" + contractId, file, contentType);

        ContractPhoto photo = new ContractPhoto();
        photo.setContract(contract);
        photo.setFilePath(relativePath);
        photo.setOriginalName(file.getOriginalFilename());
        ContractPhoto saved = contractPhotoRepository.save(photo);
        return toResponse(contractId, saved);
    }

    @Transactional(readOnly = true)
    public List<ContractPhotoResponse> list(Long contractId) {
        Contract contract = findVisible(contractId);
        return contractPhotoRepository.findByContractIdOrderByUploadedAtDesc(contract.getId()).stream()
                .map(photo -> toResponse(contract.getId(), photo))
                .toList();
    }

    @Transactional(readOnly = true)
    public Content findContent(Long contractId, Long photoId) {
        Contract contract = findVisible(contractId);
        ContractPhoto photo = contractPhotoRepository.findById(photoId)
                .filter(item -> item.getContract().getId().equals(contract.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy ảnh"));
        Path path = fileStorageService.resolve(photo.getFilePath());
        if (!Files.exists(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tệp ảnh không còn trên máy chủ");
        }
        try {
            return new Content(Files.readAllBytes(path), fileStorageService.contentTypeFor(photo.getFilePath()));
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được tệp ảnh", e);
        }
    }

    @Transactional
    public void delete(Long contractId, Long photoId) {
        Contract contract = findManageable(contractId);
        ContractPhoto photo = contractPhotoRepository.findById(photoId)
                .filter(item -> item.getContract().getId().equals(contract.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy ảnh"));
        fileStorageService.delete(photo.getFilePath());
        contractPhotoRepository.delete(photo);
    }

    public record Content(byte[] bytes, String contentType) {
    }

    private Contract findVisible(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy hợp đồng"));
        var account = currentUserService.account();
        if (account.getRole() == Role.ADMIN) {
            return contract;
        }
        if (account.getRole() == Role.MANAGER) {
            checkHouseVisible(contract);
            return contract;
        }
        Long personId = currentUserService.personIdOrNull();
        if (personId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền với hợp đồng này");
        }
        boolean related = contract.getHolder().getId().equals(personId)
                || contract.getTenants().stream().anyMatch(tenant -> tenant.getId().equals(personId));
        if (!related) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền với hợp đồng này");
        }
        return contract;
    }

    private Contract findManageable(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy hợp đồng"));
        if (currentUserService.isAdmin()) {
            return contract;
        }
        if (currentUserService.account().getRole() != Role.MANAGER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền với hợp đồng này");
        }
        checkHouseVisible(contract);
        return contract;
    }

    private void checkHouseVisible(Contract contract) {
        Long personId = currentUserService.personId();
        var house = contract.getRoom().getHouse();
        if (!personId.equals(house.getOwner().getId()) && !personId.equals(house.getManager().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền với hợp đồng này");
        }
    }

    private ContractPhotoResponse toResponse(Long contractId, ContractPhoto photo) {
        return new ContractPhotoResponse(
                photo.getId(),
                photo.getOriginalName(),
                photo.getUploadedAt(),
                "/api/contracts/" + contractId + "/photos/" + photo.getId() + "/content");
    }
}
