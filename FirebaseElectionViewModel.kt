package com.example.demoapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.demoapplication.firebase.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FirebaseElectionViewModel(
    private val firestoreService: FirestoreService
) : ViewModel() {

    private val _elections = MutableStateFlow<List<FirestoreElection>>(emptyList())
    val elections: StateFlow<List<FirestoreElection>> = _elections.asStateFlow()

    private val _activeElections = MutableStateFlow<List<FirestoreElection>>(emptyList())
    val activeElections: StateFlow<List<FirestoreElection>> = _activeElections.asStateFlow()

    private val _candidates = MutableStateFlow<Map<String, List<FirestoreCandidate>>>(emptyMap())
    val candidates: StateFlow<Map<String, List<FirestoreCandidate>>> = _candidates.asStateFlow()

    private val _votes = MutableStateFlow<Map<String, List<FirestoreVote>>>(emptyMap())
    val votes: StateFlow<Map<String, List<FirestoreVote>>> = _votes.asStateFlow()

    private val _publishedResults = MutableStateFlow<List<FirestoreResult>>(emptyList())
    val publishedResults: StateFlow<List<FirestoreResult>> = _publishedResults.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<FirestoreAuditLog>>(emptyList())
    val auditLogs: StateFlow<List<FirestoreAuditLog>> = _auditLogs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        setupRealtimeListeners()
    }

    private fun setupRealtimeListeners() {
        viewModelScope.launch {
            firestoreService.listenToElections().collect { electionsList ->
                _elections.value = electionsList
                _activeElections.value = electionsList.filter { it.status == "ACTIVE" }
            }
        }

        viewModelScope.launch {
            firestoreService.listenToPublishedResults().collect { results ->
                _publishedResults.value = results
            }
        }

        viewModelScope.launch {
            firestoreService.listenToAuditLogs().collect { logs ->
                _auditLogs.value = logs
            }
        }
    }

    fun listenToCandidates(electionId: String) {
        viewModelScope.launch {
            firestoreService.listenToCandidates(electionId).collect { candidatesList ->
                _candidates.value = _candidates.value.toMutableMap().apply {
                    put(electionId, candidatesList)
                }
            }
        }
    }

    fun listenToVotes(electionId: String) {
        viewModelScope.launch {
            firestoreService.listenToVotes(electionId).collect { votesList ->
                _votes.value = _votes.value.toMutableMap().apply {
                    put(electionId, votesList)
                }
            }
        }
    }

    suspend fun createElection(
        title: String,
        description: String,
        date: String,
        location: String,
        totalVoters: Int
    ): Boolean {
        _isLoading.value = true
        return try {
            val election = FirestoreElection(
                title = title,
                description = description,
                date = date,
                location = location,
                status = "UPCOMING",
                totalVoters = totalVoters,
                createdBy = firestoreService.getCurrentUserId() ?: ""
            )
            val result = firestoreService.createElection(election)
            result.isSuccess
        } catch (e: Exception) {
            _error.value = e.message
            false
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun addCandidate(
        electionId: String,
        name: String,
        party: String,
        symbol: String,
        description: String
    ): Boolean {
        _isLoading.value = true
        return try {
            val candidate = FirestoreCandidate(
                electionId = electionId,
                name = name,
                party = party,
                symbol = symbol,
                description = description
            )
            val result = firestoreService.addCandidate(candidate)
            result.isSuccess
        } catch (e: Exception) {
            _error.value = e.message
            false
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun castVote(
        voterId: String,
        voterName: String,
        voterIdNumber: String,
        electionId: String,
        electionTitle: String,
        candidateId: String,
        candidateName: String,
        candidateParty: String
    ): Boolean {
        _isLoading.value = true
        return try {
            val vote = FirestoreVote(
                voterId = voterId,
                voterName = voterName,
                voterIdNumber = voterIdNumber,
                electionId = electionId,
                electionTitle = electionTitle,
                candidateId = candidateId,
                candidateName = candidateName,
                candidateParty = candidateParty
            )
            val result = firestoreService.castVote(vote)
            result.isSuccess
        } catch (e: Exception) {
            _error.value = e.message
            false
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun publishResults(electionId: String, electionTitle: String): Boolean {
        _isLoading.value = true
        return try {
            val results = firestoreService.getElectionResults(electionId)
            val totalVotes = results.values.sum()
            if (totalVotes == 0) {
                _error.value = "No votes to publish"
                return false
            }

            val winnerId = results.maxByOrNull { it.value }?.key ?: return false
            val candidates = _candidates.value[electionId] ?: emptyList()
            val winner = candidates.find { it.id == winnerId }

            val turnout = calculateTurnout(electionId, totalVotes)

            val result = FirestoreResult(
                electionId = electionId,
                electionTitle = electionTitle,
                winnerId = winnerId,
                winnerName = winner?.name ?: "Unknown",
                winnerParty = winner?.party ?: "Unknown",
                winnerSymbol = winner?.symbol ?: "",
                totalVotes = totalVotes,
                turnout = turnout,
                results = results
            )

            val publishResult = firestoreService.publishResults(result)
            publishResult.isSuccess
        } catch (e: Exception) {
            _error.value = e.message
            false
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun calculateTurnout(electionId: String, totalVotes: Int): Int {
        val election = _elections.value.find { it.id == electionId }
        return if (election != null && election.totalVoters > 0) {
            (totalVotes * 100 / election.totalVoters)
        } else 0
    }

    fun clearError() {
        _error.value = null
    }
}