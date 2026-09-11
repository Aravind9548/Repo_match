package com.repomatch.api;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.repomatch.analysis.AnalysisService;
import com.repomatch.analysis.AnalysisSummary;
import com.repomatch.analysis.AnalyzeRequest;
import com.repomatch.analysis.AnalyzeResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class AnalysisController {

    private final AnalysisService analysis;

    public AnalysisController(AnalysisService analysis) {
        this.analysis = analysis;
    }

    @PostMapping("/analyze")
    public AnalyzeResponse analyze(@Valid @RequestBody AnalyzeRequest request) {
        return analysis.analyze(request);
    }

    @GetMapping("/analyses/{id}")
    public AnalyzeResponse get(@PathVariable UUID id) {
        return analysis.get(id);
    }

    @GetMapping("/analyses")
    public List<AnalysisSummary> history(@RequestParam String username) {
        return analysis.history(username);
    }
}
