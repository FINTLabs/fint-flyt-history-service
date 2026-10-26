package no.novari.flyt.instance.outbox

import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class InstanceRegisteredOutboxCodecTest {
    @Test
    fun `snapshot preserves IDs headers and dates without changing the DTO contract`() {
        val mapper = outboxObjectMapper()
        val codec = InstanceRegisteredOutboxCodec(mapper)
        val event = outboxEvent()
        val restored = codec.decode(outboxClaim(event, codec))

        assertThat(restored).isEqualTo(event)
        assertThat(
            restored.instance.objectCollectionPerKey
                .getValue("children")
                .single()
                .id,
        ).isEqualTo(124L)
        assertThat(mapper.readValue("""{"id":123,"valuePerKey":{}}""", InstanceObjectDto::class.java).id).isNull()
    }
}
