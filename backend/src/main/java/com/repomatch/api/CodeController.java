package com.repomatch.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.repomatch.code.AskService;
import com.repomatch.code.CodeIndexService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** "Ask your codebase" — optional: index one repository, then ask questions answered from its code. */
@RestController
@RequestMapping("/api/repos/{repoId}")
public class CodeController {

    private final CodeIndexService indexing;
    private final AskService asking;

    public CodeController(CodeIndexService indexing, AskService asking) {
        this.indexing = indexing;
        this.asking = asking;
    }

    public record Question(@NotBlank @Size(min = 5, max = 1000) String question) {
    }

    @GetMapping("/index")
    public CodeIndexService.IndexSummary status(@PathVariable long repoId) {
        return indexing.status(repoId);
    }

    @PostMapping("/index")
    public CodeIndexService.IndexSummary index(@PathVariable long repoId,
                                               @RequestParam(defaultValue = "false") boolean force) {
        return indexing.index(repoId, force);
    }

    @PostMapping("/ask")
    public AskService.Answer ask(@PathVariable long repoId, @Valid @RequestBody Question body, HttpServletRequest http) {
        return asking.ask(repoId, body.question(), http.getRemoteAddr());
    }
}
