package io.github.javacodeintel.mcp.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

final class SchemaMigrator {
    private SchemaMigrator() {
    }

    static void apply(Connection connection) throws IOException {
        String sql;
        try (InputStream input = SchemaMigrator.class.getResourceAsStream("/db/migration/V1__initial.sql")) {
            if (input == null) {
                throw new IOException("Missing database migration V1__initial.sql");
            }
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Statement statement = connection.createStatement()) {
            for (String command : sql.split(";")) {
                String trimmed = command.trim();
                if (!trimmed.isEmpty()) {
                    statement.execute(trimmed);
                }
            }
        } catch (SQLException e) {
            throw new IOException("Failed to initialize SQLite schema", e);
        }
    }
}
