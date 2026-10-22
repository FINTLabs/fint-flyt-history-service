package no.novari.flyt.instance

data class InstanceReceivedMessageId(
    val topic: String,
    val partition: Int,
    val offset: Long,
) {
    init {
        require(topic.isNotBlank()) { "Topic must not be blank" }
        require(partition >= 0) { "Partition must not be negative" }
        require(offset >= 0) { "Offset must not be negative" }
    }
}
