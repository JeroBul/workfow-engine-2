package com.example.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
public class WorkflowInstance extends PanacheEntity {
    public String workflowName;
    public String username;
    public String currentActionId;
    public String lastHumanActionId;

    @ElementCollection
    public List<String> history = new ArrayList<>();

    @Lob
    @Column(length = 8192)
    public String contextJson;
}