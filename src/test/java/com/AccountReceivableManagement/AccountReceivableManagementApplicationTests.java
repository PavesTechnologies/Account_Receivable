package com.AccountReceivableManagement;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Loads the full application context - every bean, configuration property
 * and wiring - against an in-memory database. It must never reach shared
 * infrastructure: the main configuration (and the local .env read by
 * spring-dotenv) points at the shared AR database, with ddl-auto=update, and
 * at the RMS/PMS CDC sources, whose connectors use the same replication
 * server ids and offset tables as the deployed service. Those are overridden
 * here; automatic billing stays off.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:context_loads;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "client-cdc.enabled=false",
        "project-cdc.enabled=false",
        "ar.automatic-billing.enabled=false",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/test-issuer"
})
@ActiveProfiles("test")
class AccountReceivableManagementApplicationTests {

	@Test
	void contextLoads() {
	}

}
