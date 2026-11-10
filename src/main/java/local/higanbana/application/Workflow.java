package local.higanbana.application;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import local.higanbana.infrastructure.ai.TextAssistant;
import local.higanbana.infrastructure.image.ImageProvider;
import local.higanbana.domain.*;

@Service
@Transactional
public class Workflow {
    private final EntityManager em;
    private final TextAssistant text;
    private final ImageProvider images;
    private final Path mangaDirectory;
    private final int analysisWindow;

    public Workflow(EntityManager em, TextAssistant text, ImageProvider images,
                    @Value("${app.manga-dir}") String mangaDirectory,
                    @Value("${app.chunks.analysis-window-chars:5000}") int analysisWindow) {
        this.em = em;
        this.text = text;
        this.images = images;
        this.mangaDirectory = Path.of(mangaDirectory).toAbsolutePath().normalize();
        if (analysisWindow < 200 || analysisWindow > 5000) {
            throw new IllegalArgumentException("app.chunks.analysis-window-chars debe estar entre 200 y 5000.");
        }
        this.analysisWindow = analysisWindow;
    }

    public String textProvenance() { return text.provenance(); }
    public String chunkProvenance() { return text.chunkProvenance(); }
    public boolean chunkingAvailable() { return text.chunkingAvailable(); }

    public List<Novel> novels() {
        return em.createQuery("from Novel order by id desc", Novel.class).getResultList();
    }

    public Novel novel(long novelId) { return required(Novel.class, novelId); }

    public Novel createNovel(String title, String source) {
        validText(title, 160, "Titulo");
        validText(source, 1_000_000, "Texto de la novela");
        Novel novel = new Novel();
        novel.title = title.strip();
        novel.source = source;
        em.persist(novel);
        return novel;
    }

    public ChunkPlan chunkPlan(long novelId) {
        novel(novelId);
        var plans = em.createQuery("from ChunkPlan where novelId=:novel", ChunkPlan.class)
                .setParameter("novel", novelId).getResultList();
        if (plans.isEmpty()) { return null; }
        plans.getFirst().segments.size();
        return plans.getFirst();
    }

    public List<NovelCharacter> characters(long novelId) {
        novel(novelId);
        return em.createQuery("from NovelCharacter where novel.id=:novel order by id", NovelCharacter.class)
                .setParameter("novel", novelId).getResultList();
    }

    public NovelCharacter addCharacter(long novelId, String name, String description) {
        Novel novel = mutableNovel(novelId);
        validText(name, 120, "Nombre del personaje");
        validText(description, 2000, "Descripcion del personaje");
        NovelCharacter character = new NovelCharacter();
        character.name = name.strip();
        character.description = description.strip();
        novel.addCharacter(character);
        em.persist(character);
        return character;
    }

    public void updateCharacter(long novelId, long characterId, String name, String description) {
        mutableNovel(novelId);
        validText(name, 120, "Nombre del personaje");
        validText(description, 2000, "Descripcion del personaje");
        NovelCharacter character = character(novelId, characterId);
        character.name = name.strip();
        character.description = description.strip();
    }

    public void deleteCharacter(long novelId, long characterId) {
        Novel novel = mutableNovel(novelId);
        novel.removeCharacter(character(novelId, characterId));
    }

