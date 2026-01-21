package com.example.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

@ApplicationScoped
public class UpdateDemandAction implements WorkflowAction{
    @Override
    public boolean execute(Map<String, Object> context) {
        return true;
    }
}
