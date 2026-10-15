package com.dk.appblocker

/** Share one OS usage query across every rule/window. Never carries a sample across midnight/reboot. */
class UsageSampleCache(private val ttlMillis: Long = 5000) {
    private var sampleKey: String? = null
    private var sampledAt = Long.MIN_VALUE
    private var sample: Map<String, Long> = emptyMap()
    var loads: Long = 0
        private set

    @Synchronized fun get(key: String, elapsedNow: Long, load: () -> Map<String, Long>): Map<String, Long> {
        if (sampleKey != key || elapsedNow < sampledAt || elapsedNow - sampledAt >= ttlMillis) {
            sample = load().toMap(); sampleKey = key; sampledAt = elapsedNow; loads++
        }
        return sample
    }
    @Synchronized fun invalidate() { sampleKey = null }
}
