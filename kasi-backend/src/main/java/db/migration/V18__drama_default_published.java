package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Locale;

public class V18__drama_default_published extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE provider_drama
                SET local_status = CASE
                    WHEN remote_show_status = '1' THEN 'PUBLISHED'
                    ELSE 'OFFLINE'
                END
                WHERE local_status = 'DRAFT'
                """)) {
            statement.executeUpdate();
        }

        String database = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        String sql = database.contains("mysql")
                ? "ALTER TABLE provider_drama MODIFY COLUMN local_status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED'"
                : "ALTER TABLE provider_drama ALTER COLUMN local_status SET DEFAULT 'PUBLISHED'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }
}
