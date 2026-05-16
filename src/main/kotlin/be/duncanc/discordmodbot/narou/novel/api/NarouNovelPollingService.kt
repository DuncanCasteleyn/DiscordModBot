package be.duncanc.discordmodbot.narou.novel.api

import be.duncanc.discordmodbot.narou.novel.api.persistence.NarouNovelAlertSettings
import be.duncanc.discordmodbot.narou.novel.api.persistence.NarouNovelAlertSettingsRepository
import be.duncanc.discordmodbot.narou.novel.api.persistence.NarouNovelSnapshot
import be.duncanc.discordmodbot.narou.novel.api.persistence.NarouNovelSnapshotRepository
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class NarouNovelPollingService(
    private val narouNovelApiClient: NarouNovelApiClient,
    private val narouNovelSnapshotRepository: NarouNovelSnapshotRepository,
    private val narouNovelAlertSettingsRepository: NarouNovelAlertSettingsRepository,
    private val jda: JDA
) {
    companion object {
        internal const val NOVEL_CODE = "n2267be"
        private const val NOVEL_URL = "https://ncode.syosetu.com/n2267be/"
        private val LOG = LoggerFactory.getLogger(NarouNovelPollingService::class.java)
    }

    @Scheduled(cron = $$"${discord-mod-bot.narou-novel-api.poll-cron:0/15 * * * * *}")
    fun pollNovel() {
        val payload = try {
            fetchPayload()
        } catch (exception: Exception) {
            LOG.warn("Failed to poll Narou novel API", exception)
            return
        }

        val snapshot = narouNovelSnapshotRepository.findById(NOVEL_CODE).orElse(null)
        if (snapshot == null) {
            narouNovelSnapshotRepository.save(
                NarouNovelSnapshot(
                    ncode = NOVEL_CODE,
                    generalLastup = payload.generalLastup!!,
                    generalAllNo = payload.generalAllNo!!,
                    length = payload.length!!,
                    time = payload.time!!,
                    novelUpdatedAt = payload.novelUpdatedAt!!,
                    updatedAt = payload.updatedAt!!
                )
            )
            initializeMissingBaselines(payload.length, payload.generalAllNo)
            return
        }

        snapshot.generalLastup = payload.generalLastup!!
        snapshot.generalAllNo = payload.generalAllNo!!
        snapshot.length = payload.length!!
        snapshot.time = payload.time!!
        snapshot.novelUpdatedAt = payload.novelUpdatedAt!!
        snapshot.updatedAt = payload.updatedAt!!
        narouNovelSnapshotRepository.save(snapshot)

        processAlerts(snapshot)
    }

    internal fun fetchPayload(): NarouNovelApiResponseEntry {
        return narouNovelApiClient.fetchNovel().firstOrNull {
            it.generalLastup != null &&
                it.generalAllNo != null &&
                it.length != null &&
                it.time != null &&
                it.novelUpdatedAt != null &&
                it.updatedAt != null
        } ?: throw IllegalStateException("Narou novel API response did not contain a novel payload")
    }

    private fun processAlerts(snapshot: NarouNovelSnapshot) {
        narouNovelAlertSettingsRepository.findAll().forEach { settings ->
            val updatedSettings = initializeOrRebaseBaselines(settings, snapshot)
            if (updatedSettings) {
                return@forEach
            }

            val lastAlertedLength = settings.lastAlertedLength ?: return@forEach
            val lastAlertedGeneralAllNo = settings.lastAlertedGeneralAllNo ?: return@forEach
            val lengthDelta = snapshot.length - lastAlertedLength
            val chapterDelta = snapshot.generalAllNo - lastAlertedGeneralAllNo
            val lengthTriggered = lengthDelta >= settings.lengthThreshold
            val chapterTriggered = chapterDelta > 0
            if (!lengthTriggered && !chapterTriggered) {
                return@forEach
            }

            val channelId = settings.channelId ?: return@forEach
            val channel = jda.getTextChannelById(channelId)
            if (channel == null) {
                LOG.warn(
                    "Narou novel alert channel {} for guild {} no longer exists",
                    settings.channelId,
                    settings.guildId
                )
                return@forEach
            }

            val message = buildAlertMessage(lengthTriggered, lengthDelta, chapterTriggered, chapterDelta, snapshot)
            if (lengthTriggered) {
                settings.lastAlertedLength = snapshot.length
            }
            if (chapterTriggered) {
                settings.lastAlertedGeneralAllNo = snapshot.generalAllNo
            }
            narouNovelAlertSettingsRepository.save(settings)

            sendAlertMessage(
                channel = channel,
                message = message,
                onSuccess = {},
                onFailure = { exception ->
                    LOG.warn("Failed to send Narou novel alert for guild {}", settings.guildId, exception)
                }
            )
        }
    }

    private fun initializeMissingBaselines(length: Long, generalAllNo: Int) {
        narouNovelAlertSettingsRepository.findAll().forEach { settings ->
            var changed = false
            if (settings.lastAlertedLength == null) {
                settings.lastAlertedLength = length
                changed = true
            }
            if (settings.lastAlertedGeneralAllNo == null) {
                settings.lastAlertedGeneralAllNo = generalAllNo
                changed = true
            }
            if (changed) {
                narouNovelAlertSettingsRepository.save(settings)
            }
        }
    }

    private fun initializeOrRebaseBaselines(settings: NarouNovelAlertSettings, snapshot: NarouNovelSnapshot): Boolean {
        var changed = false
        if (settings.lastAlertedLength == null || settings.lastAlertedLength!! > snapshot.length) {
            settings.lastAlertedLength = snapshot.length
            changed = true
        }
        if (settings.lastAlertedGeneralAllNo == null || settings.lastAlertedGeneralAllNo!! > snapshot.generalAllNo) {
            settings.lastAlertedGeneralAllNo = snapshot.generalAllNo
            changed = true
        }
        if (changed) {
            narouNovelAlertSettingsRepository.save(settings)
        }

        return changed
    }

    internal fun sendAlertMessage(
        channel: TextChannel,
        message: String,
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        channel.sendMessage(message).queue({ onSuccess() }, onFailure)
    }

    private fun buildAlertMessage(
        lengthTriggered: Boolean,
        lengthDelta: Long,
        chapterTriggered: Boolean,
        chapterDelta: Int,
        snapshot: NarouNovelSnapshot
    ): String {
        val updates = mutableListOf<String>()
        if (chapterTriggered) {
            updates += if (chapterDelta == 1) {
                "1 new chapter was published"
            } else {
                "$chapterDelta new chapters were published"
            }
        }
        if (lengthTriggered) {
            updates += "the novel grew by $lengthDelta characters"
        }

        return buildString {
            append("@everyone Narou update for ")
            append(NOVEL_CODE)
            append(": ")
            append(updates.joinToString(" and "))
            append(". Total chapters: ")
            append(snapshot.generalAllNo)
            append(". Total characters: ")
            append(snapshot.length)
            append(". ")
            append(NOVEL_URL)
        }
    }
}
