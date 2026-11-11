package local.higanbana.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import local.higanbana.infrastructure.ai.TextAssistant;
import local.higanbana.infrastructure.image.ImageProvider;
import local.higanbana.domain.ReviewState;
import local.higanbana.domain.entity.*;
import local.higanbana.domain.workflow.WorkflowRepository;

@Service
@Transactional
public class Workflow {
    private final WorkflowRepository repository;
    private final TextAssistant text;
    private final Path mangaDirectory;
    private final int analysisWindow;

    public Workflow(WorkflowRepository repository, TextAssistant text,
                    @Value("${app.manga-dir}") String mangaDirectory,
                    @Value("${app.chunks.analysis-window-chars:5000}") int analysisWindow) {
        this.repository = repository;
        this.text = text;
        this.mangaDirectory = Path.of(mangaDirectory).toAbsolutePath().normalize();
        if (analysisWindow < 200 || analysisWindow > 5000) {
            throw new IllegalArgumentException("app.chunks.analysis-window-chars debe estar entre 200 y 5000.");
        }
        this.analysisWindow = analysisWindow;
    }

    public String textProvenance() { return text.provenance(); }
    public String chunkProvenance() { return text.chunkProvenance(); }
    public boolean chunkingAvailable() { return text.chunkingAvailable(); }
    public boolean imagePanelPromptsAvailable() { return text.imagePanelPromptsAvailable(); }
    public String imagePanelPromptProvenance() { return text.imagePanelPromptProvenance(); }

    public List<Novel> novels() {
        return repository.findNovels();
    }

    public Novel novel(long novelId) { return required(repository.findNovel(novelId)); }

    public Novel createNovel(String title, String source) {
        validText(title, 160, "Titulo");
        validText(source, 1_000_000, "Texto de la novela");
        Novel novel = new Novel();
        novel.title = title.strip();
        novel.source = source;
        repository.saveNovel(novel);
        return novel;
    }

    public ChunkPlan chunkPlan(long novelId) {
        novel(novelId);
        var plans = repository.findChunkPlans(novelId);
        if (plans.isEmpty()) { return null; }
        ChunkPlan plan = plans.getFirst();
        plan.segments.size();
        plan.confirmed = repository.findChunkPlanConfirmation(plan.id).isPresent();
        return plan;
    }

    public List<NovelCharacter> characters(long novelId) {
        novel(novelId);
        return repository.findCharacters(novelId);
    }

    public NovelCharacter addCharacter(long novelId, String name, String description) {
        Novel novel = mutableNovel(novelId);
        validText(name, 120, "Nombre del personaje");
        validText(description, 2000, "Descripcion del personaje");
        NovelCharacter character = new NovelCharacter();
        character.name = name.strip();
        character.description = description.strip();
        novel.addCharacter(character);
        repository.saveCharacter(character);
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
        return repository.findCharacter(novelId, characterId)
                .orElseThrow(() -> new IllegalArgumentException("El personaje no pertenece a esta novela."));
    }

    public List<Chunk> chunks(long novelId) {
        novel(novelId);
        return repository.findChunks(novelId);
    }

