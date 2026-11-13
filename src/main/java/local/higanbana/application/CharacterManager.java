package local.higanbana.application;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.text.Normalizer;
import java.time.LocalDateTime;
import local.higanbana.domain.entity.Novel;
import local.higanbana.domain.entity.NovelCharacter;
import local.higanbana.domain.entity.Chunk;
import local.higanbana.domain.ai.PromptCharacterBuilder;
import local.higanbana.domain.ai.TextProviderException;
import local.higanbana.domain.workflow.WorkflowRepository;
import local.higanbana.infrastructure.ai.TextAssistant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CharacterManager {
    private final WorkflowRepository repository;
    private final TextAssistant text;

    public CharacterManager(WorkflowRepository repository, TextAssistant text) {
        this.repository = repository;
        this.text = text;
    }

    public boolean available() { return text.characterPromptsAvailable(); }

    public List<NovelCharacter> characters(long novelId) {
        novel(novelId);
        return repository.findCharacters(novelId);
    }

    public int extract(long novelId, long chunkId) {
        Novel novel = novel(novelId);
        Chunk chunk = repository.findChunks(novelId).stream()
                .filter(item -> item.id == chunkId && item.charactersExtractedAt == null).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("El chunk no pertenece a esta novela o ya fue examinado."));
        var known = new LinkedHashMap<String, NovelCharacter>();
        repository.findCharacters(novelId).forEach(character -> known.putIfAbsent(nameKey(character.name), character));
        var references = known.values().stream().map(character -> new PromptCharacterBuilder()
                .name(character.name).description(character.prompt).build()).toList();
        var seen = new HashSet<String>();
        var updates = new LinkedHashMap<NovelCharacter, String>();
        List<NovelCharacter> additions = new ArrayList<>();
        for (var result : text.extractCharacters(chunk.content, references)) {
            valid(result.name(), 120, "Nombre del personaje");
            valid(result.description(), 2000, "Prompt del personaje");
            String key = nameKey(result.name());
            if (!seen.add(key)) {
                throw new TextProviderException("NVIDIA devolvio personajes duplicados; el chunk sigue pendiente.");
            }
            if (known.containsKey(key)) {
                NovelCharacter existing = known.get(key);
                if (!existing.prompt.equals(result.description().strip())) {
                    updates.put(existing, result.description().strip());
                }
                continue;
            }
            NovelCharacter character = new NovelCharacter();
            character.name = result.name().strip();
            character.prompt = result.description().strip();
            additions.add(character);
        }
        updates.forEach((character, prompt) -> character.prompt = prompt);
        for (NovelCharacter character : additions) {
            novel.addCharacter(character);
            repository.saveCharacter(character);
        }
        chunk.charactersExtractedAt = LocalDateTime.now();
        return additions.size() + updates.size();
    }

    private String nameKey(String name) {
        return Normalizer.normalize(name.strip(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }
    public void revise(long novelId, long characterId, String instructions) {
        valid(instructions, 1500, "Instrucciones");
        novel(novelId);
        NovelCharacter character = character(novelId, characterId);
        String prompt = text.reviseCharacterPrompt(character.name, character.prompt, instructions.strip());
        valid(prompt, 2000, "Prompt del personaje");
        character.prompt = prompt.strip();
    }

    public void delete(long novelId, long characterId) {
        novel(novelId).removeCharacter(character(novelId, characterId));
    }

    private Novel novel(long novelId) {
        return repository.findNovel(novelId)
                .orElseThrow(() -> new IllegalArgumentException("La novela no existe."));
    }

    private NovelCharacter character(long novelId, long characterId) {
        return repository.findCharacter(novelId, characterId)
                .orElseThrow(() -> new IllegalArgumentException("El personaje no pertenece a esta novela."));
    }

    private void valid(String value, int max, String label) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(label + " es obligatorio y no puede superar " + max + " caracteres.");
        }
    }
}
