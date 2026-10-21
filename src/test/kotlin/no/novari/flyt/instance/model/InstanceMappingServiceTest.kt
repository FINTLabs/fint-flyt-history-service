package no.novari.flyt.instance.model

import no.novari.flyt.audit.entity.CreatedAuditedEntity
import no.novari.flyt.instance.model.dtos.InstanceObjectDto
import no.novari.flyt.instance.model.entities.InstanceObject
import no.novari.flyt.instance.model.entities.InstanceObjectCollection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Date

class InstanceMappingServiceTest {
    private val mapper = InstanceMappingService()

    @Test
    fun `maps empty objects and empty collections in both directions`() {
        val source = InstanceObjectDto(objectCollectionPerKey = mutableMapOf("empty" to emptyList()))
        val entity = mapper.toInstanceObject(source)
        assertThat(entity.id).isNull()
        assertThat(entity.createdAt).isNull()
        assertThat(entity.valuePerKey).isEmpty()
        assertThat(entity.objectCollectionPerKey.getValue("empty").objects).isEmpty()
        assertThat(mapper.toInstanceObjectDto(entity)).isEqualTo(source)
        assertThat(
            mapper.toInstanceObjectDto(mapper.toInstanceObject(InstanceObjectDto())),
        ).isEqualTo(InstanceObjectDto())
    }

    @Test
    fun `maps nested DTO values while ignoring incoming identity and audit timestamps at every level`() {
        val date = Date.from(Instant.parse("2000-01-01T00:00:00Z"))
        val grandchild =
            InstanceObjectDto(id = 3L, valuePerKey = mutableMapOf("level" to "grandchild"), createdAt = date)
        val child =
            InstanceObjectDto(
                id = 2L,
                valuePerKey = mutableMapOf("level" to "child"),
                objectCollectionPerKey = mutableMapOf("children" to listOf(grandchild)),
                createdAt = date,
            )
        val root =
            InstanceObjectDto(
                id = 1L,
                valuePerKey = mutableMapOf("level" to "root"),
                objectCollectionPerKey = mutableMapOf("children" to listOf(child)),
                createdAt = date,
            )
        val mappedRoot = mapper.toInstanceObject(root)
        val mappedChild =
            mappedRoot.objectCollectionPerKey
                .getValue("children")
                .objects
                .single()
        val mappedGrandchild =
            mappedChild.objectCollectionPerKey
                .getValue("children")
                .objects
                .single()
        listOf(mappedRoot, mappedChild, mappedGrandchild).forEach { entity ->
            assertThat(entity.id).isNull()
            assertThat(entity.createdAt).isNull()
        }
        assertThat(mappedRoot.valuePerKey).containsEntry("level", "root")
        assertThat(mappedChild.valuePerKey).containsEntry("level", "child")
        assertThat(mappedGrandchild.valuePerKey).containsEntry("level", "grandchild")
    }

    @Test
    fun `maps nested entity identities values and timestamps to DTOs`() {
        val rootTime = Instant.parse("2026-06-17T10:00:00Z")
        val grandchild =
            InstanceObject(id = 3L, valuePerKey = mutableMapOf("level" to "grandchild"))
                .also { it.setCreatedAt(rootTime.plusSeconds(2)) }
        val child =
            InstanceObject(
                id = 2L,
                valuePerKey = mutableMapOf("level" to "child"),
                objectCollectionPerKey =
                    mutableMapOf("children" to InstanceObjectCollection(objects = mutableListOf(grandchild))),
            ).also { it.setCreatedAt(rootTime.plusSeconds(1)) }
        val root =
            InstanceObject(
                id = 1L,
                valuePerKey = mutableMapOf("level" to "root"),
                objectCollectionPerKey =
                    mutableMapOf(
                        "children" to InstanceObjectCollection(objects = mutableListOf(child)),
                    ),
            ).also { it.setCreatedAt(rootTime) }
        val result = mapper.toInstanceObjectDto(root)
        val resultChild = result.objectCollectionPerKey.getValue("children").single()
        val resultGrandchild = resultChild.objectCollectionPerKey.getValue("children").single()
        listOf(result, resultChild, resultGrandchild).forEachIndexed { index, dto ->
            assertThat(dto.id).isEqualTo(index.toLong() + 1)
            assertThat(dto.createdAt).isEqualTo(Date.from(rootTime.plusSeconds(index.toLong())))
        }
        assertThat(result.valuePerKey).containsEntry("level", "root")
        assertThat(resultChild.valuePerKey).containsEntry("level", "child")
        assertThat(resultGrandchild.valuePerKey).containsEntry("level", "grandchild")
    }