    public Chunk chunk(long novelId, long chunkId) {
        Chunk chunk = required(repository.findChunk(chunkId));
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
            repository.saveChunkPlan(plan);
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
            repository.saveChunk(chunk);
        }
        repository.saveChunkPlanConfirmation(new ChunkPlanConfirmation(plan.id));
        plan.confirmed = true;
    }

    public PanelPlan panelPlan(long novelId, long chunkId) {
        chunk(novelId, chunkId);
        var plans = repository.findPanelPlans(chunkId);
        if (plans.isEmpty()) { return null; }
        PanelPlan plan = plans.getFirst();
        plan.imagePrompts.size();
        Optional<PanelPlanConfirmation> confirmation = repository.findPanelPlanConfirmation(plan.id);
        plan.confirmed = confirmation.isPresent();
        confirmation.map(value -> value.provenance).filter(Objects::nonNull)
                .ifPresent(value -> plan.provenance = value);
        return plan;
    }

    public PanelPlan proposePanels(long novelId, long chunkId, int count, String revision) {
        mutableNovel(novelId);
        Chunk chunk = chunk(novelId, chunkId);
        if (count < 1 || count > 12) { fail("Elige de 1 a 12 paneles."); }
        revision(revision);
        PanelPlan plan = panelPlan(novelId, chunkId);
        if (plan != null && plan.confirmed && count != plan.panelCount) {
            fail("El plan esta confirmado; al regenerar se conserva el numero de paneles.");
        }
        List<String> imagePrompts = text.imagePanelPrompts(chunk.content, count, revision);
        validateImagePrompts(imagePrompts, count);
        if (plan != null && plan.confirmed) {
            List<Panel> savedPanels = repository.findPanelsByChunk(chunkId);
            if (savedPanels.size() != count) { fail("El plan confirmado no coincide con los paneles guardados."); }
            for (int index = 0; index < count; index++) {
                savedPanels.get(index).imagePrompt = imagePrompts.get(index);
            }
            plan.imagePrompts.clear();
            plan.imagePrompts.addAll(imagePrompts);
            PanelPlanConfirmation confirmation = repository.findPanelPlanConfirmation(plan.id)
                    .orElseThrow(() -> new IllegalArgumentException("No se encontro la confirmacion del plan de paneles."));
            confirmation.provenance = text.imagePanelPromptProvenance();
            return plan;
        }
        if (plan != null) {
            repository.deletePanelPlanAndFlush(plan);
        }
        plan = new PanelPlan();
        plan.novelId = novelId;
        plan.chunkId = chunkId;
        plan.panelCount = count;
        plan.provenance = text.imagePanelPromptProvenance();
        plan.imagePrompts.addAll(imagePrompts);
        repository.savePanelPlan(plan);
        return plan;
    }

    public void savePanelImagePrompts(long novelId, long chunkId, List<String> imagePrompts) {
        mutableNovel(novelId);
        PanelPlan plan = panelPlan(novelId, chunkId);
        if (plan == null || plan.confirmed) { fail("No hay prompts de imagen editables."); }
        validateImagePrompts(imagePrompts, plan.panelCount);
        plan.imagePrompts.clear();
        plan.imagePrompts.addAll(imagePrompts);
    }

    public void confirmPanels(long novelId, long chunkId) {
        mutableNovel(novelId);
        Chunk chunk = chunk(novelId, chunkId);
        PanelPlan plan = panelPlan(novelId, chunkId);
        if (plan == null) { fail("Primero solicita una propuesta de paneles."); }
        if (plan.confirmed) { return; }
        if (!"Prompts de imagen / NVIDIA".equals(plan.provenance)) {
            fail("Esta propuesta no fue generada por NVIDIA. Regenera los prompts antes de confirmarla.");
        }
        validateImagePrompts(plan.imagePrompts, plan.panelCount);
        for (int index = 0; index < plan.imagePrompts.size(); index++) {
            Panel panel = new Panel();
            panel.novelId = novelId;
            panel.chunkId = chunkId;
            panel.chunkPosition = chunk.position;
            panel.position = index + 1;
            panel.imagePrompt = plan.imagePrompts.get(index);
            repository.savePanel(panel);
        }
        repository.savePanelPlanConfirmation(new PanelPlanConfirmation(plan.id, plan.provenance));
    }

    public List<Panel> panels(long novelId) {
        novel(novelId);
        return repository.findPanelsByNovel(novelId);
    }

    public StudioDraft createDraft(long novelId, List<Long> panelIds, String direction) {
        return createDraft(novelId, panelIds, direction, List.of());
    }

    public StudioDraft createDraft(long novelId, List<Long> panelIds, String direction, List<Long> characterIds) {
        mutableNovel(novelId);
        revision(direction);
        if (panelIds == null || panelIds.isEmpty() || panelIds.stream().distinct().count() != panelIds.size()) {
            fail("Selecciona paneles distintos de un mismo chunk.");
        }
        List<Panel> selected = new ArrayList<>();
        for (Long panelId : panelIds) {
            if (panelId == null) { fail("Panel no valido."); }
            Panel panel = required(repository.findPanel(panelId));
            owned(panel.novelId, novelId);
            selected.add(panel);
        }
        selected.sort(Comparator.comparingInt((Panel panel) -> panel.chunkPosition).thenComparingInt(panel -> panel.position));
        if (selected.stream().anyMatch(panel -> !Objects.equals(panel.chunkId, selected.getFirst().chunkId))) {
            fail("Selecciona paneles de un mismo chunk.");
        }
        Chunk sourceChunk = chunk(novelId, selected.getFirst().chunkId);
        List<Long> castIds = characterIds == null ? List.of() : characterIds;
        if (castIds.stream().anyMatch(Objects::isNull) || castIds.stream().distinct().count() != castIds.size()) {
            fail("Selecciona personajes distintos de esta novela.");
        }
        List<String> characterDescriptions = castIds.stream().sorted()
            .map(characterId -> character(novelId, characterId))
            .map(character -> character.name + ": " + character.description).toList();
        String characterContext = characterDescriptions.isEmpty() ? "" : "Personajes:\n" + String.join("\n", characterDescriptions);
        if (characterContext.length() > 3000) { fail("Las fichas seleccionadas superan 3000 caracteres. Selecciona menos personajes."); }
        List<String> selectedPrompts = selected.stream().map(panel -> panel.imagePrompt).toList();
        selectedPrompts.forEach(prompt -> validText(prompt, 1500, "Prompt de imagen del panel"));
        String format = "Dibujo de manga japones en blanco y negro, tinta negra, lineas expresivas, "
                + "contraste y tramas de semitonos. Composicion narrativa clara; mantener coherentes los personajes "
            + "y el entorno. Sin color, fotografia ni render 3D. Incluir globos con los dialogos literales "
            + "del texto original, asignados al personaje que habla; incluir cartelas rectangulares con fragmentos "
            + "descriptivos breves y literales cuando correspondan a la viñeta. No inventar ni parafrasear texto, "
            + "ni añadir rotulos, logotipos o marcas de agua. Dejar sin texto las viñetas sin dialogo o narracion "
            + "pertinentes. Interpretar cualquier color mencionado en las escenas como tonos de gris.\n\n";
        StringBuilder promptBuilder = new StringBuilder(format)
            .append("Crear una sola imagen manga con exactamente ").append(selectedPrompts.size())
            .append(selectedPrompts.size() == 1 ? " viñeta.\n\n" : " viñetas distintas, en el orden narrativo indicado. Mantener cada accion en su propia viñeta, sin mezclarlas.\n\n");
        for (int index = 0; index < selectedPrompts.size(); index++) {
            promptBuilder.append("Viñeta ").append(index + 1).append(":\n").append(selectedPrompts.get(index)).append("\n\n");
        }
        promptBuilder.append("Texto original de referencia: conserva los detalles visuales pertinentes y los dialogos y fragmentos descriptivos literales que correspondan a las viñetas seleccionadas; no añadas escenas que no se hayan seleccionado.\n")
            .append(sourceChunk.content);
        String prompt = promptBuilder.toString();
        if (!direction.isBlank()) { prompt += "\n\nDireccion visual adicional: " + direction; }
        validText(prompt, 30000, "Descripcion");
        String context = prompt + (characterContext.isEmpty() ? "" : "\n\n" + characterContext);
        var generated = text.studioPrompt(context);
        String finalPrompt = generated.text() + "\n\nReferencias de los paneles y texto original:\n" + prompt;
        validText(finalPrompt, 41000, "Descripcion");
        StudioDraft draft = new StudioDraft();
        draft.novelId = novelId;
        draft.firstPanelId = selected.getFirst().id;
        draft.secondPanelId = selected.size() > 1 ? selected.get(1).id : null;
        draft.panelIds.addAll(selected.stream().map(panel -> panel.id).toList());
        draft.narrativeOrder = selected.getFirst().chunkPosition * 100 + selected.getFirst().position;
        draft.prompt = finalPrompt;
        draft.characterContext = characterContext;
        draft.aiCacheKey = generated.cacheKey();
        draft.provenance = "Prompts de panel / " + text.imagePanelPromptProvenance();
        repository.saveDraft(draft);
        return draft;
    }

    public List<StudioDraft> drafts(long novelId) {
        novel(novelId);
        return repository.findDrafts(novelId);
    }

    public void saveDraft(long novelId, long draftId, String prompt) {
        mutableNovel(novelId);
        StudioDraft draft = required(repository.findDraft(draftId));
        owned(draft.novelId, novelId);
        validText(prompt, 41000, "Descripcion");
        draft.prompt = prompt;
    }

    public void deleteDraft(long novelId, long draftId) {
        mutableNovel(novelId);
        StudioDraft draft = required(repository.findDraft(draftId));
        owned(draft.novelId, novelId);
        var candidates = repository.findCandidatesByDraft(draftId);
        for (Candidate candidate : candidates) {
            if (candidate.panelIds.isEmpty()) {
                candidate.panelIds.addAll(draft.panelIds.isEmpty()
                        ? legacyPanels(candidate.firstPanelId, candidate.secondPanelId) : draft.panelIds);
            }
            candidate.draftId = null;
        }
        text.evictCachedResponse(draft.aiCacheKey);
        repository.deleteDraft(draft);
    }

    public Candidate uploadImage(long novelId, long draftId, byte[] bytes) {
        mutableNovel(novelId);
        StudioDraft draft = required(repository.findDraft(draftId));
        owned(draft.novelId, novelId);
        validText(draft.prompt, 41000, "Descripcion guardada");
        String imagePrompt = draft.prompt + (draft.characterContext.isEmpty() ? "" : "\n\n" + draft.characterContext);
        validText(imagePrompt, 45000, "Descripcion y fichas de personajes");
        if (bytes == null || bytes.length == 0 || bytes.length > 20_000_000) {
            fail("La imagen debe tener un maximo de 20 MB.");
        }
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) { throw new IllegalArgumentException("Solo se admiten imagenes PNG o JPEG validas."); }
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                if (!List.of("png", "jpeg", "jpg").contains(format)
                        || (long) reader.getWidth(0) * reader.getHeight(0) > 40_000_000) {
                    throw new IllegalArgumentException("La imagen debe ser PNG o JPEG y no superar 40 megapixeles.");
                }
                var decoded = reader.read(0);
                var output = new ByteArrayOutputStream();
                if (decoded == null || !ImageIO.write(decoded, format, output) || output.size() > 50_000_000) {
                    throw new IllegalArgumentException("No se pudo procesar la imagen o supera el limite de almacenamiento de 50 MB.");
                }
                Candidate candidate = new Candidate();
                candidate.novelId = novelId;
                candidate.draftId = draftId;
                candidate.firstPanelId = draft.firstPanelId;
                candidate.secondPanelId = draft.secondPanelId;
                candidate.narrativeOrder = draft.narrativeOrder;
                candidate.prompt = imagePrompt;
                candidate.image = output.toByteArray();
                candidate.mediaType = format.equals("png") ? "image/png" : "image/jpeg";
                candidate.provenance = "Imagen subida por el usuario";
                repository.saveCandidate(candidate);
                return candidate;
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("La imagen no es valida o esta incompleta.", exception);
        }
    }

    public List<Candidate> candidates(long novelId) {
        novel(novelId);
        return repository.findCandidatesByNovel(novelId);
    }

    public Candidate candidate(long novelId, long candidateId) {
        Candidate candidate = required(repository.findCandidate(candidateId));
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
        List<Long> candidatePanels = candidatePanels(candidate);
        for (MangaEntry entry : reader(novelId)) {
            Optional<Candidate> approved = entry.candidateId == null
                    ? Optional.empty() : repository.findCandidate(entry.candidateId);
            List<Long> covered = approved.map(this::candidatePanels)
                    .orElseGet(() -> legacyPanels(entry.firstPanelId, entry.secondPanelId));
            if (candidatePanels.stream().anyMatch(covered::contains)) {
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
        repository.saveEntry(entry);
        candidate.state = ReviewState.APPROVED;
        candidate.image = null;
        return entry;
    }

    public List<MangaEntry> reader(long novelId) {
        novel(novelId);
        return repository.findEntries(novelId);
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
        MangaEntry entry = required(repository.findEntry(entryId));
        owned(entry.novelId, novelId);
        try {
            return new ImageProvider.ImageResult(Files.readAllBytes(mangaDirectory.resolve(entry.filename)), entry.mediaType, entry.provenance);
        } catch (IOException exception) {
            throw new IllegalStateException("No se encuentra la imagen aprobada en el disco local.");
        }
    }

    private List<Long> candidatePanels(Candidate candidate) {
        if (!candidate.panelIds.isEmpty()) { return List.copyOf(candidate.panelIds); }
        Optional<StudioDraft> draft = candidate.draftId == null
                ? Optional.empty() : repository.findDraft(candidate.draftId);
        return draft.filter(value -> !value.panelIds.isEmpty())
                .map(value -> List.copyOf(value.panelIds))
                .orElseGet(() -> legacyPanels(candidate.firstPanelId, candidate.secondPanelId));
    }

    private List<Long> legacyPanels(Long first, Long second) {
        List<Long> panels = new ArrayList<>();
        if (first != null) { panels.add(first); }
        if (second != null) { panels.add(second); }
        return panels;
    }

    private Novel mutableNovel(long novelId) {
        return novel(novelId);
    }

    private <T> T required(Optional<T> entity) {
        return entity.orElseThrow(() -> new IllegalArgumentException("El elemento solicitado no existe."));
    }

    private void owned(Long actualNovelId, long novelId) {
        if (actualNovelId == null || actualNovelId != novelId) { fail("El elemento no pertenece a esta novela."); }
    }

    private void revision(String value) {
        if (value == null || value.length() > 1500) { fail("La revision admite hasta 1500 caracteres."); }
    }

    private void validateImagePrompts(List<String> imagePrompts, int count) {
        if (imagePrompts == null || imagePrompts.size() != count) {
            fail("La propuesta debe contener exactamente " + count + " prompts de imagen.");
        }
        for (String imagePrompt : imagePrompts) { validText(imagePrompt, 1500, "Prompt de imagen"); }
    }

    private void validText(String value, int max, String label) {
        if (value == null || value.isBlank() || value.length() > max) { fail(label + ": campo obligatorio, maximo " + max + " caracteres."); }
    }

    private void fail(String message) { throw new IllegalArgumentException(message); }
}