package no.novari.flyt.instance.kafka

import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.actor.ActorHeader
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.instance.outbox.outboxEvent
import no.novari.flyt.kafka.instanceflow.producing.InstanceFlowProducerRecord
import no.novari.flyt.kafka.instanceflow.producing.InstanceFlowTemplate
import no.novari.flyt.kafka.instanceflow.producing.InstanceFlowTemplateFactory
import no.novari.kafka.OriginHeaderProducerInterceptor
import no.novari.kafka.topic.EventTopicService
import no.novari.kafka.topic.configuration.EventCleanupFrequency
import no.novari.kafka.topic.configuration.EventTopicConfiguration
import no.novari.kafka.topic.name.EventTopicNameParameters
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.AuditorAware
import org.springframework.kafka.KafkaException
import org.springframework.kafka.support.SendResult
import java.time.Duration
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class InstanceRequestedForRetryEventProducerServiceTest {
    private val template: InstanceFlowTemplate<InstanceObjectDto> = mock()
    private val factory: InstanceFlowTemplateFactory =
        mock {
            on { createTemplate(InstanceObjectDto::class.java) }.thenReturn(template)
        }
    private val topics: EventTopicService = mock()
    private val fixture = outboxEvent()

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `publication preserves saved graph flow headers origin and current actor or system fallback`(user: Boolean) {
        val actor = if (user) Actor.User(UUID.randomUUID()) else null
        whenever(template.send(any<InstanceFlowProducerRecord<InstanceObjectDto>>()))
            .thenReturn(CompletableFuture.completedFuture(mock()))
        producer(actor).publish(fixture.headers, fixture.instance)
        val record = argumentCaptor<InstanceFlowProducerRecord<InstanceObjectDto>>()
        verify(template).send(record.capture())
        assertThat((record.firstValue.topicNameParameters as EventTopicNameParameters).eventName)
            .isEqualTo("instance-requested-for-retry")
        assertThat(record.firstValue.key).isNull()
        assertThat(record.firstValue.value).isSameAs(fixture.instance)
        assertThat(record.firstValue.instanceFlowHeaders).isEqualTo(fixture.headers)
        assertThat(
            ActorHeader.fromHeaderValue(
                record.firstValue
                    .additionalHeaders
                    .lastHeader(ActorHeader.HEADER_NAME)
                    .value(),
            ),
        ).isEqualTo(actor ?: Actor.System)
        assertThat(
            record.firstValue.additionalHeaders.lastHeader(
                OriginHeaderProducerInterceptor.ORIGIN_APPLICATION_ID_RECORD_HEADER,
            ),
        ).isNull()
        val topic = argumentCaptor<EventTopicNameParameters>()
        val configuration = argumentCaptor<EventTopicConfiguration>()
        verify(topics).createOrModifyTopic(topic.capture(), configuration.capture())
        assertThat(topic.firstValue.eventName).isEqualTo("instance-requested-for-retry")
        assertThat(configuration.firstValue.partitions).isEqualTo(1)
        assertThat(configuration.firstValue.retentionTime).isEqualTo(Duration.ofDays(4))
        assertThat(configuration.firstValue.cleanupFrequency).isEqualTo(EventCleanupFrequency.NORMAL)
    }

    @Test
    fun `publication returns before acknowledgement without turning later failure into a synchronous error`() {
        val acknowledgement = CompletableFuture<SendResult<String, InstanceObjectDto>>()
        whenever(template.send(any<InstanceFlowProducerRecord<InstanceObjectDto>>())).thenReturn(acknowledgement)
        val producer = producer()
        Executors.newSingleThreadExecutor().use { executor ->
            val call = executor.submit { producer.publish(fixture.headers, fixture.instance) }
            try {
                call.get(5, TimeUnit.SECONDS)
                assertThat(acknowledgement.isDone).isFalse()
            } finally {
                acknowledgement.completeExceptionally(KafkaException("Delayed delivery failure"))
            }
        }
        assertThat(acknowledgement.isCompletedExceptionally).isTrue()
        verify(template).send(any())
    }

    @Test
    fun `synchronous send failures propagate`() {
        val failure = KafkaException("Send failed before returning")
        doAnswer { throw failure }.whenever(template).send(any<InstanceFlowProducerRecord<InstanceObjectDto>>())
        assertThatThrownBy { producer().publish(fixture.headers, fixture.instance) }.isSameAs(failure)
    }

    private fun producer(actor: Actor? = null) =
        InstanceRequestedForRetryEventProducerService(
            factory,
            topics,
            AuditorAware { Optional.ofNullable(actor) },
            Duration.ofDays(4),
        )
}
