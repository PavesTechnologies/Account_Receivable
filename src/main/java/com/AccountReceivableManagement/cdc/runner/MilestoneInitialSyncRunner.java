package com.AccountReceivableManagement.cdc.runner;

import com.AccountReceivableManagement.cdc.config.properties.ProjectCdcProperties;
import com.AccountReceivableManagement.cdc.payload.CdcEventPayload;
import com.AccountReceivableManagement.cdc.service.MilestoneDataProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Copies milestones that already exist in PMS into project_milestone_reference.
 *
 * Debezium only snapshots tables when the connector first starts; adding
 * pms.project_milestones to an existing connector streams new changes but not the rows
 * already there. This runner closes that gap by reading the PMS table once at startup and
 * applying every row as a snapshot ("r") event through the same processor as live CDC,
 * so the version check keeps it idempotent and it never overwrites newer CDC data.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"project-cdc.enabled", "project-cdc.milestone-initial-sync.enabled"}, havingValue = "true")
public class MilestoneInitialSyncRunner {

    static final String SELECT_MILESTONES = "SELECT * FROM project_milestones ORDER BY id";

    private final ProjectCdcProperties properties;
    private final MilestoneDataProcessor milestoneDataProcessor;

    @EventListener(ApplicationReadyEvent.class)
    public void syncExistingMilestones() {
        try {
            int applied = syncAll(new JdbcTemplate(pmsDataSource()));
            log.info("[MILESTONE-CDC] Initial sync processed {} PMS milestone row(s)", applied);
        } catch (Exception e) {
            // Never block AR startup; live CDC still streams new changes, and the next restart retries.
            log.warn("[MILESTONE-CDC] Initial milestone sync skipped: {}", e.getMessage());
        }
    }

    int syncAll(JdbcTemplate pms) {
        List<Map<String, Object>> rows = pms.queryForList(SELECT_MILESTONES);
        int applied = 0;
        for (Map<String, Object> row : rows) {
            try {
                milestoneDataProcessor.process(CdcEventPayload.builder()
                        .connectorName("milestone-initial-sync")
                        .entityType("PMS-PROJECT_MILESTONES")
                        .entityId(String.valueOf(row.get("id")))
                        .operation("r")
                        .after(row)
                        .build());
                applied++;
            } catch (Exception e) {
                log.error("[MILESTONE-CDC] Initial sync failed for milestone {}: {}", row.get("id"), e.getMessage());
            }
        }
        return applied;
    }

    private DriverManagerDataSource pmsDataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setUrl(jdbcUrl());
        ds.setUsername(properties.getUsername());
        ds.setPassword(properties.getPassword());
        return ds;
    }

    /** Uses project-cdc.jdbc-url, adding the same SSL requirement as the Debezium connector. */
    private String jdbcUrl() {
        String url = properties.getJdbcUrl() != null
                ? properties.getJdbcUrl()
                : "jdbc:mysql://" + properties.getHostname() + ":" + properties.getPort() + "/" + properties.getDatabaseName();
        String sslMode = properties.getSsl() != null ? properties.getSsl().getMode() : null;
        if (sslMode != null && !"disabled".equalsIgnoreCase(sslMode) && !url.toLowerCase().contains("sslmode=")) {
            url += (url.contains("?") ? "&" : "?") + "sslMode=" + sslMode.toUpperCase();
        }
        return url;
    }
}
