package com.example.minnanohappyokai.data

/** A consistent local view: master directories plus only the one current recital's working data. */
data class ProgramSnapshot(
    val activeRecital: Recital? = null,
    val sections: List<Section> = emptyList(),
    val performers: List<Performer> = emptyList(),
    val participants: List<RecitalParticipant> = emptyList(),
    val performances: List<Performance> = emptyList(),
    val members: List<PerformanceMember> = emptyList(),
    val pieces: List<Piece> = emptyList(),
    val composers: List<Composer> = emptyList(),
    val aliases: List<ComposerAlias> = emptyList(),
)

/**
 * Immutable values needed by final PDF/archive generation. It intentionally copies display names,
 * current grades and composer display text instead of retaining live directory references.
 */
data class FinalizedProgramSnapshot(
    val recital: FinalizedRecital,
    val sections: List<FinalizedSection>,
)

data class FinalizedRecital(
    val name: String,
    val dateEpochDay: Long?,
    val venue: String,
)

data class FinalizedSection(
    val sectionId: Long,
    val name: String,
    val displayOrder: Int,
    val performances: List<FinalizedPerformance>,
)

data class FinalizedPerformance(
    val performanceId: Long,
    val displayOrder: Int,
    val performers: List<FinalizedPerformer>,
    val pieces: List<FinalizedPiece>,
)

data class FinalizedPerformer(
    val performerId: Long,
    val name: String,
    val type: PerformerType,
    val grade: String?,
)

data class FinalizedPiece(
    val composerId: Long?,
    val title: String,
    val composerDisplayText: String,
)

fun ProgramSnapshot.toFinalizedProgramSnapshot(): FinalizedProgramSnapshot {
    val recital = checkNotNull(activeRecital) { "There is no current recital to finalize" }
    val performersById = performers.associateBy { it.id }
    val membersByPerformance = members.groupBy { it.performanceId }
    val piecesByPerformance = pieces.groupBy { it.performanceId }
    val performancesBySection = performances.groupBy { it.sectionId }

    return FinalizedProgramSnapshot(
        recital = FinalizedRecital(recital.name, recital.dateEpochDay, recital.venue),
        sections = sections.sortedWith(compareBy(Section::displayOrder, Section::id)).map { section ->
            FinalizedSection(
                sectionId = section.id,
                name = section.name,
                displayOrder = section.displayOrder,
                performances = performancesBySection[section.id].orEmpty()
                    .sortedWith(compareBy(Performance::displayOrder, Performance::id))
                    .map { performance ->
                        FinalizedPerformance(
                            performanceId = performance.id,
                            displayOrder = performance.displayOrder,
                            performers = membersByPerformance[performance.id].orEmpty()
                                .sortedWith(compareBy(PerformanceMember::displayOrder, PerformanceMember::performerId))
                                .map { member ->
                                    val person = checkNotNull(performersById[member.performerId]) {
                                        "A performance member is missing from the directory"
                                    }
                                    FinalizedPerformer(person.id, person.name, person.type, person.grade)
                                },
                            pieces = piecesByPerformance[performance.id].orEmpty()
                                .sortedWith(compareBy(Piece::displayOrder, Piece::id))
                                .map { piece ->
                                    FinalizedPiece(piece.composerId, piece.title, piece.composerDisplayText)
                                },
                        )
                    },
            )
        },
    )
}
