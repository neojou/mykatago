package com.neojou.mykatago.settings

interface SettingsStore {
    suspend fun load(): AppSettings
    suspend fun save(settings: AppSettings)
}

class MemorySettingsStore(initial: AppSettings = AppSettings()) : SettingsStore {
    var current: AppSettings = initial
        private set

    override suspend fun load(): AppSettings = current

    override suspend fun save(settings: AppSettings) {
        current = settings
    }
}
