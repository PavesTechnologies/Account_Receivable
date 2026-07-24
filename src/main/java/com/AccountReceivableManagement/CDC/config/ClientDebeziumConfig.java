package com.AccountReceivableManagement.CDC.config;

import com.AccountReceivableManagement.CDC.config.properties.ClientCdcProperties;
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
@EnableConfigurationProperties(ClientCdcProperties.class)
public class ClientDebeziumConfig {

    private final ClientCdcProperties clientCdcProperties;

    @Value("${spring.datasource.url}")
    private String arJdbcUrl;

    @Value("${spring.datasource.username}")
    private String arUsername;

    @Value("${spring.datasource.password}")
    private String arPassword;

    @Bean("clientDebeziumConfiguration")
    public io.debezium.config.Configuration clientDebeziumConfiguration() {

        log.info("======================================================");
        log.info("Starting RMS Client CDC Configuration");
        log.info("======================================================");

        // Validate required properties
        validateClientCdcProperties();

        log.info("Connector Name              : {}", clientCdcProperties.getConnectorName());
        log.info("Connector Class             : {}", clientCdcProperties.getConnectorClass());

        log.info("RMS Host                    : {}", clientCdcProperties.getHostname());
        log.info("RMS Port                    : {}", clientCdcProperties.getPort());
        log.info("RMS Database                : {}", clientCdcProperties.getDatabaseName());
        log.info("RMS Username                : {}", clientCdcProperties.getUsername());

        log.info("Topic Prefix                : {}", clientCdcProperties.getTopicPrefix());

        // Use databaseIncludeList if available, otherwise fallback to databaseName
        String databaseIncludeList = clientCdcProperties.getDatabaseIncludeList() != null
                ? clientCdcProperties.getDatabaseIncludeList()
                : clientCdcProperties.getDatabaseName();

        log.info("Database Include List       : {}", databaseIncludeList);
        log.info("Table Include List          : {}", clientCdcProperties.getTableIncludeList());
        log.info("Snapshot Mode               : {}", clientCdcProperties.getSnapshotMode());

        log.info("------------------------------------------------------");
        log.info("AR Database (Offset Storage)");
        log.info("------------------------------------------------------");
        log.info("AR JDBC URL                 : {}", arJdbcUrl);
        log.info("AR Username                 : {}", arUsername);

        log.info("Offset Table                : {}", clientCdcProperties.getOffsetTable());
        log.info("Schema History Table        : {}", clientCdcProperties.getSchemaHistoryTable());

        log.info("SSL Mode                    : {}", clientCdcProperties.getSsl().getMode());

        io.debezium.config.Configuration configuration =
                io.debezium.config.Configuration.create()

                        .with("name",
                                clientCdcProperties.getConnectorName())

                        .with("connector.class",
                                clientCdcProperties.getConnectorClass())

                        // RMS Source Database
                        .with("database.hostname",
                                clientCdcProperties.getHostname())

                        .with("database.port",
                                clientCdcProperties.getPort())

                        .with("database.user",
                                clientCdcProperties.getUsername())

                        .with("database.password",
                                clientCdcProperties.getPassword())

                        .with("database.server.id",
                                clientCdcProperties.getServerId())

                        .with("database.include.list",
                                databaseIncludeList)

                        .with("table.include.list",
                                clientCdcProperties.getTableIncludeList())

                        .with("topic.prefix",
                                clientCdcProperties.getTopicPrefix())

                        .with("snapshot.mode",
                                clientCdcProperties.getSnapshotMode())

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
                                clientCdcProperties.getOffsetTable())

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
                                clientCdcProperties.getSchemaHistoryTable())

                        .with("database.ssl.mode",
                                clientCdcProperties.getSsl().getMode())

                        .with("heartbeat.interval.ms",
                                "30000")

                        .with("include.schema.changes",
                                "true")

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

    private void validateClientCdcProperties() {
        if (clientCdcProperties.getConnectorName() == null) {
            throw new IllegalStateException("Client CDC connector name is not configured");
        }
        if (clientCdcProperties.getConnectorClass() == null) {
            throw new IllegalStateException("Client CDC connector class is not configured");
        }
        if (clientCdcProperties.getHostname() == null) {
            throw new IllegalStateException("Client CDC hostname is not configured");
        }
        if (clientCdcProperties.getPort() == null) {
            throw new IllegalStateException("Client CDC port is not configured");
        }
        if (clientCdcProperties.getDatabaseName() == null) {
            throw new IllegalStateException("Client CDC database name is not configured");
        }
        if (clientCdcProperties.getUsername() == null) {
            throw new IllegalStateException("Client CDC username is not configured");
        }
        if (clientCdcProperties.getPassword() == null) {
            throw new IllegalStateException("Client CDC password is not configured");
        }
        if (clientCdcProperties.getServerId() == null) {
            throw new IllegalStateException("Client CDC server ID is not configured");
        }
        if (clientCdcProperties.getTableIncludeList() == null) {
            throw new IllegalStateException("Client CDC table include list is not configured");
        }
        if (clientCdcProperties.getTopicPrefix() == null) {
            throw new IllegalStateException("Client CDC topic prefix is not configured");
        }
        if (clientCdcProperties.getOffsetTable() == null) {
            throw new IllegalStateException("Client CDC offset table is not configured");
        }
        if (clientCdcProperties.getSchemaHistoryTable() == null) {
            throw new IllegalStateException("Client CDC schema history table is not configured");
        }
        if (clientCdcProperties.getSsl() == null || clientCdcProperties.getSsl().getMode() == null) {
            throw new IllegalStateException("Client CDC SSL mode is not configured");
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
