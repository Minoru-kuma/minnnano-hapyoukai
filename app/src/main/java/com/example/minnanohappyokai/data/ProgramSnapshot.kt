package com.example.minnanohappyokai.data

/** A consistent local database snapshot for lists, editors, and program previews. */
data class ProgramSnapshot(
    val recitals: List<Recital> = emptyList(),
    val sections: List<Section> = emptyList(),
    val performers: List<Performer> = emptyList(),
    val performances: List<Performance> = emptyList(),
    val members: List<PerformanceMember> = emptyList(),
    val pieces: List<Piece> = emptyList(),
    val composers: List<Composer> = emptyList(),
    val aliases: List<ComposerAlias> = emptyList(),
)
