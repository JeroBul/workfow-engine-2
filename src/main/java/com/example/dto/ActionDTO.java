package com.example.dto;

import java.util.List;

public class ActionDTO {
    public String actionId;
    public String description;
    public String type; // "HUMAN" ou "AUTOMATIC"
    public List<ParameterDefinitionDTO> parameters;
}