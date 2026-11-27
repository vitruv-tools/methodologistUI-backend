package tools.vitruv.methodologist.vsum.model.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * Applies the migration that marks the snapshots taken before writebacks of the collaboration
 * server to the {@code vsum_history} table of {@code V6}, on an in-memory H2 database in PostgreSQL
 * mode. The earlier migrations use PostgreSQL features H2 lacks, so the tables {@code vsum_history}
 * references are stubbed.
 */
class VsumHistoryMigrationTest {

  private static final String URL =
      "jdbc:h2:mem:vsum-history-migration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";

  @Test
  void marksExistingSnapshotsAsUserSnapshots_rejectsNull_andIndexesThemByVsumAndNewestFirst()
      throws SQLException {
    try (Connection connection = DriverManager.getConnection(URL, "sa", "")) {
      try (Statement statement = connection.createStatement()) {
        statement.execute("CREATE TABLE usr (id BIGINT PRIMARY KEY)");
        statement.execute("CREATE TABLE vsum (id BIGINT PRIMARY KEY)");
        statement.execute("INSERT INTO usr (id) VALUES (1)");
        statement.execute("INSERT INTO vsum (id) VALUES (1)");
      }
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/migration/V6__VSUM_HISTORY.sql"));
      try (Statement statement = connection.createStatement()) {
        statement.execute(
            "INSERT INTO vsum_history (id, created_at, creator_id, vsum_id, representation)"
                + " VALUES (1, TIMESTAMP '2026-01-01 10:00:00', 1, 1, JSON '{}')");
      }

      ScriptUtils.executeSqlScript(
          connection,
          new ClassPathResource("db/migration/V24__VSUM_HISTORY_COLLABORATION_WRITEBACK.sql"));

      try (Statement statement = connection.createStatement()) {
        statement.execute(
            "INSERT INTO vsum_history (id, created_at, creator_id, vsum_id, representation)"
                + " VALUES (2, TIMESTAMP '2026-01-01 11:00:00', 1, 1, JSON '{}')");
        statement.execute(
            "INSERT INTO vsum_history"
                + " (id, created_at, creator_id, vsum_id, representation, collaboration_writeback)"
                + " VALUES (3, TIMESTAMP '2026-01-01 12:00:00', 1, 1, JSON '{}', TRUE)");
      }
      Map<Long, Boolean> writebacks = new LinkedHashMap<>();
      try (Statement query = connection.createStatement();
          ResultSet rows =
              query.executeQuery(
                  "SELECT id, collaboration_writeback FROM vsum_history ORDER BY id")) {
        while (rows.next()) {
          writebacks.put(rows.getLong("id"), rows.getBoolean("collaboration_writeback"));
        }
      }
      assertThat(writebacks)
          .containsExactly(Map.entry(1L, false), Map.entry(2L, false), Map.entry(3L, true));

      try (Statement statement = connection.createStatement()) {
        assertThatThrownBy(
                () ->
                    statement.execute(
                        "INSERT INTO vsum_history"
                            + " (id, created_at, creator_id, vsum_id, representation,"
                            + " collaboration_writeback)"
                            + " VALUES (4, TIMESTAMP '2026-01-01 13:00:00', 1, 1, JSON '{}', NULL)"))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("collaboration_writeback");
      }

      List<String> indexedColumns = new ArrayList<>();
      try (Statement query = connection.createStatement();
          ResultSet rows =
              query.executeQuery(
                  "SELECT column_name, ordering_specification FROM information_schema.index_columns"
                      + " WHERE index_name = 'idx_vsum_history_vsum_id_created_at'"
                      + " ORDER BY ordinal_position")) {
        while (rows.next()) {
          indexedColumns.add(
              rows.getString("column_name") + " " + rows.getString("ordering_specification"));
        }
      }
      assertThat(indexedColumns).containsExactly("vsum_id ASC", "created_at DESC");
    }
  }
}
