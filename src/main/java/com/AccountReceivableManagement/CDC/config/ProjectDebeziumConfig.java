package com.AccountReceivableManagement.CDC.config;

import com.AccountReceivableManagement.CDC.config.properties.ProjectCdcProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import javax.sql.DataSource;

@Slf4j
@RequiredArgsConstructor
@Configuration
@EnableConfigurationProperties(ProjectCdcProperties.class)
public class ProjectDebeziumConfig {

    private final ProjectCdcProperties projectCdcProperties;

    @Value("${spring.datasource.url}")
    private String arJdbcUrl;

    @Value("${spring.datasource.username}")
    private String arUsername;

    @Value("${spring.datasource.password}")
    private String arPassword;

    @Bean("projectDebeziumConfiguration")
    public io.debezium.config.Configuration projectDebeziumConfiguration() {

        log.info("======================================================");
        log.info("Starting PMS Project CDC Configuration");
        log.info("======================================================");

        // Validate required properties
        validateProjectCdcProperties();

        log.info("Connector Name              : {}", projectCdcProperties.getConnectorName());
        log.info("Connector Class             : {}", projectCdcProperties.getConnectorClass());

        log.info("PMS Host                    : {}", projectCdcProperties.getHostname());
        log.info("PMS Port                    : {}", projectCdcProperties.getPort());
        log.info("PMS Database                : {}", projectCdcProperties.getDatabaseName());
        log.info("PMS Username                : {}", projectCdcProperties.getUsername());

        log.info("Topic Prefix                : {}", projectCdcProperties.getTopicPrefix());

        // Use databaseIncludeList if available, otherwise fallback to databaseName
        String databaseIncludeList = projectCdcProperties.getDatabaseIncludeList() != null
                ? projectCdcProperties.getDatabaseIncludeList()
                : projectCdcProperties.getDatabaseName();

        log.info("Database Include List       : {}", databaseIncludeList);
        log.info("Table Include List          : {}", projectCdcProperties.getTableIncludeList());
        log.info("Snapshot Mode               : {}", projectCdcProperties.getSnapshotMode());

        log.info("------------------------------------------------------");
        log.info("AR Database (Offset Storage)");
        log.info("------------------------------------------------------");
        log.info("AR JDBC URL                 : {}", arJdbcUrl);
        log.info("AR Username                 : {}", arUsername);

        log.info("Offset Table                : {}", projectCdcProperties.getOffsetTable());
        log.info("Schema History Table        : {}", projectCdcProperties.getSchemaHistoryTable());

        log.info("SSL Mode                    : {}", projectCdcProperties.getSsl().getMode());

        io.debezium.config.Configuration configuration =
                io.debezium.config.Configuration.create()

                        .with("name",
                                projectCdcProperties.getConnectorName())

                        .with("connector.class",
                                projectCdcProperties.getConnectorClass())

                        // PMS Source Database
                        .with("database.hostname",
                                projectCdcProperties.getHostname())

                        .with("database.port",
                                projectCdcProperties.getPort())

                        .with("database.user",
                                projectCdcProperties.getUsername())

                        .with("database.password",
                                projectCdcProperties.getPassword())

                        .with("database.server.id",
                                projectCdcProperties.getServerId())

                        .with("database.include.list",
                                databaseIncludeList)

                        .with("table.include.list",
                                projectCdcProperties.getTableIncludeList())

                        .with("topic.prefix",
                                projectCdcProperties.getTopicPrefix())

                        .with("snapshot.mode",
                                projectCdcProperties.getSnapshotMode())

                        // Offset Storage
                        .with("offset.storage",
                                "io.debezium.storage.jdbc.offset.JdbcOffsetBackingStore")

                        .with("offset.storage.jdbc.url",
                                arJdbcUrl)

                        .with("offset.storage.jdbc.user",
                                arUsername)

                        .with("offset.storage.jdbc.password",
                                arPassword)

                        .with("offset.storage.jdbc.offset.table.name",
                                projectCdcProperties.getOffsetTable())

                        .with("offset.flush.interval.ms",
                                "60000")

                        // Schema History
                        .with("schema.history.internal",
                                "io.debezium.storage.jdbc.history.JdbcSchemaHistory")

                        .with("schema.history.internal.jdbc.url",
                                arJdbcUrl)

                        .with("schema.history.internal.jdbc.user",
                                arUsername)

                        .with("schema.history.internal.jdbc.password",
                                arPassword)

                        .with("schema.history.internal.jdbc.schema.history.table.name",
                                projectCdcProperties.getSchemaHistoryTable())

                        .with("database.ssl.mode",
                                projectCdcProperties.getSsl().getMode())

                        .with("heartbeat.interval.ms",
                                "30000")

                        .with("include.schema.changes",
                                "false")
                        
                        .with("decimal.handling.mode", 
                                "string")

                        .with("tombstones.on.delete",
                                "false")

                        .with("key.converter.schemas.enable",
                                "false")

                        .with("value.converter.schemas.enable",
                                "false")

                        .build();

        log.info("======================================================");
        log.info("Debezium Configuration Built Successfully");
        log.info("======================================================");

        return configuration;
    }

    private void validateProjectCdcProperties() {
        if (projectCdcProperties.getConnectorName() == null) {
            throw new IllegalStateException("Project CDC connector name is not configured");
        }
        if (projectCdcProperties.getConnectorClass() == null) {
            throw new IllegalStateException("Project CDC connector class is not configured");
        }
        if (projectCdcProperties.getHostname() == null) {
            throw new IllegalStateException("Project CDC hostname is not configured");
        }
        if (projectCdcProperties.getPort() == null) {
            throw new IllegalStateException("Project CDC port is not configured");
        }
        if (projectCdcProperties.getDatabaseName() == null) {
            throw new IllegalStateException("Project CDC database name is not configured");
        }
        if (projectCdcProperties.getUsername() == null) {
            throw new IllegalStateException("Project CDC username is not configured");
        }
        if (projectCdcProperties.getPassword() == null) {
            throw new IllegalStateException("Project CDC password is not configured");
        }
        if (projectCdcProperties.getServerId() == null) {
            throw new IllegalStateException("Project CDC server ID is not configured");
        }
        if (projectCdcProperties.getTableIncludeList() == null) {
            throw new IllegalStateException("Project CDC table include list is not configured");
        }
        if (projectCdcProperties.getTopicPrefix() == null) {
            throw new IllegalStateException("Project CDC topic prefix is not configured");
        }
        if (projectCdcProperties.getOffsetTable() == null) {
            throw new IllegalStateException("Project CDC offset table is not configured");
        }
        if (projectCdcProperties.getSchemaHistoryTable() == null) {
            throw new IllegalStateException("Project CDC schema history table is not configured");
        }
        if (projectCdcProperties.getSsl() == null || projectCdcProperties.getSsl().getMode() == null) {
            throw new IllegalStateException("Project CDC SSL mode is not configured");
        }
        if (arJdbcUrl == null) {
            throw new IllegalStateException("AR JDBC URL is not configured");
        }
        if (arUsername == null) {
            throw new IllegalStateException("AR username is not configured");
        }
        if (arPassword == null) {
            throw new IllegalStateException("AR password is not configured");
        }
    }
}
