package be.duncanc.discordmodbot.narou.novel.api

import be.duncanc.discordmodbot.narou.novel.api.persistence.NarouNovelAlertSettings
import be.duncanc.discordmodbot.narou.novel.api.persistence.NarouNovelAlertSettingsRepository
import be.duncanc.discordmodbot.narou.novel.api.persistence.NarouNovelSnapshot
import be.duncanc.discordmodbot.narou.novel.api.persistence.NarouNovelSnapshotRepository
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
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
    private lateinit var jda: JDA

    @Mock
    private lateinit var textChannel: TextChannel

    private lateinit var service: TestNarouNovelPollingService

    @BeforeEach
    fun setUp() {
        service = TestNarouNovelPollingService(
            narouNovelApiClient,
            narouNovelSnapshotRepository,
            narouNovelAlertSettingsRepository,
            jda
        )
    }

    @Test
    fun `first poll ignores allcount and stores latest payload`() {
        whenever(narouNovelApiClient.fetchNovel()).thenReturn(
            listOf(
                NarouNovelApiResponseEntry(allcount = 1),
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

        assertEquals(listOf("Narou update for n2267be: 1 new chapter was published. Total chapters: 779. Total characters: 9445500. https://ncode.syosetu.com/n2267be/"), service.sentMessages)
        val settingsCaptor = argumentCaptor<NarouNovelAlertSettings>()
        verify(narouNovelAlertSettingsRepository).save(settingsCaptor.capture())
        assertEquals(9_445_269L, settingsCaptor.lastValue.lastAlertedLength)
        assertEquals(779, settingsCaptor.lastValue.lastAlertedGeneralAllNo)
    }

    @Test
    fun `length alert triggers when threshold is reached`() {
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

        assertEquals(listOf("Narou update for n2267be: the novel grew by 1231 characters. Total chapters: 778. Total characters: 9446500. https://ncode.syosetu.com/n2267be/"), service.sentMessages)
        val settingsCaptor = argumentCaptor<NarouNovelAlertSettings>()
        verify(narouNovelAlertSettingsRepository).save(settingsCaptor.capture())
        assertEquals(9_446_500L, settingsCaptor.lastValue.lastAlertedLength)
        assertEquals(778, settingsCaptor.lastValue.lastAlertedGeneralAllNo)
    }

    @Test
    fun `combined alert updates both baselines`() {
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
                "Narou update for n2267be: 2 new chapters were published and the novel grew by 1231 characters. Total chapters: 780. Total characters: 9446500. https://ncode.syosetu.com/n2267be/"
            ),
            service.sentMessages
        )
        val settingsCaptor = argumentCaptor<NarouNovelAlertSettings>()
        verify(narouNovelAlertSettingsRepository).save(settingsCaptor.capture())
        assertEquals(9_446_500L, settingsCaptor.lastValue.lastAlertedLength)
        assertEquals(780, settingsCaptor.lastValue.lastAlertedGeneralAllNo)
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

    private class TestNarouNovelPollingService(
        narouNovelApiClient: NarouNovelApiClient,
        narouNovelSnapshotRepository: NarouNovelSnapshotRepository,
        narouNovelAlertSettingsRepository: NarouNovelAlertSettingsRepository,
        jda: JDA
    ) : NarouNovelPollingService(
        narouNovelApiClient,
        narouNovelSnapshotRepository,
        narouNovelAlertSettingsRepository,
        jda
    ) {
        val sentMessages = mutableListOf<String>()

        override fun sendAlertMessage(
            channel: TextChannel,
            message: String,
            onSuccess: () -> Unit,
            onFailure: (Throwable) -> Unit
        ) {
            sentMessages += message
            onSuccess()
        }
    }
}
