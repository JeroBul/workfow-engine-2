package com.example.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

/**
 * Action humaine typique (exécution peut-être vide ici).
 */
@ApplicationScoped
public class FillUserInfoAction implements WorkflowAction {
    @Override
    public boolean execute(Map<String, Object> context) {
        // Pour une humaine : rien à faire (inputs déja dans context)
        return true;
    }
}