package com.example.service;

import java.util.Map;

public interface WorkflowAction {
    /**
     * Exécuter une action sur le contexte du workflow (modifiable).
     * @param context Contexte partagé instance.
     * @return true si OK
     */
    boolean execute(Map<String, Object> context);
}