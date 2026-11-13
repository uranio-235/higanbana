package local.higanbana.web.controller;

import local.higanbana.application.CharacterManager;
import local.higanbana.domain.ai.TextProviderException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import static local.higanbana.web.WebRoutes.novel;

@Controller
public class CharacterController {
    private final CharacterManager characters;

    public CharacterController(CharacterManager characters) { this.characters = characters; }

    @PostMapping("/novels/{novelId}/characters") public String extract(@PathVariable long novelId,
            @RequestParam long chunkId, RedirectAttributes flash) {
        try {
            int count = characters.extract(novelId, chunkId);
            flash.addFlashAttribute("notice", count == 0 ? "No hay cambios en las fichas de personajes."
                    : count + (count == 1 ? " ficha de personaje creada o actualizada." : " fichas de personajes creadas o actualizadas."));
        } catch (TextProviderException exception) {
            flash.addFlashAttribute("characterError", exception.getMessage());
        }
        return novel(novelId, "characters");
    }

    @PostMapping("/novels/{novelId}/characters/{characterId}/revise") public String revise(@PathVariable long novelId,
            @PathVariable long characterId, @RequestParam String instructions) {
        characters.revise(novelId, characterId, instructions);
        return novel(novelId, "characters");
    }

    @PostMapping("/novels/{novelId}/characters/{characterId}/delete") public String delete(@PathVariable long novelId,
            @PathVariable long characterId) {
        characters.delete(novelId, characterId);
        return novel(novelId, "characters");
    }
}