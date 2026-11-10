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
import local.higanbana.domain.*;
import local.higanbana.domain.Chunking;
import local.higanbana.application.Workflow;
import local.higanbana.infrastructure.ai.TextAssistant;
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
        doAnswer(invocation -> Chunking.split(invocation.getArgument(0), invocation.getArgument(1)))
                .when(text).chunks(anyString(), anyInt(), anyString());
    }

    @Test void failedNarrativeRevisionKeepsThePreviousProposal() {
        Novel novel = workflow.createNovel("Escenas", "Primera escena. Segunda escena.");
        var plan = workflow.proposeChunks(novel.id, "Cambios de lugar");
        var previous = List.copyOf(plan.segments);
        doThrow(new IllegalArgumentException("Proveedor no disponible")).when(text).chunks(anyString(), anyInt(), anyString());
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.proposeChunks(novel.id, "Revisar"));
        assertThat(workflow.chunkPlan(novel.id).segments).containsExactlyElementsOf(previous);
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
        assertThat(draft.prompt).contains("Aiko", "Ojos grises y abrigo rojo.").doesNotContain("Sombrero verde");
        workflow.saveDraft(novel.id, draft.id, "Primer plano al amanecer.");
        workflow.updateCharacter(novel.id, selected.id, "Aiko", "Ahora lleva abrigo azul.");
        workflow.deleteCharacter(novel.id, selected.id);
        em.flush();
        Candidate candidate = workflow.generate(novel.id, draft.id);
        assertThat(candidate.prompt).contains("Primer plano al amanecer.", "Aiko", "Ojos grises y abrigo rojo.")
                .doesNotContain("abrigo azul", "Sombrero verde");
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
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.createDraft(first.id, panels.stream().map(panel -> panel.id).toList(), ""));
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.createDraft(first.id, List.of(panels.getFirst().id, panels.getFirst().id), ""));
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.createDraft(first.id, List.of(foreign), ""));
        StudioDraft draft = workflow.createDraft(first.id, List.of(panels.get(1).id, panels.getFirst().id), "");
        assertThat(draft.firstPanelId).isEqualTo(panels.getFirst().id);
        assertThat(draft.secondPanelId).isEqualTo(panels.get(1).id);
    }

    @Test void completeApprovalFlowOnlyStoresApprovedImages() throws Exception {
        Novel novel = prepared();
        var panels = workflow.panels(novel.id);
        var draft = workflow.createDraft(novel.id, List.of(panels.getFirst().id), "Plano general");
        workflow.saveDraft(novel.id, draft.id, "Tinta negra. Estacion al amanecer.");
        Candidate candidate = workflow.generate(novel.id, draft.id);
        assertThat(workflow.reader(novel.id)).isEmpty();
        assertThat(Files.exists(storage.resolve("anything.svg"))).isFalse();
        assertThat(candidate.image).isNotEmpty();
        assertThat(new String(candidate.image, java.nio.charset.StandardCharsets.UTF_8)).contains("<svg", "DEMO");
        MangaEntry entry = workflow.approve(novel.id, candidate.id);
        assertThat(workflow.approve(novel.id, candidate.id).id).isEqualTo(entry.id);
        assertThat(workflow.reader(novel.id)).hasSize(1);
        assertThat(Files.readAllBytes(storage.resolve(entry.filename))).isNotEmpty();
        assertThat(candidate.image).isNull();
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.reject(novel.id, candidate.id));
        Candidate duplicate = workflow.generate(novel.id, draft.id);
        assertThatIllegalArgumentException().isThrownBy(() -> workflow.approve(novel.id, duplicate.id));
    }

    @Test void rejectionCannotBeApprovedAndReplanningPreservesDownstream() {
        Novel novel = prepared();
        Chunk chunk = workflow.chunks(novel.id).getFirst();
        Long panel = workflow.panels(novel.id).getFirst().id;
        var draft = workflow.createDraft(novel.id, List.of(panel), "");
        Candidate candidate = workflow.generate(novel.id, draft.id);
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
        Candidate later = workflow.generate(novel.id, laterDraft.id);
        workflow.approve(novel.id, later.id);
        var earlierDraft = workflow.createDraft(novel.id, List.of(panels.get(1).id, panels.getFirst().id), "");
        workflow.saveDraft(novel.id, earlierDraft.id, "Descripcion original guardada.");
        Candidate earlier = workflow.generate(novel.id, earlierDraft.id);
        workflow.saveDraft(novel.id, earlierDraft.id, "Nueva direccion para otro candidato.");
        assertThat(earlier.prompt).isEqualTo("Descripcion original guardada.");
        var firstEntry = workflow.approve(novel.id, earlier.id);
        assertThat(workflow.reader(novel.id)).extracting(entry -> entry.candidateId)
                .containsExactly(earlier.id, later.id);
        assertThat(firstEntry.firstPanelId).isEqualTo(panels.getFirst().id);
        assertThat(firstEntry.secondPanelId).isEqualTo(panels.get(1).id);
    }
}