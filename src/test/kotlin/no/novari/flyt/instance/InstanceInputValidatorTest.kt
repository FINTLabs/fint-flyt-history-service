package no.novari.flyt.instance

import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeaders
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class InstanceInputValidatorTest {
    @Test
    fun `value and collection keys use the database limit at every graph level`() {
        val child = InstanceObjectDto(valuePerKey = mutableMapOf("x".repeat(256) to "value"))
        val root = InstanceObjectDto(objectCollectionPerKey = mutableMapOf("children" to listOf(child)))
        val valueFailure = assertThrows<InstanceInputException> { InstanceInputValidator.validate(root) }
        assertThat(valueFailure.code).isEqualTo(InstanceInputErrorCode.KEY_TOO_LONG)
        assertThat(valueFailure.fieldPath).isEqualTo("$.objectCollectionPerKey[0].objects[0].valuePerKey[0].key")

        child.valuePerKey.clear()
        child.objectCollectionPerKey["x".repeat(256)] = emptyList()
        val collectionFailure = assertThrows<InstanceInputException> { InstanceInputValidator.validate(root) }
        assertThat(collectionFailure.code).isEqualTo(InstanceInputErrorCode.KEY_TOO_LONG)
        assertThat(
            collectionFailure.fieldPath,
        ).isEqualTo("$.objectCollectionPerKey[0].objects[0].objectCollectionPerKey[0].key")
    }

    @Test
    fun `keys count Unicode codepoints and text values have no varchar limit`() {
        val key = "\uD83D\uDE00".repeat(255)
        InstanceInputValidator.validate(
            InstanceObjectDto(
                valuePerKey = mutableMapOf(key to "x".repeat(10000)),
                objectCollectionPerKey = mutableMapOf(key to emptyList()),
            ),
        )
        assertThrows<InstanceInputException> {
            InstanceInputValidator.validate(InstanceObjectDto(valuePerKey = mutableMapOf(key + "a" to "value")))
        }
    }

    @Test
    fun `PostgreSQL text cannot store null characters in keys or values`() {
        val instances =
            listOf(
                InstanceObjectDto(valuePerKey = mutableMapOf("secret\u0000key" to "value")),
                InstanceObjectDto(valuePerKey = mutableMapOf("key" to "secret\u0000value")),
                InstanceObjectDto(objectCollectionPerKey = mutableMapOf("secret\u0000key" to emptyList())),
            )
        instances.forEach { instance ->
            val failure = assertThrows<InstanceInputException> { InstanceInputValidator.validate(instance) }
            assertThat(failure.code).isEqualTo(InstanceInputErrorCode.TEXT_CONTAINS_NULL_CHARACTER)
            assertThat(failure.message).doesNotContain("secret")
            assertThat(failure.fieldPath).doesNotContain("secret")
        }
    }

    @Test
    fun `invalid history headers stop processing without becoming a graph input error`() {
        val headers =
            InstanceFlowHeaders
                .builder()
                .sourceApplicationId(1L)
                .sourceApplicationIntegrationId("integration")
                .sourceApplicationInstanceId("instance")
                .correlationId(UUID.randomUUID())
                .build()
        InstanceInputValidator.validateHeaders(headers)
        listOf(
            headers.toBuilder().sourceApplicationIntegrationId("x".repeat(256)).build(),
            headers.toBuilder().sourceApplicationInstanceId("x\u0000").build(),
            headers.toBuilder().archiveInstanceId("x".repeat(256)).build(),
        ).forEach { invalid ->
            assertThat(assertThrows<IllegalArgumentException> { InstanceInputValidator.validateHeaders(invalid) })
                .hasMessage("Invalid instance flow headers")
        }
    }
}
