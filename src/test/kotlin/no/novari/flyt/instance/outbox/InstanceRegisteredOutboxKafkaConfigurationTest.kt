package no.novari.flyt.instance.outbox

import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.kafka.health.ProducerFailureTracker
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.errors.SerializationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import java.time.Duration
import java.util.concurrent.CompletableFuture

@ExtendWith(OutputCaptureExtension::class)
class InstanceRegisteredOutboxKafkaConfigurationTest {
    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `send errors support optional health tracking without logging sensitive data`(
        trackHealth: Boolean,
        output: CapturedOutput,
    ) {
        val producer: Producer<String, InstanceObjectDto> = mock()
        val factory: DefaultKafkaProducerFactory<String, InstanceObjectDto> = mock()
        whenever(factory.createProducer()).thenReturn(producer)
        val failure = SerializationException("confidential instance content")
        whenever(producer.send(any(), any())).thenAnswer {
            it.getArgument<Callback>(1).onCompletion(null, failure)
            CompletableFuture.failedFuture<RecordMetadata>(failure)
        }
        val tracker = ProducerFailureTracker(1, Duration.ofMinutes(1))
        val template =
            InstanceRegisteredOutboxKafkaConfiguration().instanceRegisteredOutboxKafkaTemplate(
                factory,
                if (trackHealth) tracker else null,
            )
        try {
            assertThatThrownBy {
                template.send(ProducerRecord("instance-registered-test", outboxEvent().instance))
            }.hasRootCauseInstanceOf(SerializationException::class.java)

            assertThat(tracker.isUnhealthy()).isEqualTo(trackHealth)
            assertThat(output.all).doesNotContain("confidential instance content")
            if (trackHealth) {
                assertThat(output.all).contains("Kafka producer send failed for topic instance-registered-test")
            }
        } finally {
            template.destroy()
        }
    }
}
