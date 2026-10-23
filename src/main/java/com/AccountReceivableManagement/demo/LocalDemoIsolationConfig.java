package com.AccountReceivableManagement.demo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Late layer of the {@code local-demo} isolation guard. A static {@link BeanFactoryPostProcessor} runs before any regular
 * bean (DataSource, JPA, mail, ...) is created, with the complete environment, so a value that only appears after the early
 * check (for example from {@code .env}) still cannot reach shared infrastructure. Only active under {@code local-demo}.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@Profile(LocalDemoIsolationValidator.PROFILE)
public class LocalDemoIsolationConfig {

    @Bean
    static BeanFactoryPostProcessor localDemoIsolationGuard(ConfigurableEnvironment environment) {
        return beanFactory -> {
            LocalDemoIsolationValidator.validateIfActive(environment);
            log.info("local-demo isolation OK: datasource host is loopback, CDC engines/runners/milestone sync/inbox "
                    + "processing/billing schedulers are disabled, TMS/XMS/mail/issuer endpoints are local or blank");
        };
    }
}
