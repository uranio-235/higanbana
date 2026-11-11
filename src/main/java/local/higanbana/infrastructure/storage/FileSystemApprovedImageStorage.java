package local.higanbana.infrastructure.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import local.higanbana.domain.image.ApprovedImageStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FileSystemApprovedImageStorage implements ApprovedImageStorage {
    private final Path directory;

    public FileSystemApprovedImageStorage(@Value("${app.manga-dir}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public void save(String filename, byte[] bytes) throws IOException {
        Files.createDirectories(directory);
        Path path = resolve(filename);
        try {
            Files.write(path, bytes);
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    @Override
    public byte[] read(String filename) throws IOException {
        return Files.readAllBytes(resolve(filename));
    }

    @Override
    public void delete(String filename) throws IOException {
        Files.deleteIfExists(resolve(filename));
    }

    private Path resolve(String filename) throws IOException {
        Path path = directory.resolve(filename).normalize();
        if (!directory.equals(path.getParent())) {
            throw new IOException("Nombre de archivo de imagen no valido.");
        }
        return path;
    }
}
