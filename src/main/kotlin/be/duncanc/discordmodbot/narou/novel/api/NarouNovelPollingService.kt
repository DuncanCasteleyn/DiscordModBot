package be.duncanc.discordmodbot.narou.novel.api

import be.duncanc.discordmodbot.narou.novel.api.persistence.*
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.requests.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NarouNovelPollingService(
    private val narouNovelApiClient: NarouNovelApiClient,
    private val narouNovelSnapshotRepository: NarouNovelSnapshotRepository,
    private val narouNovelAlertSettingsRepository: NarouNovelAlertSettingsRepository,
    private val narouNovelPendingAlertRepository: NarouNovelPendingAlertRepository,
    private val jda: JDA
) {
    data class NarouNovelPollSnapshot(
        val generalLastup: String,
        val generalAllNo: Int,
        val length: Long,
        val authorProfileLength: Long,
        val time: Int,
        val novelUpdatedAt: String,
        val updatedAt: String
    )

    companion object {
        internal const val NOVEL_CODE = "n2267be"
        private const val NOVEL_URL = "https://ncode.syosetu.com/n2267be/"
        private const val PREDICTION_ALERT_MESSAGE =
            "Detected an increase in the author's profile character count. This may indicate that a new chapter is coming soon"
        private val LOG = LoggerFactory.getLogger(NarouNovelPollingService::class.java)
    }

    @Scheduled(cron = $$"${discord-mod-bot.narou-novel-api.poll-cron:0/15 * * * * *}")
    @Transactional
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
                    generalLastup = payload.generalLastup,
                    generalAllNo = payload.generalAllNo,
                    length = payload.length,
                    authorProfileLength = payload.authorProfileLength,
                    time = payload.time,
                    novelUpdatedAt = payload.novelUpdatedAt,
                    updatedAt = payload.updatedAt
                )
            )
            initializeMissingBaselines(payload.length, payload.authorProfileLength, payload.generalAllNo)
            return
        }

        snapshot.generalLastup = payload.generalLastup
        snapshot.generalAllNo = payload.generalAllNo
        snapshot.length = payload.length
        snapshot.authorProfileLength = payload.authorProfileLength
        snapshot.time = payload.time
        snapshot.novelUpdatedAt = payload.novelUpdatedAt
        snapshot.updatedAt = payload.updatedAt
        narouNovelSnapshotRepository.save(snapshot)

        processAlerts(snapshot)
    }

    internal fun fetchPayload(): NarouNovelPollSnapshot {
        val novelPayload = narouNovelApiClient.fetchNovel().firstOrNull {
            it.generalLastup != null &&
                    it.generalAllNo != null &&
                    it.length != null &&
                    it.time != null &&
                    it.novelUpdatedAt != null &&
                    it.updatedAt != null
        } ?: throw IllegalStateException("Narou novel API response did not contain a novel payload")
        val authorProfilePayload = narouNovelApiClient.fetchAuthorProfile().firstOrNull {
            it.novelLength != null
        } ?: throw IllegalStateException("Narou author profile API response did not contain a profile length payload")

        return NarouNovelPollSnapshot(
            generalLastup = novelPayload.generalLastup!!,
            generalAllNo = novelPayload.generalAllNo!!,
            length = novelPayload.length!!,
            authorProfileLength = authorProfilePayload.novelLength!!,
            time = novelPayload.time!!,
            novelUpdatedAt = novelPayload.novelUpdatedAt!!,
            updatedAt = novelPayload.updatedAt!!
        )
    }

    private fun processAlerts(snapshot: NarouNovelSnapshot) {
        narouNovelAlertSettingsRepository.findAll().forEach { settings ->
            val updatedSettings = initializeOrRebaseBaselines(settings, snapshot)
            if (updatedSettings) {
                return@forEach
            }

            val lastAlertedLength = settings.lastAlertedLength ?: return@forEach
            val lastAlertedAuthorProfileLength = settings.lastAlertedAuthorProfileLength ?: return@forEach
            val lastAlertedGeneralAllNo = settings.lastAlertedGeneralAllNo ?: return@forEach
            val lengthDelta = snapshot.length - lastAlertedLength
            val predictionLengthDelta = snapshot.authorProfileLength - lastAlertedAuthorProfileLength
            val chapterDelta = snapshot.generalAllNo - lastAlertedGeneralAllNo
            val lengthTriggered = lengthDelta >= settings.lengthThreshold
            val predictionTriggered = predictionLengthDelta >= settings.predictionLengthThreshold
            val chapterTriggered = chapterDelta > 0
            if (!lengthTriggered && !predictionTriggered && !chapterTriggered) {
                return@forEach
            }

            val pendingAlert = narouNovelPendingAlertRepository.findById(settings.guildId).orElse(null)
            if (pendingAlert != null) {
                if (
                    pendingAlert.snapshotGeneralAllNo == snapshot.generalAllNo &&
                    pendingAlert.snapshotLength == snapshot.length &&
                    pendingAlert.snapshotAuthorProfileLength == snapshot.authorProfileLength
                ) {
                    LOG.debug(
                        "Skipping Narou novel alert for guild {} because snapshot {}:{}:{} was already sent or is still being finalized",
                        settings.guildId,
                        pendingAlert.snapshotGeneralAllNo,
                        pendingAlert.snapshotLength,
                        pendingAlert.snapshotAuthorProfileLength
                    )
                    return@forEach
                }
                LOG.debug(
                    "Skipping Narou novel alert for guild {} because a pending alert lock exists for snapshot {}:{}:{}",
                    settings.guildId,
                    pendingAlert.snapshotGeneralAllNo,
                    pendingAlert.snapshotLength,
                    pendingAlert.snapshotAuthorProfileLength
                )
                return@forEach
            }

            val channelId = settings.channelId ?: return@forEach
            val channel = jda.getTextChannelById(channelId)
            if (channel == null) {
                disableAlertChannel(
                    settings = settings,
                    channelId = channelId,
                    reason = "channel {} no longer exists"
                )
                return@forEach
            }

            val message = buildAlertMessage(
                lengthTriggered,
                lengthDelta,
                predictionTriggered,
                chapterTriggered,
                chapterDelta,
                snapshot
            )
            narouNovelPendingAlertRepository.save(
                NarouNovelPendingAlert(
                    guildId = settings.guildId,
                    snapshotLength = snapshot.length,
                    snapshotAuthorProfileLength = snapshot.authorProfileLength,
                    snapshotGeneralAllNo = snapshot.generalAllNo
                )
            )
            try {
                sendAlertMessage(
                    channel = channel,
                    message = message,
                    onSuccess = {
                        try {
                            if (lengthTriggered) {
                                settings.lastAlertedLength = snapshot.length
                            }
                            if (predictionTriggered || chapterTriggered) {
                                settings.lastAlertedAuthorProfileLength = snapshot.authorProfileLength
                            }
                            if (chapterTriggered) {
                                settings.lastAlertedGeneralAllNo = snapshot.generalAllNo
                            }
                            narouNovelAlertSettingsRepository.save(settings)
                            narouNovelPendingAlertRepository.deleteById(settings.guildId)
                        } catch (exception: Exception) {
                            LOG.warn(
                                "Failed to persist Narou novel alert baselines for guild {} after sending snapshot {}:{}",
                                settings.guildId,
                                snapshot.generalAllNo,
                                snapshot.length,
                                exception
                            )
                        }
                    },
                    onFailure = { exception ->
                        if (isTerminalChannelFailure(exception)) {
                            disableAlertChannel(
                                settings = settings,
                                channelId = channelId,
                                reason = "the bot can no longer post in channel {}",
                                cause = exception
                            )
                            return@sendAlertMessage
                        }

                        try {
                            LOG.warn("Failed to send Narou novel alert for guild {}", settings.guildId, exception)
                        } finally {
                            narouNovelPendingAlertRepository.deleteById(settings.guildId)
                        }
                    }
                )
            } catch (exception: Exception) {
                narouNovelPendingAlertRepository.deleteById(settings.guildId)
                throw exception
            }
        }
    }

    private fun disableAlertChannel(
        settings: NarouNovelAlertSettings,
        channelId: Long,
        reason: String,
        cause: Throwable? = null
    ) {
        try {
            settings.channelId = null
            narouNovelAlertSettingsRepository.save(settings)
            narouNovelPendingAlertRepository.deleteById(settings.guildId)
            if (cause == null) {
                LOG.warn(
                    "Disabled Narou novel alerts for guild {} because $reason",
                    settings.guildId,
                    channelId
                )
            } else {
                LOG.warn(
                    "Disabled Narou novel alerts for guild {} because $reason",
                    settings.guildId,
                    channelId,
                    cause
                )
            }
        } catch (persistenceException: Exception) {
            LOG.warn(
                "Failed to disable Narou novel alerts for guild {} after channel failure in channel {}",
                settings.guildId,
                channelId,
                persistenceException
            )
        }
    }

    private fun initializeMissingBaselines(length: Long, authorProfileLength: Long, generalAllNo: Int) {
        narouNovelAlertSettingsRepository.findAll().forEach { settings ->
            var changed = false
            if (settings.lastAlertedLength == null) {
                settings.lastAlertedLength = length
                changed = true
            }
            if (settings.lastAlertedAuthorProfileLength == null) {
                settings.lastAlertedAuthorProfileLength = authorProfileLength
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
        if (
            settings.lastAlertedAuthorProfileLength == null ||
            settings.lastAlertedAuthorProfileLength!! > snapshot.authorProfileLength
        ) {
            settings.lastAlertedAuthorProfileLength = snapshot.authorProfileLength
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

    internal fun isTerminalChannelFailure(exception: Throwable): Boolean {
        val errorResponseException = exception as? ErrorResponseException ?: return false
        return when (errorResponseException.errorResponse) {
            ErrorResponse.MISSING_PERMISSIONS,
            ErrorResponse.MISSING_ACCESS,
            ErrorResponse.UNKNOWN_CHANNEL -> true

            else -> false
        }
    }

    private fun buildAlertMessage(
        lengthTriggered: Boolean,
        lengthDelta: Long,
        predictionTriggered: Boolean,
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
        if (predictionTriggered) {
            updates += PREDICTION_ALERT_MESSAGE
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
            append("Current chapter: ")
            append(NOVEL_URL + snapshot.generalAllNo)
            append("Next chapter: ")
            append(NOVEL_URL + snapshot.generalAllNo)
        }
    }
}
