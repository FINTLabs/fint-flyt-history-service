package no.novari.flyt.instance.model.entities

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class InstanceObjectTest {
    @Test
    fun `instance object has empty mutable maps by default`() {
        val instance = InstanceObject()

        assertThat(instance.valuePerKey).isEmpty()
        assertThat(instance.objectCollectionPerKey).isEmpty()

        instance.valuePerKey["status"] = "received"
        assertThat(instance.valuePerKey).containsEntry("status", "received")
    }

    @Test
    fun `object collections retain nested mutable instances`() {
        val instance = InstanceObject()
        val collection = InstanceObjectCollection()
        val nestedInstance = InstanceObject()

        instance.objectCollectionPerKey["children"] = collection
        collection.objects.add(nestedInstance)
        nestedInstance.valuePerKey["name"] = "child"

        assertThat(instance.objectCollectionPerKey["children"]).isSameAs(collection)
        assertThat(collection.objects).containsExactly(nestedInstance)
        assertThat(collection.objects.single().valuePerKey).containsEntry("name", "child")
    }
}
