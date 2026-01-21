package com.example.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;

/**
 * Contient la config workflow (JSON dynamique via DTO)
 */
@Entity
public class WorkflowConfig extends PanacheEntity {
    public String name; // ex: "onboard"
    @Lob
    public String jsonConfig; // contient le DTO serialisé
}