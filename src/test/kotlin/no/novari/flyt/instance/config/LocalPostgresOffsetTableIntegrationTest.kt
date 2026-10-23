package no.novari.flyt.instance.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager

@Testcontainers(disabledWithoutDocker = true)
class LocalPostgresOffsetTableIntegrationTest {
    @Test
    fun `Flyway creates history schema and offset table from datasource configuration`() {
        val hikariConfig =
            HikariConfig().apply {
                jdbcUrl = postgreSQLContainer.jdbcUrl
                username = postgreSQLContainer.username
                password = postgreSQLContainer.password
                schema = "fintlabs_no"
            }
        HikariDataSource(hikariConfig).use { dataSource ->
            val flywayConfiguration = Flyway.configure()
            flywayConfiguration.dataSource(dataSource)
            flywayConfiguration.locations("classpath:db/migration")
            flywayConfiguration.load().migrate()
        }

        val connection =
            DriverManager.getConnection(
                postgreSQLContainer.jdbcUrl,
                postgreSQLContainer.username,
                postgreSQLContainer.password,
            )
        connection.use { connection ->
            val schemas = mutableSetOf<String>()
            connection.createStatement().use { statement ->
                val query =
                    """
                    SELECT schema_name
                    FROM information_schema.schemata
                    WHERE schema_name IN ('fintlabs_no', 'fintlabs_no_instance', 'instance_received_offset')
                    """.trimIndent()
                statement.executeQuery(query).use { resultSet ->
                    while (resultSet.next()) {
                        schemas.add(resultSet.getString("schema_name"))
                    }
                }
            }
            assertThat(schemas).containsExactly("fintlabs_no")

            connection.createStatement().use { statement ->
                val query =
                    """
                    SELECT relation.relname
                    FROM pg_class relation
                    JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
                    WHERE namespace.nspname = 'fintlabs_no'
                      AND relation.relname = 'instance_received_offset'
                      AND relation.relkind = 'r'
                    """.trimIndent()
                statement.executeQuery(query).use { resultSet ->
                    assertThat(resultSet.next()).isTrue()
                    assertThat(resultSet.getString("relname")).isEqualTo("instance_received_offset")
                    assertThat(resultSet.next()).isFalse()
                }
            }

            connection.createStatement().use { statement ->
                val query =
                    """
                    SELECT column_name
                    FROM information_schema.columns
                    WHERE table_schema = 'fintlabs_no'
                      AND table_name = 'instance_received_offset'
                    """.trimIndent()
                statement.executeQuery(query).use { resultSet ->
                    val columns = mutableSetOf<String>()
                    while (resultSet.next()) {
                        columns.add(resultSet.getString("column_name"))
                    }
                    assertThat(columns).containsExactlyInAnyOrder(
                        "topic_name",
                        "partition_id",
                        "next_offset",
                        "updated_at",
                        "cluster_id",
                        "topic_id",
                    )
                }
            }

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT 1 FROM fintlabs_no.instance_received_offset").use { resultSet ->
                    assertThat(resultSet.next()).isFalse()
                }
            }

            connection.metaData.getPrimaryKeys(null, "fintlabs_no", "instance_received_offset").use { resultSet ->
                val primaryKeyColumns = mutableSetOf<String>()
                while (resultSet.next()) {
                    primaryKeyColumns.add(resultSet.getString("COLUMN_NAME"))
                }
                assertThat(primaryKeyColumns).containsExactlyInAnyOrder("topic_name", "partition_id")
            }
        }
    }

    @Test
    fun `generation migration preserves existing V9 positions without inventing broker identity`() {
        val config =
            HikariConfig().apply {
                jdbcUrl = postgreSQLContainer.jdbcUrl
                username = postgreSQLContainer.username
                password = postgreSQLContainer.password
                schema = "offset_upgrade"
            }
        HikariDataSource(config).use { dataSource ->
            Flyway
                .configure()
                .dataSource(dataSource)
                .schemas("offset_upgrade")
                .target("9")
                .load()
                .migrate()
            val jdbc = JdbcTemplate(dataSource)
            jdbc.update(
                "INSERT INTO instance_received_offset (topic_name, partition_id, next_offset) VALUES (?, ?, ?)",
                "existing-instance-received",
                0,
                123L,
            )

            Flyway
                .configure()
                .dataSource(dataSource)
                .schemas("offset_upgrade")
                .load()
                .migrate()

            val position = jdbc.queryForMap("SELECT * FROM instance_received_offset")
            assertThat(position["topic_name"]).isEqualTo("existing-instance-received")
            assertThat(position["next_offset"]).isEqualTo(123L)
            assertThat(position["cluster_id"]).isNull()
            assertThat(position["topic_id"]).isNull()
            assertThat(
                jdbc.queryForObject(
                    "SELECT to_regclass('offset_upgrade.processed_instance_received')",
                    String::class.java,
                ),
            ).isNull()
        }
    }

    companion object {
        @JvmField
        @Container
        val postgreSQLContainer: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:17")
    }
}
