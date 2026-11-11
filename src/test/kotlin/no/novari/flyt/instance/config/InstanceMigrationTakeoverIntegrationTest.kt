package no.novari.flyt.instance.config

import jakarta.persistence.EntityManagerFactory
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.core.NestedExceptionUtils
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import javax.sql.DataSource

@Testcontainers(disabledWithoutDocker = true)
class InstanceMigrationTakeoverIntegrationTest {
    @Test
    fun `takes over existing Flyway history and data with DDL rights on the original schema`() {
        val admin = JdbcTemplate(dataSource(postgres.username, postgres.password))
        admin.execute("CREATE ROLE legacy_instance_owner LOGIN PASSWORD 'takeover_test'")
        admin.execute("CREATE ROLE history_migrator LOGIN PASSWORD 'takeover_test'")
        admin.execute("CREATE SCHEMA legacy_instances AUTHORIZATION legacy_instance_owner")
        admin.execute("CREATE SCHEMA takeover_history AUTHORIZATION history_migrator")
        val legacyDataSource = dataSource("legacy_instance_owner", "takeover_test")
        Flyway
            .configure()
            .dataSource(legacyDataSource)
            .defaultSchema("legacy_instances")
            .locations("classpath:db/instance-migration")
            .target("3")
            .load()
            .migrate()
        val legacy = JdbcTemplate(legacyDataSource)
        val id =
            legacy.queryForObject(
                "INSERT INTO legacy_instances.instance_object DEFAULT VALUES RETURNING id",
                Long::class.java,
            )!!
        legacy.update(
            "INSERT INTO legacy_instances.instance_object_value_per_key (instance_object_id, key, value) VALUES (?, ?, ?)",
            id,
            "label",
            "existing instance",
        )
        val existingHistory =
            admin.queryForList(
                "SELECT * FROM legacy_instances.flyway_schema_history ORDER BY installed_rank",
            )
        admin.execute("GRANT USAGE ON SCHEMA legacy_instances TO history_migrator")
        admin.execute(
            "GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA legacy_instances TO history_migrator",
        )
        admin.execute("GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA legacy_instances TO history_migrator")

        val runner =
            instanceJpaContextRunner(postgres, "takeover_history").withPropertyValues(
                "spring.datasource.username=history_migrator",
                "spring.datasource.password=takeover_test",
                "novari.flyt.instance-functionality.enabled=true",
                "novari.flyt.instance.database.schema=legacy_instances",
            )
        runner.run { context ->
            assertThat(context).hasFailed()
            assertThat(NestedExceptionUtils.getMostSpecificCause(context.startupFailure!!))
                .hasMessageContaining("must be owner of table instance_object")
        }
        assertThat(admin.queryForList("SELECT * FROM legacy_instances.flyway_schema_history ORDER BY installed_rank"))
            .isEqualTo(existingHistory)

        // DML grants alone cannot run ALTER TABLE; the migration role must also inherit ownership rights.
        admin.execute("GRANT legacy_instance_owner TO history_migrator")
        runner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasSingleBean(DataSource::class.java)
            assertThat(context).hasSingleBean(EntityManagerFactory::class.java)
            val jdbc = JdbcTemplate(context.getBean(DataSource::class.java))
            assertThat(
                jdbc.queryForObject(
                    "SELECT value FROM legacy_instances.instance_object_value_per_key WHERE instance_object_id = ?",
                    String::class.java,
                    id,
                ),
            ).isEqualTo("existing instance")
            assertThat(
                jdbc.queryForObject(
                    "SELECT created_by->>'type' FROM legacy_instances.instance_object WHERE id = ?",
                    String::class.java,
                    id,
                ),
            ).isEqualTo("UNKNOWN")
            assertThat(
                jdbc.queryForObject("SELECT nextval('legacy_instances.instance_object_id_seq')", Long::class.java),
            ).isGreaterThan(id)
            jdbc.execute("ALTER TABLE legacy_instances.instance_object ADD COLUMN takeover_ddl_check text")
            jdbc.execute("ALTER TABLE legacy_instances.instance_object DROP COLUMN takeover_ddl_check")
            assertThat(
                jdbc.queryForObject(
                    "SELECT to_regclass('takeover_history.instance_received_offset')::text",
                    String::class.java,
                ),
            ).isEqualTo("instance_received_offset")
        }
        val migratedHistory =
            admin.queryForList(
                "SELECT * FROM legacy_instances.flyway_schema_history ORDER BY installed_rank",
            )
        assertThat(migratedHistory.take(existingHistory.size)).isEqualTo(existingHistory)
        assertThat(
            migratedHistory
                .filter {
                    it["version"] == "4"
                }.map { it["installed_by"] },
        ).containsExactly("history_migrator")

        runner.run { context -> assertThat(context).hasNotFailed() }
        assertThat(admin.queryForList("SELECT * FROM legacy_instances.flyway_schema_history ORDER BY installed_rank"))
            .isEqualTo(migratedHistory)
    }

    private fun dataSource(
        username: String,
        password: String,
    ) = DriverManagerDataSource(postgres.jdbcUrl, username, password)

    companion object {
        @JvmField
        @Container
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17")
    }
}
