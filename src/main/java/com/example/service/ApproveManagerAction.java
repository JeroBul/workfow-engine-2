package com.example.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

@ApplicationScoped
public class ApproveManagerAction implements WorkflowAction {
    @Override
    public boolean execute(Map<String, Object> context) {
        // Action humaine : rien à faire ici ; la validation est réalisée via input user.
        return true;
    }
}