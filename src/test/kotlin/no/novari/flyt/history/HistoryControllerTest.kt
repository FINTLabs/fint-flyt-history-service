package no.novari.flyt.history

import jakarta.validation.Validator
import jakarta.validation.ValidatorFactory
import no.novari.flyt.history.model.statistics.IntegrationStatisticsFilter
import no.novari.flyt.history.repository.projections.IntegrationStatisticsProjection
import no.novari.flyt.history.validation.ValidationErrorsFormattingService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.security.core.Authentication

class HistoryControllerTest {
    private lateinit var authorizationService: AuthorizationService
    private lateinit var eventService: EventService
    private lateinit var validatorFactory: ValidatorFactory
    private lateinit var validator: Validator
    private lateinit var controller: HistoryController

    @BeforeEach
    fun setup() {
        authorizationService = mock()
        eventService = mock()
        validatorFactory = mock()
        validator = mock()
        whenever(validatorFactory.validator).thenReturn(validator)
        controller =
            HistoryController(
                authorizationService,
                eventService,
                mock(),
                validatorFactory,
                mock<ValidationErrorsFormattingService>(),
            )
    }

    @Test
    fun `integration statistics authorizes database candidates before loading data`() {
        val authentication: Authentication = mock()
        val candidateSourceApplicationIds = setOf(1L, 2L, 3L)
        val authorizedSourceApplicationIds = setOf(1L, 3L)
        val filter = IntegrationStatisticsFilter.builder().build()
        val pageable = PageRequest.of(0, 10)
        val expected: Slice<IntegrationStatisticsProjection> =
            SliceImpl(
                listOf(mock()),
                pageable,
                false,
            )

        whenever(eventService.findDistinctSourceApplicationIds()).thenReturn(candidateSourceApplicationIds)
        whenever(
            authorizationService.getUserAuthorizedSourceApplicationIds(
                authentication,
                candidateSourceApplicationIds,
            ),
        ).thenReturn(authorizedSourceApplicationIds)
        whenever(
            eventService.getIntegrationStatistics(
                filter.copy(sourceApplicationIds = authorizedSourceApplicationIds),
                pageable,
            ),
        ).thenReturn(expected)

        val response = controller.getIntegrationStatistics(authentication, filter, pageable)

        assertThat(response.content).isEqualTo(expected.content)
        verify(eventService).findDistinctSourceApplicationIds()
        verify(authorizationService).getUserAuthorizedSourceApplicationIds(
            authentication,
            candidateSourceApplicationIds,
        )
        verify(eventService).getIntegrationStatistics(
            filter.copy(sourceApplicationIds = authorizedSourceApplicationIds),
            pageable,
        )
    }
}
