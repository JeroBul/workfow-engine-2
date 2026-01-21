package com.example.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

/**
 * Action automatique : on va typiquement modifier le contexte.
 */
@ApplicationScoped
public class ValidateEmailAction implements WorkflowAction {
    @Override
    public boolean execute(Map<String, Object> context) {
        // exemple : validation d'email automatique
        if (context.get("email") == null)
            return false;
        context.put("emailVerified", true);
        return true;
    }
}