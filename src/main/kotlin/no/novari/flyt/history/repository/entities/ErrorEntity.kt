package no.novari.flyt.history.repository.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapKeyColumn
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table

@Entity
@Table(name = "error")
class ErrorEntity(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "error_id_seq_gen")
    @field:SequenceGenerator(name = "error_id_seq_gen", sequenceName = "error_id_seq", allocationSize = 500)
    @get:JsonIgnore
    var id: Long = 0,
    var errorCode: String? = null,
    @field:ElementCollection
    @field:CollectionTable(
        name = "error_args",
        joinColumns = [JoinColumn(name = "error_id", referencedColumnName = "id")],
    )
    @field:MapKeyColumn(name = "map_key")
    @field:Column(name = "\"value\"", columnDefinition = "text")
    var args: Map<String, String>? = null,
) {
    companion object {
        @JvmStatic
        fun builder() = Builder()
    }

    class Builder {
        private var id: Long = 0
        private var errorCode: String? = null
        private var args: Map<String, String>? = null

        fun id(id: Long) = apply { this.id = id }

        fun errorCode(errorCode: String?) = apply { this.errorCode = errorCode }

        fun args(args: Map<String, String>?) = apply { this.args = args }

        fun build() =
            ErrorEntity(
                id = id,
                errorCode = errorCode,
                args = args,
            )
    }
}
