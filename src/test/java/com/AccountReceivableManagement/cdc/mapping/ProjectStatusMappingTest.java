package com.AccountReceivableManagement.cdc.mapping;

import com.AccountReceivableManagement.cdc.parsing.CdcValueConverter;
import com.AccountReceivableManagement.entity.project_entity.ProjectMasterReference;
import com.AccountReceivableManagement.entity_enums.project.ProjectStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Status values mirror PMS (pms.projects.status = ACTIVE, ARCHIVED, PLANNING, COMPLETED) and are stored as text,
 * not as a native MySQL ENUM that can drift from the Java enum.
 */
class ProjectStatusMappingTest {

    private static final Set<String> PMS_STATUS_VALUES = Set.of("ACTIVE", "ARCHIVED", "PLANNING", "COMPLETED");

    private final CdcValueConverter converter = new CdcValueConverter();

    @Test
    void everyPmsStatusIsRepresentedByTheJavaEnum() {
        Set<String> names = Arrays.stream(ProjectStatus.values()).map(Enum::name).collect(Collectors.toSet());
        assertTrue(names.containsAll(PMS_STATUS_VALUES), "missing PMS statuses: " + PMS_STATUS_VALUES);
    }

    @Test
    void existingEnumConstantsAreKept() {
        Set<String> names = Arrays.stream(ProjectStatus.values()).map(Enum::name).collect(Collectors.toSet());
        assertTrue(names.containsAll(Set.of("NOT_STARTED", "IN_PROGRESS", "ON_HOLD", "COMPLETED", "ACTIVE",
                "CANCELLED", "DELAYED")));
    }

    @Test
    void converterMapsEachPmsStatusToItsOwnConstantNotAnotherOne() {
        ColumnMapping mapping = ProjectCdcMappingRegistry.PMS_TO_AR.get("status");
        assertEquals("projectStatus", mapping.getTargetField());
        for (String pms : PMS_STATUS_VALUES) {
            assertSame(ProjectStatus.valueOf(pms), converter.convertValue(pms, mapping), pms);
        }
    }

    @Test
    void entityStoresStatusAsStringVarchar30() throws Exception {
        Field field = ProjectMasterReference.class.getDeclaredField("projectStatus");

        assertEquals(EnumType.STRING, field.getAnnotation(Enumerated.class).value());
        assertEquals(SqlTypes.VARCHAR, field.getAnnotation(JdbcTypeCode.class).value());
        Column column = field.getAnnotation(Column.class);
        assertEquals("project_status", column.name());
        assertEquals(30, column.length());
    }

    @Test
    void hibernateGeneratesVarcharForProjectStatusButLeavesLifecycleStageUnchanged(@TempDir Path dir) throws Exception {
        Path script = dir.resolve("create.sql");
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .applySetting("hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                .applySetting("hibernate.connection.url", "jdbc:h2:mem:project_status_ddl")
                .applySetting("jakarta.persistence.schema-generation.database.action", "none")
                .applySetting("jakarta.persistence.schema-generation.scripts.action", "create")
                .applySetting("jakarta.persistence.schema-generation.scripts.create-target", script.toString())
                .build();
        try {
            Metadata metadata = new MetadataSources(registry)
                    .addAnnotatedClass(ProjectMasterReference.class).buildMetadata();
            metadata.buildSessionFactory().close();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }

        String ddl = String.join(" ", Files.readAllLines(script));
        Matcher status = Pattern.compile("project_status (\\w+(?:\\(\\d+\\))?)").matcher(ddl);
        assertTrue(status.find(), "project_status column not found in generated DDL");
        assertEquals("varchar(30)", status.group(1));
        // lifecycle_stage is intentionally untouched here (separate decision, V15): still a native enum.
        assertTrue(ddl.contains("lifecycle_stage enum"), "lifecycle_stage should be unchanged");
    }
}
