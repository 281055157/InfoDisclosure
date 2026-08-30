package com.example.disclosurereview.storage;

import com.example.disclosurereview.config.ReviewProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class LocalFileStorageService implements FileStorageService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final Path rootDirectory;

    public LocalFileStorageService(ReviewProperties properties) {
        this.rootDirectory = Path.of(properties.getStorage().getRootDirectory()).toAbsolutePath().normalize();
    }

    @Override
    public StoredFile save(InputStream inputStream, String originalFileName) {
        try {
            String extension = extension(originalFileName);
            if (!isSupportedExtension(extension)) {
                throw new IllegalArgumentException("不支持的文件扩展名: " + extension);
            }
            String day = LocalDate.now().format(DAY);
            String storedFileName = UUID.randomUUID() + extension;
            Path directory = rootDirectory.resolve(day).normalize();
            ensureWithinRoot(directory);
            Files.createDirectories(directory);
            Path target = directory.resolve(storedFileName).normalize();
            ensureWithinRoot(target);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = 0;
            try (InputStream in = inputStream; OutputStream out = Files.newOutputStream(target)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                    size += read;
                }
            }
            try {
                validateMagic(target, extension);
            } catch (RuntimeException | IOException e) {
                Files.deleteIfExists(target);
                throw e;
            }
            String storageKey = day + "/" + storedFileName;
            return new StoredFile(originalFileName, storedFileName, storageKey,
                    target.toString(), HexFormat.of().formatHex(digest.digest()), size);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new FileStorageException("文件保存失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Resource load(String storageKey) {
        Path path = resolve(storageKey);
        if (!Files.exists(path)) {
            throw new FileStorageException("文件不存在: " + storageKey);
        }
        return new FileSystemResource(path);
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            throw new FileStorageException("文件删除失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.exists(resolve(storageKey));
    }

    private Path resolve(String storageKey) {
        if (!StringUtils.hasText(storageKey)) {
            throw new FileStorageException("storageKey 不能为空");
        }
        Path path = rootDirectory.resolve(storageKey).normalize();
        ensureWithinRoot(path);
        return path;
    }

    private void ensureWithinRoot(Path path) {
        if (!path.startsWith(rootDirectory)) {
            throw new FileStorageException("非法文件路径");
        }
    }

    private String extension(String originalFileName) {
        String name = originalFileName == null ? "" : Path.of(originalFileName).getFileName().toString();
        int idx = name.lastIndexOf('.');
        if (idx < 0) {
            return "";
        }
        return name.substring(idx).toLowerCase(Locale.ROOT);
    }

    private boolean isSupportedExtension(String extension) {
        return ".pdf".equals(extension) || ".xlsx".equals(extension) || ".xls".equals(extension);
    }

    private void validateMagic(Path path, String extension) throws IOException {
        byte[] header = new byte[8];
        int read;
        try (InputStream in = Files.newInputStream(path)) {
            read = in.read(header);
        }
        if (".pdf".equals(extension) && !startsWith(header, read, "%PDF".getBytes())) {
            throw new FileStorageException("PDF 文件格式校验失败");
        }
        if (".xlsx".equals(extension) && !startsWith(header, read, new byte[]{'P', 'K'})) {
            throw new FileStorageException("XLSX 文件格式校验失败");
        }
        if (".xls".equals(extension) && !startsWith(header, read,
                new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0})) {
            throw new FileStorageException("XLS 文件格式校验失败");
        }
    }

    private boolean startsWith(byte[] header, int read, byte[] expected) {
        if (read < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (header[i] != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
