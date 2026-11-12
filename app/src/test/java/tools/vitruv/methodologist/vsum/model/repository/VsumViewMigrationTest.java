package tools.vitruv.methodologist.vsum.model.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * Applies the migration that adds a name and a description to the {@code vsum_view} table of {@code
 * V8}, on an in-memory H2 database in PostgreSQL mode. The earlier migrations use PostgreSQL
 * features H2 lacks, so the tables {@code vsum_view} references are stubbed. H2 has no deferrable
 * constraints either, so the unique constraint on the name is added without its {@code DEFERRABLE
 * INITIALLY DEFERRED} clause.
 */
class VsumViewMigrationTest {

  private static final String URL =
      "jdbc:h2:mem:vsum-view-migration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
  private static final String DEFERRED = " DEFERRABLE INITIALLY DEFERRED";

  @Test
  void namesExistingViewsUniquelyAndRequiresUniqueNamesPerVsum() throws SQLException, IOException {
    try (Connection connection = DriverManager.getConnection(URL, "sa", "")) {
      try (Statement statement = connection.createStatement()) {
        statement.execute("CREATE TABLE vsum (id BIGINT PRIMARY KEY)");
        statement.execute("CREATE TABLE meta_model (id BIGINT PRIMARY KEY)");
        statement.execute(
            "CREATE TABLE file_storage (id BIGINT PRIMARY KEY, filename VARCHAR(255) NOT NULL)");
        statement.execute("INSERT INTO vsum (id) VALUES (1), (2)");
        statement.execute(
            "INSERT INTO file_storage (id, filename) VALUES (1, 'Brake.nj'), (2, 'Brake.nj'),"
                + " (3, 'notes.txt')");
      }
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/migration/V8__VIEW_TYPE.sql"));
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/migration/V9__VIEW_TYPE_CONSTRAINTS.sql"));
      try (Statement statement = connection.createStatement()) {
        statement.execute(
            "INSERT INTO vsum_view (id, vsum_id, file_storage_id) VALUES (10, 1, 1), (11, 1, 2),"
                + " (12, 1, 3), (13, 2, 1)");
      }

      ScriptUtils.executeSqlScript(connection, migrationWithoutDeferral());

      Map<Long, String> names = new LinkedHashMap<>();
      try (Statement query = connection.createStatement();
          ResultSet rows =
              query.executeQuery("SELECT id, name, description FROM vsum_view ORDER BY id")) {
        while (rows.next()) {
          names.put(rows.getLong("id"), rows.getString("name"));
          assertThat(rows.getString("description")).isNull();
        }
      }
      assertThat(names)
          .containsExactly(
              Map.entry(10L, "Brake (10)"),
              Map.entry(11L, "Brake (11)"),
              Map.entry(12L, "notes.txt (12)"),
              Map.entry(13L, "Brake (13)"));

      try (Statement statement = connection.createStatement()) {
        statement.execute(
            "INSERT INTO vsum_view (vsum_id, file_storage_id, name, description)"
                + " VALUES (2, 1, 'Brake (10)', 'same name in another VSUM')");
        assertThatThrownBy(
                () ->
                    statement.execute(
                        "INSERT INTO vsum_view (vsum_id, file_storage_id, name)"
                            + " VALUES (1, 1, 'Brake (10)')"))
            .isInstanceOf(SQLException.class)
            .satisfies(
                e -> assertThat(e.getMessage()).containsIgnoringCase("uk_vsum_view_vsum_name"));
        assertThatThrownBy(
                () ->
                    statement.execute(
                        "INSERT INTO vsum_view (vsum_id, file_storage_id) VALUES (1, 1)"))
            .isInstanceOf(SQLException.class)
            .satisfies(e -> assertThat(e.getMessage()).containsIgnoringCase("name"));
      }
    }
  }

  /** The migration script with the clause H2 can not parse removed. */
  private static ByteArrayResource migrationWithoutDeferral() throws IOException {
    String script =
        new ClassPathResource("db/migration/V22__VSUM_VIEW_NAME.sql")
            .getContentAsString(StandardCharsets.UTF_8);
    assertThat(script).contains(DEFERRED);
    return new ByteArrayResource(
        script.replace(DEFERRED, "").getBytes(StandardCharsets.UTF_8), "V22 without deferral");
  }
}
