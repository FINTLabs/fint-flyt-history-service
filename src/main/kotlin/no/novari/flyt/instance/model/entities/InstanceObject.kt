package no.novari.flyt.instance.model.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.CascadeType
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapKeyColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import no.novari.flyt.audit.entity.CreatedAuditedEntity
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "instance_object")
class InstanceObject(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    var id: Long? = null,
    @ElementCollection
    @CollectionTable(
        name = "instance_object_value_per_key",
        joinColumns = [JoinColumn(name = "instance_object_id")],
    )
    @MapKeyColumn(name = "key")
    @Column(name = "value")
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    var valuePerKey: MutableMap<String, String> = mutableMapOf(),
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "instance_object_id")
    @MapKeyColumn(name = "key")
    var objectCollectionPerKey: MutableMap<String, InstanceObjectCollection> = mutableMapOf(),
) : CreatedAuditedEntity()
