package no.novari.flyt.instance.model.dtos

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import no.novari.flyt.history.Application
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import org.springframework.test.context.ContextConfiguration
import java.time.Instant
import java.util.Date

@JsonTest
@ContextConfiguration(classes = [Application::class])
class InstanceObjectDtoSerializationTest(
    @Autowired private val objectMapper: ObjectMapper,
) {
    @Test
    fun `deserializes instance received with recursive collections and default values`() {
        val instance = objectMapper.readValue(fixture("instance-received.json"), InstanceObjectDto::class.java)

        assertThat(instance.id).isNull()
        assertThat(instance.createdAt).isNull()
        assertThat(instance.valuePerKey)
            .containsEntry("caseNumber", "2026-123")
            .containsEntry("source", "test")

        val document = requireNotNull(instance.objectCollectionPerKey["documents"]).single()
        assertThat(document.valuePerKey)
            .containsEntry("documentType", "application")
            .containsEntry("fileName", "application.pdf")
        assertThat(document.objectCollectionPerKey).isEmpty()

        val emptyInstance = objectMapper.readValue("{}", InstanceObjectDto::class.java)
        assertThat(emptyInstance.valuePerKey).isEmpty()
        assertThat(emptyInstance.objectCollectionPerKey).isEmpty()
    }

    @Test
    fun `deserializes instance registered without accepting incoming id`() {
        val instance = objectMapper.readValue(fixture("instance-registered.json"), InstanceObjectDto::class.java)

        assertThat(instance.id).isNull()
        assertThat(instance.createdAt?.toInstant()).isEqualTo(Instant.parse("2026-01-15T10:20:30Z"))
        assertThat(instance.valuePerKey).containsEntry("caseNumber", "2026-123")
        assertThat(requireNotNull(instance.objectCollectionPerKey["documents"]).single().valuePerKey)
            .containsEntry("documentType", "application")
    }

    @Test
    fun `serializes instance registered with id and UTC date format`() {
        val document =
            InstanceObjectDto(
                valuePerKey = mutableMapOf("documentType" to "application", "fileName" to "application.pdf"),
            )
        val instance =
            InstanceObjectDto(
                id = 42L,
                valuePerKey = mutableMapOf("caseNumber" to "2026-123", "source" to "test"),
                objectCollectionPerKey = mutableMapOf("documents" to listOf(document)),
                createdAt = Date.from(Instant.parse("2026-01-15T10:20:30Z")),
            )

        val serialized = objectMapper.readTree(objectMapper.writeValueAsString(instance))
        val expected = objectMapper.readTree(fixture("instance-registered.json"))

        assertThat(serialized).isEqualTo(expected)
        assertThat(serialized["id"].asLong()).isEqualTo(42L)
        assertThat(serialized["createdAt"].asText()).isEqualTo("2026-01-15T10:20:30.000+00:00")
    }

    @Test
    fun `ignores unknown fields while preserving known fields`() {
        val fixtureWithUnknownField = objectMapper.readTree(fixture("instance-received.json")) as ObjectNode
        fixtureWithUnknownField.put("futureField", "ignored")

        val instance = objectMapper.treeToValue(fixtureWithUnknownField, InstanceObjectDto::class.java)

        assertThat(instance.valuePerKey).containsEntry("caseNumber", "2026-123")
        assertThat(requireNotNull(instance.objectCollectionPerKey["documents"]).single().valuePerKey)
            .containsEntry("fileName", "application.pdf")
    }

    private fun fixture(fileName: String): String =
        checkNotNull(javaClass.getResourceAsStream("/contracts/instance-object/$fileName"))
            .bufferedReader()
            .use { it.readText() }
}
