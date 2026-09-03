package com.kasi.backend.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("生产数据库Schema来源")
class DatabaseSchemaSourceTest {

    private static final Path PRODUCTION_SCHEMA =
            Path.of("src/main/resources/db/kasi_promotion.sql");
    private static final Path FLYWAY_BASELINE =
            Path.of("src/main/resources/db/migration/V1__baseline.sql");

    @Test
    @DisplayName("生产迁移使用独立Flyway配置且应用启动不自动迁移")
    void productionSchemaUsesReleaseOnlyFlywayAndDisablesRuntimeMigration() throws Exception {
        assertThat(PRODUCTION_SCHEMA).isRegularFile();
        assertThat(FLYWAY_BASELINE).isRegularFile();

        Element project = mavenProject();
        assertThat(xpathText(project, "profiles/profile[id='migration']/id"))
                .isEqualTo("migration");
        assertThat(xpathText(project,
                "profiles/profile[id='migration']/build/plugins/plugin[artifactId='flyway-maven-plugin']/groupId"))
                .isEqualTo("org.flywaydb");
        assertThat(xpathText(project,
                "profiles/profile[id='migration']/build/plugins/plugin[artifactId='flyway-maven-plugin']/configuration/url"))
                .isEqualTo("${env.FLYWAY_URL}");
        assertThat(xpathText(project,
                "profiles/profile[id='migration']/build/plugins/plugin[artifactId='flyway-maven-plugin']/configuration/user"))
                .isEqualTo("${env.FLYWAY_USER}");
        assertThat(xpathText(project,
                "profiles/profile[id='migration']/build/plugins/plugin[artifactId='flyway-maven-plugin']/configuration/password"))
                .isEqualTo("${env.FLYWAY_PASSWORD}");
        assertThat(xpathText(project,
                "profiles/profile[id='migration']/build/plugins/plugin[artifactId='flyway-maven-plugin']/configuration/locations/location"))
                .isEqualTo("filesystem:src/main/resources/db/migration");
        assertThat(xpathText(project,
                "profiles/profile[id='migration']/build/plugins/plugin[artifactId='flyway-maven-plugin']/configuration/baselineOnMigrate"))
                .isEqualTo("false");
        assertThat(xpathText(project,
                "profiles/profile[id='migration']/build/plugins/plugin[artifactId='flyway-maven-plugin']/configuration/validateOnMigrate"))
                .isEqualTo("true");
        assertThat(xpathText(project,
                "profiles/profile[id='migration']/build/plugins/plugin[artifactId='flyway-maven-plugin']/configuration/validateMigrationNaming"))
                .isEqualTo("true");
        assertThat(xpathText(project,
                "profiles/profile[id='migration']/build/plugins/plugin[artifactId='flyway-maven-plugin']/configuration/outOfOrder"))
                .isEqualTo("false");
        assertThat(xpathText(project,
                "profiles/profile[id='migration']/build/plugins/plugin[artifactId='flyway-maven-plugin']/configuration/cleanDisabled"))
                .isEqualTo("true");
        assertThat(xpathCount(project,
                "dependencies/dependency[groupId='org.flywaydb']"))
                .isZero();
        assertThat(xpathCount(project,
                "profiles/profile[id='migration']/build/plugins/plugin[artifactId='flyway-maven-plugin']"
                        + "/dependencies/dependency[groupId='org.flywaydb' and artifactId='flyway-mysql']"))
                .isEqualTo(1);
        assertThat(xpathCount(project,
                "profiles/profile[id='migration']/build/plugins/plugin[artifactId='flyway-maven-plugin']"
                        + "/dependencies/dependency[groupId='com.mysql' and artifactId='mysql-connector-j']"))
                .isEqualTo(1);

        Properties application = new Properties();
        try (Reader reader = Files.newBufferedReader(
                Path.of("src/main/resources/application.properties"), StandardCharsets.UTF_8)) {
            application.load(reader);
        }
        assertThat(application.getProperty("spring.flyway.enabled")).isEqualTo("false");
        assertThat(application.getProperty("spring.sql.init.mode")).isNull();
        assertThat(application.getProperty("spring.sql.init.schema-locations")).isNull();
    }

    private static Element mavenProject() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        return factory.newDocumentBuilder().parse(Path.of("pom.xml").toFile())
                .getDocumentElement();
    }

    private static String xpathText(Node node, String expression) throws Exception {
        var xpath = XPathFactory.newInstance().newXPath();
        return ((String) xpath.evaluate(expression, node, XPathConstants.STRING)).trim();
    }

    private static int xpathCount(Node node, String expression) throws Exception {
        var xpath = XPathFactory.newInstance().newXPath();
        return ((Number) xpath.evaluate(
                "count(" + expression + ")",
                node,
                XPathConstants.NUMBER)).intValue();
    }
}
