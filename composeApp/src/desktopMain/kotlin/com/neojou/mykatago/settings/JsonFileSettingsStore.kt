package com.neojou.mykatago.settings

import com.neojou.mykatago.engine.KataGoPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class JsonFileSettingsStore(
    private val file: File = File(KataGoPaths.supportDir(), "settings.json"),
) : SettingsStore {
    override suspend fun load(): AppSettings = withContext(Dispatchers.IO) {
        if (!file.isFile) AppSettings()
        else runCatching { decodeSettings(file.readText()) }.getOrDefault(AppSettings())
    }

    override suspend fun save(settings: AppSettings) {
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            file.writeText(encodeSettings(settings))
        }
    }
}
