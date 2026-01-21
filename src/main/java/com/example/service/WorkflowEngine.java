package com.example.service;

import com.example.dto.*;
import com.example.model.WorkflowInstance;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.All;
import io.quarkus.arc.InstanceHandle;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.mvel2.MVEL;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ApplicationScoped
public class WorkflowEngine {
    @Inject WorkflowConfigService configService;
    @Inject ObjectMapper objectMapper;
    @Inject
    @All
    List<InstanceHandle<WorkflowAction>> actions;

    // Exécute une action humaine et toutes les auto-actions suivantes dans la même transaction
    @Transactional
    public AvailableActionsResponse executeHumanAndAutomatics(Long instanceId, String actionId, Map<String,Object> params) {
        WorkflowInstance instance = WorkflowInstance.findById(instanceId);
        if (instance == null) throw new IllegalArgumentException("Instance manquante");
        WorkflowDTO config = configService.loadWorkflowConfig(instance.workflowName);

        Map<String, Object> context = new HashMap<>();
        if (instance.contextJson != null && !instance.contextJson.isEmpty())
            context = deserialize(instance.contextJson);

        // 1. Vérifier si action demandée est autorisée à ce stade
        List<ActionDTO> allowedActions = getNextPossibleActions(config, instance.currentActionId, context);
        boolean isAllowed = allowedActions.stream().anyMatch(a -> a.actionId.equals(actionId));
        if (!isAllowed) {
            throw new IllegalArgumentException("Action '" + actionId + "' non autorisée à ce stade du workflow.");
        }


        ActionDTO curAction = config.actions.stream()
                .filter(a -> a.actionId.equals(actionId))
                .findFirst().orElseThrow(() -> new RuntimeException("Action inconnue"));
        if (!"HUMAN".equals(curAction.type))
            throw new IllegalArgumentException("Trigger doit être de type HUMAN");

        // Validation param en entrée
        validateActionParameters(curAction, params);

        // Injecte params dans le contexte (pour user)
        if (params != null) context.putAll(params);

        // Appel CDI bean de l'action (delegate execution)
        WorkflowAction bean = resolveActionBean(actionId);
        if (!bean.execute(context))
            throw new RuntimeException("Erreur Action HUMAINE");

        // Historisation
        instance.contextJson = serialize(context);
        instance.lastHumanActionId = actionId;
        instance.currentActionId = actionId;
        instance.history.add(actionId);

        // Run auto tant que possible
        while (true) {
            List<ActionDTO> nexts = getNextPossibleActions(config, instance.currentActionId, context);
            Optional<ActionDTO> nextAuto = nexts.stream().filter(a -> "AUTOMATIC".equals(a.type)).findFirst();
            if (nextAuto.isEmpty()) break;

            String nextActionId = nextAuto.get().actionId;
            WorkflowAction autoBean = resolveActionBean(nextActionId);
            if (!autoBean.execute(context))
                throw new RuntimeException("Action automatique " + nextActionId + " en erreur");

            instance.contextJson = serialize(context);
            instance.currentActionId = nextActionId;
            instance.history.add(nextActionId);
        }
        instance.persist();

        // Retourne les actions humaines possibles suivantes + contexte
        List<ActionDTO> possibles = getNextPossibleActions(config, instance.currentActionId, context);
        List<ActionDTO> onlyHuman = possibles.stream().filter(a -> "HUMAN".equals(a.type)).collect(Collectors.toList());

        AvailableActionsResponse resp = new AvailableActionsResponse();
        resp.nextActions = onlyHuman;
        resp.context = context;
        return resp;
    }

    // Mapping CDI: par convention ActionId → Bean nommé actionId + "Action"
    private WorkflowAction resolveActionBean(String actionId) {
        String expectedBeanClass = actionId.substring(0,1).toUpperCase() + actionId.substring(1) + "Action";
        for (InstanceHandle<WorkflowAction> a : actions) {
            if (a.getBean().getBeanClass().getSimpleName().equals(expectedBeanClass))
                return a.get();
        }
        throw new IllegalArgumentException("Aucune action Java pour : " + actionId);
    }

    // Pure logique de validation paramétrique
    public void validateActionParameters(ActionDTO action, Map<String, Object> params) {
        if (action.parameters == null) return;
        for (ParameterDefinitionDTO paramDef : action.parameters) {
            Object val = (params == null) ? null : params.get(paramDef.name);
            if (paramDef.required && (val == null || val.toString().isEmpty()))
                throw new IllegalArgumentException("Paramètre requis manquant : " + paramDef.name);
            if (val != null) {
                switch (paramDef.type) {
                    case "string":
                        if (paramDef.pattern != null && !paramDef.pattern.isEmpty())
                            if (!Pattern.matches(paramDef.pattern, val.toString()))
                                throw new IllegalArgumentException("Format incorrect pour " + paramDef.name);
                        break;
                    case "int":
                        try { Integer.parseInt(val.toString()); }
                        catch (Exception e) { throw new IllegalArgumentException("Entier attendu pour " + paramDef.name); }
                        break;
                    case "double":
                        try { Double.parseDouble(val.toString()); }
                        catch (Exception e) { throw new IllegalArgumentException("Nombre attendu pour " + paramDef.name); }
                        break;
                    case "boolean":
                        if (!(val.toString().equalsIgnoreCase("true") || val.toString().equalsIgnoreCase("false")))
                            throw new IllegalArgumentException("Booléen attendu pour " + paramDef.name);
                        break;
                    case "jsonNode":
                        break;
                }
            }
        }
    }

    // Recherche transitions dispo depuis action courante + contexte
    public List<ActionDTO> getNextPossibleActions(WorkflowDTO config, String currentActionId, Map<String,Object> context) {
        List<ActionDTO> possibles = new ArrayList<>();
        for (TransitionDTO t : config.transitions) {
            if (!t.fromActionId.equals(currentActionId)) continue;
            // OR logique entre groupes
            if (t.conditionGroups == null || t.conditionGroups.isEmpty() ||
                    t.conditionGroups.stream().anyMatch(g -> groupValid(g, context))) {
                possibles.add(config.actions.stream().filter(a -> a.actionId.equals(t.toActionId)).findFirst().get());
            }
        }
        return possibles;
    }

    private boolean groupValid(ConditionGroupDTO group, Map<String, Object> context) {
        if (group == null || group.mvelConditions == null || group.mvelConditions.isEmpty())
            return false;
        for (String expr : group.mvelConditions) {
            if (expr == null || expr.isBlank()) continue;
            try {
                Object result = MVEL.eval(expr, context);
                if (!(result instanceof Boolean) || !(Boolean) result) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private String serialize(Map<String, Object> ctx) {
        try { return objectMapper.writeValueAsString(ctx); }
        catch (Exception e) { throw new RuntimeException("Erreur serialisation", e); }
    }
    private Map<String, Object> deserialize(String ctxJson) {
        try { return objectMapper.readValue(ctxJson, Map.class); }
        catch (Exception e) { throw new RuntimeException("Erreur deserialisation context", e); }
    }
}