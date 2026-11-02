package tools.vitruv.methodologist.vsum.model.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * Applies the migration that adds the error code and problems of failed builds to the {@code
 * vsum_build} table of {@code V16}, on an in-memory H2 database in PostgreSQL mode. The earlier
 * migrations use PostgreSQL features H2 lacks, so the tables {@code vsum_build} references are
 * stubbed.
 */
class VsumBuildMigrationTest {

  private static final String URL =
      "jdbc:h2:mem:vsum-build-migration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";

  @Test
  void addsTheErrorCodeAndProblemsColumns() throws SQLException {
    try (Connection connection = DriverManager.getConnection(URL, "sa", "")) {
      try (Statement statement = connection.createStatement()) {
        statement.execute("CREATE TABLE usr (id BIGINT PRIMARY KEY)");
        statement.execute("CREATE TABLE vsum (id BIGINT PRIMARY KEY)");
        statement.execute("CREATE TABLE file_storage (id BIGINT PRIMARY KEY)");
        statement.execute("INSERT INTO usr (id) VALUES (1)");
        statement.execute("INSERT INTO vsum (id) VALUES (1)");
      }
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/migration/V16__VSUM_BUILD.sql"));
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/migration/V18__VSUM_BUILD_PROBLEMS.sql"));

      try (PreparedStatement insert =
          connection.prepareStatement(
              "INSERT INTO vsum_build (vsum_id, requested_by_id, fingerprint, status, error_code,"
                  + " problems) VALUES (1, 1, 'f', 'FAILED', ?, ? FORMAT JSON)")) {
        insert.setString(1, "VSUM_BUILD_FAILED");
        insert.setString(2, "[{\"source\":\"REACTIONS\",\"message\":\"m\"}]");
        assertThat(insert.executeUpdate()).isEqualTo(1);
      }
      try (Statement query = connection.createStatement();
          ResultSet row = query.executeQuery("SELECT error_code, problems FROM vsum_build")) {
        assertThat(row.next()).isTrue();
        assertThat(row.getString("error_code")).isEqualTo("VSUM_BUILD_FAILED");
        assertThat(row.getString("problems")).contains("\"source\":\"REACTIONS\"");
      }
    }
  }
}
