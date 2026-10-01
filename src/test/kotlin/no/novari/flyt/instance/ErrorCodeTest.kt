package no.novari.flyt.instance

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ErrorCodeTest {
    @Test
    fun `general system error code retains the instance service contract`() {
        assertThat(ErrorCode.GENERAL_SYSTEM_ERROR.getCode())
            .isEqualTo("FINT_FLYT_INSTANCE_SERVICE_GENERAL_SYSTEM_ERROR")
    }
}
