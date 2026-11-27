package no.novari.flyt.history.kafka

import no.novari.flyt.history.InstanceErrorEventService
import no.novari.flyt.history.InstanceInfoEventService
import no.novari.flyt.instance.InstanceDeletionService
import no.novari.flyt.instance.kafka.InstanceDeletedTopicConfiguration
import org.apache.kafka.clients.admin.NewTopic
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.beans.factory.getBean
import org.springframework.beans.factory.getBeansOfType
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.convert.ApplicationConversionService
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.kafka.config.KafkaListenerEndpointRegistry

/** Group ids, topics and topic config must stay as they were with fint-kafka 6. No broker is needed. */
class HistoryKafkaContractTest {
    // NovariKafkaAutoConfiguration is internal in kafka-starter, so it is loaded by name.
    private val runner =
        ApplicationContextRunner()
            .withInitializer { it.beanFactory.conversionService = ApplicationConversionService.getSharedInstance() }
            .withConfiguration(
                AutoConfigurations.of(
                    JacksonAutoConfiguration::class.java,
                    KafkaAutoConfiguration::class.java,
                    Class.forName("no.novari.kafka.starter.autoconfigure.NovariKafkaAutoConfiguration"),
                    Class.forName("no.novari.flyt.kafka.autoconfigure.FlytKafkaAutoConfiguration"),
                ),
            ).withUserConfiguration(
                HistoryEventTopics::class.java,
                HistoryTopicConfiguration::class.java,
                InstanceDeletedTopicConfiguration::class.java,
                HistoryEventListener::class.java,
                LegacyErrorEventListener::class.java,
                InstanceDeletedScrubberListener::class.java,
            ).withBean(InstanceInfoEventService::class.java, { mock() })
            .withBean(InstanceErrorEventService::class.java, { mock() })
            .withBean(InstanceDeletionService::class.java, { mock() })
            .withPropertyValues(
                "spring.kafka.listener.auto-startup=false",
                "spring.kafka.consumer.group-id=$GROUP",
                "novari.kafka.topic.org-id=$ORG",
                "novari.kafka.topic.domain-context=flyt",
                "novari.flyt.history-service.kafka.topic.instance-processing-events-retention-time=4d",
            )

    @Test
    fun `listeners keep their group ids and topics`() {
        runner.run {
            assertThat(it).hasNotFailed()
            assertThat(groupsByTopic(it)).containsExactlyInAnyOrderEntriesOf(
                (INFO_TOPICS + LEGACY_ERROR_TOPICS + "$ORG.flyt.event.error.instance-error").associateWith { GROUP } +
                    mapOf("$ORG.flyt.event.instance-deleted" to listOf(GROUP, "${GROUP}instance-deleted-scrubber")),
            )
        }
    }

    @Test
    fun `legacy error listeners and the receival error topic can be turned off`() {
        runner.withPropertyValues("novari.flyt.history-service.kafka.legacy-error-topic-listeners-enabled=false").run {
            assertThat(it).hasNotFailed().doesNotHaveBean(LegacyErrorEventListener::class.java)
            assertThat(groupsByTopic(it).keys).doesNotContainAnyElementsOf(LEGACY_ERROR_TOPICS)
            assertThat(it).doesNotHaveBean("instanceReceivalErrorTopicCreation")
        }
    }

    @Test
    fun `created topics keep 1 partition and the configured retention`() {
        runner.run {
            val topics = it.getBeansOfType<NewTopic>().values.associateBy(NewTopic::name)
            assertThat(topics.keys).containsExactlyInAnyOrder(
                "$ORG.flyt.event.error.instance-error",
                "$ORG.flyt.event.error.instance-receival-error",
                "$ORG.flyt.event.instance-deleted",
            )
            topics.values.forEach { topic ->
                assertThat(topic.numPartitions()).isEqualTo(1)
                assertThat(
                    topic.configs(),
                ).containsEntry("retention.ms", "345600000").containsEntry("cleanup.policy", "delete")
            }
        }
    }

    private fun groupsByTopic(context: AssertableApplicationContext): Map<String, Any> =
        context
            .getBean<KafkaListenerEndpointRegistry>()
            .listenerContainers
            .flatMap { container -> container.containerProperties.topics!!.map { it to container.groupId!! } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, groups) -> if (groups.size == 1) groups.single() else groups.sorted() }

    private companion object {
        const val ORG = "test-org"
        const val GROUP = "fint-flyt-history-service"
        val INFO_TOPICS =
            listOf(
                "instance-received",
                "instance-registered",
                "instance-requested-for-retry",
                "instance-mapped",
                "instance-dispatched",
            ).map { "$ORG.flyt.event.$it" }
        val LEGACY_ERROR_TOPICS =
            listOf(
                "instance-receival-error",
                "instance-registration-error",
                "instance-retry-request-error",
                "instance-mapping-error",
                "instance-dispatching-error",
            ).map { "$ORG.flyt.event.error.$it" }
    }
}
