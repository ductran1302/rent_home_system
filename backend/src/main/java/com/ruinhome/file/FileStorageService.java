package com.ruinhome.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private static final Set<String> ALLOWED_TYPES = EXTENSIONS.keySet();

    private final Path baseDir;

    public FileStorageService(@Value("${upload-dir:./uploads}") String uploadDir) {
        this.baseDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public boolean isAllowedType(String contentType) {
        return contentType != null && ALLOWED_TYPES.contains(contentType.toLowerCase(Locale.ROOT));
    }

    public String extensionFor(String contentType) {
        return EXTENSIONS.get(contentType.toLowerCase(Locale.ROOT));
    }

    public String store(String folder, MultipartFile file, String contentType) {
        String filename = UUID.randomUUID() + "." + extensionFor(contentType);
        Path targetDir = baseDir.resolve(folder);
        Path target = targetDir.resolve(filename);
        try {
            Files.createDirectories(targetDir);
            file.transferTo(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Không lưu được tệp", e);
        }
        return folder + "/" + filename;
    }

    public Path resolve(String relativePath) {
        Path resolved = baseDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new SecurityException("Đường dẫn tệp không hợp lệ");
        }
        return resolved;
    }

    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException e) {
            throw new UncheckedIOException("Không xoá được tệp", e);
        }
    }

    public String contentTypeFor(String relativePath) {
        String lower = relativePath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    public void move(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
