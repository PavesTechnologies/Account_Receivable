package com.AccountReceivableManagement.CDC.config;

import com.AccountReceivableManagement.CDC.config.properties.ClientCdcProperties;
import com.AccountReceivableManagement.CDC.config.properties.ProjectCdcProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

@Slf4j
@Component
@Profile("!test") // Do not run this during tests
@RequiredArgsConstructor
public class DebeziumJdbcTableInitializer implements SmartLifecycle {

    private final JdbcTemplate jdbcTemplate;
    private final ClientCdcProperties clientCdcProperties;
    private final ProjectCdcProperties projectCdcProperties;
    private volatile boolean running = false;

    @Override
    public void start() {
        log.info("Initializing Debezium JDBC storage tables if they do not exist...");
        
        // Client CDC tables
        createOffsetsTable(clientCdcProperties.getOffsetTable());
        createSchemaHistoryTable(clientCdcProperties.getSchemaHistoryTable());
        
        // Project CDC tables
        createOffsetsTable(projectCdcProperties.getOffsetTable());
        createSchemaHistoryTable(projectCdcProperties.getSchemaHistoryTable());
        
        log.info("Debezium JDBC storage tables initialization check complete.");
        this.running = true;
    }

    @Override
    public void stop() {
        this.running = false;
    }

    @Override
    public boolean isRunning() {
        return this.running;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public int getPhase() {
        // Start before Debezium engine (which should have a higher phase)
        return Integer.MIN_VALUE;
    }

    private void createOffsetsTable(String tableName) {
        if (tableExists(tableName)) {
            log.info("Debezium offsets table '{}' already exists. Skipping creation.", tableName);
            return;
        }

        log.info("Creating Debezium offsets table '{}'...", tableName);
        String sql = "CREATE TABLE " + tableName + " ("
                + "id VARCHAR(36) NOT NULL PRIMARY KEY,"
                + "offset_key VARCHAR(2048),"
                + "offset_val TEXT,"
                + "record_insert_seq INTEGER,"
                + "record_insert_ts TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                + "record_update_ts TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                + ")";
        try {
            jdbcTemplate.execute(sql);
            log.info("Debezium offsets table '{}' created successfully.", tableName);
        } catch (Exception e) {
            log.error("Failed to create Debezium offsets table '{}'. This might not be an issue if another instance created it concurrently.", tableName, e);
        }
    }

    private void createSchemaHistoryTable(String tableName) {
        if (tableExists(tableName)) {
            log.info("Debezium schema history table '{}' already exists. Skipping creation.", tableName);
            return;
        }

        log.info("Creating Debezium schema history table '{}'...", tableName);
        String sql = "CREATE TABLE " + tableName + " ("
                + "id VARCHAR(36) NOT NULL PRIMARY KEY,"
                + "history_data LONGTEXT,"
                + "history_data_seq INTEGER,"
                + "record_insert_seq INTEGER,"
                + "record_insert_ts TIMESTAMP,"
                + "record_update_ts TIMESTAMP"
                + ")";
        try {
            jdbcTemplate.execute(sql);
            log.info("Debezium schema history table '{}' created successfully.", tableName);
        } catch (Exception e) {
            log.error("Failed to create Debezium schema history table '{}'. This might not be an issue if another instance created it concurrently.", tableName, e);
        }
    }

    private boolean tableExists(String tableName) {
        try {
            return Boolean.TRUE.equals(
                    jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
                        DatabaseMetaData metaData = connection.getMetaData();

                        try (ResultSet rs = metaData.getTables(
                                connection.getCatalog(),
                                null,
                                tableName,
                                new String[]{"TABLE"})) {
                            return rs.next();
                        }
                    })
            );
        } catch (Exception e) {
            log.error("Failed to check if table '{}' exists.", tableName, e);
            return false;
        }
    }
}
