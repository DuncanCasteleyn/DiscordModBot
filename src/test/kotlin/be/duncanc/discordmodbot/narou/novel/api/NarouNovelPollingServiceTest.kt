package be.duncanc.discordmodbot.narou.novel.api

import be.duncanc.discordmodbot.narou.novel.api.persistence.NarouNovelAlertSettings
import be.duncanc.discordmodbot.narou.novel.api.persistence.NarouNovelAlertSettingsRepository
import be.duncanc.discordmodbot.narou.novel.api.persistence.NarouNovelPendingAlert
import be.duncanc.discordmodbot.narou.novel.api.persistence.NarouNovelPendingAlertRepository
import be.duncanc.discordmodbot.narou.novel.api.persistence.NarouNovelSnapshot
import be.duncanc.discordmodbot.narou.novel.api.persistence.NarouNovelSnapshotRepository
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.MessageEmbed
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
        whenever(narouNovelApiClient.fetchAuthorProfile()).thenReturn(listOf(profilePayload(9_876_000)))
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
        assertEquals(9_876_000L, snapshotCaptor.firstValue.authorProfileLength)
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
            predictionLengthThreshold = 1_000L,
            lastAlertedLength = 9_445_269L,
            lastAlertedAuthorProfileLength = 9_876_000L,
            lastAlertedGeneralAllNo = 778
        )
        whenever(narouNovelApiClient.fetchNovel()).thenReturn(listOf(payload(length = 9_445_500, generalAllNo = 779)))
        whenever(narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE)).thenReturn(Optional.of(snapshot))
        whenever(narouNovelAlertSettingsRepository.findAll()).thenReturn(listOf(settings))
        whenever(jda.getTextChannelById(11L)).thenReturn(textChannel)

        service.pollNovel()

        assertAlert(
            service = service,
            description = "1 new chapter was published.",
            totalChapters = 779,
            totalCharacters = 9_445_500L,
            chapterLink = "https://ncode.syosetu.com/n2267be/779"
        )
        val settingsCaptor = argumentCaptor<NarouNovelAlertSettings>()
        verify(narouNovelAlertSettingsRepository).save(settingsCaptor.capture())
        assertEquals(9_445_269L, settingsCaptor.lastValue.lastAlertedLength)
        assertEquals(9_876_000L, settingsCaptor.lastValue.lastAlertedAuthorProfileLength)
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
            predictionLengthThreshold = 1_000L,
            lastAlertedLength = 9_445_269L,
            lastAlertedAuthorProfileLength = 9_876_000L,
            lastAlertedGeneralAllNo = 778
        )
        whenever(narouNovelApiClient.fetchNovel()).thenReturn(listOf(payload(length = 9_446_500, generalAllNo = 778)))
        whenever(narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE)).thenReturn(Optional.of(snapshot))
        whenever(narouNovelAlertSettingsRepository.findAll()).thenReturn(listOf(settings))
        whenever(jda.getTextChannelById(11L)).thenReturn(textChannel)

        service.pollNovel()

        assertAlert(
            service = service,
            description = "the novel grew by 1231 characters.",
            totalChapters = 778,
            totalCharacters = 9_446_500L,
            chapterLink = "https://ncode.syosetu.com/n2267be/778"
        )
        val settingsCaptor = argumentCaptor<NarouNovelAlertSettings>()
        verify(narouNovelAlertSettingsRepository).save(settingsCaptor.capture())
        assertEquals(9_446_500L, settingsCaptor.lastValue.lastAlertedLength)
        assertEquals(9_876_000L, settingsCaptor.lastValue.lastAlertedAuthorProfileLength)
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
            predictionLengthThreshold = 1_000L,
            lastAlertedLength = 9_445_269L,
            lastAlertedAuthorProfileLength = 9_876_000L,
            lastAlertedGeneralAllNo = 778
        )
        whenever(narouNovelApiClient.fetchNovel()).thenReturn(listOf(payload(length = 9_446_500, generalAllNo = 780)))
        whenever(narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE)).thenReturn(Optional.of(snapshot))
        whenever(narouNovelAlertSettingsRepository.findAll()).thenReturn(listOf(settings))
        whenever(jda.getTextChannelById(11L)).thenReturn(textChannel)

        service.pollNovel()

        assertAlert(
            service = service,
            description = "2 new chapters were published and the novel grew by 1231 characters.",
            totalChapters = 780,
            totalCharacters = 9_446_500L,
            chapterLink = "https://ncode.syosetu.com/n2267be/780"
        )
        val settingsCaptor = argumentCaptor<NarouNovelAlertSettings>()
        verify(narouNovelAlertSettingsRepository).save(settingsCaptor.capture())
        assertEquals(9_446_500L, settingsCaptor.lastValue.lastAlertedLength)
        assertEquals(9_876_000L, settingsCaptor.lastValue.lastAlertedAuthorProfileLength)
        assertEquals(780, settingsCaptor.lastValue.lastAlertedGeneralAllNo)
        assertEquals(emptyMap<Long, NarouNovelPendingAlert>(), pendingAlerts)
    }

    @Test
    fun `prediction alert triggers from author profile growth`() {
        stubPendingAlertRepository()
        val snapshot = snapshot(length = 9_445_500, generalAllNo = 779, authorProfileLength = 9_877_250)
        val settings = NarouNovelAlertSettings(
            guildId = 1L,
            channelId = 11L,
            lengthThreshold = 1_000L,
            predictionLengthThreshold = 1_000L,
            lastAlertedLength = 9_445_500L,
            lastAlertedAuthorProfileLength = 9_876_000L,
            lastAlertedGeneralAllNo = 779
        )
        whenever(narouNovelApiClient.fetchNovel()).thenReturn(listOf(payload(length = 9_445_500, generalAllNo = 779)))
        whenever(narouNovelApiClient.fetchAuthorProfile()).thenReturn(listOf(profilePayload(9_877_250)))
        whenever(narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE)).thenReturn(Optional.of(snapshot(length = 9_445_500, generalAllNo = 779)))
        whenever(narouNovelAlertSettingsRepository.findAll()).thenReturn(listOf(settings))
        whenever(jda.getTextChannelById(11L)).thenReturn(textChannel)

        service.pollNovel()

        assertAlert(
            service = service,
            description = "Detected an increase in the author's profile character count. This may indicate that a new chapter is coming soon.",
            totalChapters = 779,
            totalCharacters = 9_445_500L,
            chapterLink = "https://ncode.syosetu.com/n2267be/779"
        )
        val settingsCaptor = argumentCaptor<NarouNovelAlertSettings>()
        verify(narouNovelAlertSettingsRepository).save(settingsCaptor.capture())
        assertEquals(9_445_500L, settingsCaptor.lastValue.lastAlertedLength)
        assertEquals(9_877_250L, settingsCaptor.lastValue.lastAlertedAuthorProfileLength)
        assertEquals(779, settingsCaptor.lastValue.lastAlertedGeneralAllNo)
    }

    @Test
    fun `chapter alert still sends when author profile fetch fails`() {
        stubPendingAlertRepository()
        val snapshot = snapshot(length = 9_445_500, generalAllNo = 779, authorProfileLength = 9_876_000)
        val settings = NarouNovelAlertSettings(
            guildId = 1L,
            channelId = 11L,
            lengthThreshold = 1_000L,
            predictionLengthThreshold = 1_000L,
            lastAlertedLength = 9_445_269L,
            lastAlertedAuthorProfileLength = 9_876_000L,
            lastAlertedGeneralAllNo = 778
        )
        whenever(narouNovelApiClient.fetchNovel()).thenReturn(listOf(payload(length = 9_445_500, generalAllNo = 779)))
        whenever(narouNovelApiClient.fetchAuthorProfile()).thenThrow(IllegalStateException("profile down"))
        whenever(narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE)).thenReturn(Optional.of(snapshot))
        whenever(narouNovelAlertSettingsRepository.findAll()).thenReturn(listOf(settings))
        whenever(jda.getTextChannelById(11L)).thenReturn(textChannel)

        service.pollNovel()

        assertAlert(
            service = service,
            description = "1 new chapter was published.",
            totalChapters = 779,
            totalCharacters = 9_445_500L,
            chapterLink = "https://ncode.syosetu.com/n2267be/779"
        )
        val settingsCaptor = argumentCaptor<NarouNovelAlertSettings>()
        verify(narouNovelAlertSettingsRepository).save(settingsCaptor.capture())
        assertEquals(9_445_269L, settingsCaptor.lastValue.lastAlertedLength)
        assertEquals(9_876_000L, settingsCaptor.lastValue.lastAlertedAuthorProfileLength)
        assertEquals(779, settingsCaptor.lastValue.lastAlertedGeneralAllNo)
    }

    @Test
    fun `missing baselines stay unset for zero author profile snapshot when profile fetch fails`() {
        val snapshot = snapshot(length = 9_445_500, generalAllNo = 779, authorProfileLength = 0)
        val settings = NarouNovelAlertSettings(
            guildId = 1L,
            channelId = 11L,
            lastAlertedLength = null,
            lastAlertedAuthorProfileLength = null,
            lastAlertedGeneralAllNo = null
        )
        whenever(narouNovelApiClient.fetchNovel()).thenReturn(listOf(payload(length = 9_445_500, generalAllNo = 779)))
        whenever(narouNovelApiClient.fetchAuthorProfile()).thenThrow(IllegalStateException("profile down"))
        whenever(narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE)).thenReturn(Optional.of(snapshot))
        whenever(narouNovelAlertSettingsRepository.findAll()).thenReturn(listOf(settings))

        service.pollNovel()

        verify(jda, never()).getTextChannelById(11L)
        val settingsCaptor = argumentCaptor<NarouNovelAlertSettings>()
        verify(narouNovelAlertSettingsRepository).save(settingsCaptor.capture())
        assertEquals(9_445_500L, settingsCaptor.lastValue.lastAlertedLength)
        assertEquals(null, settingsCaptor.lastValue.lastAlertedAuthorProfileLength)
        assertEquals(779, settingsCaptor.lastValue.lastAlertedGeneralAllNo)
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
            predictionLengthThreshold = 1_000L,
            lastAlertedLength = 9_445_269L,
            lastAlertedAuthorProfileLength = 9_876_000L,
            lastAlertedGeneralAllNo = 778
        )
        whenever(narouNovelApiClient.fetchNovel()).thenReturn(listOf(payload(length = 9_446_500, generalAllNo = 780)))
        whenever(narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE)).thenReturn(Optional.of(snapshot))
        whenever(narouNovelAlertSettingsRepository.findAll()).thenReturn(listOf(settings))
        whenever(jda.getTextChannelById(11L)).thenReturn(textChannel)

        service.pollNovel()
        service.pollNovel()

        assertAlert(
            service = service,
            description = "2 new chapters were published and the novel grew by 1231 characters.",
            totalChapters = 780,
            totalCharacters = 9_446_500L,
            chapterLink = "https://ncode.syosetu.com/n2267be/780"
        )
        assertEquals(NarouNovelPendingAlert(1L, 9_446_500L, 9_876_000L, 780), pendingAlerts[1L])
        verify(narouNovelAlertSettingsRepository, never()).save(settings)
        assertEquals(9_445_269L, settings.lastAlertedLength)
        assertEquals(9_876_000L, settings.lastAlertedAuthorProfileLength)
        assertEquals(778, settings.lastAlertedGeneralAllNo)
    }

    @Test
    fun `poll skips resend when matching pending snapshot already exists`() {
        val snapshot = snapshot(length = 9_446_500, generalAllNo = 780)
        val settings = NarouNovelAlertSettings(
            guildId = 1L,
            channelId = 11L,
            lengthThreshold = 1_000L,
            predictionLengthThreshold = 1_000L,
            lastAlertedLength = 9_445_269L,
            lastAlertedAuthorProfileLength = 9_876_000L,
            lastAlertedGeneralAllNo = 778
        )
        whenever(narouNovelPendingAlertRepository.findById(1L)).thenReturn(Optional.of(NarouNovelPendingAlert(1L, 9_446_500L, 9_876_000L, 780)))
        whenever(narouNovelApiClient.fetchNovel()).thenReturn(listOf(payload(length = 9_446_500, generalAllNo = 780)))
        whenever(narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE)).thenReturn(Optional.of(snapshot))
        whenever(narouNovelAlertSettingsRepository.findAll()).thenReturn(listOf(settings))

        service.pollNovel()

        assertEquals(emptyList<String>(), service.sentMessageContents)
        assertEquals(emptyList<MessageEmbed>(), service.sentEmbeds)
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
            predictionLengthThreshold = 1_000L,
            lastAlertedLength = 9_445_269L,
            lastAlertedAuthorProfileLength = 9_876_000L,
            lastAlertedGeneralAllNo = 778
        )
        whenever(narouNovelApiClient.fetchNovel()).thenReturn(listOf(payload(length = 9_446_500, generalAllNo = 780)))
        whenever(narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE)).thenReturn(Optional.of(snapshot))
        whenever(narouNovelAlertSettingsRepository.findAll()).thenReturn(listOf(settings))
        whenever(jda.getTextChannelById(11L)).thenReturn(textChannel)

        service.pollNovel()
        service.pollNovel()

        assertEquals(2, service.sentMessageContents.size)
        assertEquals(2, service.sentEmbeds.size)
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
            predictionLengthThreshold = 1_000L,
            lastAlertedLength = 9_445_269L,
            lastAlertedAuthorProfileLength = 9_876_000L,
            lastAlertedGeneralAllNo = 778
        )
        whenever(narouNovelApiClient.fetchNovel()).thenReturn(listOf(payload(length = 9_446_500, generalAllNo = 780)))
        whenever(narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE)).thenReturn(Optional.of(snapshot))
        whenever(narouNovelAlertSettingsRepository.findAll()).thenReturn(listOf(settings))
        whenever(jda.getTextChannelById(11L)).thenReturn(textChannel)
        whenever(narouNovelAlertSettingsRepository.save(settings)).thenThrow(IllegalStateException("db down"))

        service.pollNovel()
        service.pollNovel()

        assertEquals(1, service.sentMessageContents.size)
        assertEquals(1, service.sentEmbeds.size)
        assertEquals(NarouNovelPendingAlert(1L, 9_446_500L, 9_876_000L, 780), pendingAlerts[1L])
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
            predictionLengthThreshold = 1_000L,
            lastAlertedLength = 9_445_269L,
            lastAlertedAuthorProfileLength = 9_876_000L,
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
        assertEquals(2, service.sentMessageContents.size)
        assertEquals(2, service.sentEmbeds.size)
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
            predictionLengthThreshold = 1_000L,
            lastAlertedLength = 9_445_269L,
            lastAlertedAuthorProfileLength = 9_876_000L,
            lastAlertedGeneralAllNo = 778
        )
        whenever(narouNovelApiClient.fetchNovel()).thenReturn(listOf(payload(length = 9_446_500, generalAllNo = 780)))
        whenever(narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE)).thenReturn(Optional.of(snapshot))
        whenever(narouNovelAlertSettingsRepository.findAll()).thenReturn(listOf(settings))
        whenever(jda.getTextChannelById(11L)).thenReturn(textChannel)

        service.pollNovel()
        service.pollNovel()

        assertEquals(1, service.sentMessageContents.size)
        assertEquals(1, service.sentEmbeds.size)
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
            predictionLengthThreshold = 1_000L,
            lastAlertedLength = 9_445_269L,
            lastAlertedAuthorProfileLength = 9_876_000L,
            lastAlertedGeneralAllNo = 778
        )
        whenever(narouNovelApiClient.fetchNovel()).thenReturn(listOf(payload(length = 9_446_500, generalAllNo = 780)))
        whenever(narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE)).thenReturn(Optional.of(snapshot))
        whenever(narouNovelAlertSettingsRepository.findAll()).thenReturn(listOf(settings))
        whenever(jda.getTextChannelById(11L)).thenReturn(null)

        service.pollNovel()
        service.pollNovel()

        assertEquals(emptyList<String>(), service.sentMessageContents)
        assertEquals(emptyList<MessageEmbed>(), service.sentEmbeds)
        assertNull(settings.channelId)
        assertEquals(emptyMap<Long, NarouNovelPendingAlert>(), pendingAlerts)
    }

    @Test
    fun `missing baselines are initialized without alerting`() {
        val snapshot = snapshot(length = 9_445_500, generalAllNo = 779)
        val settings = NarouNovelAlertSettings(
            guildId = 1L,
            channelId = 11L,
            lastAlertedLength = null,
            lastAlertedAuthorProfileLength = null,
            lastAlertedGeneralAllNo = null
        )
        whenever(narouNovelApiClient.fetchNovel()).thenReturn(listOf(payload(length = 9_445_500, generalAllNo = 779)))
        whenever(narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE)).thenReturn(Optional.of(snapshot))
        whenever(narouNovelAlertSettingsRepository.findAll()).thenReturn(listOf(settings))

        service.pollNovel()

        verify(jda, never()).getTextChannelById(11L)
        val settingsCaptor = argumentCaptor<NarouNovelAlertSettings>()
        verify(narouNovelAlertSettingsRepository).save(settingsCaptor.capture())
        assertEquals(9_445_500L, settingsCaptor.lastValue.lastAlertedLength)
        assertEquals(9_876_000L, settingsCaptor.lastValue.lastAlertedAuthorProfileLength)
        assertEquals(779, settingsCaptor.lastValue.lastAlertedGeneralAllNo)
    }

    @Test
    fun `poll ignores incomplete author profile entries`() {
        whenever(narouNovelApiClient.fetchNovel()).thenReturn(listOf(payload(length = 9_445_269, generalAllNo = 778)))
        whenever(narouNovelApiClient.fetchAuthorProfile()).thenReturn(listOf(NarouAuthorProfileApiResponseEntry(), profilePayload(9_876_000)))
        whenever(narouNovelSnapshotRepository.findById(NarouNovelPollingService.NOVEL_CODE)).thenReturn(Optional.empty())
        whenever(narouNovelAlertSettingsRepository.findAll()).thenReturn(emptyList())

        service.pollNovel()

        val snapshotCaptor = argumentCaptor<NarouNovelSnapshot>()
        verify(narouNovelSnapshotRepository).save(snapshotCaptor.capture())
        assertEquals(9_876_000L, snapshotCaptor.firstValue.authorProfileLength)
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

    private fun profilePayload(novelLength: Long): NarouAuthorProfileApiResponseEntry {
        return NarouAuthorProfileApiResponseEntry(novelLength = novelLength)
    }

    private fun assertAlert(
        service: TestNarouNovelPollingService,
        description: String,
        totalChapters: Int,
        totalCharacters: Long,
        chapterLink: String,
        index: Int = 0
    ) {
        assertEquals("@everyone", service.sentMessageContents[index])
        val embed = service.sentEmbeds[index]
        assertEquals("Narou update for n2267be", embed.title)
        assertEquals(description, embed.description)
        assertEquals(totalChapters.toString(), embed.fieldValue("Total chapters"))
        assertEquals(totalCharacters.toString(), embed.fieldValue("Total characters"))
        assertEquals(chapterLink, embed.fieldValue("Chapter link"))
    }

    private fun MessageEmbed.fieldValue(name: String): String? {
        return fields.firstOrNull { it.name == name }?.value
    }

    private fun snapshot(length: Long, generalAllNo: Int, authorProfileLength: Long = 9_876_000): NarouNovelSnapshot {
        return NarouNovelSnapshot(
            ncode = NarouNovelPollingService.NOVEL_CODE,
            generalLastup = "2026-05-15 07:00:00",
            generalAllNo = generalAllNo,
            length = length,
            authorProfileLength = authorProfileLength,
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
        val sentMessageContents = mutableListOf<String>()
        val sentEmbeds = mutableListOf<MessageEmbed>()

        override fun isTerminalChannelFailure(exception: Throwable): Boolean {
            return terminalFailurePredicate(exception)
        }

        override fun sendAlertMessage(
            channel: TextChannel,
            messageContent: String,
            embed: MessageEmbed,
            onSuccess: () -> Unit,
            onFailure: (Throwable) -> Unit
        ) {
            sentMessageContents += messageContent
            sentEmbeds += embed
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
