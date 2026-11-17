package com.AccountReceivableManagement.config;

import com.AccountReceivableManagement.config.properties.AutomaticBillingSchedulerProperties;
import com.AccountReceivableManagement.config.properties.OperationsMonitoringProperties;
import com.AccountReceivableManagement.config.properties.UmsServiceAuthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the automatic billing scheduler, UMS service authentication and
 * operations monitoring settings, as ClientDebeziumConfig does for CDC.
 */
@Configuration
@EnableConfigurationProperties({AutomaticBillingSchedulerProperties.class, OperationsMonitoringProperties.class,
        UmsServiceAuthProperties.class})
public class AutomaticBillingConfig {
}
