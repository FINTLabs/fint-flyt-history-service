package no.novari.flyt.instance.config

import no.novari.flyt.instance.model.entities.INSTANCE_SCHEMA
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy
import org.hibernate.boot.model.naming.Identifier
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class InstanceSchemaNamingStrategyTest {
    private val jdbcEnvironment: JdbcEnvironment = mock()

    @Test
    fun `maps logical instance schema while preserving identifier quoting`() {
        val strategy = InstanceSchemaNamingStrategy(InstanceDatabaseProperties("afk_no_fint_flyt_instance_service_db"))
        val schema = strategy.toPhysicalSchemaName(Identifier.toIdentifier(INSTANCE_SCHEMA, true), jdbcEnvironment)
        assertThat(schema!!.text).isEqualTo("afk_no_fint_flyt_instance_service_db")
        assertThat(schema.isQuoted).isTrue()
    }

    @Test
    fun `history schema and naming rules work without instance configuration`() {
        val strategy = InstanceSchemaNamingStrategy(InstanceDatabaseProperties())
        val delegate = CamelCaseToUnderscoresNamingStrategy()
        val historySchema = Identifier.toIdentifier("afk_no_fint_flyt_history_service_db")
        assertThat(strategy.toPhysicalSchemaName(null, jdbcEnvironment)).isNull()
        assertThat(strategy.toPhysicalSchemaName(historySchema, jdbcEnvironment))
            .isEqualTo(delegate.toPhysicalSchemaName(historySchema, jdbcEnvironment))
        val column = Identifier.toIdentifier("sourceApplicationId")
        assertThat(strategy.toPhysicalColumnName(column, jdbcEnvironment))
            .isEqualTo(delegate.toPhysicalColumnName(column, jdbcEnvironment))
        val table = Identifier.toIdentifier("EventEntity")
        assertThat(strategy.toPhysicalTableName(table, jdbcEnvironment))
            .isEqualTo(delegate.toPhysicalTableName(table, jdbcEnvironment))
        val sequence = Identifier.toIdentifier("event_id_seq")
        assertThat(strategy.toPhysicalSequenceName(sequence, jdbcEnvironment))
            .isEqualTo(delegate.toPhysicalSequenceName(sequence, jdbcEnvironment))
    }
}
