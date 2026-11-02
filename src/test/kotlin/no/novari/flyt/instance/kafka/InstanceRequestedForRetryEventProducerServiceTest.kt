package no.novari.flyt.instance.kafka

import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.actor.ActorHeader
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.instance.outbox.outboxEvent
import no.novari.flyt.instance.outbox.outboxObjectMapper
import no.novari.flyt.kafka.instanceflow.headers.InstanceFlowHeadersMapper
import no.novari.kafka.OriginHeaderProducerInterceptor
import no.novari.kafka.topic.EventTopicService
import no.novari.kafka.topic.configuration.EventCleanupFrequency
import no.novari.kafka.topic.configuration.EventTopicConfiguration
import no.novari.kafka.topic.name.EventTopicNameParameters
import no.novari.kafka.topic.name.TopicNameService
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
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
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.kafka.support.serializer.JsonSerializer
import java.time.Duration
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class InstanceRequestedForRetryEventProducerServiceTest {
    private val template: KafkaTemplate<String, InstanceObjectDto> = mock()
    private val names: TopicNameService = mock { on { validateAndMapToTopicName(any()) }.thenReturn("retry-topic") }
    private val topics: EventTopicService = mock()
    private val mapper = InstanceFlowHeadersMapper(outboxObjectMapper())
    private val fixture = outboxEvent()

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `publication preserves saved graph flow headers origin and current actor or system fallback`(user: Boolean) {
        val actor = if (user) Actor.User(UUID.randomUUID()) else null
        whenever(template.send(any<ProducerRecord<String, InstanceObjectDto>>()))
            .thenReturn(CompletableFuture.completedFuture(mock()))
        producer(actor).publish(fixture.headers, fixture.instance)
        val record = argumentCaptor<ProducerRecord<String, InstanceObjectDto>>()
        verify(template).send(record.capture())
        assertThat(record.firstValue.topic()).isEqualTo("retry-topic")
        assertThat(record.firstValue.key()).isNull()
        assertThat(record.firstValue.value()).isSameAs(fixture.instance)
        assertThat(mapper.getInstanceFlowHeaders(record.firstValue.headers())).isEqualTo(fixture.headers)
        assertThat(
            ActorHeader.fromHeaderValue(
                record.firstValue
                    .headers()
                    .lastHeader(ActorHeader.HEADER_NAME)
                    .value(),
            ),
        ).isEqualTo(actor ?: Actor.System)
        assertThat(
            String(
                record.firstValue
                    .headers()
                    .lastHeader(
                        OriginHeaderProducerInterceptor.ORIGIN_APPLICATION_ID_RECORD_HEADER,
                    ).value(),
            ),
        ).isEqualTo("history-app")
        val topic = argumentCaptor<EventTopicNameParameters>()
        val configuration = argumentCaptor<EventTopicConfiguration>()
        verify(topics).createOrModifyTopic(topic.capture(), configuration.capture())
        assertThat(topic.firstValue.eventName).isEqualTo("instance-requested-for-retry")
        assertThat(configuration.firstValue.partitions).isEqualTo(1)
        assertThat(configuration.firstValue.retentionTime).isEqualTo(Duration.ofDays(4))
        assertThat(configuration.firstValue.cleanupFrequency).isEqualTo(EventCleanupFrequency.NORMAL)
    }

    @Test
    fun `publication waits for broker acknowledgement`() {
        val sent = CountDownLatch(1)
        val acknowledgement = CompletableFuture<SendResult<String, InstanceObjectDto>>()
        doAnswer {
            sent.countDown()
            acknowledgement
        }.whenever(template).send(any<ProducerRecord<String, InstanceObjectDto>>())
        val producer = producer()
        Executors.newSingleThreadExecutor().use { executor ->
            val call = executor.submit { producer.publish(fixture.headers, fixture.instance) }
            assertThat(sent.await(5, TimeUnit.SECONDS)).isTrue()
            assertThat(call.isDone).isFalse()
            acknowledgement.complete(mock())
            call.get(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `missing acknowledgement times out and failed acknowledgement propagates`() {
        val future = CompletableFuture<SendResult<String, InstanceObjectDto>>()
        whenever(template.send(any<ProducerRecord<String, InstanceObjectDto>>())).thenReturn(future)
        val producer = producer(timeout = Duration.ofMillis(30))
        assertThatThrownBy {
            producer.publish(
                fixture.headers,
                fixture.instance,
            )
        }.isInstanceOf(TimeoutException::class.java)
        val failure = IllegalStateException("broker failed")
        future.completeExceptionally(failure)
        assertThatThrownBy { producer.publish(fixture.headers, fixture.instance) }.hasCause(failure)
    }

    @Test
    fun `interruption is preserved`() {
        whenever(template.send(any<ProducerRecord<String, InstanceObjectDto>>())).thenReturn(CompletableFuture())
        val producer = producer()
        Thread.currentThread().interrupt()
        try {
            assertThatThrownBy {
                producer.publish(fixture.headers, fixture.instance)
            }.isInstanceOf(InterruptedException::class.java)
            assertThat(Thread.currentThread().isInterrupted).isTrue()
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `dedicated producer bounds blocking and delivery without modifying shared configuration`() {
        val config =
            ProducerConfig(
                mapOf(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
                ),
            )
        val original = config.originals().toMap()
        val factory =
            InstanceRetryKafkaConfiguration().instanceRetryProducerFactory(
                config,
                outboxObjectMapper(),
                InstanceRetryProperties(Duration.ofSeconds(2)),
            )
        try {
            assertThat(factory.configurationProperties[ProducerConfig.MAX_BLOCK_MS_CONFIG]).isEqualTo(2000L)
            assertThat(factory.configurationProperties[ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG]).isEqualTo(2000)
            assertThat(factory.configurationProperties[ProducerConfig.ACKS_CONFIG]).isEqualTo("all")
            assertThat(config.originals()).isEqualTo(original)
        } finally {
            factory.destroy()
        }
    }

    @ParameterizedTest
    @ValueSource(longs = [0, -1, 2, 2147483648])
    fun `invalid send timeout is rejected`(milliseconds: Long) {
        assertThatThrownBy {
            InstanceRetryProperties(Duration.ofMillis(milliseconds))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun producer(
        actor: Actor? = null,
        timeout: Duration = Duration.ofSeconds(5),
    ) = InstanceRequestedForRetryEventProducerService(
        template,
        mapper,
        names,
        topics,
        AuditorAware { Optional.ofNullable(actor) },
        InstanceRetryProperties(timeout),
        "history-app",
        Duration.ofDays(4),
    )
}
