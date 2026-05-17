package be.duncanc.discordmodbot.narou.novel.api.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "narou_novel_snapshots")
data class NarouNovelSnapshot(
    @Id
    @Column(updatable = false)
    val ncode: String,
    @Column(name = "general_lastup", nullable = false)
    var generalLastup: String,
    @Column(name = "general_all_no", nullable = false)
    var generalAllNo: Int,
    @Column(name = "length_value", nullable = false)
    var length: Long,
    @Column(name = "author_profile_length", nullable = false)
    var authorProfileLength: Long,
    @Column(name = "reading_time", nullable = false)
    var time: Int,
    @Column(name = "novel_updated_at", nullable = false)
    var novelUpdatedAt: String,
    @Column(name = "api_updated_at", nullable = false)
    var updatedAt: String
)
