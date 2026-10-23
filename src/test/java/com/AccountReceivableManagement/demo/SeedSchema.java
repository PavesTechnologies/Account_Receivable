package com.AccountReceivableManagement.demo;

import jakarta.persistence.Entity;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.ForeignKey;
import org.hibernate.mapping.Table;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The schema Hibernate generates for ALL current entities with the MySQL dialect and Spring's naming strategy - i.e. what
 * {@code ddl-auto=update} creates in an empty demo database. Built offline: the only connection is to an in-memory H2 database
 * Hibernate needs to run its script generation; nothing real is contacted.
 */
final class SeedSchema {

    record Column(String name, String type, boolean notNull, List<String> enumValues, Integer varcharLength, int[] decimal) {
    }

    record TableDef(String name, Map<String, Column> columns, List<String> primaryKey, String engine) {
    }

    record ForeignKeyDef(String table, String column, String parent, String parentColumn) {
    }

    private static SeedSchema instance;

    final String ddl;
    final Metadata metadata;
    final Map<String, TableDef> tables = new LinkedHashMap<>();
    final List<List<String>> uniqueKeys = new ArrayList<>();
    final Map<String, List<List<String>>> uniquesByTable = new LinkedHashMap<>();
    final List<ForeignKeyDef> foreignKeys = new ArrayList<>();

    private SeedSchema(String ddl, Metadata metadata) {
        this.ddl = ddl;
        this.metadata = metadata;
        parse();
    }

    static synchronized SeedSchema get() throws IOException {
        if (instance == null) {
            instance = build();
        }
        return instance;
    }

    private static SeedSchema build() throws IOException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        Path script = Files.createTempFile("seed-schema", ".sql");
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .applySetting("hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                .applySetting("hibernate.connection.url", "jdbc:h2:mem:seed_schema")
                .applySetting("jakarta.persistence.schema-generation.database.action", "none")
                .applySetting("jakarta.persistence.schema-generation.scripts.action", "create")
                .applySetting("jakarta.persistence.schema-generation.scripts.create-target", script.toString())
                .build();
        try {
            MetadataSources sources = new MetadataSources(registry);
            for (BeanDefinition definition : scanner.findCandidateComponents("com.AccountReceivableManagement.entity")) {
                sources.addAnnotatedClass(Class.forName(definition.getBeanClassName()));
            }
            Metadata metadata = sources.buildMetadata();
            metadata.buildSessionFactory().close();
            return new SeedSchema(Files.readString(script, StandardCharsets.UTF_8).replace("\r\n", "\n"), metadata);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
            Files.deleteIfExists(script);
        }
    }

    private void parse() {
        Matcher create = Pattern.compile("create table (\\w+) \\((.*?)\\) engine=(\\w+)", Pattern.DOTALL).matcher(ddl);
        while (create.find()) {
            Map<String, Column> columns = new LinkedHashMap<>();
            List<String> primaryKey = new ArrayList<>();
            for (String part : create.group(2).trim().split(",\\s*(?![^()]*\\))")) {
                part = part.trim();
                Matcher pk = Pattern.compile("^primary key \\(([^)]*)\\)").matcher(part);
                if (pk.find()) {
                    for (String c : pk.group(1).split(",")) {
                        primaryKey.add(c.trim());
                    }
                    continue;
                }
                if (part.startsWith("unique") || part.startsWith("foreign") || part.startsWith("constraint") || part.startsWith("key ")) {
                    continue;
                }
                Matcher col = Pattern.compile("^(\\w+) (.*)$", Pattern.DOTALL).matcher(part);
                if (!col.matches()) {
                    continue;
                }
                String type = col.group(2);
                Matcher en = Pattern.compile("^enum \\((.*?)\\)").matcher(type);
                List<String> enumValues = null;
                if (en.find()) {
                    enumValues = new ArrayList<>();
                    Matcher v = Pattern.compile("'([^']*)'").matcher(en.group(1));
                    while (v.find()) {
                        enumValues.add(v.group(1));
                    }
                }
                Matcher vc = Pattern.compile("^varchar\\((\\d+)\\)").matcher(type);
                Matcher dc = Pattern.compile("^decimal\\((\\d+),(\\d+)\\)").matcher(type);
                columns.put(col.group(1), new Column(col.group(1), type, type.contains("not null"), enumValues,
                        vc.find() ? Integer.valueOf(vc.group(1)) : null,
                        dc.find() ? new int[]{Integer.parseInt(dc.group(1)), Integer.parseInt(dc.group(2))} : null));
            }
            tables.put(create.group(1), new TableDef(create.group(1), columns, primaryKey, create.group(3)));
        }
        Matcher unique = Pattern.compile("alter table (\\w+) add constraint \\S+ unique \\(([^)]*)\\)").matcher(ddl);
        while (unique.find()) {
            List<String> cols = new ArrayList<>();
            for (String c : unique.group(2).split(",")) {
                cols.add(c.trim());
            }
            uniquesByTable.computeIfAbsent(unique.group(1), k -> new ArrayList<>()).add(cols);
        }
        Matcher fk = Pattern.compile("alter table (\\w+) add constraint \\S+ foreign key \\((\\w+)\\) references (\\w+) \\((\\w+)\\)").matcher(ddl);
        while (fk.find()) {
            foreignKeys.add(new ForeignKeyDef(fk.group(1), fk.group(2), fk.group(3), fk.group(4)));
        }
    }

    /** Tables (per Hibernate's own mapping model) that hold a foreign key to one of {@code parents}. */
    Set<String> tablesReferencing(Set<String> parents) {
        Set<String> children = new TreeSet<>();
        for (Table table : metadata.collectTableMappings()) {
            for (ForeignKey fk : table.getForeignKeys().values()) {
                if (fk.getReferencedTable() != null && parents.contains(fk.getReferencedTable().getName())) {
                    children.add(table.getName());
                }
            }
        }
        return children;
    }
}
