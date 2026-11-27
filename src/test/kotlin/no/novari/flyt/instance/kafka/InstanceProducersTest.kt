package no.novari.flyt.instance.kafka

import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.actor.ActorHeader
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.instance.retryHeaders
import no.novari.flyt.instance.retryInstance
import no.novari.flyt.kafka.instanceflow.headers.instanceFlowHeaders
import no.novari.kafka.starter.topic.EventTopicName
import org.apache.kafka.clients.producer.ProducerRecord
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.AuditorAware
import org.springframework.kafka.KafkaException
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture

class InstanceProducersTest {
    private val future = CompletableFuture<SendResult<String, InstanceObjectDto>>()
    private val template: KafkaTemplate<String, InstanceObjectDto> =
        mock { on { send(any<ProducerRecord<String, InstanceObjectDto>>()) } doReturn future }

    @Test
    fun `instance-deleted is a tombstone without key, with the flow headers`() {
        InstanceDeletedEventProducerService(template, topic("instance-deleted")).publish(retryHeaders())

        val record = sent()
        assertThat(record.topic()).isEqualTo("org.flyt.event.instance-deleted")
        assertThat(record.key()).isNull()
        assertThat(record.value()).isNull()
        assertThat(record.headers().instanceFlowHeaders()).isEqualTo(retryHeaders())
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `instance-requested-for-retry carries the current actor, or System without one`(user: Boolean) {
        val actor = if (user) Actor.User(UUID.randomUUID()) else null
        retryProducer(AuditorAware { Optional.ofNullable(actor) }).publish(retryHeaders(), retryInstance())

        val record = sent()
        assertThat(record.value()).isEqualTo(retryInstance())
        assertThat(ActorHeader.fromHeaderValue(record.headers().lastHeader(ActorHeader.HEADER_NAME).value()))
            .isEqualTo(actor ?: Actor.System)
    }

    @Test
    fun `instance-requested-for-retry returns before the acknowledgement`() {
        retryProducer().publish(retryHeaders(), retryInstance())

        assertThat(future.isDone).isFalse()
        future.completeExceptionally(KafkaException("Asynchronous delivery failure"))
    }

    @Test
    fun `a synchronous send failure propagates`() {
        whenever(template.send(any<ProducerRecord<String, InstanceObjectDto>>())).thenThrow(KafkaException("down"))

        assertThatThrownBy {
            retryProducer().publish(
                retryHeaders(),
                retryInstance(),
            )
        }.isInstanceOf(KafkaException::class.java)
    }

    @Test
    fun `instance-registered waits for the acknowledgement`() {
        future.completeExceptionally(KafkaException("not acknowledged"))

        assertThatThrownBy {
            InstanceRegisteredEventProducerService(
                template,
                topic("instance-registered"),
            ).publish(retryHeaders(), retryInstance())
        }.hasRootCauseInstanceOf(KafkaException::class.java)
    }

    private fun retryProducer(auditorAware: AuditorAware<Actor> = AuditorAware { Optional.of(Actor.System) }) =
        InstanceRequestedForRetryEventProducerService(template, topic("instance-requested-for-retry"), auditorAware)

    private fun topic(name: String): EventTopicName = mock { on { this.name } doReturn "org.flyt.event.$name" }

    private fun sent(): ProducerRecord<String, InstanceObjectDto> {
        val record = argumentCaptor<ProducerRecord<String, InstanceObjectDto>>()
        verify(template).send(record.capture())
        return record.firstValue
    }
}
