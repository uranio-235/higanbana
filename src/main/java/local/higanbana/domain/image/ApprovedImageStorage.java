package local.higanbana.domain.image;

import java.io.IOException;

public interface ApprovedImageStorage {
    void save(String filename, byte[] bytes) throws IOException;
    byte[] read(String filename) throws IOException;
    void delete(String filename) throws IOException;
}
