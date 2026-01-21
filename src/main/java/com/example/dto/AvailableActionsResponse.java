package com.example.dto;

import java.util.List;
import java.util.Map;

public class AvailableActionsResponse {
    public List<ActionDTO> nextActions;
    public Map<String, Object> context;
}