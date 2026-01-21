package com.example.rest;

import com.example.dto.WorkflowDTO;
import com.example.service.WorkflowConfigService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;

@Path("/admin/workflows")
@Consumes("application/json")
@Produces("application/json")
public class WorkflowConfigResource {

    @Inject WorkflowConfigService configService;

    @POST
    @Path("/import/{name}")
    public void importWorkflow(@PathParam("name") String name, WorkflowDTO dto) {
        configService.saveWorkflowConfig(name, dto);
    }

    @GET
    @Path("/export/{name}")
    public WorkflowDTO exportWorkflow(@PathParam("name") String name) {
        return configService.loadWorkflowConfig(name);
    }
}