package com.example.demoapplication.firebase

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class FirestoreUser(
    val id: String = "",
    val idNumber: String = "",
    val name: String = "",
    val email: String = "",
    val isAdmin: Boolean = false,
    val hasVoted: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

data class FirestoreElection(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val date: String = "",
    val location: String = "",
    val status: String = "UPCOMING", // ACTIVE, UPCOMING, COMPLETED
    val totalVoters: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String = ""
)

data class FirestoreCandidate(
    val id: String = "",
    val electionId: String = "",
    val name: String = "",
    val party: String = "",
    val symbol: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val voteCount: Int = 0
)

data class FirestoreVote(
    val id: String = "",
    val voterId: String = "",
    val voterName: String = "",
    val voterIdNumber: String = "",
    val electionId: String = "",
    val electionTitle: String = "",
    val candidateId: String = "",
    val candidateName: String = "",
    val candidateParty: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isVerified: Boolean = true
)

data class FirestoreAuditLog(
    val id: String = "",
    val action: String = "",
    val userId: String = "",
    val userName: String = "",
    val userIdNumber: String = "",
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class FirestoreResult(
    val electionId: String = "",
    val electionTitle: String = "",
    val winnerId: String = "",
    val winnerName: String = "",
    val winnerParty: String = "",
    val winnerSymbol: String = "",
    val totalVotes: Int = 0,
    val turnout: Int = 0,
    val results: Map<String, Int> = emptyMap(),
    val publishedAt: Long = System.currentTimeMillis()
)

class FirestoreService {
    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "FirestoreService"
        private const val USERS_COLLECTION = "users"
        private const val ELECTIONS_COLLECTION = "elections"
        private const val CANDIDATES_COLLECTION = "candidates"
        private const val VOTES_COLLECTION = "votes"
        private const val AUDIT_LOGS_COLLECTION = "audit_logs"
        private const val RESULTS_COLLECTION = "results"
    }

    // ============ AUTHENTICATION ============

    suspend fun loginWithIdNumber(idNumber: String, password: String): Result<FirestoreUser> {
        return try {
            val email = "${idNumber.lowercase()}@smartballot.com"
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = getUserFromFirestore(result.user?.uid ?: "")
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(Exception("User not found in database"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login failed", e)
            Result.failure(e)
        }
    }

    suspend fun signUpWithIdNumber(idNumber: String, name: String, password: String): Result<FirestoreUser> {
        return try {
            val email = "${idNumber.lowercase()}@smartballot.com"
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val userId = result.user?.uid ?: throw Exception("User creation failed")

            val isAdmin = idNumber.startsWith("ADMIN")
            val user = FirestoreUser(
                id = userId,
                idNumber = idNumber,
                name = name,
                email = email,
                isAdmin = isAdmin,
                createdAt = System.currentTimeMillis()
            )

            db.collection(USERS_COLLECTION).document(userId).set(user).await()
            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "Signup failed", e)
            Result.failure(e)
        }
    }

    private suspend fun getUserFromFirestore(userId: String): FirestoreUser? {
        return try {
            val document = db.collection(USERS_COLLECTION).document(userId).get().await()
            document.toObject(FirestoreUser::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    fun logout() {
        auth.signOut()
    }

    // ============ REAL-TIME LISTENERS ============

    fun listenToElections(): Flow<List<FirestoreElection>> = callbackFlow {
        val listener = db.collection(ELECTIONS_COLLECTION)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val elections = snapshot?.toObjects(FirestoreElection::class.java) ?: emptyList()
                trySend(elections)
            }
        awaitClose { listener.remove() }
    }

    fun listenToActiveElections(): Flow<List<FirestoreElection>> = callbackFlow {
        val listener = db.collection(ELECTIONS_COLLECTION)
            .whereEqualTo("status", "ACTIVE")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val elections = snapshot?.toObjects(FirestoreElection::class.java) ?: emptyList()
                trySend(elections)
            }
        awaitClose { listener.remove() }
    }

    fun listenToCandidates(electionId: String): Flow<List<FirestoreCandidate>> = callbackFlow {
        val listener = db.collection(CANDIDATES_COLLECTION)
            .whereEqualTo("electionId", electionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val candidates = snapshot?.toObjects(FirestoreCandidate::class.java) ?: emptyList()
                trySend(candidates)
            }
        awaitClose { listener.remove() }
    }

    fun listenToVotes(electionId: String): Flow<List<FirestoreVote>> = callbackFlow {
        val listener = db.collection(VOTES_COLLECTION)
            .whereEqualTo("electionId", electionId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val votes = snapshot?.toObjects(FirestoreVote::class.java) ?: emptyList()
                trySend(votes)
            }
        awaitClose { listener.remove() }
    }

    fun listenToPublishedResults(): Flow<List<FirestoreResult>> = callbackFlow {
        val listener = db.collection(RESULTS_COLLECTION)
            .orderBy("publishedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val results = snapshot?.toObjects(FirestoreResult::class.java) ?: emptyList()
                trySend(results)
            }
        awaitClose { listener.remove() }
    }

    fun listenToAuditLogs(): Flow<List<FirestoreAuditLog>> = callbackFlow {
        val listener = db.collection(AUDIT_LOGS_COLLECTION)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val logs = snapshot?.toObjects(FirestoreAuditLog::class.java) ?: emptyList()
                trySend(logs)
            }
        awaitClose { listener.remove() }
    }

    // ============ CRUD OPERATIONS ============

    suspend fun createElection(election: FirestoreElection): Result<String> {
        return try {
            val electionId = UUID.randomUUID().toString()
            val electionWithId = election.copy(id = electionId)
            db.collection(ELECTIONS_COLLECTION).document(electionId).set(electionWithId).await()

            // Add audit log
            addAuditLog(
                FirestoreAuditLog(
                    id = UUID.randomUUID().toString(),
                    action = "ELECTION_CREATED",
                    userId = getCurrentUserId() ?: "",
                    userName = "Admin",
                    userIdNumber = "ADMIN",
                    details = "Created election: ${election.title}"
                )
            )
            Result.success(electionId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateElection(electionId: String, election: FirestoreElection): Result<Unit> {
        return try {
            db.collection(ELECTIONS_COLLECTION).document(electionId).set(election).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteElection(electionId: String): Result<Unit> {
        return try {
            // Delete all candidates first
            val candidates = db.collection(CANDIDATES_COLLECTION)
                .whereEqualTo("electionId", electionId)
                .get()
                .await()

            candidates.forEach { candidate ->
                candidate.reference.delete().await()
            }

            // Delete all votes
            val votes = db.collection(VOTES_COLLECTION)
                .whereEqualTo("electionId", electionId)
                .get()
                .await()

            votes.forEach { vote ->
                vote.reference.delete().await()
            }

            // Delete election
            db.collection(ELECTIONS_COLLECTION).document(electionId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addCandidate(candidate: FirestoreCandidate): Result<String> {
        return try {
            val candidateId = UUID.randomUUID().toString()
            val candidateWithId = candidate.copy(id = candidateId)
            db.collection(CANDIDATES_COLLECTION).document(candidateId).set(candidateWithId).await()
            Result.success(candidateId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateCandidate(candidateId: String, candidate: FirestoreCandidate): Result<Unit> {
        return try {
            db.collection(CANDIDATES_COLLECTION).document(candidateId).set(candidate).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCandidate(candidateId: String): Result<Unit> {
        return try {
            db.collection(CANDIDATES_COLLECTION).document(candidateId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun castVote(vote: FirestoreVote): Result<Unit> {
        return try {
            // Use a deterministic ID to prevent double voting (voterId + electionId)
            val voteId = "${vote.voterId}_${vote.electionId}"
            val voteWithId = vote.copy(id = voteId)

            // Use transaction to ensure atomic operation
            db.runTransaction { transaction ->
                val voteRef = db.collection(VOTES_COLLECTION).document(voteId)
                val candidateRef = db.collection(CANDIDATES_COLLECTION).document(vote.candidateId)

                // Check if user already voted in this election by trying to get the vote document
                val voteSnapshot = transaction.get(voteRef)
                if (voteSnapshot.exists()) {
                    throw Exception("User has already voted in this election")
                }

                // Add vote
                transaction.set(voteRef, voteWithId)

                // Increment candidate vote count
                val candidateSnapshot = transaction.get(candidateRef)
                val candidate = candidateSnapshot.toObject(FirestoreCandidate::class.java)
                if (candidate != null) {
                    transaction.update(candidateRef, "voteCount", candidate.voteCount + 1)
                }
            }.await()

            // Add audit log
            addAuditLog(
                FirestoreAuditLog(
                    id = UUID.randomUUID().toString(),
                    action = "VOTE_CAST",
                    userId = vote.voterId,
                    userName = vote.voterName,
                    userIdNumber = vote.voterIdNumber,
                    details = "Voted for ${vote.candidateName} in ${vote.electionTitle}"
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Cast vote failed", e)
            Result.failure(e)
        }
    }

    suspend fun publishResults(result: FirestoreResult): Result<Unit> {
        return try {
            db.collection(RESULTS_COLLECTION).document(result.electionId).set(result).await()

            // Update election status to COMPLETED
            val electionRef = db.collection(ELECTIONS_COLLECTION).document(result.electionId)
            electionRef.update("status", "COMPLETED").await()

            // Add audit log
            addAuditLog(
                FirestoreAuditLog(
                    id = UUID.randomUUID().toString(),
                    action = "RESULTS_PUBLISHED",
                    userId = getCurrentUserId() ?: "",
                    userName = "Admin",
                    userIdNumber = "ADMIN",
                    details = "Published results for ${result.electionTitle}. Winner: ${result.winnerName} with ${result.totalVotes} votes"
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun addAuditLog(log: FirestoreAuditLog) {
        try {
            db.collection(AUDIT_LOGS_COLLECTION).document(log.id).set(log).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add audit log", e)
        }
    }

    suspend fun getElectionResults(electionId: String): Map<String, Int> {
        return try {
            val votes = db.collection(VOTES_COLLECTION)
                .whereEqualTo("electionId", electionId)
                .get()
                .await()

            votes.documents
                .mapNotNull { it.toObject(FirestoreVote::class.java) }
                .groupingBy { it.candidateId }
                .eachCount()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun getUserVotes(userId: String): List<FirestoreVote> {
        return try {
            val votes = db.collection(VOTES_COLLECTION)
                .whereEqualTo("voterId", userId)
                .get()
                .await()

            votes.documents.mapNotNull { it.toObject(FirestoreVote::class.java) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
