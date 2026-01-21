package com.example.rest;

import com.example.dto.AvailableActionsResponse;
import com.example.dto.WorkflowDTO;
import com.example.model.WorkflowInstance;
import com.example.service.WorkflowConfigService;
import com.example.service.WorkflowEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;

import java.util.Collections;
import java.util.Map;

@Path("/workflows")
@Produces("application/json")
@Consumes("application/json")
public class WorkflowResource {
    @Inject WorkflowEngine engine;
    @Inject WorkflowConfigService configService;

    // Crée une nouvelle instance de workflow d'après une config workflow importée
    @POST
    @Path("/instance")
    @Transactional
    public WorkflowInstance startInstance(@QueryParam("workflowName") String workflowName,
                                          @QueryParam("username") String username,
                                          @QueryParam("startActionId") String startActionId) {
        WorkflowInstance instance = new WorkflowInstance();
        instance.workflowName = workflowName;
        instance.username = username;
        instance.currentActionId = startActionId;
        instance.lastHumanActionId = null;
        instance.contextJson = "{}";
        instance.persist();
        return instance;
    }

    // Donne toutes les prochaines actions possibles et le contexte actuel
    @GET
    @Path("/instance/{instanceId}/available")
    @Transactional
    public AvailableActionsResponse getAvailableActions(@PathParam("instanceId") Long instanceId) {
        WorkflowInstance instance = WorkflowInstance.findById(instanceId);
        if (instance == null) throw new NotFoundException();
        WorkflowDTO config = configService.loadWorkflowConfig(instance.workflowName);
        Map<String, Object> ctx = parseContext(instance.contextJson);
        var next = engine.getNextPossibleActions(config, instance.currentActionId, ctx);

        AvailableActionsResponse resp = new AvailableActionsResponse();
        resp.nextActions = next;
        resp.context = ctx;
        return resp;
    }

    // Déclenche une action humaine + auto-runner dans la même transaction
    @POST
    @Path("/instance/{instanceId}/action/{actionId}")
    @Transactional
    public AvailableActionsResponse executeWithAutomatics(
            @PathParam("instanceId") Long instanceId,
            @PathParam("actionId") String actionId,
            Map<String, Object> inputParams
    ) {
        return engine.executeHumanAndAutomatics(instanceId, actionId, inputParams);
    }

    private Map<String, Object> parseContext(String ctxJson) {
        if (ctxJson == null || ctxJson.isBlank()) return Collections.emptyMap();
        try {
            return new ObjectMapper().readValue(ctxJson, Map.class);
        } catch (Exception e) {
            throw new NotFoundException("Context JSON mal formé");
        }
    }
}