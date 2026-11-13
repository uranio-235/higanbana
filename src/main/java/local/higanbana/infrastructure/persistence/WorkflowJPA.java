package local.higanbana.infrastructure.persistence;

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
import local.higanbana.domain.workflow.WorkflowRepository;
import org.springframework.stereotype.Repository;

@Repository
public class WorkflowJPA implements WorkflowRepository {
    private final NovelJPA novels;
    private final ChunkPlanJPA chunkPlans;
    private final ChunkPlanConfirmationJPA chunkPlanConfirmations;
    private final NovelCharacterJPA characters;
    private final ChunkJPA chunks;
    private final PanelPlanJPA panelPlans;
    private final PanelPlanConfirmationJPA panelPlanConfirmations;
    private final PanelJPA panels;
    private final StudioDraftJPA drafts;
    private final CandidateJPA candidates;
    private final MangaEntryJPA entries;

    public WorkflowJPA(NovelJPA novels, ChunkPlanJPA chunkPlans,
                       ChunkPlanConfirmationJPA chunkPlanConfirmations,
                       NovelCharacterJPA characters, ChunkJPA chunks,
                       PanelPlanJPA panelPlans,
                       PanelPlanConfirmationJPA panelPlanConfirmations,
                       PanelJPA panels, StudioDraftJPA drafts,
                       CandidateJPA candidates, MangaEntryJPA entries) {
        this.novels = novels;
        this.chunkPlans = chunkPlans;
        this.chunkPlanConfirmations = chunkPlanConfirmations;
        this.characters = characters;
        this.chunks = chunks;
        this.panelPlans = panelPlans;
        this.panelPlanConfirmations = panelPlanConfirmations;
        this.panels = panels;
        this.drafts = drafts;
        this.candidates = candidates;
        this.entries = entries;
    }

    @Override public List<Novel> findNovels() { return novels.findNovels(); }
    @Override public Optional<Novel> findNovel(long id) { return novels.findById(id); }
    @Override public void saveNovel(Novel novel) { novels.save(novel); }

    @Override public List<ChunkPlan> findChunkPlans(long novelId) { return chunkPlans.findPlansForNovel(novelId); }
    @Override public Optional<ChunkPlanConfirmation> findChunkPlanConfirmation(long id) {
        return chunkPlanConfirmations.findById(id);
    }
    @Override public void saveChunkPlan(ChunkPlan plan) { chunkPlans.save(plan); }
    @Override public void saveChunkPlanConfirmation(ChunkPlanConfirmation confirmation) {
        chunkPlanConfirmations.save(confirmation);
    }

    @Override public List<NovelCharacter> findCharacters(long novelId) {
        return characters.findCharactersForNovel(novelId);
    }
    @Override public Optional<NovelCharacter> findCharacter(long novelId, long characterId) {
        return characters.findCharacterForNovel(novelId, characterId);
    }
    @Override public void saveCharacter(NovelCharacter character) { characters.save(character); }

    @Override public List<Chunk> findChunks(long novelId) { return chunks.findChunksForNovel(novelId); }
    @Override public Optional<Chunk> findChunk(long id) { return chunks.findById(id); }
    @Override public void saveChunk(Chunk chunk) { chunks.save(chunk); }

    @Override public List<PanelPlan> findPanelPlans(long chunkId) { return panelPlans.findPlansForChunk(chunkId); }
    @Override public Optional<PanelPlanConfirmation> findPanelPlanConfirmation(long id) {
        return panelPlanConfirmations.findById(id);
    }
    @Override public void savePanelPlan(PanelPlan plan) { panelPlans.save(plan); }
    @Override public void deletePanelPlanAndFlush(PanelPlan plan) {
        panelPlans.deleteAndFlush(plan);
    }
    @Override public void savePanelPlanConfirmation(PanelPlanConfirmation confirmation) {
        panelPlanConfirmations.save(confirmation);
    }

    @Override public List<Panel> findPanelsByChunk(long chunkId) { return panels.findPanelsForChunk(chunkId); }
    @Override public List<Panel> findPanelsByNovel(long novelId) { return panels.findPanelsForNovel(novelId); }
    @Override public Optional<Panel> findPanel(long id) { return panels.findById(id); }
    @Override public void savePanel(Panel panel) { panels.save(panel); }

    @Override public List<StudioDraft> findDrafts(long novelId) { return drafts.findDraftsForNovel(novelId); }
    @Override public Optional<StudioDraft> findDraft(long id) { return drafts.findById(id); }
    @Override public void saveDraft(StudioDraft draft) { drafts.save(draft); }
    @Override public void deleteDraft(StudioDraft draft) { drafts.delete(draft); }

    @Override public List<Candidate> findCandidatesByDraft(long draftId) {
        return candidates.findCandidatesForDraft(draftId);
    }
    @Override public List<Candidate> findCandidatesByNovel(long novelId) {
        return candidates.findCandidatesForNovel(novelId);
    }
    @Override public Optional<Candidate> findCandidate(long id) { return candidates.findById(id); }
    @Override public void saveCandidate(Candidate candidate) { candidates.save(candidate); }

    @Override public List<MangaEntry> findEntries(long novelId) { return entries.findEntriesForNovel(novelId); }
    @Override public Optional<MangaEntry> findEntry(long id) { return entries.findById(id); }
    @Override public void saveEntry(MangaEntry entry) { entries.save(entry); }
    @Override public void deleteEntry(MangaEntry entry) { entries.delete(entry); }
}
