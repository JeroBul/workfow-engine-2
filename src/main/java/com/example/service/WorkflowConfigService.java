package com.example.service;

import com.example.dto.WorkflowDTO;
import com.example.model.WorkflowConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class WorkflowConfigService {
    @Inject ObjectMapper mapper;

    @Transactional
    public void saveWorkflowConfig(String name, WorkflowDTO dto) {
        try {
            String json = mapper.writeValueAsString(dto);
            WorkflowConfig config = WorkflowConfig.find("name", name).firstResult();
            if (config == null) {
                config = new WorkflowConfig();
                config.name = name;
            }
            config.jsonConfig = json;
            config.persist();
        } catch(Exception e) {
            throw new RuntimeException("Erreur serialisation/sauvegarde workflow", e);
        }
    }

    public WorkflowDTO loadWorkflowConfig(String name) {
        WorkflowConfig cfg = WorkflowConfig.find("name", name).firstResult();
        if (cfg == null) throw new IllegalArgumentException("Workflow non trouvé");
        try {
            return mapper.readValue(cfg.jsonConfig, WorkflowDTO.class);
        } catch(Exception e) {
            throw new RuntimeException("Erreur parse JSON workflow", e);
        }
    }
}