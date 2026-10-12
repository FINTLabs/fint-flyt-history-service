package no.novari.flyt.instance.config

import no.novari.flyt.instance.model.entities.INSTANCE_SCHEMA
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy
import org.hibernate.boot.model.naming.Identifier
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment

class InstanceSchemaNamingStrategy(
    private val properties: InstanceDatabaseProperties,
) : CamelCaseToUnderscoresNamingStrategy() {
    override fun toPhysicalSchemaName(
        logicalName: Identifier?,
        jdbcEnvironment: JdbcEnvironment,
    ): Identifier? {
        if (logicalName?.text != INSTANCE_SCHEMA) {
            return super.toPhysicalSchemaName(logicalName, jdbcEnvironment)
        }
        require(properties.schema.isNotBlank()) {
            "novari.flyt.instance.database.schema must be configured when instance entities are included in the JPA metamodel"
        }
        return Identifier.toIdentifier(properties.schema, logicalName.isQuoted)
    }
}
