package local.higanbana;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

public final class Domain {
    private Domain() {}

    public enum ReviewState { PENDING, APPROVED, REJECTED }

    @MappedSuperclass
    public abstract static class Row {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        public Long id;
        @Version public long version;
    }

    @Entity(name = "Novel")
    public static class Novel extends Row {
        public String title;
        @Lob public String source;
    }

    @Entity(name = "ChunkPlan")
    @Table(uniqueConstraints = @UniqueConstraint(columnNames = "novelId"))
    public static class ChunkPlan extends Row {
        public Long novelId;
        public int chunkLimit;
        public boolean confirmed;
        @Lob public String advice;
        @ElementCollection @OrderColumn(name = "segment_order") @Lob
        public List<String> segments = new ArrayList<>();
    }

    @Entity(name = "Chunk")
    @Table(uniqueConstraints = @UniqueConstraint(columnNames = {"novelId", "position"}))
    public static class Chunk extends Row {
        public Long novelId;
        public int position;
        @Lob public String content;
    }

    @Entity(name = "PanelPlan")
    @Table(uniqueConstraints = @UniqueConstraint(columnNames = "chunkId"))
    public static class PanelPlan extends Row {
        public Long novelId;
        public Long chunkId;
        public boolean confirmed;
        public int panelCount;
        public String provenance;
        @ElementCollection @OrderColumn(name = "panel_order") @Lob
        public List<String> descriptions = new ArrayList<>();
    }

    @Entity(name = "Panel")
    @Table(uniqueConstraints = @UniqueConstraint(columnNames = {"chunkId", "position"}))
    public static class Panel extends Row {
        public Long novelId;
        public Long chunkId;
        public int chunkPosition;
        public int position;
        @Lob public String description;
    }

    @Entity(name = "StudioDraft")
    public static class StudioDraft extends Row {
        public Long novelId;
        public Long firstPanelId;
        public Long secondPanelId;
        public int narrativeOrder;
        @Lob public String prompt;
        public String provenance;
    }

    @Entity(name = "Candidate")
    public static class Candidate extends Row {
        public Long novelId;
        public Long draftId;
        public Long firstPanelId;
        public Long secondPanelId;
        public int narrativeOrder;
        @Lob public String prompt;
        @Enumerated(EnumType.STRING) public ReviewState state = ReviewState.PENDING;
        @Lob public byte[] image;
        public String mediaType;
        public String provenance;
    }

    @Entity(name = "MangaEntry")
    @Table(uniqueConstraints = @UniqueConstraint(columnNames = "candidateId"))
    public static class MangaEntry extends Row {
        public Long novelId;
        public Long candidateId;
        public Long firstPanelId;
        public Long secondPanelId;
        public int narrativeOrder;
        public String filename;
        public String mediaType;
        public String provenance;
        @Lob public String caption;
    }
}