package no.novari.flyt.history

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.validation.Validation
import no.novari.flyt.audit.actor.Actor
import no.novari.flyt.audit.actor.ActorDisplayProperties
import no.novari.flyt.audit.actor.ActorDisplayResolver
import no.novari.flyt.history.mapping.EventMappingService
import no.novari.flyt.history.mapping.InstanceFlowHeadersMappingService
import no.novari.flyt.history.mapping.InstanceFlowSummariesFilterMappingService
import no.novari.flyt.history.mapping.InstanceFlowSummaryMappingService
import no.novari.flyt.history.mapping.IntegrationStatisticsFilterMappingService
import no.novari.flyt.history.model.action.InstanceStatusTransferredOverrideAction
import no.novari.flyt.history.model.action.ManuallyProcessedEventAction
import no.novari.flyt.history.model.action.ManuallyRejectedEventAction
import no.novari.flyt.history.model.event.EventCategorizationService
import no.novari.flyt.history.model.event.EventCategory
import no.novari.flyt.history.model.event.EventType
import no.novari.flyt.history.repository.EventRepository
import no.novari.flyt.history.repository.entities.EventEntity
import no.novari.flyt.history.repository.entities.InstanceFlowHeadersEmbeddable
import no.novari.flyt.history.validation.ValidationErrorsFormattingService
import no.novari.flyt.webresourceserver.security.AuthorityMappingService
import no.novari.flyt.webresourceserver.security.properties.InternalApiSecurityProperties
import no.novari.flyt.webresourceserver.security.user.UserClaim
import no.novari.flyt.webresourceserver.security.user.UserJwtConverter
import no.novari.flyt.webresourceserver.security.user.UserRoleAuthorityMappingService
import no.novari.flyt.webresourceserver.security.user.UserRoleFilteringService
import no.novari.flyt.webresourceserver.security.user.UserRoleHierarchyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.unit.DataSize
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingTestConfig::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ManualStatusEventCreatedByIntegrationTest {
    @Autowired
    lateinit var eventRepository: EventRepository

    private val clock: Clock = Clock.fixed(Instant.parse("2024-01-01T12:00:00Z"), ZoneOffset.UTC)
    private val objectMapper = jacksonObjectMapper().findAndRegisterModules()
    private lateinit var controller: HistoryController
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        eventRepository.deleteAll()
        controller = controller()
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(controller)
                .setMessageConverters(MappingJackson2HttpMessageConverter(objectMapper))
                .build()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `manually processed endpoint persists USER createdBy with oid`() {
        assertManualEndpointStoresUserActor(
            endpoint = "/api/intern/instance-flow-tracking/events/instance-manually-processed",
            body = manuallyProcessedAction(),
            expectedCategory = EventCategory.INSTANCE_MANUALLY_PROCESSED,
        )
    }

    @Test
    fun `manually rejected endpoint persists USER createdBy with oid`() {
        assertManualEndpointStoresUserActor(
            endpoint = "/api/intern/instance-flow-tracking/events/instance-manually-rejected",
            body = manuallyRejectedAction(),
            expectedCategory = EventCategory.INSTANCE_MANUALLY_REJECTED,
        )
    }

    @Test
    fun `status transferred override endpoint persists USER createdBy with oid`() {
        assertManualEndpointStoresUserActor(
            endpoint = "/api/intern/instance-flow-tracking/events/instance-status-overridden-as-transferred",
            body = transferredOverrideAction(),
            expectedCategory = EventCategory.INSTANCE_STATUS_OVERRIDDEN_AS_TRANSFERRED,
        )
    }

    private fun assertManualEndpointStoresUserActor(
        endpoint: String,
        body: Any,
        expectedCategory: EventCategory,
    ) {
        saveLatestErrorEvent()
        val oid = UUID.fromString("53134ef2-4480-46d6-99a3-1920d36ea333")
        val authentication = userAuthentication(oid)
        SecurityContextHolder.setContext(
            SecurityContextHolder.createEmptyContext().apply {
                this.authentication = authentication
            },
        )

        mockMvc
            .perform(
                post(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body))
                    .principal(authentication),
            ).andExpect(status().isOk)

        val eventsByName = eventRepository.findAll().associateBy { it.name }
        assertThat(eventsByName[expectedCategory.eventName]?.createdBy).isEqualTo(Actor.User(oid))
        assertThat(eventsByName[EventCategory.INSTANCE_MAPPING_ERROR.eventName]?.createdBy).isEqualTo(Actor.System)
    }

    private fun controller(): HistoryController {
        val instanceFlowHeadersMappingService = InstanceFlowHeadersMappingService()
        val eventCategorizationService = EventCategorizationService()
        val eventService =
            EventService(
                eventRepository = eventRepository,
                eventMappingService =
                    EventMappingService(
                        instanceFlowHeadersMappingService,
                        eventCategorizationService,
                        ActorDisplayResolver(
                            { oids -> oids.associateWith { it.toString() } },
                            ActorDisplayProperties(),
                        ),
                    ),
                instanceFlowHeadersMappingService = instanceFlowHeadersMappingService,
                instanceFlowSummariesFilterMappingService = mock<InstanceFlowSummariesFilterMappingService>(),
                instanceFlowSummaryMappingService = mock<InstanceFlowSummaryMappingService>(),
                integrationStatisticsFilterMappingService = mock<IntegrationStatisticsFilterMappingService>(),
                eventCategorizationService = eventCategorizationService,
            )
        val uuidService: UuidService = mock()
        whenever(uuidService.generateUuid()).thenReturn(UUID.fromString("7864b8da-04ba-4e62-a224-dbd938cfdc53"))

        return HistoryController(
            authorizationService = mock(),
            eventService = eventService,
            manualEventCreationService =
                ManualEventCreationService(
                    clock = clock,
                    uuidService = uuidService,
                    applicationId = "testApplicationId",
                    eventService = eventService,
                ),
            validatorFactory = Validation.buildDefaultValidatorFactory(),
            validationErrorsFormattingService = mock<ValidationErrorsFormattingService>(),
        )
    }

    private fun saveLatestErrorEvent() {
        eventRepository.saveAndFlush(
            EventEntity
                .builder()
                .instanceFlowHeaders(
                    InstanceFlowHeadersEmbeddable
                        .builder()
                        .sourceApplicationId(SOURCE_APPLICATION_ID)
                        .sourceApplicationIntegrationId(SOURCE_APPLICATION_INTEGRATION_ID)
                        .sourceApplicationInstanceId(SOURCE_APPLICATION_INSTANCE_ID)
                        .correlationId(UUID.fromString("97c50875-b5ef-4800-bacd-3e5123c2368f"))
                        .integrationId(100L)
                        .build(),
                ).name(EventCategory.INSTANCE_MAPPING_ERROR.eventName)
                .timestamp(OffsetDateTime.ofInstant(clock.instant().minusSeconds(60), ZoneOffset.UTC))
                .type(EventType.ERROR)
                .build(),
        )
    }

    private fun userAuthentication(oid: UUID): Authentication =
        UserJwtConverter(
            userRoleFilteringService = UserRoleFilteringService(InternalApiSecurityProperties()),
            userRoleHierarchyService = UserRoleHierarchyService(),
            userRoleAuthorityMappingService = UserRoleAuthorityMappingService(AuthorityMappingService()),
        ).convert(
            Jwt
                .withTokenValue("token")
                .header("alg", "none")
                .subject("internal-user")
                .claim(UserClaim.ORGANIZATION_ID.tokenClaimName, "test-org")
                .claim(UserClaim.OBJECT_IDENTIFIER.tokenClaimName, oid.toString())
                .build(),
        )

    private fun manuallyProcessedAction() =
        ManuallyProcessedEventAction(
            sourceApplicationId = SOURCE_APPLICATION_ID,
            sourceApplicationIntegrationId = SOURCE_APPLICATION_INTEGRATION_ID,
            sourceApplicationInstanceId = SOURCE_APPLICATION_INSTANCE_ID,
            archiveInstanceId = "archive-instance-1",
        )

    private fun manuallyRejectedAction() =
        ManuallyRejectedEventAction(
            sourceApplicationId = SOURCE_APPLICATION_ID,
            sourceApplicationIntegrationId = SOURCE_APPLICATION_INTEGRATION_ID,
            sourceApplicationInstanceId = SOURCE_APPLICATION_INSTANCE_ID,
        )

    private fun transferredOverrideAction() =
        InstanceStatusTransferredOverrideAction(
            sourceApplicationId = SOURCE_APPLICATION_ID,
            sourceApplicationIntegrationId = SOURCE_APPLICATION_INTEGRATION_ID,
            sourceApplicationInstanceId = SOURCE_APPLICATION_INSTANCE_ID,
        )

    companion object {
        private const val SOURCE_APPLICATION_ID = 1L
        private const val SOURCE_APPLICATION_INTEGRATION_ID = "sa-integration-1"
        private const val SOURCE_APPLICATION_INSTANCE_ID = "sa-instance-1"

        @JvmField
        @Container
        val postgreSQLContainer: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:17")
                .withUrlParam("reWriteBatchedInserts", "true")
                .withCreateContainerCmdModifier { createContainerCmd ->
                    requireNotNull(createContainerCmd.hostConfig)
                        .withCpuCount(2L)
                        .withMemory(DataSize.ofGigabytes(8).toBytes())
                }

        @JvmStatic
        @DynamicPropertySource
        fun postgreSQLProperties(registry: DynamicPropertyRegistry) {
            postgreSQLContainer.start()
            registry.add("fint.database.url", postgreSQLContainer::getJdbcUrl)
            registry.add("fint.database.username", postgreSQLContainer::getUsername)
            registry.add("fint.database.password", postgreSQLContainer::getPassword)
        }
    }
}
