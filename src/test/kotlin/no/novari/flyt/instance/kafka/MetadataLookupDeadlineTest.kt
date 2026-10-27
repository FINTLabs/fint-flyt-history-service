package no.novari.flyt.instance.kafka

import org.apache.kafka.common.KafkaFuture
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class MetadataLookupDeadlineTest {
    @Test
    fun `all futures and closing share a single budget`() {
        var now = 0L
        val deadline = MetadataLookupDeadline(Duration.ofSeconds(10)) { now }
        val future: KafkaFuture<String> = mock()
        whenever(future.get(any(), eq(TimeUnit.NANOSECONDS))).thenAnswer {
            now += Duration.ofSeconds(2).toNanos()
            "metadata"
        }

        repeat(3) { assertThat(deadline.await(future)).isEqualTo("metadata") }
        val timeout = argumentCaptor<Long>()
        verify(future, times(3)).get(timeout.capture(), eq(TimeUnit.NANOSECONDS))
        assertThat(timeout.allValues).containsExactly(
            Duration.ofSeconds(10).toNanos(),
            Duration.ofSeconds(8).toNanos(),
            Duration.ofSeconds(6).toNanos(),
        )
        assertThat(deadline.remaining()).isEqualTo(Duration.ofSeconds(4))

        now = Duration.ofSeconds(11).toNanos()
        assertThatThrownBy { deadline.await(future) }.isInstanceOf(TimeoutException::class.java)
        assertThat(deadline.remaining()).isEqualTo(Duration.ZERO)
        verify(future, times(3)).get(any(), eq(TimeUnit.NANOSECONDS))
    }
}