    private NovelCharacter character(long novelId, long characterId) {
        return em.createQuery("from NovelCharacter where id=:id and novel.id=:novel", NovelCharacter.class)
                .setParameter("id", characterId).setParameter("novel", novelId).getResultStream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("El personaje no pertenece a esta novela."));
    }

    public List<Chunk> chunks(long novelId) {
        novel(novelId);
        return em.createQuery("from Chunk where novelId=:novel order by position", Chunk.class)
                .setParameter("novel", novelId).getResultList();
    }

    public Chunk chunk(long novelId, long chunkId) {
        Chunk chunk = required(Chunk.class, chunkId);
        owned(chunk.novelId, novelId);
        return chunk;
    }

    public ChunkPlan proposeChunks(long novelId, String revision) {
        return proposeChunks(novelId, analysisWindow, revision);
    }

    public ChunkPlan proposeChunks(long novelId, int limit, String revision) {
        Novel novel = mutableNovel(novelId);
        if (limit < 200 || limit > 8000) { fail("El limite debe estar entre 200 y 8000 caracteres."); }
        revision(revision);
        ChunkPlan plan = chunkPlan(novelId);
        if (plan != null && plan.confirmed) { fail("Los chunks ya estan confirmados. No se modifica trabajo posterior."); }
        if (!text.chunkingAvailable()) { fail("La segmentacion narrativa no esta disponible. Configura NVIDIA y su modelo de texto."); }
        List<String> segments = text.chunks(novel.source, limit, revision);
        String advice = text.chunkProvenance() + ": escenas segun cambios de lugar, tiempo y accion. Direccion: " + revision;
        if (plan == null) {
            plan = new ChunkPlan();
            plan.novelId = novelId;
            em.persist(plan);
        }
        plan.chunkLimit = limit;
        plan.segments.clear();
        plan.segments.addAll(segments);
        plan.advice = advice;
        return plan;
    }

    public void confirmChunks(long novelId) {
        mutableNovel(novelId);
        ChunkPlan plan = chunkPlan(novelId);
        if (plan == null) { fail("Primero solicita una propuesta de chunks."); }
        if (plan.confirmed) { return; }
        if (!String.join("", plan.segments).equals(novel(novelId).source)) { fail("La propuesta no conserva el texto original."); }
        for (int index = 0; index < plan.segments.size(); index++) {
            Chunk chunk = new Chunk();
            chunk.novelId = novelId;
            chunk.position = index + 1;
            chunk.content = plan.segments.get(index);
            em.persist(chunk);
        }
        plan.confirmed = true;
    }

    public PanelPlan panelPlan(long novelId, long chunkId) {
        chunk(novelId, chunkId);
        var plans = em.createQuery("from PanelPlan where chunkId=:chunk", PanelPlan.class)
                .setParameter("chunk", chunkId).getResultList();
        if (plans.isEmpty()) { return null; }
        plans.getFirst().descriptions.size();
        return plans.getFirst();
    }

    public PanelPlan proposePanels(long novelId, long chunkId, int count, String revision) {
        mutableNovel(novelId);
        Chunk chunk = chunk(novelId, chunkId);
        if (count < 1 || count > 12) { fail("Elige de 1 a 12 paneles."); }
        revision(revision);
        PanelPlan plan = panelPlan(novelId, chunkId);
        if (plan != null && plan.confirmed) { fail("Los paneles ya estan confirmados; se conserva el trabajo posterior."); }
        List<String> descriptions = text.panels(chunk.content, count, revision);
        validateDescriptions(descriptions, count);
        if (plan == null) {
            plan = new PanelPlan();
            plan.novelId = novelId;
            plan.chunkId = chunkId;
            em.persist(plan);
        }
        plan.panelCount = count;
        plan.provenance = text.provenance();
        plan.descriptions.clear();
        plan.descriptions.addAll(descriptions);
        return plan;
    }

    public void savePanels(long novelId, long chunkId, List<String> descriptions) {
        mutableNovel(novelId);
        PanelPlan plan = panelPlan(novelId, chunkId);
        if (plan == null || plan.confirmed) { fail("No hay una propuesta editable."); }
        validateDescriptions(descriptions, plan.panelCount);
        plan.descriptions.clear();
        plan.descriptions.addAll(descriptions);
    }

    public void confirmPanels(long novelId, long chunkId) {
        mutableNovel(novelId);
        Chunk chunk = chunk(novelId, chunkId);
        PanelPlan plan = panelPlan(novelId, chunkId);
        if (plan == null) { fail("Primero solicita una propuesta de paneles."); }
        if (plan.confirmed) { return; }
        validateDescriptions(plan.descriptions, plan.panelCount);
        for (int index = 0; index < plan.descriptions.size(); index++) {
            Panel panel = new Panel();
            panel.novelId = novelId;
            panel.chunkId = chunkId;
            panel.chunkPosition = chunk.position;
            panel.position = index + 1;
            panel.description = plan.descriptions.get(index);
            em.persist(panel);
        }
        plan.confirmed = true;
    }

    public List<Panel> panels(long novelId) {
        novel(novelId);
        return em.createQuery("from Panel where novelId=:novel order by chunkPosition, position", Panel.class)
                .setParameter("novel", novelId).getResultList();
    }

    public StudioDraft createDraft(long novelId, List<Long> panelIds, String direction) {
        return createDraft(novelId, panelIds, direction, List.of());
    }

    public StudioDraft createDraft(long novelId, List<Long> panelIds, String direction, List<Long> characterIds) {
        mutableNovel(novelId);
        revision(direction);
        if (panelIds == null || panelIds.isEmpty() || panelIds.size() > 2 || panelIds.stream().distinct().count() != panelIds.size()) {
            fail("Selecciona exactamente uno o dos paneles distintos de la misma novela.");
        }
        List<Panel> selected = new ArrayList<>();
        for (Long panelId : panelIds) {
            if (panelId == null) { fail("Panel no valido."); }
            Panel panel = required(Panel.class, panelId);
            owned(panel.novelId, novelId);
            selected.add(panel);
        }
        selected.sort(Comparator.comparingInt((Panel panel) -> panel.chunkPosition).thenComparingInt(panel -> panel.position));
        List<Long> castIds = characterIds == null ? List.of() : characterIds;
        if (castIds.stream().anyMatch(Objects::isNull) || castIds.stream().distinct().count() != castIds.size()) {
            fail("Selecciona personajes distintos de esta novela.");
        }
        List<String> characterDescriptions = castIds.stream().sorted()
            .map(characterId -> character(novelId, characterId))
            .map(character -> character.name + ": " + character.description).toList();
        String characterContext = characterDescriptions.isEmpty() ? "" : "Personajes:\n" + String.join("\n", characterDescriptions);
        if (characterContext.length() > 3000) { fail("Las fichas seleccionadas superan 3000 caracteres. Selecciona menos personajes."); }
        String context = characterContext + "\n" + String.join("\n", selected.stream().map(panel -> panel.description).toList());
        validText(context, 8000, "Contexto de paneles y personajes; reduce la seleccion");
        String prompt = text.describe(context, direction);
        validText(prompt, 6000, "Descripcion");
        StudioDraft draft = new StudioDraft();
        draft.novelId = novelId;
        draft.firstPanelId = selected.getFirst().id;
        draft.secondPanelId = selected.size() == 2 ? selected.get(1).id : null;
        draft.narrativeOrder = selected.getFirst().chunkPosition * 100 + selected.getFirst().position;
        draft.prompt = prompt;
        draft.characterContext = characterContext;
        draft.provenance = text.provenance();
        em.persist(draft);
        return draft;
    }

    public List<StudioDraft> drafts(long novelId) {
        novel(novelId);
        return em.createQuery("from StudioDraft where novelId=:novel order by id desc", StudioDraft.class)
                .setParameter("novel", novelId).getResultList();
    }

    public void saveDraft(long novelId, long draftId, String prompt) {
        mutableNovel(novelId);
        StudioDraft draft = required(StudioDraft.class, draftId);
        owned(draft.novelId, novelId);
        validText(prompt, 6000, "Descripcion");
        draft.prompt = prompt;
    }

    public Candidate generate(long novelId, long draftId) {
        mutableNovel(novelId);
        StudioDraft draft = required(StudioDraft.class, draftId);
        owned(draft.novelId, novelId);
        validText(draft.prompt, 6000, "Descripcion guardada");
        String imagePrompt = draft.prompt + (draft.characterContext.isEmpty() ? "" : "\n\n" + draft.characterContext);
        validText(imagePrompt, 10000, "Descripcion y fichas de personajes");
        ImageProvider.ImageResult image = images.generate(imagePrompt);
        if (image.bytes() == null || image.bytes().length == 0 || image.bytes().length > 5_000_000
                || !List.of("image/svg+xml", "image/png", "image/jpeg").contains(image.mediaType())) {
            fail("El proveedor devolvio una imagen no valida.");
        }
        Candidate candidate = new Candidate();
        candidate.novelId = novelId;
        candidate.draftId = draftId;
        candidate.firstPanelId = draft.firstPanelId;
        candidate.secondPanelId = draft.secondPanelId;
        candidate.narrativeOrder = draft.narrativeOrder;
        candidate.prompt = imagePrompt;
        candidate.image = image.bytes();
        candidate.mediaType = image.mediaType();
        candidate.provenance = image.provenance();
        em.persist(candidate);
        return candidate;
    }

    public List<Candidate> candidates(long novelId) {
        novel(novelId);
        return em.createQuery("from Candidate where novelId=:novel order by id desc", Candidate.class)
                .setParameter("novel", novelId).getResultList();
    }

    public Candidate candidate(long novelId, long candidateId) {
        Candidate candidate = required(Candidate.class, candidateId);
        owned(candidate.novelId, novelId);
        return candidate;
    }

    public void reject(long novelId, long candidateId) {
        mutableNovel(novelId);
        Candidate candidate = candidate(novelId, candidateId);
        if (candidate.state == ReviewState.REJECTED) { return; }
        if (candidate.state != ReviewState.PENDING) { fail("Una imagen aprobada no se puede rechazar."); }
        candidate.state = ReviewState.REJECTED;
        candidate.image = null;
    }

    public MangaEntry approve(long novelId, long candidateId) {
        mutableNovel(novelId);
        Candidate candidate = candidate(novelId, candidateId);
        if (candidate.state == ReviewState.APPROVED) {
            return reader(novelId).stream().filter(entry -> entry.candidateId == candidateId).findFirst().orElseThrow();
        }
        if (candidate.state != ReviewState.PENDING) { fail("Solo se pueden aprobar candidatos pendientes."); }
        for (MangaEntry entry : reader(novelId)) {
            if (overlaps(entry, candidate.firstPanelId) || (candidate.secondPanelId != null && overlaps(entry, candidate.secondPanelId))) {
                fail("Uno de estos paneles ya tiene una imagen aprobada.");
            }
        }
        String extension = switch (candidate.mediaType) { case "image/svg+xml" -> ".svg"; case "image/png" -> ".png"; default -> ".jpg"; };
        String filename = UUID.randomUUID() + extension;
        Path destination = mangaDirectory.resolve(filename);
        try {
            Files.createDirectories(mangaDirectory);
            Files.write(destination, candidate.image);
        } catch (IOException exception) {
            try { Files.deleteIfExists(destination); } catch (IOException ignored) {}
            throw new IllegalStateException("No se pudo guardar la imagen aprobada en el almacenamiento local.");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try { Files.deleteIfExists(destination); } catch (IOException ignored) {}
                }
            }
        });
        MangaEntry entry = new MangaEntry();
        entry.novelId = novelId;
        entry.candidateId = candidateId;
        entry.firstPanelId = candidate.firstPanelId;
        entry.secondPanelId = candidate.secondPanelId;
        entry.narrativeOrder = candidate.narrativeOrder;
        entry.filename = filename;
        entry.mediaType = candidate.mediaType;
        entry.provenance = candidate.provenance;
        entry.caption = candidate.prompt;
        em.persist(entry);
        candidate.state = ReviewState.APPROVED;
        candidate.image = null;
        return entry;
    }

    public List<MangaEntry> reader(long novelId) {
        novel(novelId);
        return em.createQuery("from MangaEntry where novelId=:novel order by narrativeOrder, id", MangaEntry.class)
                .setParameter("novel", novelId).getResultList();
    }

    public ImageProvider.ImageResult candidateImage(long novelId, long candidateId) {
        Candidate candidate = candidate(novelId, candidateId);
        if (candidate.state == ReviewState.APPROVED) {
            MangaEntry entry = reader(novelId).stream().filter(item -> item.candidateId == candidateId).findFirst().orElseThrow();
            return entryImage(novelId, entry.id);
        }
        if (candidate.state != ReviewState.PENDING) { fail("La imagen rechazada ya no esta disponible."); }
        return new ImageProvider.ImageResult(candidate.image, candidate.mediaType, candidate.provenance);
    }

    public ImageProvider.ImageResult entryImage(long novelId, long entryId) {
        MangaEntry entry = required(MangaEntry.class, entryId);
        owned(entry.novelId, novelId);
        try {
            return new ImageProvider.ImageResult(Files.readAllBytes(mangaDirectory.resolve(entry.filename)), entry.mediaType, entry.provenance);
        } catch (IOException exception) {
            throw new IllegalStateException("No se encuentra la imagen aprobada en el disco local.");
        }
    }

    private boolean overlaps(MangaEntry entry, Long panelId) {
        return Objects.equals(entry.firstPanelId, panelId) || Objects.equals(entry.secondPanelId, panelId);
    }

    private Novel mutableNovel(long novelId) {
        return novel(novelId);
    }

    private <T> T required(Class<T> type, long id) {
        T entity = em.find(type, id);
        if (entity == null) { fail("El elemento solicitado no existe."); }
        return entity;
    }

    private void owned(Long actualNovelId, long novelId) {
        if (actualNovelId == null || actualNovelId != novelId) { fail("El elemento no pertenece a esta novela."); }
    }

    private void revision(String value) {
        if (value == null || value.length() > 1500) { fail("La revision admite hasta 1500 caracteres."); }
    }

    private void validateDescriptions(List<String> descriptions, int count) {
        if (descriptions == null || descriptions.size() != count) { fail("La propuesta debe contener exactamente " + count + " paneles."); }
        for (String description : descriptions) { validText(description, 3000, "Descripcion del panel"); }
    }

    private void validText(String value, int max, String label) {
        if (value == null || value.isBlank() || value.length() > max) { fail(label + ": campo obligatorio, maximo " + max + " caracteres."); }
    }

    private void fail(String message) { throw new IllegalArgumentException(message); }
}