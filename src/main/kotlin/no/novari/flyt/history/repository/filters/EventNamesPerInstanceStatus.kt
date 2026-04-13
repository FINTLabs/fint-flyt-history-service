package no.novari.flyt.history.repository.filters

data class EventNamesPerInstanceStatus(
    val inProgressStatusEventNames: Collection<String>? = null,
    val transferredStatusEventNames: Collection<String>? = null,
    val abortedStatusEventNames: Collection<String>? = null,
    val failedStatusEventNames: Collection<String>? = null,
    val allStatusEventNames: Collection<String>? = null,
) {
    companion object {
        @JvmStatic
        fun builder() = Builder()
    }

    class Builder {
        private var inProgressStatusEventNames: Collection<String>? = null
        private var transferredStatusEventNames: Collection<String>? = null
        private var abortedStatusEventNames: Collection<String>? = null
        private var failedStatusEventNames: Collection<String>? = null
        private var allStatusEventNames: Collection<String>? = null

        fun inProgressStatusEventNames(inProgressStatusEventNames: Collection<String>?) =
            apply {
                this.inProgressStatusEventNames = inProgressStatusEventNames
            }

        fun transferredStatusEventNames(transferredStatusEventNames: Collection<String>?) =
            apply {
                this.transferredStatusEventNames = transferredStatusEventNames
            }

        fun abortedStatusEventNames(abortedStatusEventNames: Collection<String>?) =
            apply {
                this.abortedStatusEventNames = abortedStatusEventNames
            }

        fun failedStatusEventNames(failedStatusEventNames: Collection<String>?) =
            apply {
                this.failedStatusEventNames = failedStatusEventNames
            }

        fun allStatusEventNames(allStatusEventNames: Collection<String>?) =
            apply {
                this.allStatusEventNames = allStatusEventNames
            }

        fun build() =
            EventNamesPerInstanceStatus(
                inProgressStatusEventNames = inProgressStatusEventNames,
                transferredStatusEventNames = transferredStatusEventNames,
                abortedStatusEventNames = abortedStatusEventNames,
                failedStatusEventNames = failedStatusEventNames,
                allStatusEventNames = allStatusEventNames,
            )
    }
}
