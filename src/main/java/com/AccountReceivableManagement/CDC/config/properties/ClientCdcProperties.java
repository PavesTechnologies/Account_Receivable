package com.AccountReceivableManagement.CDC.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "client-cdc")
public class ClientCdcProperties {

    /**
     * Enable/Disable Client CDC
     */
    private boolean enabled = true;

    /**
     * Connector Name
     * Example : RMS
     */
    private String connectorName = "RMS";

    /**
     * Database Type
     * mysql / postgres
     */
    private String databaseType;

    /**
     * Host
     */
    private String hostname;

    /**
     * Port
     */
    private Integer port;

    /**
     * Database Name
     */
    private String databaseName;

    /**
     * Database Include List
     * Explicit list of databases to monitor
     */
    private String databaseIncludeList;

    /**
     * Username
     */
    private String username;

    /**
     * Password
     */
    private String password;

    /**
     * Debezium logical server name
     */
    private String serverName;

    /**
     * Client table
     */
    private String tableIncludeList;

    private Integer serverId = 185744;

//    private Integer serverId;

    private String jdbcUrl;

    /**
     * Snapshot Mode
     */
    private String snapshotMode = "initial";

    /**
     * Offset Storage Table
     */
    private String offsetTable = "debezium_client_offsets";

    /**
     * Schema History Table
     */
    private String schemaHistoryTable = "debezium_client_schema_history";

    /**
     * Topic Prefix
     */
    private String topicPrefix = "client-cdc";

    /**
     * Connector Class
     */
    private String connectorClass = "io.debezium.connector.mysql.MySqlConnector";

    /**
     * SSL Configuration
     */
    private SslProperties ssl = new SslProperties();

    @Data
    public static class SslProperties {
        private String mode = "disabled";
    }

}