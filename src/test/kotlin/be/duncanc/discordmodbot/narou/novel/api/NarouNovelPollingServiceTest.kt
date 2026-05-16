package be.duncanc.discordmodbot.narou.novel.api

import be.duncanc.discordmodbot.narou.novel.api.persistence.NarouNovelAlertSettings
import be.duncanc.discordmodbot.narou.novel.api.persistence.NarouNovelAlertSettingsRepository
import be.duncanc.discordmodbot.narou.novel.api.persistence.NarouNovelPendingAlert
import be.duncanc.discordmodbot.narou.novel.api.persistence.NarouNovelPendingAlertRepository
import be.duncanc.discordmodbot.narou.novel.api.persistence.NarouNovelSnapshot
import be.duncanc.discordmodbot.narou.novel.api.persistence.NarouNovelSnapshotRepository
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class NarouNovelPollingServiceTest {
    @Mock
    private lateinit var narouNovelApiClient: NarouNovelApiClient

    @Mock
    private lateinit var narouNovelSnapshotRepository: NarouNovelSnapshotRepository

    @Mock
    private lateinit var narouNovelAlertSettingsRepository: NarouNovelAlertSettingsRepository

    @Mock
    private lateinit var narouNovelPendingAlertRepository: NarouNovelPendingAlertRepository

    @Mock
    private lateinit var jda: JDA

    @Mock
    private lateinit var textChannel: TextChannel

    private lateinit var service: TestNarouNovelPollingService
    private val pendingAlerts = mutableMapOf<Long, NarouNovelPendingAlert>()

    @BeforeEach
    fun setUp() {
        pendingAlerts.clear()
        service = TestNarouNovelPollingService(
            narouNovelApiClient,
            narouNovelSnapshotRepository,
            narouNovelAlertSettingsRepository,
            narouNovelPendingAlertRepository,
            jda
        )
    }

    @Test
    fun `first poll ignores incomplete entries and stores latest payload`() {
        whenever(narouNovelApiClient.fetchNovel()).thenReturn(
            listOf(
                NarouNovelApiResponseEntry(),
                payload(length = 9_445_269, generalAllNo = 778)
            )
        )
        whenever(narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE)).thenReturn(Optional.empty())
        whenever(narouNovelAlertSettingsRepository.findAll()).thenReturn(emptyList())

        service.pollNovel()

        val snapshotCaptor = argumentCaptor<NarouNovelSnapshot>()
        verify(narouNovelSnapshotRepository).save(snapshotCaptor.capture())
        assertEquals(9_445_269L, snapshotCaptor.firstValue.length)
        assertEquals(778, snapshotCaptor.firstValue.generalAllNo)
    }

    @Test
    fun `chapter alert does not reset length baseline when threshold not met`() {
        stubPendingAlertRepository()
        val snapshot = snapshot(length = 9_445_500, generalAllNo = 779)
        val settings = NarouNovelAlertSettings(
            guildId = 1L,
            channelId = 11L,
            lengthThreshold = 1_000L,
            lastAlertedLength = 9_445_269L,
            lastAlertedGeneralAllNo = 778
        )
        whenever(narouNovelApiClient.fetchNovel()).thenReturn(listOf(payload(length = 9_445_500, generalAllNo = 779)))
        whenever(narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE)).thenReturn(Optional.of(snapshot))
        whenever(narouNovelAlertSettingsRepository.findAll()).thenReturn(listOf(settings))
        whenever(jda.getTextChannelById(11L)).thenReturn(textChannel)

        service.pollNovel()

        assertEquals(listOf("@everyone Narou update for n2267be: 1 new chapter was published. Total chapters: 779. Total characters: 9445500. https://ncode.syosetu.com/n2267be/"), service.sentMessages)
        val settingsCaptor = argumentCaptor<NarouNovelAlertSettings>()
        verify(narouNovelAlertSettingsRepository).save(settingsCaptor.capture())
        assertEquals(9_445_269L, settingsCaptor.lastValue.lastAlertedLength)
        assertEquals(779, settingsCaptor.lastValue.lastAlertedGeneralAllNo)
        assertEquals(emptyMap<Long, NarouNovelPendingAlert>(), pendingAlerts)
    }

    @Test
    fun `length alert triggers when threshold is reached`() {
        stubPendingAlertRepository()
        val snapshot = snapshot(length = 9_446_500, generalAllNo = 778)
        val settings = NarouNovelAlertSettings(
            guildId = 1L,
            channelId = 11L,
            lengthThreshold = 1_000L,
            lastAlertedLength = 9_445_269L,
            lastAlertedGeneralAllNo = 778
        )
        whenever(narouNovelApiClient.fetchNovel()).thenReturn(listOf(payload(length = 9_446_500, generalAllNo = 778)))
        whenever(narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE)).thenReturn(Optional.of(snapshot))
        whenever(narouNovelAlertSettingsRepository.findAll()).thenReturn(listOf(settings))
        whenever(jda.getTextChannelById(11L)).thenReturn(textChannel)

        service.pollNovel()

        assertEquals(listOf("@everyone Narou update for n2267be: the novel grew by 1231 characters. Total chapters: 778. Total characters: 9446500. https://ncode.syosetu.com/n2267be/"), service.sentMessages)
        val settingsCaptor = argumentCaptor<NarouNovelAlertSettings>()
        verify(narouNovelAlertSettingsRepository).save(settingsCaptor.capture())
        assertEquals(9_446_500L, settingsCaptor.lastValue.lastAlertedLength)
        assertEquals(778, settingsCaptor.lastValue.lastAlertedGeneralAllNo)
        assertEquals(emptyMap<Long, NarouNovelPendingAlert>(), pendingAlerts)
    }

    @Test
    fun `combined alert updates both baselines`() {
        stubPendingAlertRepository()
        val snapshot = snapshot(length = 9_446_500, generalAllNo = 780)
        val settings = NarouNovelAlertSettings(
            guildId = 1L,
            channelId = 11L,
            lengthThreshold = 1_000L,
            lastAlertedLength = 9_445_269L,
            lastAlertedGeneralAllNo = 778
        )
        whenever(narouNovelApiClient.fetchNovel()).thenReturn(listOf(payload(length = 9_446_500, generalAllNo = 780)))
        whenever(narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE)).thenReturn(Optional.of(snapshot))
        whenever(narouNovelAlertSettingsRepository.findAll()).thenReturn(listOf(settings))
        whenever(jda.getTextChannelById(11L)).thenReturn(textChannel)

        service.pollNovel()

        assertEquals(
            listOf(
                "@everyone Narou update for n2267be: 2 new chapters were published and the novel grew by 1231 characters. Total chapters: 780. Total characters: 9446500. https://ncode.syosetu.com/n2267be/"
            ),
            service.sentMessages
        )
        val settingsCaptor = argumentCaptor<NarouNovelAlertSettings>()
        verify(narouNovelAlertSettingsRepository).save(settingsCaptor.capture())
        assertEquals(9_446_500L, settingsCaptor.lastValue.lastAlertedLength)
        assertEquals(780, settingsCaptor.lastValue.lastAlertedGeneralAllNo)
        assertEquals(emptyMap<Long, NarouNovelPendingAlert>(), pendingAlerts)
    }

    @Test
    fun `poll skips duplicate guild alert while Discord send callback is still pending`() {
        stubPendingAlertRepository(includeDeleteById = false)
        service = TestNarouNovelPollingService(
            narouNovelApiClient,
            narouNovelSnapshotRepository,
            narouNovelAlertSettingsRepository,
            narouNovelPendingAlertRepository,
            jda,
            completeSendsImmediately = false
        )
        val snapshot = snapshot(length = 9_446_500, generalAllNo = 780)
        val settings = NarouNovelAlertSettings(
            guildId = 1L,
            channelId = 11L,
            lengthThreshold = 1_000L,
            lastAlertedLength = 9_445_269L,
            lastAlertedGeneralAllNo = 778
        )
        whenever(narouNovelApiClient.fetchNovel()).thenReturn(listOf(payload(length = 9_446_500, generalAllNo = 780)))
        whenever(narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE)).thenReturn(Optional.of(snapshot))
        whenever(narouNovelAlertSettingsRepository.findAll()).thenReturn(listOf(settings))
        whenever(jda.getTextChannelById(11L)).thenReturn(textChannel)

        service.pollNovel()
        service.pollNovel()

        assertEquals(
            listOf(
                "@everyone Narou update for n2267be: 2 new chapters were published and the novel grew by 1231 characters. Total chapters: 780. Total characters: 9446500. https://ncode.syosetu.com/n2267be/"
            ),
            service.sentMessages
        )
        assertEquals(NarouNovelPendingAlert(1L, 9_446_500L, 780), pendingAlerts[1L])
        verify(narouNovelAlertSettingsRepository, never()).save(settings)
        assertEquals(9_445_269L, settings.lastAlertedLength)
        assertEquals(778, settings.lastAlertedGeneralAllNo)
    }

    @Test
    fun `poll skips resend when matching pending snapshot already exists`() {
        val snapshot = snapshot(length = 9_446_500, generalAllNo = 780)
        val settings = NarouNovelAlertSettings(
            guildId = 1L,
            channelId = 11L,
            lengthThreshold = 1_000L,
            lastAlertedLength = 9_445_269L,
            lastAlertedGeneralAllNo = 778
        )
        whenever(narouNovelPendingAlertRepository.findById(1L)).thenReturn(Optional.of(NarouNovelPendingAlert(1L, 9_446_500L, 780)))
        whenever(narouNovelApiClient.fetchNovel()).thenReturn(listOf(payload(length = 9_446_500, generalAllNo = 780)))
        whenever(narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE)).thenReturn(Optional.of(snapshot))
        whenever(narouNovelAlertSettingsRepository.findAll()).thenReturn(listOf(settings))

        service.pollNovel()

        assertEquals(emptyList<String>(), service.sentMessages)
        verify(jda, never()).getTextChannelById(any<Long>())
        verify(narouNovelPendingAlertRepository).findById(1L)
    }

    @Test
    fun `poll retries after alert failure callback clears pending lock`() {
        stubPendingAlertRepository()
        service = TestNarouNovelPollingService(
            narouNovelApiClient,
            narouNovelSnapshotRepository,
            narouNovelAlertSettingsRepository,
            narouNovelPendingAlertRepository,
            jda,
            completeSendsImmediately = false,
            failSendsImmediately = true
        )
        val snapshot = snapshot(length = 9_446_500, generalAllNo = 780)
        val settings = NarouNovelAlertSettings(
            guildId = 1L,
            channelId = 11L,
            lengthThreshold = 1_000L,
            lastAlertedLength = 9_445_269L,
            lastAlertedGeneralAllNo = 778
        )
        whenever(narouNovelApiClient.fetchNovel()).thenReturn(listOf(payload(length = 9_446_500, generalAllNo = 780)))
        whenever(narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE)).thenReturn(Optional.of(snapshot))
        whenever(narouNovelAlertSettingsRepository.findAll()).thenReturn(listOf(settings))
        whenever(jda.getTextChannelById(11L)).thenReturn(textChannel)

        service.pollNovel()
        service.pollNovel()

        assertEquals(2, service.sentMessages.size)
        verify(narouNovelAlertSettingsRepository, never()).save(settings)
        assertEquals(emptyMap<Long, NarouNovelPendingAlert>(), pendingAlerts)
    }

    @Test
    fun `poll keeps matching pending alert after baseline save failure to avoid duplicate resend`() {
        stubPendingAlertRepository(includeDeleteById = false)
        service = TestNarouNovelPollingService(
            narouNovelApiClient,
            narouNovelSnapshotRepository,
            narouNovelAlertSettingsRepository,
            narouNovelPendingAlertRepository,
            jda
        )
        val snapshot = snapshot(length = 9_446_500, generalAllNo = 780)
        val settings = NarouNovelAlertSettings(
            guildId = 1L,
            channelId = 11L,
            lengthThreshold = 1_000L,
            lastAlertedLength = 9_445_269L,
            lastAlertedGeneralAllNo = 778
        )
        whenever(narouNovelApiClient.fetchNovel()).thenReturn(listOf(payload(length = 9_446_500, generalAllNo = 780)))
        whenever(narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE)).thenReturn(Optional.of(snapshot))
        whenever(narouNovelAlertSettingsRepository.findAll()).thenReturn(listOf(settings))
        whenever(jda.getTextChannelById(11L)).thenReturn(textChannel)
        whenever(narouNovelAlertSettingsRepository.save(settings)).thenThrow(IllegalStateException("db down"))

        service.pollNovel()
        service.pollNovel()

        assertEquals(1, service.sentMessages.size)
        assertEquals(NarouNovelPendingAlert(1L, 9_446_500L, 780), pendingAlerts[1L])
    }

    @Test
    fun `poll retries after synchronous send exception clears pending lock`() {
        stubPendingAlertRepository()
        service = TestNarouNovelPollingService(
            narouNovelApiClient,
            narouNovelSnapshotRepository,
            narouNovelAlertSettingsRepository,
            narouNovelPendingAlertRepository,
            jda,
            throwOnSend = IllegalStateException("boom")
        )
        val snapshot = snapshot(length = 9_446_500, generalAllNo = 780)
        val settings = NarouNovelAlertSettings(
            guildId = 1L,
            channelId = 11L,
            lengthThreshold = 1_000L,
            lastAlertedLength = 9_445_269L,
            lastAlertedGeneralAllNo = 778
        )
        whenever(narouNovelApiClient.fetchNovel()).thenReturn(listOf(payload(length = 9_446_500, generalAllNo = 780)))
        whenever(narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE)).thenReturn(Optional.of(snapshot))
        whenever(narouNovelAlertSettingsRepository.findAll()).thenReturn(listOf(settings))
        whenever(jda.getTextChannelById(11L)).thenReturn(textChannel)

        val firstFailure = assertThrows(IllegalStateException::class.java) {
            service.pollNovel()
        }
        val secondFailure = assertThrows(IllegalStateException::class.java) {
            service.pollNovel()
        }

        assertEquals("boom", firstFailure.message)
        assertEquals("boom", secondFailure.message)
        assertEquals(2, service.sentMessages.size)
        assertEquals(emptyMap<Long, NarouNovelPendingAlert>(), pendingAlerts)
    }

    @Test
    fun `terminal send failure disables configured channel`() {
        stubPendingAlertRepository()
        service = TestNarouNovelPollingService(
            narouNovelApiClient,
            narouNovelSnapshotRepository,
            narouNovelAlertSettingsRepository,
            narouNovelPendingAlertRepository,
            jda,
            failWith = IllegalStateException("missing access"),
            terminalFailurePredicate = { true }
        )
        val snapshot = snapshot(length = 9_446_500, generalAllNo = 780)
        val settings = NarouNovelAlertSettings(
            guildId = 1L,
            channelId = 11L,
            lengthThreshold = 1_000L,
            lastAlertedLength = 9_445_269L,
            lastAlertedGeneralAllNo = 778
        )
        whenever(narouNovelApiClient.fetchNovel()).thenReturn(listOf(payload(length = 9_446_500, generalAllNo = 780)))
        whenever(narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE)).thenReturn(Optional.of(snapshot))
        whenever(narouNovelAlertSettingsRepository.findAll()).thenReturn(listOf(settings))
        whenever(jda.getTextChannelById(11L)).thenReturn(textChannel)

        service.pollNovel()
        service.pollNovel()

        assertEquals(1, service.sentMessages.size)
        assertNull(settings.channelId)
        assertEquals(emptyMap<Long, NarouNovelPendingAlert>(), pendingAlerts)
    }

    @Test
    fun `missing channel disables configured channel`() {
        val snapshot = snapshot(length = 9_446_500, generalAllNo = 780)
        val settings = NarouNovelAlertSettings(
            guildId = 1L,
            channelId = 11L,
            lengthThreshold = 1_000L,
            lastAlertedLength = 9_445_269L,
            lastAlertedGeneralAllNo = 778
        )
        whenever(narouNovelApiClient.fetchNovel()).thenReturn(listOf(payload(length = 9_446_500, generalAllNo = 780)))
        whenever(narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE)).thenReturn(Optional.of(snapshot))
        whenever(narouNovelAlertSettingsRepository.findAll()).thenReturn(listOf(settings))
        whenever(jda.getTextChannelById(11L)).thenReturn(null)

        service.pollNovel()
        service.pollNovel()

        assertEquals(emptyList<String>(), service.sentMessages)
        assertNull(settings.channelId)
        assertEquals(emptyMap<Long, NarouNovelPendingAlert>(), pendingAlerts)
    }

    @Test
    fun `missing baselines are initialized without alerting`() {
        val snapshot = snapshot(length = 9_445_500, generalAllNo = 779)
        val settings = NarouNovelAlertSettings(guildId = 1L, channelId = 11L, lastAlertedLength = null, lastAlertedGeneralAllNo = null)
        whenever(narouNovelApiClient.fetchNovel()).thenReturn(listOf(payload(length = 9_445_500, generalAllNo = 779)))
        whenever(narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE)).thenReturn(Optional.of(snapshot))
        whenever(narouNovelAlertSettingsRepository.findAll()).thenReturn(listOf(settings))

        service.pollNovel()

        verify(jda, never()).getTextChannelById(11L)
        val settingsCaptor = argumentCaptor<NarouNovelAlertSettings>()
        verify(narouNovelAlertSettingsRepository).save(settingsCaptor.capture())
        assertEquals(9_445_500L, settingsCaptor.lastValue.lastAlertedLength)
        assertEquals(779, settingsCaptor.lastValue.lastAlertedGeneralAllNo)
    }

    private fun payload(length: Long, generalAllNo: Int): NarouNovelApiResponseEntry {
        return NarouNovelApiResponseEntry(
            generalLastup = "2026-05-15 07:00:00",
            generalAllNo = generalAllNo,
            length = length,
            time = 18_891,
            novelUpdatedAt = "2026-05-15 07:00:36",
            updatedAt = "2026-05-15 20:14:23"
        )
    }

    private fun snapshot(length: Long, generalAllNo: Int): NarouNovelSnapshot {
        return NarouNovelSnapshot(
            ncode = NarouNovelPollingService.NOVEL_CODE,
            generalLastup = "2026-05-15 07:00:00",
            generalAllNo = generalAllNo,
            length = length,
            time = 18_891,
            novelUpdatedAt = "2026-05-15 07:00:36",
            updatedAt = "2026-05-15 20:14:23"
        )
    }

    private fun stubPendingAlertRepository(includeDeleteById: Boolean = true) {
        whenever(narouNovelPendingAlertRepository.findById(any<Long>())).thenAnswer { invocation ->
            Optional.ofNullable(pendingAlerts[invocation.getArgument(0)])
        }
        whenever(narouNovelPendingAlertRepository.save(any<NarouNovelPendingAlert>())).thenAnswer { invocation ->
            invocation.getArgument<NarouNovelPendingAlert>(0).also { pendingAlerts[it.guildId] = it }
        }
        if (includeDeleteById) {
            doAnswer { invocation ->
                pendingAlerts.remove(invocation.getArgument(0))
                null
            }.whenever(narouNovelPendingAlertRepository).deleteById(any<Long>())
        }
    }

    private class TestNarouNovelPollingService(
        narouNovelApiClient: NarouNovelApiClient,
        narouNovelSnapshotRepository: NarouNovelSnapshotRepository,
        narouNovelAlertSettingsRepository: NarouNovelAlertSettingsRepository,
        narouNovelPendingAlertRepository: NarouNovelPendingAlertRepository,
        jda: JDA,
        private val completeSendsImmediately: Boolean = true,
        private val failSendsImmediately: Boolean = false,
        private val throwOnSend: RuntimeException? = null,
        private val failWith: Throwable? = null,
        private val terminalFailurePredicate: (Throwable) -> Boolean = { false },
        private val onSend: (() -> Unit)? = null
    ) : NarouNovelPollingService(
        narouNovelApiClient,
        narouNovelSnapshotRepository,
        narouNovelAlertSettingsRepository,
        narouNovelPendingAlertRepository,
        jda
    ) {
        val sentMessages = mutableListOf<String>()

        override fun isTerminalChannelFailure(exception: Throwable): Boolean {
            return terminalFailurePredicate(exception)
        }

        override fun sendAlertMessage(
            channel: TextChannel,
            message: String,
            onSuccess: () -> Unit,
            onFailure: (Throwable) -> Unit
        ) {
            sentMessages += message
            onSend?.invoke()
            throwOnSend?.let { throw it }
            failWith?.let {
                onFailure(it)
                return
            }
            if (failSendsImmediately) {
                onFailure(IllegalStateException("send failed"))
                return
            }
            if (completeSendsImmediately) {
                onSuccess()
            }
        }
    }
}
