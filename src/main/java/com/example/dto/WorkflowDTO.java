package com.example.dto;

import java.util.List;

/**
 * La configuration complète du workflow (persistée en JSON en base)
 */
public class WorkflowDTO {
    public String workflowId;
    public List<ActionDTO> actions;
    public List<TransitionDTO> transitions;
}