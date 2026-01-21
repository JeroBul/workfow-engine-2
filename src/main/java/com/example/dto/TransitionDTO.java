package com.example.dto;

import java.util.List;

public class TransitionDTO {
    public String fromActionId;
    public String toActionId;
    public List<ConditionGroupDTO> conditionGroups;
}