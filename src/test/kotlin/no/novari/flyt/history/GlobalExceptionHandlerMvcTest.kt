package no.novari.flyt.history

import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

class GlobalExceptionHandlerMvcTest {
    private val mockMvc: MockMvc =
        MockMvcBuilders
            .standaloneSetup(ProbeController())
            .setControllerAdvice(GlobalExceptionHandler(mock()))
            .build()

    @Test
    fun `path variable type mismatch returns 400 problem detail`() {
        mockMvc
            .perform(get("/probe/not-a-number"))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value("Invalid value for request parameter 'id'"))
    }

    @RestController
    private class ProbeController {
        @GetMapping("/probe/{id}")
        fun path(
            @PathVariable id: Long,
        ): Long = id
    }
}
