package com.AccountReceivableManagement.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Replaces Spring Boot's SMTP-backed mail sender with {@link LogOnlyMailSender} under {@code local-demo}. Boot's mail
 * auto-configuration backs off when a {@code JavaMailSender} bean exists, so no SMTP client is ever configured.
 */
@Configuration(proxyBeanMethods = false)
@Profile(LocalDemoIsolationValidator.PROFILE)
public class DemoMailConfig {

    @Bean
    JavaMailSender demoLogOnlyMailSender() {
        return new LogOnlyMailSender();
    }
}
