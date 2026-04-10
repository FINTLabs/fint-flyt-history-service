package no.novari.flyt.history.repository.filters

data class InstanceStorageStatusQueryFilter(
    val instanceStorageStatusNames: Collection<String>? = null,
    val neverStored: Boolean? = null,
) {
    companion object {
        @JvmField
        val EMPTY = InstanceStorageStatusQueryFilter()
    }
}
