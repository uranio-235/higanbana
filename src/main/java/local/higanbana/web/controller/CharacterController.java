package local.higanbana.web.controller;

import jakarta.validation.Valid;
import local.higanbana.application.Workflow;
import local.higanbana.web.dto.CharacterInput;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import static local.higanbana.web.WebRoutes.novel;

@Controller
public class CharacterController {
    private final Workflow workflow;

    public CharacterController(Workflow workflow) { this.workflow = workflow; }

    @PostMapping("/novels/{novelId}/characters") public String add(@PathVariable long novelId,
            @Valid CharacterInput input) {
        workflow.addCharacter(novelId, input.name(), input.description());
        return novel(novelId, "characters");
    }

    @PostMapping("/novels/{novelId}/characters/{characterId}/save") public String save(@PathVariable long novelId,
            @PathVariable long characterId, @Valid CharacterInput input) {
        workflow.updateCharacter(novelId, characterId, input.name(), input.description());
        return novel(novelId, "characters");
    }

    @PostMapping("/novels/{novelId}/characters/{characterId}/delete") public String delete(@PathVariable long novelId,
            @PathVariable long characterId) {
        workflow.deleteCharacter(novelId, characterId);
        return novel(novelId, "characters");
    }
}