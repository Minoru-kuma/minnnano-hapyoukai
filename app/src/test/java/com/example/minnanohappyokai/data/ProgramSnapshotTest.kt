package com.example.minnanohappyokai.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgramSnapshotTest {
    @Test
    fun finalizedSnapshotCopiesVisibleValuesInDisplayOrder() {
        val snapshot = ProgramSnapshot(
            activeRecital = Recital(id = 1, name = "架空の発表会", venue = "架空会場"),
            sections = listOf(Section(id = 1, recitalId = 1, name = "自由な部名", displayOrder = 0)),
            performers = listOf(Performer(id = 1, name = "架空生徒", type = PerformerType.STUDENT, grade = "小3")),
            participants = listOf(RecitalParticipant(1, 1)),
            performances = listOf(Performance(id = 1, sectionId = 1, displayOrder = 0)),
            members = listOf(PerformanceMember(1, 1, 0)),
            pieces = listOf(Piece(1, 1, "架空曲", 0, composerDisplayText = "  架空表記  ")),
        )

        val finalized = snapshot.toFinalizedProgramSnapshot()

        assertEquals("架空の発表会", finalized.recital.name)
        assertEquals("架空生徒", finalized.sections.single().performances.single().performers.single().name)
        assertEquals("小3", finalized.sections.single().performances.single().performers.single().grade)
        assertEquals("  架空表記  ", finalized.sections.single().performances.single().pieces.single().composerDisplayText)
    }

    @Test
    fun gradeProgressionOnlySuggestsKnownNextGrades() {
        assertEquals("小4", GradeProgression.nextGrade("小3"))
        assertEquals("高3", GradeProgression.nextGrade("高2"))
        assertNull(GradeProgression.nextGrade("高3"))
        assertNull(GradeProgression.nextGrade("個別の学年表記"))
    }
}
