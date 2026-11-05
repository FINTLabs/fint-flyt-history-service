package no.novari.flyt.instance.config

import no.novari.flyt.instance.model.entities.INSTANCE_SCHEMA
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy
import org.hibernate.boot.model.naming.Identifier
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment

class InstanceSchemaNamingStrategy(
    private val schema: String?,
) : CamelCaseToUnderscoresNamingStrategy() {
    override fun toPhysicalSchemaName(
        logicalName: Identifier?,
        jdbcEnvironment: JdbcEnvironment,
    ): Identifier? {
        if (logicalName?.text != INSTANCE_SCHEMA) {
            return super.toPhysicalSchemaName(logicalName, jdbcEnvironment)
        }
        requireNotNull(schema) {
            "Cannot derive the instance schema from the database user; set novari.flyt.instance.database.schema"
        }
        return Identifier.toIdentifier(schema, logicalName.isQuoted)
    }
}
