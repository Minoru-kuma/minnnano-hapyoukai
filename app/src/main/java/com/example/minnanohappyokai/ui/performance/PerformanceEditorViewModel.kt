package com.example.minnanohappyokai.ui.performance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.minnanohappyokai.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class PerformanceEditorViewModel(
    private val repository: RecitalRepository,
    val sectionId: Long,
    val performanceId: Long?,
) : ViewModel() {
    var program by mutableStateOf<ProgramSnapshot?>(null)
        private set
    var loadFailed by mutableStateOf(false)
        private set
    var members by mutableStateOf<List<Long>>(emptyList())
        private set
    var pieces by mutableStateOf<List<NewPiece>>(emptyList())
        private set
    var page by mutableStateOf("main")
    var query by mutableStateOf("")
    var pieceIndex by mutableStateOf(-1)
    var pieceDraft by mutableStateOf(NewPiece(""))
    private var originalPiece = NewPiece("")
    private var originalMembers = emptyList<Long>()
    private var originalPieces = emptyList<NewPiece>()
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var memberError by mutableStateOf(false)
    var titleError by mutableStateOf(false)
    var done by mutableStateOf(false)
        private set
    var discard by mutableStateOf(false)
    var deletePerformance by mutableStateOf(false)
    var deletePieceIndex by mutableStateOf<Int?>(null)
    var performerDialog by mutableStateOf(false)
    var performerName by mutableStateOf("")
    var performerType by mutableStateOf<PerformerType?>(null)
    var performerGrade by mutableStateOf("")
    var performerValidation by mutableStateOf(false)
    var composerDialog by mutableStateOf(false)
    var composerName by mutableStateOf("")
    var composerValidation by mutableStateOf(false)
    var notice by mutableStateOf<String?>(null)
    private var initialized = false
    private var observation: Job? = null
    val dirty get() = members != originalMembers || pieces != originalPieces
    val pieceDirty get() = pieceDraft != originalPiece
    val missing get() = program?.let { snapshot ->
        snapshot.sections.none { it.id == sectionId } ||
            (performanceId != null && snapshot.performances.none { it.id == performanceId && it.sectionId == sectionId })
    } ?: false

    init { retry() }

    fun retry() {
        observation?.cancel()
        loadFailed = false
        observation = viewModelScope.launch {
            try {
                repository.observeProgram().collect { snapshot ->
                    program = snapshot
                    if (!initialized) {
                        members = snapshot.members.filter { it.performanceId == performanceId }
                            .sortedBy { it.displayOrder }.map { it.performerId }
                        pieces = snapshot.pieces.filter { it.performanceId == performanceId }
                            .sortedBy { it.displayOrder }
                            .map { NewPiece(it.title, it.composerId, it.composerDisplayText) }
                        originalMembers = members
                        originalPieces = pieces
                        initialized = true
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { loadFailed = true }
        }
    }

    fun toggleMember(id: Long) {
        if (id !in members && program?.participants?.none { it.performerId == id } != false) return
        members = if (id in members) members - id else members + id
        memberError = false
    }

    fun moveMember(index: Int, delta: Int) { members = members.moved(index, delta) }
    fun movePiece(index: Int, delta: Int) { pieces = pieces.moved(index, delta) }
    fun removePiece(index: Int) { pieces = pieces.filterIndexed { i, _ -> i != index } }

    fun editPiece(index: Int) {
        pieceIndex = index
        pieceDraft = pieces.getOrNull(index) ?: NewPiece("")
        originalPiece = pieceDraft
        titleError = false
        query = pieceDraft.composerDisplayText
        notice = null
        page = "piece"
    }

    fun applyPiece(): Boolean {
        titleError = pieceDraft.title.isBlank()
        if (titleError) return false
        pieces = if (pieceIndex < 0) pieces + pieceDraft else pieces.toMutableList().also { it[pieceIndex] = pieceDraft }
        page = "main"
        return true
    }

    fun requestBack(onBack: () -> Unit) {
        if (busy) return
        when (page) {
            "performers" -> page = "main"
            "piece" -> if (pieceDirty) discard = true else page = "main"
            else -> if (dirty) discard = true else onBack()
        }
    }

    fun confirmDiscard(onBack: () -> Unit) {
        discard = false
        if (page == "piece") page = "main" else onBack()
    }

    fun save() {
        memberError = members.isEmpty()
        if (memberError || missing) return
        mutate {
            if (performanceId == null) repository.createPerformance(sectionId, members, pieces)
            else repository.updatePerformanceProgram(performanceId, members, pieces)
            done = true
        }
    }

    fun removePerformance() = mutate {
        program?.performances?.firstOrNull { it.id == performanceId }?.let { repository.deletePerformance(it) }
        done = true
    }

    fun registerPerformer() {
        performerValidation = true
        val type = performerType ?: return
        if (performerName.isBlank()) return
        mutate {
            val id = repository.createCurrentParticipantPerformer(performerName, type,
                performerGrade.takeIf { type == PerformerType.STUDENT && it.isNotBlank() })
            members = members + id
            memberError = false
            performerDialog = false
            performerName = ""
            performerGrade = ""
            performerType = null
            performerValidation = false
        }
    }

    fun registerComposer() {
        composerValidation = true
        if (composerName.isBlank()) return
        mutate {
            val id = repository.createComposer(composerName)
            pieceDraft = pieceDraft.copy(composerId = id)
            composerDialog = false
            composerName = ""
            composerValidation = false
            notice = "作曲家を登録して関連付けました。入力した表記は保持しています"
        }
    }

    fun registerAlias() {
        val id = pieceDraft.composerId ?: return
        val text = pieceDraft.composerDisplayText
        if (text.isBlank()) return
        if (program?.aliases?.any { it.composerId == id && it.displayName == text } == true) {
            notice = "この表記は登録済みです"
            return
        }
        mutate {
            repository.createComposerAlias(id, text)
            notice = "この表記を候補に追加しました"
        }
    }

    private fun mutate(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "保存できませんでした。もう一度お試しください" }
            finally { busy = false }
        }
    }
}

private fun <T> List<T>.moved(index: Int, delta: Int): List<T> {
    val target = index + delta
    if (index !in indices || target !in indices) return this
    return toMutableList().also { list -> list.add(target, list.removeAt(index)) }
}