    @Test
    fun `DTO to entity mapping creates independent maps and child collections`() {
        val child = InstanceObjectDto(valuePerKey = mutableMapOf("key" to "child"))
        val children = mutableListOf(child)
        val source =
            InstanceObjectDto(
                valuePerKey = mutableMapOf("key" to "root"),
                objectCollectionPerKey = mutableMapOf("children" to children),
            )
        val mapped = mapper.toInstanceObject(source)
        val mappedChildren = mapped.objectCollectionPerKey.getValue("children").objects
        val mappedChild = mappedChildren.single()
        source.valuePerKey["key"] = "changed source"
        child.valuePerKey["key"] = "changed source child"
        children.add(InstanceObjectDto())
        source.objectCollectionPerKey["another"] = emptyList()
        assertThat(mapped.valuePerKey).containsEntry("key", "root")
        assertThat(mappedChild.valuePerKey).containsEntry("key", "child")
        assertThat(mappedChildren).hasSize(1)
        assertThat(mapped.objectCollectionPerKey).containsOnlyKeys("children")
        mapped.valuePerKey["key"] = "changed entity"
        mappedChild.valuePerKey["key"] = "changed entity child"
        mappedChildren.clear()
        assertThat(source.valuePerKey).containsEntry("key", "changed source")
        assertThat(child.valuePerKey).containsEntry("key", "changed source child")
        assertThat(children).hasSize(2)
    }

    @Test
    fun `entity to DTO mapping creates independent maps and child collections`() {
        val child = InstanceObject(valuePerKey = mutableMapOf("key" to "child"))
        val children = InstanceObjectCollection(objects = mutableListOf(child))
        val source =
            InstanceObject(
                valuePerKey = mutableMapOf("key" to "root"),
                objectCollectionPerKey = mutableMapOf("children" to children),
            )
        val mapped = mapper.toInstanceObjectDto(source)
        val mappedChildren = mapped.objectCollectionPerKey.getValue("children")
        val mappedChild = mappedChildren.single()
        source.valuePerKey["key"] = "changed source"
        child.valuePerKey["key"] = "changed source child"
        children.objects.clear()
        source.objectCollectionPerKey["another"] = InstanceObjectCollection()
        assertThat(mapped.valuePerKey).containsEntry("key", "root")
        assertThat(mappedChild.valuePerKey).containsEntry("key", "child")
        assertThat(mappedChildren).hasSize(1)
        assertThat(mapped.objectCollectionPerKey).containsOnlyKeys("children")
        mapped.valuePerKey["key"] = "changed DTO"
        mappedChild.valuePerKey["key"] = "changed DTO child"
        mapped.objectCollectionPerKey.clear()
        assertThat(source.valuePerKey).containsEntry("key", "changed source")
        assertThat(child.valuePerKey).containsEntry("key", "changed source child")
        assertThat(source.objectCollectionPerKey).containsKeys("children", "another")
    }

    private fun InstanceObject.setCreatedAt(createdAt: Instant) {
        CreatedAuditedEntity::class.java
            .getDeclaredField("createdAt")
            .apply { isAccessible = true }
            .set(this, createdAt)
    }
}
