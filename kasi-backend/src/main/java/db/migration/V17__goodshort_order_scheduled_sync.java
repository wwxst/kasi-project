package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class V17__goodshort_order_scheduled_sync extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        ensureColumn(connection, "cycle_type", "VARCHAR(32) NOT NULL DEFAULT 'INTERVAL_MINUTES'");
        ensureColumn(connection, "interval_value", "INT DEFAULT NULL");
        ensureColumn(connection, "interval_hours_part", "INT DEFAULT 0");
        ensureColumn(connection, "interval_minutes_part", "INT DEFAULT 0");

        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO system_scheduled_task
                    (task_code, title, description, cycle_type, interval_value,
                     interval_hours_part, interval_minutes_part, interval_minutes,
                     enabled, next_run_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, "GOODSHORT_ORDER_SYNC");
            statement.setString(2, "GoodShort 订单同步");
            statement.setString(3, "每隔1分钟同步最近3天的GoodShort订单");
            statement.setString(4, "INTERVAL_MINUTES");
            statement.setInt(5, 1);
            statement.setInt(6, 0);
            statement.setInt(7, 0);
            statement.setInt(8, 5);
            statement.setInt(9, 1);
            statement.setTimestamp(10, new Timestamp(System.currentTimeMillis() + 60_000L));
            statement.executeUpdate();
        }
    }

    private void ensureColumn(Connection connection, String column, String definition) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE UPPER(TABLE_NAME) = 'SYSTEM_SCHEDULED_TASK'
                  AND UPPER(COLUMN_NAME) = UPPER(?)
                """)) {
            statement.setString(1, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                if (resultSet.getInt(1) > 0) {
                    return;
                }
            }
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "ALTER TABLE system_scheduled_task ADD COLUMN " + column + " " + definition)) {
            statement.executeUpdate();
        }
    }
}
