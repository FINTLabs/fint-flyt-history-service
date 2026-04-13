package no.novari.flyt.history

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UuidService {
    fun generateUuid(): UUID = UUID.randomUUID()
}
