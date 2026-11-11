package local.higanbana.domain.workflow;

import java.util.List;
import java.util.Optional;
import local.higanbana.domain.entity.Candidate;
import local.higanbana.domain.entity.Chunk;
import local.higanbana.domain.entity.ChunkPlan;
import local.higanbana.domain.entity.ChunkPlanConfirmation;
import local.higanbana.domain.entity.MangaEntry;
import local.higanbana.domain.entity.Novel;
import local.higanbana.domain.entity.NovelCharacter;
import local.higanbana.domain.entity.Panel;
import local.higanbana.domain.entity.PanelPlan;
import local.higanbana.domain.entity.PanelPlanConfirmation;
import local.higanbana.domain.entity.StudioDraft;

public interface WorkflowRepository {
    List<Novel> findNovels();
    Optional<Novel> findNovel(long id);
    void saveNovel(Novel novel);

    List<ChunkPlan> findChunkPlans(long novelId);
    Optional<ChunkPlanConfirmation> findChunkPlanConfirmation(long chunkPlanId);
    void saveChunkPlan(ChunkPlan plan);
    void saveChunkPlanConfirmation(ChunkPlanConfirmation confirmation);

    List<NovelCharacter> findCharacters(long novelId);
    Optional<NovelCharacter> findCharacter(long novelId, long characterId);
    void saveCharacter(NovelCharacter character);

    List<Chunk> findChunks(long novelId);
    Optional<Chunk> findChunk(long id);
    void saveChunk(Chunk chunk);

    List<PanelPlan> findPanelPlans(long chunkId);
    Optional<PanelPlanConfirmation> findPanelPlanConfirmation(long panelPlanId);
    void savePanelPlan(PanelPlan plan);
    void deletePanelPlanAndFlush(PanelPlan plan);
    void savePanelPlanConfirmation(PanelPlanConfirmation confirmation);

    List<Panel> findPanelsByChunk(long chunkId);
    List<Panel> findPanelsByNovel(long novelId);
    Optional<Panel> findPanel(long id);
    void savePanel(Panel panel);

    List<StudioDraft> findDrafts(long novelId);
    Optional<StudioDraft> findDraft(long id);
    void saveDraft(StudioDraft draft);
    void deleteDraft(StudioDraft draft);

    List<Candidate> findCandidatesByDraft(long draftId);
    List<Candidate> findCandidatesByNovel(long novelId);
    Optional<Candidate> findCandidate(long id);
    void saveCandidate(Candidate candidate);

    List<MangaEntry> findEntries(long novelId);
    Optional<MangaEntry> findEntry(long id);
    void saveEntry(MangaEntry entry);
}
