package com.AccountReceivableManagement.controller.projectbilling_config;

import com.AccountReceivableManagement.dto.projectbilling_config.ProjectMilestoneResponseDto;
import com.AccountReceivableManagement.global_exception_handler.GlobalExceptionHandler;
import com.AccountReceivableManagement.service_interface.projectbilling_config.ProjectMilestoneService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The real controller behind the real exception handler, without a Spring context or a database. (Authentication is
 * enforced by SecurityConfig for every /api/** path and is covered there, not re-tested here.)
 */
@ExtendWith(MockitoExtension.class)
class ProjectMilestoneControllerTest {

    @Mock
    private ProjectMilestoneService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ProjectMilestoneController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsTheMilestonesInTheApiEnvelopeWithPmsIdsAsNumbers() throws Exception {
        when(service.getMilestones(9002L, null, false)).thenReturn(List.of(ProjectMilestoneResponseDto.builder()
                .pmsMilestoneId(5L).pmsProjectId(9002L).milestoneCode("MS-5").milestoneName("Go-live")
                .plannedDueDate(LocalDate.of(2026, 12, 15)).allocationPercentage(new BigDecimal("25.00"))
                .billable(true).billingReadiness("READY_FOR_BILLING").executionStatus("COMPLETED").build()));

        mvc.perform(get("/api/projects/9002/milestones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].pmsMilestoneId").value(5))
                .andExpect(jsonPath("$.data[0].pmsProjectId").value(9002))
                .andExpect(jsonPath("$.data[0].milestoneName").value("Go-live"))
                .andExpect(jsonPath("$.data[0].allocationPercentage").value(25.00))
                .andExpect(jsonPath("$.data[0].billingReadiness").value("READY_FOR_BILLING"))
                .andExpect(jsonPath("$.data[0].sourceDeleted").doesNotExist());
    }

    @Test
    void passesTheFiltersThrough() throws Exception {
        when(service.getMilestones(9002L, "READY_FOR_BILLING", true)).thenReturn(List.of());

        mvc.perform(get("/api/projects/9002/milestones").param("billingReadiness", "READY_FOR_BILLING").param("billableOnly", "true"))
                .andExpect(status().isOk());

        verify(service).getMilestones(9002L, "READY_FOR_BILLING", true);
    }

    @Test
    void anEmptyListIsSuccessWithAnExplanatoryMessage() throws Exception {
        when(service.getMilestones(9002L, null, false)).thenReturn(List.of());

        mvc.perform(get("/api/projects/9002/milestones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("No milestones")));
    }

    @Test
    void anUnknownProjectIs404() throws Exception {
        when(service.getMilestones(404L, null, false))
                .thenThrow(new GlobalExceptionHandler.ResourceNotFoundException("Project 404 was not found."));

        mvc.perform(get("/api/projects/404/milestones"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Project 404 was not found."));
    }

    @Test
    void anInvalidReadinessIs400() throws Exception {
        when(service.getMilestones(9002L, "ELIGIBLE", false))
                .thenThrow(new GlobalExceptionHandler.ValidationException("billingReadiness must be one of NOT_BILLABLE, NOT_READY, READY_FOR_BILLING."));

        mvc.perform(get("/api/projects/9002/milestones").param("billingReadiness", "ELIGIBLE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // The two tests below check what the CONTROLLER maps (Spring's own rules), so they run WITHOUT the project-wide
    // GlobalExceptionHandler. That handler's catch-all currently turns a bad path variable or a wrong HTTP method into
    // HTTP 500 for every endpoint in the application; that is existing behaviour and is deliberately not changed here.
    private MockMvc springDefaults() {
        return MockMvcBuilders.standaloneSetup(new ProjectMilestoneController(service)).build();
    }

    @Test
    void aNonNumericProjectIdNeverReachesTheService() throws Exception {
        springDefaults().perform(get("/api/projects/not-a-number/milestones")).andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void theEndpointIsReadOnlyOnlyGetIsMapped() throws Exception {
        MockMvc plain = springDefaults();
        plain.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/projects/9002/milestones"))
                .andExpect(status().isMethodNotAllowed());
        plain.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/projects/9002/milestones"))
                .andExpect(status().isMethodNotAllowed());
        plain.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/projects/9002/milestones"))
                .andExpect(status().isMethodNotAllowed());
        plain.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/projects/9002/milestones"))
                .andExpect(status().isMethodNotAllowed());
        org.mockito.Mockito.verifyNoInteractions(service);
    }
}
