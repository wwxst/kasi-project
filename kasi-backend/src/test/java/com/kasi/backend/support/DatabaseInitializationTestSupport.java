package com.kasi.backend.support;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class DatabaseInitializationTestSupport {

    private DatabaseInitializationTestSupport() {
    }

    public static JdbcTemplate initializeDatabase(String databaseName) {
        return initializeDatabase(databaseName, "db/kasi_promotion.sql");
    }

    public static JdbcTemplate initializeDatabase(String databaseName, String scriptPath) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:" + databaseName + "_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.addScript(new ClassPathResource(scriptPath));
        populator.execute(dataSource);
        return new JdbcTemplate(dataSource);
    }
}
