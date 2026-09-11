package com.repomatch.analysis;

import java.util.UUID;

public class AnalysisNotFoundException extends RuntimeException {
    public AnalysisNotFoundException(UUID id) {
        super("Analysis " + id + " was not found");
    }
}
