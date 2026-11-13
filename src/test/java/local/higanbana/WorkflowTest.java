package local.higanbana;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import local.higanbana.domain.ReviewState;
import local.higanbana.domain.entity.*;
import local.higanbana.domain.Chunking;
import local.higanbana.application.Workflow;
import local.higanbana.infrastructure.ai.TextAssistant;
import local.higanbana.domain.ai.ChunkRequest;
import local.higanbana.domain.ai.PanelPromptRequest;
import local.higanbana.domain.ai.PanelPromptRequestBuilder;
import local.higanbana.domain.ai.CachedTextBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(properties = "app.nvidia.api-key=test-key")
@ActiveProfiles("test")
@Transactional
class WorkflowTest {
    @TempDir static Path storage;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.manga-dir", () -> storage.toString());
        registry.add("app.text-mode", () -> "demo");
    }
    @Autowired Workflow workflow;
    @MockitoSpyBean TextAssistant text;
    @Autowired jakarta.persistence.EntityManager em;

    @BeforeEach void stubChunkProviderOnlyInTests() {
        doAnswer(invocation -> {
            ChunkRequest request = invocation.getArgument(0);
            return Chunking.split(request.source(), request.limit());
        }).when(text).chunks(any(ChunkRequest.class));
        doAnswer(invocation -> java.util.stream.IntStream.rangeClosed(1, invocation.getArgument(0, PanelPromptRequest.class).count())
            .mapToObj(index -> "Prompt visual de prueba " + index + ": mantener la escena y su encuadre.").toList())
            .when(text).imagePanelPrompts(any(PanelPromptRequest.class));
        doReturn(new CachedTextBuilder().text("Propuesta de prueba.").cacheKey("test-key").build())
            .when(text).studioPrompt(any());
    }

    @Test void failedNarrativeRevisionKeepsThePreviousProposal() {
        Novel novel = workflow.createNovel("Escenas", "Primera escena. Segunda escena.");
        var plan = workflow.proposeChunks(novel.id, "Cambios de lugar");
        var previous = List.copyOf(plan.segments);
        doThrow(new IllegalArgumentException("Proveedor no disponible")).when(text).chunks(any(ChunkRequest.class));
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.proposeChunks(novel.id, "Revisar"));
        assertThat(workflow.chunkPlan(novel.id).segments).containsExactlyElementsOf(previous);
    }

    @Test void failedStudioGenerationDoesNotCreateADescription() {
        Novel novel = prepared();
        Long panelId = workflow.panels(novel.id).getFirst().id;
        doThrow(new IllegalArgumentException("NVIDIA no disponible")).when(text).studioPrompt(any());
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.createDraft(novel.id, List.of(panelId), ""))
                .withMessageContaining("NVIDIA no disponible");
        assertThat(workflow.drafts(novel.id)).isEmpty();
    }

    @Test void novelCharactersPersistThroughRelationshipAndOrphansAreRemoved() {
        Novel novel = workflow.createNovel("Reparto", "Texto original.");
        Novel other = workflow.createNovel("Otra obra", "Otro texto.");
        NovelCharacter character = workflow.addCharacter(novel.id, " Aiko ", " Cabello corto, abrigo blanco. ");
        Long novelId = novel.id;
        Long characterId = character.id;
        assertThat(character.novel).isSameAs(novel);
        em.flush();
        em.clear();
        assertThat(workflow.novel(novelId).characters).extracting(item -> item.name).containsExactly("Aiko");
        assertThat(workflow.characters(other.id)).isEmpty();
        workflow.updateCharacter(novelId, characterId, "Aiko", "Cabello corto, abrigo negro.");
        em.flush();
        em.clear();
        assertThat(workflow.characters(novelId).getFirst().description).contains("abrigo negro");
        workflow.deleteCharacter(novelId, characterId);
        em.flush();
        em.clear();
        assertThat(workflow.novel(novelId).characters).isEmpty();
        assertThat(em.find(NovelCharacter.class, characterId)).isNull();
    }

    @Test void characterValidationAndOwnershipApplyToChangesAndStudio() {
        Novel novel = prepared();
        Novel other = workflow.createNovel("Otra obra", "Otro texto.");
        NovelCharacter foreign = workflow.addCharacter(other.id, "Rin", "Ojos verdes.");
        Long panelId = workflow.panels(novel.id).getFirst().id;
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.addCharacter(novel.id, " ", "Descripcion"));
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.addCharacter(novel.id, "Nombre", " "));
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.addCharacter(novel.id, "a".repeat(121), "Descripcion"));
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.addCharacter(novel.id, "Nombre", "a".repeat(2001)));
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.updateCharacter(novel.id, foreign.id, "Otro", "Descripcion"));
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.deleteCharacter(novel.id, foreign.id));
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.createDraft(novel.id, List.of(panelId), "", List.of(foreign.id)));
        assertThat(workflow.characters(other.id).getFirst().name).isEqualTo("Rin");
    }

    @Test void studioUsesSelectedCharacterSnapshotsEvenAfterEditingOrDeletingCharacter() {
        Novel novel = prepared();
        NovelCharacter selected = workflow.addCharacter(novel.id, "Aiko", "Ojos grises y abrigo rojo.");
        workflow.addCharacter(novel.id, "Rin", "Sombrero verde.");
        Long panelId = workflow.panels(novel.id).getFirst().id;
        StudioDraft draft = workflow.createDraft(novel.id, List.of(panelId), "", List.of(selected.id));
        assertThat(draft.prompt).isEqualTo("Propuesta de prueba.");
        verify(text).studioPrompt(argThat(request -> request.panelPrompts().getFirst().contains("Prompt visual de prueba 1")
                && request.characters().size() == 1 && request.characters().getFirst().name().equals("Aiko")));
        assertThat(draft.characterContext).contains("Aiko", "Ojos grises y abrigo rojo.").doesNotContain("Sombrero verde");
        workflow.saveDraft(novel.id, draft.id, "Primer plano al amanecer.");
        workflow.updateCharacter(novel.id, selected.id, "Aiko", "Ahora lleva abrigo azul.");
        workflow.deleteCharacter(novel.id, selected.id);
        em.flush();
        Candidate candidate = workflow.uploadImage(novel.id, draft.id, imageBytes());
        assertThat(candidate.prompt).contains("Primer plano al amanecer.", "Aiko", "Ojos grises y abrigo rojo.")
                .doesNotContain("abrigo azul", "Sombrero verde");
    }

    @Test void confirmedImagePanelPromptIsThePromptSentToImageGeneration() {
        Novel novel = workflow.createNovel("Prompt de panel", "Aiko dice: «Espera, Rin». La lluvia golpea la puerta del faro.");
        workflow.proposeChunks(novel.id, 5000, "");
        workflow.confirmChunks(novel.id);
        Chunk chunk = workflow.chunks(novel.id).getFirst();
        String generated = "Aiko abre una puerta de madera bajo la lluvia, plano medio, faro al fondo.";
        doReturn(List.of(generated)).when(text).imagePanelPrompts(new PanelPromptRequestBuilder()
                .source(chunk.content).count(1).direction("Plano medio").build());
        workflow.proposePanels(novel.id, chunk.id, 1, "Plano medio");
        PanelPlan plan = workflow.panelPlan(novel.id, chunk.id);
        assertThat(plan.imagePrompts).containsExactly(generated);
        workflow.confirmPanels(novel.id, chunk.id);
        Panel panel = workflow.panels(novel.id).getFirst();
        assertThat(panel.imagePrompt).isEqualTo(generated);

        String lettering = "Viñeta 1: Aiko -> globo: \"Espera, Rin\". "
            + "Narrador -> cartela: \"La lluvia golpea la puerta del faro.\"";
        doReturn(new CachedTextBuilder().text(lettering).cacheKey("test-key").build())
            .when(text).studioPrompt(any());
        StudioDraft draft = workflow.createDraft(novel.id, List.of(panel.id), "Luz fria");
        assertThat(draft.prompt).isEqualTo(lettering);
        verify(text).studioPrompt(argThat(request -> request.source().equals(chunk.content)
                && request.panelPrompts().equals(List.of(generated)) && request.direction().equals("Luz fria")));
        Candidate candidate = workflow.uploadImage(novel.id, draft.id, imageBytes());
        assertThat(candidate.prompt).isEqualTo(draft.prompt).contains(lettering);
    }

    @Test void uploadedImageCreatesAReviewCandidateAndRejectsInvalidFiles() throws Exception {
        Novel novel = prepared();
        StudioDraft draft = workflow.createDraft(novel.id, List.of(workflow.panels(novel.id).getFirst().id), "");
        var image = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", output);

        Candidate candidate = workflow.uploadImage(novel.id, draft.id, output.toByteArray());
        assertThat(candidate.mediaType).isEqualTo("image/png");
        assertThat(candidate.provenance).isEqualTo("Imagen subida por el usuario");
        assertThat(candidate.prompt).isEqualTo(draft.prompt);
        assertThat(candidate.state).isEqualTo(ReviewState.PENDING);
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.uploadImage(novel.id, draft.id, new byte[]{1, 2, 3}));
        Novel other = workflow.createNovel("Otra", "Texto.");
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.uploadImage(other.id, draft.id, output.toByteArray()));
    }

    @Test void acceptsGeneratedPngLargerThanTwoMegabytes() throws Exception {
        Novel novel = prepared();
        StudioDraft draft = workflow.createDraft(novel.id, List.of(workflow.panels(novel.id).getFirst().id), "");
        var image = new java.awt.image.BufferedImage(1200, 900, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var random = new java.util.Random(1);
        for (int row = 0; row < image.getHeight(); row++) {
            for (int column = 0; column < image.getWidth(); column++) {
                image.setRGB(column, row, random.nextInt(0x1000000));
            }
        }
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", output);
        assertThat(output.size()).isGreaterThan(2_000_000);
        Candidate candidate = workflow.uploadImage(novel.id, draft.id, output.toByteArray());
        assertThat(candidate.mediaType).isEqualTo("image/png");
        assertThat(candidate.image).isNotEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.uploadImage(novel.id, draft.id, new byte[20_000_001]))
            .withMessageContaining("20 MB");
    }

    private byte[] imageBytes() {
        try {
            var output = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", output);
            return output.toByteArray();
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    @Test void demoPanelPromptsCannotBeConfirmedAsRealImagePrompts() {
        Novel novel = workflow.createNovel("Propuesta antigua", "Aiko abre el faro.");
        workflow.proposeChunks(novel.id, 5000, "");
        workflow.confirmChunks(novel.id);
        Chunk chunk = workflow.chunks(novel.id).getFirst();
        doReturn("DEMO local - sin IA").when(text).imagePanelPromptProvenance();
        workflow.proposePanels(novel.id, chunk.id, 1, "");

        assertThatIllegalArgumentException().isThrownBy(() -> workflow.confirmPanels(novel.id, chunk.id))
                .withMessageContaining("Regenera los prompts");
        assertThat(workflow.panels(novel.id)).isEmpty();
    }

    @Test void selectedCharacterContextIsBoundedWithoutSilentTruncation() {
        Novel novel = prepared();
        NovelCharacter first = workflow.addCharacter(novel.id, "Aiko", "a".repeat(2000));
        NovelCharacter second = workflow.addCharacter(novel.id, "Rin", "b".repeat(2000));
        Long panelId = workflow.panels(novel.id).getFirst().id;
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.createDraft(novel.id,
                List.of(panelId), "", List.of(first.id, second.id))).withMessageContaining("Selecciona menos personajes");
        assertThat(workflow.drafts(novel.id)).isEmpty();
    }

    private Novel prepared() {
        Novel novel = workflow.createNovel("La estacion", "Una viajera llega al anden. La ciudad espera.\n".repeat(15));
        workflow.proposeChunks(novel.id, 400, "");
        workflow.confirmChunks(novel.id);
        Chunk chunk = workflow.chunks(novel.id).getFirst();
        workflow.proposePanels(novel.id, chunk.id, 3, "");
        workflow.confirmPanels(novel.id, chunk.id);
        return novel;
    }

    @Test void chunkRevisionIsLosslessAndConfirmationIsIdempotent() {
        Novel novel = workflow.createNovel("Test", " a\r\n b\n ".repeat(200));
        workflow.proposeChunks(novel.id, 200, "primera");
        workflow.proposeChunks(novel.id, 400, "segunda");
        workflow.confirmChunks(novel.id);
        workflow.confirmChunks(novel.id);
        assertThat(String.join("", workflow.chunks(novel.id).stream().map(chunk -> chunk.content).toList())).isEqualTo(novel.source);
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.proposeChunks(novel.id, 500, ""));
    }

    @Test void selectionEnforcesLimitsOwnershipAndNarrativeOrder() {
        Novel first = prepared();
        Novel second = prepared();
        var panels = workflow.panels(first.id);
        Long foreign = workflow.panels(second.id).getFirst().id;
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.createDraft(first.id, List.of(), ""));
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.createDraft(first.id, List.of(panels.getFirst().id, panels.getFirst().id), ""));
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.createDraft(first.id, List.of(foreign), ""));
        Panel otherChunkPanel = new Panel();
        otherChunkPanel.novelId = first.id;
        otherChunkPanel.chunkId = workflow.chunks(first.id).get(1).id;
        otherChunkPanel.chunkPosition = 2;
        otherChunkPanel.position = 1;
        otherChunkPanel.imagePrompt = "Otra escena.";
        em.persist(otherChunkPanel);
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.createDraft(first.id, List.of(panels.getFirst().id, otherChunkPanel.id), ""));
        StudioDraft draft = workflow.createDraft(first.id, List.of(panels.get(2).id, panels.getFirst().id, panels.get(1).id), "");
        assertThat(draft.firstPanelId).isEqualTo(panels.getFirst().id);
        assertThat(draft.secondPanelId).isEqualTo(panels.get(1).id);
        assertThat(draft.panelIds).containsExactly(panels.getFirst().id, panels.get(1).id, panels.get(2).id);
        assertThat(draft.prompt).isEqualTo("Propuesta de prueba.");
        verify(text).studioPrompt(argThat(request -> request.panelPrompts().equals(
                panels.stream().map(panel -> panel.imagePrompt).toList())
                && request.source().equals(workflow.chunks(first.id).getFirst().content)));
        Candidate candidate = workflow.uploadImage(first.id, draft.id, imageBytes());
        em.flush();
        em.clear();
        workflow.approve(first.id, candidate.id);
        StudioDraft overlap = workflow.createDraft(first.id, List.of(panels.get(2).id), "");
        Candidate duplicate = workflow.uploadImage(first.id, overlap.id, imageBytes());
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.approve(first.id, duplicate.id));
    }

    @Test void completeApprovalFlowOnlyStoresApprovedImages() throws Exception {
        Novel novel = prepared();
        var panels = workflow.panels(novel.id);
        var draft = workflow.createDraft(novel.id, List.of(panels.getFirst().id), "Plano general");
        workflow.saveDraft(novel.id, draft.id, "Tinta negra. Estacion al amanecer.");
        Candidate candidate = workflow.uploadImage(novel.id, draft.id, imageBytes());
        assertThat(workflow.reader(novel.id)).isEmpty();
        assertThat(Files.exists(storage.resolve("anything.svg"))).isFalse();
        assertThat(candidate.image).isNotEmpty();
        assertThat(candidate.mediaType).isEqualTo("image/png");
        MangaEntry entry = workflow.approve(novel.id, candidate.id);
        assertThat(workflow.approve(novel.id, candidate.id).id).isEqualTo(entry.id);
        assertThat(workflow.reader(novel.id)).hasSize(1);
        assertThat(Files.readAllBytes(storage.resolve(entry.filename))).isNotEmpty();
        assertThat(candidate.image).isNull();
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.reject(novel.id, candidate.id));
        Candidate duplicate = workflow.uploadImage(novel.id, draft.id, imageBytes());
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.approve(novel.id, duplicate.id));
    }

    @Test void deletingUnusedAndLegacyDraftsPreservesLegacyCandidatePanels() {
        Novel novel = prepared();
        var panels = workflow.panels(novel.id);
        StudioDraft unused = workflow.createDraft(novel.id, List.of(panels.getFirst().id), "");
        workflow.deleteDraft(novel.id, unused.id);
        assertThat(workflow.drafts(novel.id)).isEmpty();

        StudioDraft legacy = workflow.createDraft(novel.id, List.of(panels.getFirst().id, panels.get(1).id), "");
        legacy.panelIds.clear();
        Candidate candidate = workflow.uploadImage(novel.id, legacy.id, imageBytes());
        em.flush();
        em.clear();
        workflow.deleteDraft(novel.id, legacy.id);
        em.flush();
        em.clear();
        Candidate saved = workflow.candidate(novel.id, candidate.id);
        assertThat(saved.draftId).isNull();
        assertThat(saved.panelIds).containsExactly(panels.getFirst().id, panels.get(1).id);
        workflow.approve(novel.id, candidate.id);
        StudioDraft overlap = workflow.createDraft(novel.id, List.of(panels.get(1).id), "");
        Candidate duplicate = workflow.uploadImage(novel.id, overlap.id, imageBytes());
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.approve(novel.id, duplicate.id));
    }

    @Test void rejectionCannotBeApprovedAndReplanningPreservesDownstream() {
        Novel novel = prepared();
        Chunk chunk = workflow.chunks(novel.id).getFirst();
        Long panel = workflow.panels(novel.id).getFirst().id;
        var draft = workflow.createDraft(novel.id, List.of(panel), "");
        Candidate candidate = workflow.uploadImage(novel.id, draft.id, imageBytes());
        workflow.reject(novel.id, candidate.id);
        workflow.reject(novel.id, candidate.id);
        assertThat(candidate.state).isEqualTo(ReviewState.REJECTED);
        assertThat(candidate.image).isNull();
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.approve(novel.id, candidate.id));
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.proposePanels(novel.id, chunk.id, 2, ""));
        assertThat(workflow.panels(novel.id)).hasSize(3);
        assertThat(workflow.drafts(novel.id)).hasSize(1);
        assertThat(workflow.reader(novel.id)).isEmpty();
    }

    @Test void readerUsesNarrativeOrderAndCandidatesKeepTheirPromptSnapshot() {
        Novel novel = prepared();
        var panels = workflow.panels(novel.id);
        var laterDraft = workflow.createDraft(novel.id, List.of(panels.get(2).id), "");
        Candidate later = workflow.uploadImage(novel.id, laterDraft.id, imageBytes());
        workflow.approve(novel.id, later.id);
        var earlierDraft = workflow.createDraft(novel.id, List.of(panels.get(1).id, panels.getFirst().id), "");
        workflow.saveDraft(novel.id, earlierDraft.id, "Descripcion original guardada.");
        Candidate earlier = workflow.uploadImage(novel.id, earlierDraft.id, imageBytes());
        workflow.saveDraft(novel.id, earlierDraft.id, "Nueva direccion para otro candidato.");
        assertThat(earlier.prompt).isEqualTo("Descripcion original guardada.");
        var firstEntry = workflow.approve(novel.id, earlier.id);
        assertThat(workflow.reader(novel.id)).extracting(entry -> entry.candidateId)
                .containsExactly(earlier.id, later.id);
        assertThat(firstEntry.firstPanelId).isEqualTo(panels.getFirst().id);
        assertThat(firstEntry.secondPanelId).isEqualTo(panels.get(1).id);
    }
}