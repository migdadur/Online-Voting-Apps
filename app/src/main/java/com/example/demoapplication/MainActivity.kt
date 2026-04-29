package com.example.demoapplication

import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.*
import com.example.demoapplication.ui.theme.DemoApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DemoApplicationTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val authViewModel = remember { AuthViewModel() }
    val electionViewModel = remember { ElectionViewModel() }
    val voteViewModel = remember { VoteViewModel() }

    NavHost(
        navController = navController,
        startDestination = if (authViewModel.isLoggedIn) "dashboard" else "login"
    ) {
        composable("login") { LoginPage(navController, authViewModel) }
        composable("signup") { SignUpPage(navController, authViewModel) }
        composable("forgot_password") { ForgotPasswordPage(navController, authViewModel) }
        composable("dashboard") { DashboardPage(navController, authViewModel, electionViewModel, voteViewModel) }
        composable("active_elections") { ActiveElectionsPage(navController, electionViewModel, voteViewModel, authViewModel) }
        composable("upcoming_elections") { UpcomingElectionsPage(navController, electionViewModel) }
        composable("my_votes") { MyVotesPage(navController, voteViewModel, authViewModel) }
        composable("candidates") { CandidatesPage(navController, electionViewModel) }
        composable("results") { ResultsPage(navController, electionViewModel, voteViewModel) }
        composable("settings") { SettingsPage(navController, authViewModel) }
        composable("admin_add_candidate") { AdminAddCandidatePage(navController, electionViewModel) }
        composable("admin_manage_elections") { AdminManageElectionsPage(navController, electionViewModel) }
        composable("create_election") { CreateElectionPage(navController, electionViewModel) }
        composable("audit_log") { AuditLogPage(navController, voteViewModel, authViewModel) }
        composable("election_detail/{electionId}") { backStackEntry ->
            val electionId = backStackEntry.arguments?.getString("electionId") ?: ""
            ElectionDetailScreen(
                electionId = electionId,
                navController = navController,
                electionViewModel = electionViewModel,
                voteViewModel = voteViewModel,
                authViewModel = authViewModel
            )
        }
        composable("results_detail/{electionId}") { backStackEntry ->
            val electionId = backStackEntry.arguments?.getString("electionId") ?: ""
            ResultsDetailScreen(
                electionId = electionId,
                navController = navController,
                electionViewModel = electionViewModel,
                voteViewModel = voteViewModel
            )
        }
        composable("edit_candidate/{electionId}/{candidateId}") { backStackEntry ->
            val electionId = backStackEntry.arguments?.getString("electionId") ?: ""
            val candidateId = backStackEntry.arguments?.getString("candidateId") ?: ""
            EditCandidatePage(navController, electionViewModel, electionId, candidateId)
        }
    }
}

// ==================== VIEW MODELS ====================

class VoteViewModel {
    private val _votes = mutableStateOf<Map<String, Vote>>(emptyMap())
    val votes: Map<String, Vote> get() = _votes.value

    private val _auditLog = mutableStateOf<List<AuditEntry>>(emptyList())
    val auditLog: List<AuditEntry> get() = _auditLog.value

    data class Vote(
        val voterId: Int,
        val voterName: String,
        val electionId: String,
        val electionTitle: String,
        val candidateId: String,
        val candidateName: String,
        val candidateParty: String,
        val timestamp: Long
    )

    data class AuditEntry(
        val id: String,
        val action: String,
        val userId: Int,
        val userName: String,
        val details: String,
        val timestamp: Long
    )

    fun castVote(
        voterId: Int,
        voterName: String,
        electionId: String,
        electionTitle: String,
        candidateId: String,
        candidateName: String,
        candidateParty: String
    ): Boolean {
        val voteKey = "${voterId}_${electionId}"
        if (_votes.value.containsKey(voteKey)) {
            return false
        }

        val vote = Vote(
            voterId = voterId,
            voterName = voterName,
            electionId = electionId,
            electionTitle = electionTitle,
            candidateId = candidateId,
            candidateName = candidateName,
            candidateParty = candidateParty,
            timestamp = System.currentTimeMillis()
        )

        _votes.value = _votes.value + (voteKey to vote)

        addAuditEntry(
            action = "VOTE_CAST",
            userId = voterId,
            userName = voterName,
            details = "Voted for $candidateName ($candidateParty) in $electionTitle"
        )

        return true
    }

    fun hasVoted(voterId: Int, electionId: String): Boolean {
        return _votes.value.containsKey("${voterId}_${electionId}")
    }

    fun getUserVotes(voterId: Int): List<Vote> {
        return _votes.value.values.filter { it.voterId == voterId }
    }

    fun getElectionResults(electionId: String): Map<String, Int> {
        val electionVotes = _votes.value.values.filter { it.electionId == electionId }
        return electionVotes.groupingBy { it.candidateId }.eachCount()
    }

    fun getVoterTurnout(electionId: String, totalVoters: Int): Int {
        val votedCount = _votes.value.values.count { it.electionId == electionId }
        return if (totalVoters > 0) (votedCount * 100.0 / totalVoters).toInt() else 0
    }

    private fun addAuditEntry(action: String, userId: Int, userName: String, details: String) {
        val entry = AuditEntry(
            id = System.currentTimeMillis().toString(),
            action = action,
            userId = userId,
            userName = userName,
            details = details,
            timestamp = System.currentTimeMillis()
        )
        _auditLog.value = _auditLog.value + entry
    }
}

class AuthViewModel {
    private val _currentUser = mutableStateOf<User?>(null)
    val currentUser: User? get() = _currentUser.value
    val isLoggedIn: Boolean get() = _currentUser.value != null

    private val _registeredUsers = mutableStateOf<List<User>>(emptyList())
    val registeredUsers: List<User> get() = _registeredUsers.value

    data class User(
        val id: Int,
        val name: String,
        val email: String,
        val password: String,
        val hasVoted: MutableList<String>,
        val isAdmin: Boolean = false,
        val voterId: String = generateVoterId(),
        val createdAt: Long = System.currentTimeMillis()
    ) {
        companion object {
            private fun generateVoterId(): String {
                return "VOT${System.currentTimeMillis() % 1000000}${(1000..9999).random()}"
            }
        }
    }

    init {
        registerUser(
            name = "Admin User",
            email = "admin@demo.com",
            password = "admin123",
            isAdmin = true
        )
        registerUser(
            name = "Test User",
            email = "user@demo.com",
            password = "user123",
            isAdmin = false
        )
    }

    fun login(email: String, password: String): Boolean {
        val user = _registeredUsers.value.find {
            it.email.equals(email, ignoreCase = true) && it.password == password
        }
        return if (user != null) {
            _currentUser.value = user
            true
        } else {
            false
        }
    }

    fun registerUser(name: String, email: String, password: String, isAdmin: Boolean = false): Boolean {
        if (_registeredUsers.value.any { it.email.equals(email, ignoreCase = true) }) {
            return false
        }

        if (password.length < 6) {
            return false
        }

        val newUser = User(
            id = _registeredUsers.value.size + 1,
            name = name,
            email = email,
            password = password,
            hasVoted = mutableListOf(),
            isAdmin = isAdmin || _registeredUsers.value.isEmpty()
        )

        _registeredUsers.value = _registeredUsers.value + newUser
        _currentUser.value = newUser

        return true
    }

    fun signUp(name: String, email: String, password: String): Boolean {
        return registerUser(name, email, password, false)
    }

    fun logout() {
        _currentUser.value = null
    }

    fun isAdmin(): Boolean {
        return _currentUser.value?.isAdmin == true
    }

    fun markVoted(electionId: String) {
        _currentUser.value?.hasVoted?.add(electionId)
    }

    fun hasVoted(electionId: String): Boolean {
        return _currentUser.value?.hasVoted?.contains(electionId) == true
    }

    fun updateUserProfile(name: String, email: String): Boolean {
        val currentUser = _currentUser.value ?: return false

        if (email != currentUser.email && _registeredUsers.value.any { it.email.equals(email, ignoreCase = true) }) {
            return false
        }

        val userIndex = _registeredUsers.value.indexOfFirst { it.id == currentUser.id }
        if (userIndex == -1) return false

        val updatedUser = currentUser.copy(name = name, email = email)

        _registeredUsers.value = _registeredUsers.value.toMutableList().apply {
            set(userIndex, updatedUser)
        }
        _currentUser.value = updatedUser

        return true
    }

    fun changePassword(oldPassword: String, newPassword: String): Boolean {
        val currentUser = _currentUser.value ?: return false

        if (currentUser.password != oldPassword) {
            return false
        }

        if (newPassword.length < 6) {
            return false
        }

        val userIndex = _registeredUsers.value.indexOfFirst { it.id == currentUser.id }
        val updatedUser = currentUser.copy(password = newPassword)

        _registeredUsers.value = _registeredUsers.value.toMutableList().apply {
            set(userIndex, updatedUser)
        }
        _currentUser.value = updatedUser

        return true
    }

    fun deleteAccount(): Boolean {
        val currentUser = _currentUser.value ?: return false

        _registeredUsers.value = _registeredUsers.value.filter { it.id != currentUser.id }
        _currentUser.value = null

        return true
    }
}

class ElectionViewModel {
    private val _elections = mutableStateOf<List<Election>>(emptyList())
    val elections: List<Election> get() = _elections.value

    init {
        loadInitialElections()
    }

    private fun loadInitialElections() {
        _elections.value = getInitialElections()
    }

    fun addCandidate(electionId: String, candidate: Candidate): Boolean {
        val election = _elections.value.find { it.id == electionId } ?: return false

        _elections.value = _elections.value.map { currentElection ->
            if (currentElection.id == electionId) {
                currentElection.copy(
                    candidates = currentElection.candidates + candidate
                )
            } else {
                currentElection
            }
        }
        return true
    }

    fun updateCandidate(electionId: String, candidateId: String, updatedCandidate: Candidate): Boolean {
        _elections.value = _elections.value.map { currentElection ->
            if (currentElection.id == electionId) {
                currentElection.copy(
                    candidates = currentElection.candidates.map { candidate ->
                        if (candidate.id == candidateId) {
                            updatedCandidate.copy(id = candidateId)
                        } else {
                            candidate
                        }
                    }
                )
            } else {
                currentElection
            }
        }
        return true
    }

    fun removeCandidate(electionId: String, candidateId: String): Boolean {
        _elections.value = _elections.value.map { currentElection ->
            if (currentElection.id == electionId) {
                currentElection.copy(
                    candidates = currentElection.candidates.filter { it.id != candidateId }
                )
            } else {
                currentElection
            }
        }
        return true
    }

    fun addElection(election: Election): Boolean {
        if (_elections.value.any { it.title.equals(election.title, ignoreCase = true) }) {
            return false
        }

        if (election.totalVoters <= 0) {
            return false
        }

        _elections.value = _elections.value + election
        return true
    }

    fun updateElection(electionId: String, updatedElection: Election): Boolean {
        _elections.value = _elections.value.map { currentElection ->
            if (currentElection.id == electionId) {
                updatedElection.copy(
                    id = electionId,
                    candidates = currentElection.candidates
                )
            } else {
                currentElection
            }
        }
        return true
    }

    fun removeElection(electionId: String): Boolean {
        _elections.value = _elections.value.filter { it.id != electionId }
        return true
    }

    fun getElectionById(electionId: String): Election? {
        return _elections.value.find { it.id == electionId }
    }

    fun getElectionsByStatus(status: ElectionStatus): List<Election> {
        return _elections.value.filter { it.status == status }
    }

    fun updateElectionStatus(electionId: String, newStatus: ElectionStatus): Boolean {
        _elections.value = _elections.value.map { currentElection ->
            if (currentElection.id == electionId) {
                currentElection.copy(status = newStatus)
            } else {
                currentElection
            }
        }
        return true
    }
}

// ==================== DATA MODELS ====================

data class DashboardItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val route: String,
    val color: Color
)

data class Election(
    val id: String,
    val title: String,
    val description: String,
    val date: String,
    val location: String,
    val status: ElectionStatus,
    val candidates: List<Candidate>,
    val totalVoters: Int,
    val voterTurnout: Int? = null
)

data class Candidate(
    val id: String,
    val name: String,
    val party: String,
    val symbol: String,
    val description: String,
    val imageUrl: String? = null
)

enum class ElectionStatus {
    ACTIVE, UPCOMING, COMPLETED
}

// ==================== INITIAL DATA ====================

fun getInitialElections(): List<Election> = listOf(
    Election(
        id = "1",
        title = "Presidential Election 2024",
        description = "Vote for the next President of the Student Council.",
        date = "March 15, 2024 (9:00 AM - 5:00 PM)",
        location = "Main Auditorium & Online Portal",
        status = ElectionStatus.ACTIVE,
        candidates = listOf(
            Candidate("c1", "Sarah Johnson", "Progressive Student Union", "🌿", "Committed to improving campus facilities."),
            Candidate("c2", "Michael Chen", "Future Leaders Party", "⚡", "Focus on career development opportunities."),
            Candidate("c3", "Emily Rodriguez", "Student First Alliance", "📚", "Advocating for student rights.")
        ),
        totalVoters = 5000,
        voterTurnout = 1247
    ),
    Election(
        id = "2",
        title = "Department Representative - CS",
        description = "Elect your department representative.",
        date = "March 15-17, 2024",
        location = "Online Voting System",
        status = ElectionStatus.ACTIVE,
        candidates = listOf(
            Candidate("c4", "Alex Thompson", "Tech Innovators", "💻", "Expert in AI and ML."),
            Candidate("c5", "Jessica Lee", "CS Excellence", "🎯", "Industry-academia bridge."),
            Candidate("c6", "David Kim", "Code for All", "🌐", "Inclusive coding education.")
        ),
        totalVoters = 800,
        voterTurnout = 342
    ),
    Election(
        id = "3",
        title = "Vice President Election",
        description = "Choose the Vice President.",
        date = "March 25, 2024",
        location = "Student Center",
        status = ElectionStatus.UPCOMING,
        candidates = listOf(
            Candidate("c7", "Olivia Martinez", "Unity Coalition", "🤝", "Event management expert."),
            Candidate("c8", "James Wilson", "Action Party", "⚡", "Digital transformation focus."),
            Candidate("c9", "Sophia Brown", "Bridge Builders", "🌉", "Inter-department collaboration.")
        ),
        totalVoters = 5000
    ),
    Election(
        id = "4",
        title = "Class Representative 2023",
        description = "Annual class representative election.",
        date = "December 10, 2023",
        location = "Online",
        status = ElectionStatus.COMPLETED,
        candidates = listOf(
            Candidate("c10", "Thomas Anderson", "Student Voice", "🎓", "Student welfare focus."),
            Candidate("c11", "Nina Williams", "Change Makers", "⭐", "Curriculum improvement.")
        ),
        totalVoters = 1200,
        voterTurnout = 892
    )
)

// ==================== LOGIN PAGE ====================

@Composable
fun LoginPage(navController: NavController, authViewModel: AuthViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.HowToVote,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Smart Ballot",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Secure Digital Voting Platform",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        showError = false
                    },
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    isError = showError,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        showError = false
                    },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = showError,
                    shape = RoundedCornerShape(12.dp)
                )

                if (showError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Invalid email or password",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            delay(500)
                            if (authViewModel.login(email, password)) {
                                navController.navigate("dashboard") {
                                    popUpTo("login") { inclusive = true }
                                }
                            } else {
                                showError = true
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Text("Login", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { navController.navigate("signup") }) {
                        Text("Sign Up", color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = { navController.navigate("forgot_password") }) {
                        Text("Forgot Password?", color = MaterialTheme.colorScheme.primary)
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Demo Credentials:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Admin: admin@demo.com / admin123", style = MaterialTheme.typography.bodySmall)
                        Text("User: user@demo.com / user123", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

// ==================== SIGN UP PAGE ====================

@Composable
fun SignUpPage(navController: NavController, authViewModel: AuthViewModel) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Create Account",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Join the future of voting",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = password != confirmPassword && confirmPassword.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        when {
                            name.isBlank() -> errorMessage = "Please enter your name"
                            !isValidEmail(email) -> errorMessage = "Please enter a valid email"
                            password.length < 6 -> errorMessage = "Password must be at least 6 characters"
                            password != confirmPassword -> errorMessage = "Passwords do not match"
                            else -> {
                                scope.launch {
                                    isLoading = true
                                    delay(500)
                                    if (authViewModel.signUp(name, email, password)) {
                                        navController.navigate("dashboard") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    } else {
                                        errorMessage = "Email already registered"
                                    }
                                    isLoading = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Text("Register", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = { navController.popBackStack() }) {
                    Text("Already have an account? Login")
                }
            }
        }
    }
}

// ==================== FORGOT PASSWORD PAGE ====================

@Composable
fun ForgotPasswordPage(navController: NavController, authViewModel: AuthViewModel) {
    var email by remember { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Reset Password",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Enter your email to receive a reset link",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                if (showSuccess) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reset link sent to your email!", color = Color(0xFF4CAF50))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            delay(1000)
                            showSuccess = true
                            isLoading = false
                            delay(2000)
                            navController.popBackStack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && email.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Text("Send Reset Link")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = { navController.popBackStack() }) {
                    Text("Back to Login")
                }
            }
        }
    }
}

// ==================== DASHBOARD PAGE ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardPage(
    navController: NavController,
    authViewModel: AuthViewModel,
    electionViewModel: ElectionViewModel,
    voteViewModel: VoteViewModel
) {
    val isAdmin = authViewModel.isAdmin()

    val dashboardItems = buildList {
        add(DashboardItem("Active Elections", "Cast your vote now", Icons.Default.HowToVote, "active_elections", Color(0xFF4CAF50)))
        add(DashboardItem("Upcoming Elections", "Coming soon", Icons.Default.Event, "upcoming_elections", Color(0xFF2196F3)))
        add(DashboardItem("My Votes", "View voting history", Icons.Default.History, "my_votes", Color(0xFFFF9800)))
        add(DashboardItem("Candidates", "View all candidates", Icons.Default.Person, "candidates", Color(0xFF9C27B0)))
        add(DashboardItem("Results", "Election results", Icons.AutoMirrored.Filled.ShowChart, "results", Color(0xFFF44336)))
        add(DashboardItem("Settings", "Account settings", Icons.Default.Settings, "settings", Color(0xFF607D8B)))

        if (isAdmin) {
            add(DashboardItem("Add Candidate", "Add new candidates", Icons.Default.PersonAdd, "admin_add_candidate", Color(0xFFE91E63)))
            add(DashboardItem("Manage Elections", "Create/edit elections", Icons.Default.Edit, "admin_manage_elections", Color(0xFF673AB7)))
            add(DashboardItem("Audit Log", "View system logs", Icons.Default.History, "audit_log", Color(0xFF795548)))
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Smart Ballot", fontWeight = FontWeight.Bold)
                        Text(
                            "Welcome, ${authViewModel.currentUser?.name?.split(" ")?.first() ?: "Voter"}${if (isAdmin) " (Admin)" else ""}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        authViewModel.logout()
                        navController.navigate("login") {
                            popUpTo("dashboard") { inclusive = true }
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                StatsCard(electionViewModel, voteViewModel)
            }
            items(dashboardItems.chunked(2)) { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    rowItems.forEach { item ->
                        DashboardCard(
                            item = item,
                            modifier = Modifier.weight(1f)
                        ) {
                            navController.navigate(item.route)
                        }
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun StatsCard(electionViewModel: ElectionViewModel, voteViewModel: VoteViewModel) {
    val elections = electionViewModel.elections
    val activeCount = elections.count { it.status == ElectionStatus.ACTIVE }
    val upcomingCount = elections.count { it.status == ElectionStatus.UPCOMING }
    val completedCount = elections.count { it.status == ElectionStatus.COMPLETED }
    val totalVotes = voteViewModel.votes.size

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("Active", activeCount.toString(), Color(0xFF4CAF50))
            StatItem("Upcoming", upcomingCount.toString(), Color(0xFF2196F3))
            StatItem("Completed", completedCount.toString(), Color(0xFF9E9E9E))
            StatItem("Total Votes", totalVotes.toString(), Color(0xFFFF9800))
        }
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
fun DashboardCard(item: DashboardItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(item.color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    item.icon,
                    contentDescription = null,
                    tint = item.color,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                item.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// ==================== ADMIN ADD CANDIDATE PAGE ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAddCandidatePage(navController: NavController, electionViewModel: ElectionViewModel) {
    var selectedElectionId by remember { mutableStateOf<String?>(null) }
    var candidateName by remember { mutableStateOf("") }
    var candidateParty by remember { mutableStateOf("") }
    var candidateSymbol by remember { mutableStateOf("") }
    var candidateDescription by remember { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val elections = electionViewModel.elections
    val selectedElection = selectedElectionId?.let { electionViewModel.getElectionById(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add New Candidate") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Select Election",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    var expanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedElection?.let { "${it.title} (${it.status.name})" } ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Election") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            elections.forEach { election ->
                                DropdownMenuItem(
                                    text = { Text("${election.title} (${election.status.name})") },
                                    onClick = {
                                        selectedElectionId = election.id
                                        expanded = false
                                        errorMessage = null
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (selectedElection != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Candidate Information",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = candidateName,
                            onValueChange = {
                                candidateName = it
                                errorMessage = null
                            },
                            label = { Text("Candidate Name") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = candidateParty,
                            onValueChange = {
                                candidateParty = it
                                errorMessage = null
                            },
                            label = { Text("Party Name") },
                            leadingIcon = { Icon(Icons.Default.Group, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = candidateSymbol,
                            onValueChange = { candidateSymbol = it },
                            label = { Text("Symbol (Emoji)") },
                            leadingIcon = { Icon(Icons.Default.EmojiSymbols, contentDescription = null) },
                            placeholder = { Text("Example: 🌟, 🦁, ⚡") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = candidateDescription,
                            onValueChange = { candidateDescription = it },
                            label = { Text("Description / Manifesto") },
                            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                if (errorMessage != null) {
                    Text(
                        errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                if (candidateName.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Preview",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    candidateSymbol.ifEmpty { "?" },
                                    fontSize = 32.sp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        candidateName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        candidateParty.ifEmpty { "Party Name" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        when {
                            candidateName.isBlank() -> errorMessage = "Please enter candidate name"
                            candidateParty.isBlank() -> errorMessage = "Please enter party name"
                            else -> {
                                val newCandidate = Candidate(
                                    id = System.currentTimeMillis().toString(),
                                    name = candidateName,
                                    party = candidateParty,
                                    symbol = candidateSymbol.ifEmpty { "👤" },
                                    description = candidateDescription.ifEmpty { "No description provided" }
                                )
                                if (electionViewModel.addCandidate(selectedElection.id, newCandidate)) {
                                    showSuccess = true
                                    candidateName = ""
                                    candidateParty = ""
                                    candidateSymbol = ""
                                    candidateDescription = ""
                                } else {
                                    errorMessage = "Failed to add candidate"
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = candidateName.isNotBlank() && candidateParty.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Candidate", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showSuccess) {
            AlertDialog(
                onDismissRequest = { showSuccess = false },
                icon = {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                },
                title = { Text("Success!") },
                text = { Text("Candidate has been added successfully to ${selectedElection?.title}") },
                confirmButton = {
                    TextButton(onClick = { showSuccess = false }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

// ==================== ADMIN MANAGE ELECTIONS PAGE ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminManageElectionsPage(navController: NavController, electionViewModel: ElectionViewModel) {
    val elections = electionViewModel.elections

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Elections") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("create_election") }) {
                        Icon(Icons.Default.Add, contentDescription = "Create Election")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(elections) { election ->
                ElectionManagementCard(
                    election = election,
                    onEditClick = {
                        navController.navigate("create_election")
                    },
                    onDeleteClick = {
                        electionViewModel.removeElection(election.id)
                    },
                    onManageCandidates = {
                        navController.navigate("admin_add_candidate")
                    }
                )
            }
        }
    }
}

@Composable
fun ElectionManagementCard(
    election: Election,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onManageCandidates: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        election.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${election.candidates.size} candidates • ${election.totalVoters} voters",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (election.status) {
                        ElectionStatus.ACTIVE -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                        ElectionStatus.UPCOMING -> Color(0xFF2196F3).copy(alpha = 0.2f)
                        ElectionStatus.COMPLETED -> Color(0xFF9E9E9E).copy(alpha = 0.2f)
                    }
                ) {
                    Text(
                        election.status.name,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = when (election.status) {
                            ElectionStatus.ACTIVE -> Color(0xFF4CAF50)
                            ElectionStatus.UPCOMING -> Color(0xFF2196F3)
                            ElectionStatus.COMPLETED -> Color(0xFF757575)
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                election.description.take(100),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onEditClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onManageCandidates,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF2196F3)
                    )
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Candidates", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", fontSize = 12.sp)
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Election") },
            text = { Text("Are you sure you want to delete \"${election.title}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteClick()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ==================== CREATE ELECTION PAGE ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateElectionPage(navController: NavController, electionViewModel: ElectionViewModel) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var totalVoters by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf(ElectionStatus.UPCOMING) }
    var showSuccess by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create New Election") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Election Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it
                            errorMessage = null
                        },
                        label = { Text("Election Title") },
                        leadingIcon = { Icon(Icons.Default.Title, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date (e.g., March 25, 2024)") },
                        leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("Location") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = totalVoters,
                        onValueChange = {
                            totalVoters = it
                            errorMessage = null
                        },
                        label = { Text("Total Voters") },
                        leadingIcon = { Icon(Icons.Default.People, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "Status",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedStatus == ElectionStatus.UPCOMING,
                            onClick = { selectedStatus = ElectionStatus.UPCOMING },
                            label = { Text("Upcoming") }
                        )
                        FilterChip(
                            selected = selectedStatus == ElectionStatus.ACTIVE,
                            onClick = { selectedStatus = ElectionStatus.ACTIVE },
                            label = { Text("Active") }
                        )
                        FilterChip(
                            selected = selectedStatus == ElectionStatus.COMPLETED,
                            onClick = { selectedStatus = ElectionStatus.COMPLETED },
                            label = { Text("Completed") }
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Text(
                    errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Button(
                onClick = {
                    when {
                        title.isBlank() -> errorMessage = "Please enter election title"
                        description.isBlank() -> errorMessage = "Please enter description"
                        date.isBlank() -> errorMessage = "Please enter date"
                        location.isBlank() -> errorMessage = "Please enter location"
                        totalVoters.isBlank() -> errorMessage = "Please enter total voters"
                        totalVoters.toIntOrNull() == null -> errorMessage = "Please enter a valid number"
                        totalVoters.toInt() <= 0 -> errorMessage = "Total voters must be greater than 0"
                        else -> {
                            val totalVotersInt = totalVoters.toInt()
                            val newElection = Election(
                                id = System.currentTimeMillis().toString(),
                                title = title,
                                description = description,
                                date = date,
                                location = location,
                                status = selectedStatus,
                                candidates = emptyList(),
                                totalVoters = totalVotersInt,
                                voterTurnout = if (selectedStatus == ElectionStatus.COMPLETED) Random.nextInt(500, totalVotersInt) else null
                            )
                            if (electionViewModel.addElection(newElection)) {
                                showSuccess = true
                            } else {
                                errorMessage = "Failed to create election (duplicate title?)"
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank() && description.isNotBlank() && date.isNotBlank() &&
                        location.isNotBlank() && totalVoters.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Election", fontWeight = FontWeight.Bold)
            }
        }

        if (showSuccess) {
            AlertDialog(
                onDismissRequest = {
                    showSuccess = false
                    navController.popBackStack()
                },
                icon = {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                },
                title = { Text("Success!") },
                text = { Text("Election \"$title\" has been created successfully.") },
                confirmButton = {
                    TextButton(onClick = {
                        showSuccess = false
                        navController.popBackStack()
                    }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

// ==================== EDIT CANDIDATE PAGE ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCandidatePage(
    navController: NavController,
    electionViewModel: ElectionViewModel,
    electionId: String,
    candidateId: String
) {
    val election = electionViewModel.getElectionById(electionId)
    val candidate = election?.candidates?.find { it.id == candidateId }

    var candidateName by remember { mutableStateOf(candidate?.name ?: "") }
    var candidateParty by remember { mutableStateOf(candidate?.party ?: "") }
    var candidateSymbol by remember { mutableStateOf(candidate?.symbol ?: "") }
    var candidateDescription by remember { mutableStateOf(candidate?.description ?: "") }
    var showSuccess by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Candidate") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Edit Candidate Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Election: ${election?.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = candidateName,
                        onValueChange = { candidateName = it },
                        label = { Text("Candidate Name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = candidateParty,
                        onValueChange = { candidateParty = it },
                        label = { Text("Party Name") },
                        leadingIcon = { Icon(Icons.Default.Group, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = candidateSymbol,
                        onValueChange = { candidateSymbol = it },
                        label = { Text("Symbol (Emoji)") },
                        leadingIcon = { Icon(Icons.Default.EmojiSymbols, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = candidateDescription,
                        onValueChange = { candidateDescription = it },
                        label = { Text("Description / Manifesto") },
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (candidateName.isNotBlank() && candidateParty.isNotBlank() && candidate != null) {
                            val updatedCandidate = candidate.copy(
                                name = candidateName,
                                party = candidateParty,
                                symbol = candidateSymbol.ifEmpty { candidate.symbol },
                                description = candidateDescription.ifEmpty { candidate.description }
                            )
                            electionViewModel.updateCandidate(electionId, candidateId, updatedCandidate)
                            showSuccess = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = candidateName.isNotBlank() && candidateParty.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Changes")
                }

                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Candidate")
                }
            }
        }

        if (showSuccess) {
            AlertDialog(
                onDismissRequest = {
                    showSuccess = false
                    navController.popBackStack()
                },
                icon = {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                },
                title = { Text("Success!") },
                text = { Text("Candidate has been updated successfully.") },
                confirmButton = {
                    TextButton(onClick = {
                        showSuccess = false
                        navController.popBackStack()
                    }) {
                        Text("OK")
                    }
                }
            )
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Candidate") },
                text = { Text("Are you sure you want to delete ${candidate?.name}? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            electionViewModel.removeCandidate(electionId, candidateId)
                            showDeleteDialog = false
                            navController.popBackStack()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

// ==================== ACTIVE ELECTIONS PAGE ====================

@Composable
fun ActiveElectionsPage(
    navController: NavController,
    electionViewModel: ElectionViewModel,
    voteViewModel: VoteViewModel,
    authViewModel: AuthViewModel
) {
    val activeElections = electionViewModel.elections.filter { it.status == ElectionStatus.ACTIVE }

    ElectionListScreen(
        title = "Active Elections",
        elections = activeElections,
        navController = navController,
        isActive = true,
        onElectionClick = { election ->
            navController.navigate("election_detail/${election.id}")
        }
    )
}

// ==================== UPCOMING ELECTIONS PAGE ====================

@Composable
fun UpcomingElectionsPage(navController: NavController, electionViewModel: ElectionViewModel) {
    val upcomingElections = electionViewModel.elections.filter { it.status == ElectionStatus.UPCOMING }

    ElectionListScreen(
        title = "Upcoming Elections",
        elections = upcomingElections,
        navController = navController,
        isActive = false,
        onElectionClick = { }
    )
}

// ==================== MY VOTES PAGE ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyVotesPage(navController: NavController, voteViewModel: VoteViewModel, authViewModel: AuthViewModel) {
    val userVotes = voteViewModel.getUserVotes(authViewModel.currentUser?.id ?: 0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Voting History") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (userVotes.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.HowToVote,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No voting history yet",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                "Your voted elections will appear here",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            } else {
                items(userVotes) { vote ->
                    HistoryCard(vote = vote)
                }
            }
        }
    }
}

@Composable
fun HistoryCard(vote: VoteViewModel.Vote) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    vote.electionTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF4CAF50).copy(alpha = 0.2f)
                ) {
                    Text(
                        "VOTED",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold
                    )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Voted for: ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        "${vote.candidateName} (${vote.candidateParty})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Cast on: ${android.text.format.DateFormat.format("MMM dd, yyyy 'at' hh:mm a", vote.timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }

// ==================== CANDIDATES PAGE ====================

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun CandidatesPage(navController: NavController, electionViewModel: ElectionViewModel) {
        val allCandidates = remember(electionViewModel.elections) {
            electionViewModel.elections.flatMap { election ->
                election.candidates.map { candidate ->
                    Triple(election.title, candidate, election.id)
                }
            }
        }

        var selectedElection by remember { mutableStateOf<String?>(null) }
        val filteredCandidates = if (selectedElection == null) {
            allCandidates
        } else {
            allCandidates.filter { it.first == selectedElection }
        }

        val elections = electionViewModel.elections.map { it.title }.distinct()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Candidate Profiles") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LazyRow(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedElection == null,
                            onClick = { selectedElection = null },
                            label = { Text("All Elections") }
                        )
                    }
                    items(elections) { election ->
                        FilterChip(
                            selected = selectedElection == election,
                            onClick = { selectedElection = election },
                            label = { Text(election.take(20)) }
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(filteredCandidates) { (electionTitle, candidate, electionId) ->
                        CandidateDetailCard(
                            candidate = candidate,
                            electionTitle = electionTitle,
                            electionId = electionId,
                            navController = navController
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun CandidateDetailCard(
        candidate: Candidate,
        electionTitle: String,
        electionId: String,
        navController: NavController
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        candidate.symbol,
                        fontSize = 32.sp
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        candidate.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${candidate.party} • ${electionTitle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        candidate.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { navController.navigate("election_detail/$electionId") },
                        modifier = Modifier.height(36.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Icon(Icons.Default.HowToVote, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Vote Now", fontSize = 13.sp)
                    }
                }
            }
        }
    }

// ==================== RESULTS PAGE ====================

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ResultsPage(navController: NavController, electionViewModel: ElectionViewModel, voteViewModel: VoteViewModel) {
        val completedElections = electionViewModel.elections.filter { it.status == ElectionStatus.COMPLETED }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Election Results") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (completedElections.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.BarChart,
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No Results Available",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Text(
                                    "Completed election results will appear here",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                )
                            }
                        }
                    } else {
                        items(completedElections) { election ->
                            val results = voteViewModel.getElectionResults(election.id)
                            val winnerId = results.maxByOrNull { it.value }?.key
                            val winner = election.candidates.find { it.id == winnerId }
                            val turnout = voteViewModel.getVoterTurnout(election.id, election.totalVoters)

                            ResultCard(
                                election = election,
                                winner = winner,
                                turnout = turnout,
                                onClick = { navController.navigate("results_detail/${election.id}") }
                            )
                        }
                    }
                }
            }
        }

        @Composable
        fun ResultCard(election: Election, winner: Candidate?, turnout: Int, onClick: () -> Unit) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick() },
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        election.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (winner != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Winner: ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Text(
                                    "${winner.symbol} ${winner.name}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4CAF50)
                                )
                            }
                            Icon(
                                Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = turnout / 100f,
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF4CAF50),
                            trackColor = Color(0xFFE0E0E0)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Turnout: $turnout%", style = MaterialTheme.typography.bodySmall)
                            Text("Total Voters: ${election.totalVoters}", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Text(
                            "No votes cast yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

// ==================== RESULTS DETAIL SCREEN ====================

        @OptIn(ExperimentalMaterial3Api::class)
        @Composable
        fun ResultsDetailScreen(
            electionId: String,
            navController: NavController,
            electionViewModel: ElectionViewModel,
            voteViewModel: VoteViewModel
        ) {
            val election = electionViewModel.getElectionById(electionId)
            val results = voteViewModel.getElectionResults(electionId)
            val totalVotes = results.values.sum()

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Election Results") },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                }
            ) { paddingValues ->
                election?.let { selectedElection ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        selectedElection.title,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Final Results",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color(0xFF4CAF50)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Date: ${selectedElection.date}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Total Votes Cast: $totalVotes",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Voter Turnout: ${voteViewModel.getVoterTurnout(electionId, selectedElection.totalVoters)}%",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        item {
                            Text(
                                "Vote Distribution",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        items(selectedElection.candidates) { candidate ->
                            val votes = results[candidate.id] ?: 0
                            val percentage = if (totalVotes > 0) (votes * 100.0 / totalVotes).toInt() else 0
                            val isWinner = votes == results.maxByOrNull { it.value }?.value && votes > 0

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isWinner)
                                        Color(0xFF4CAF50).copy(alpha = 0.1f)
                                    else
                                        MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                candidate.symbol,
                                                fontSize = 28.sp
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    candidate.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    candidate.party,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        if (isWinner) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFF4CAF50)
                                            ) {
                                                Text(
                                                    "WINNER",
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("$percentage%", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                        Text("$votes votes", style = MaterialTheme.typography.bodySmall)
                                    }
                                    LinearProgressIndicator(
                                        progress = (percentage / 100f),
                                        modifier = Modifier.fillMaxWidth(),
                                        color = if (isWinner) Color(0xFF4CAF50) else Color(0xFF2196F3),
                                        trackColor = Color(0xFFE0E0E0)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

// ==================== SETTINGS PAGE ====================

        @OptIn(ExperimentalMaterial3Api::class)
        @Composable
        fun SettingsPage(navController: NavController, authViewModel: AuthViewModel) {
            var notificationsEnabled by remember { mutableStateOf(true) }
            var showDeleteDialog by remember { mutableStateOf(false) }
            var showPasswordDialog by remember { mutableStateOf(false) }
            var oldPassword by remember { mutableStateOf("") }
            var newPassword by remember { mutableStateOf("") }
            var confirmPassword by remember { mutableStateOf("") }
            var passwordErrorMessage by remember { mutableStateOf<String?>(null) }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Settings") },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White
                        )
                    )
                }
            ) { paddingValues ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        SettingsCard(
                            title = "Account Information",
                            icon = Icons.Default.Person
                        ) {
                            SettingsItem(
                                label = "Name",
                                value = authViewModel.currentUser?.name ?: "Not set",
                                icon = Icons.Default.PersonOutline
                            )
                            SettingsItem(
                                label = "Email",
                                value = authViewModel.currentUser?.email ?: "Not set",
                                icon = Icons.Default.Email
                            )
                            if (authViewModel.isAdmin()) {
                                SettingsItem(
                                    label = "Role",
                                    value = "Administrator",
                                    icon = Icons.Default.AdminPanelSettings
                                )
                            }
                        }
                    }

                    item {
                        SettingsCard(
                            title = "Preferences",
                            icon = Icons.Default.Settings
                        ) {
                            SettingsSwitchItem(
                                label = "Push Notifications",
                                checked = notificationsEnabled,
                                onCheckedChange = { notificationsEnabled = it },
                                icon = Icons.Default.Notifications
                            )
                        }
                    }

                    item {
                        SettingsCard(
                            title = "Security",
                            icon = Icons.Default.Lock
                        ) {
                            SettingsActionItem(
                                label = "Change Password",
                                icon = Icons.Default.LockReset,
                                onClick = { showPasswordDialog = true }
                            )
                        }
                    }

                    item {
                        Button(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete Account")
                        }
                    }

                    item {
                        TextButton(
                            onClick = {
                                authViewModel.logout()
                                navController.navigate("login") {
                                    popUpTo("dashboard") { inclusive = true }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text("Logout", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            if (showPasswordDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showPasswordDialog = false
                        passwordErrorMessage = null
                        oldPassword = ""
                        newPassword = ""
                        confirmPassword = ""
                    },
                    title = { Text("Change Password") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = oldPassword,
                                onValueChange = {
                                    oldPassword = it
                                    passwordErrorMessage = null
                                },
                                label = { Text("Current Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                isError = passwordErrorMessage != null
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = newPassword,
                                onValueChange = {
                                    newPassword = it
                                    passwordErrorMessage = null
                                },
                                label = { Text("New Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                isError = passwordErrorMessage != null
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = {
                                    confirmPassword = it
                                    passwordErrorMessage = null
                                },
                                label = { Text("Confirm New Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                isError = passwordErrorMessage != null
                            )
                            if (passwordErrorMessage != null) {
                                Text(
                                    passwordErrorMessage!!,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                when {
                                    oldPassword.isBlank() -> passwordErrorMessage = "Please enter current password"
                                    newPassword.length < 6 -> passwordErrorMessage = "Password must be at least 6 characters"
                                    newPassword != confirmPassword -> passwordErrorMessage = "Passwords do not match"
                                    else -> {
                                        if (authViewModel.changePassword(oldPassword, newPassword)) {
                                            showPasswordDialog = false
                                            oldPassword = ""
                                            newPassword = ""
                                            confirmPassword = ""
                                        } else {
                                            passwordErrorMessage = "Incorrect current password"
                                        }
                                    }
                                }
                            }
                        ) {
                            Text("Change")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPasswordDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Delete Account") },
                    text = {
                        Text("Are you sure? This action cannot be undone and all your data will be permanently deleted.")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                authViewModel.deleteAccount()
                                showDeleteDialog = false
                                navController.navigate("login") {
                                    popUpTo("dashboard") { inclusive = true }
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }

        @Composable
        fun SettingsCard(
            title: String,
            icon: ImageVector,
            content: @Composable () -> Unit
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    content()
                }
            }
        }

        @Composable
        fun SettingsItem(label: String, value: String, icon: ImageVector) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
                Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }

        @Composable
        fun SettingsSwitchItem(
            label: String,
            checked: Boolean,
            onCheckedChange: (Boolean) -> Unit,
            icon: ImageVector
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF4CAF50),
                        checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                    )
                )
            }
        }

        @Composable
        fun SettingsActionItem(label: String, icon: ImageVector, onClick: () -> Unit) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick() }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }

// ==================== AUDIT LOG PAGE ====================

        @OptIn(ExperimentalMaterial3Api::class)
        @Composable
        fun AuditLogPage(navController: NavController, voteViewModel: VoteViewModel, authViewModel: AuthViewModel) {
            val auditLog = voteViewModel.auditLog

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Audit Log") },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White
                        )
                    )
                }
            ) { paddingValues ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (auditLog.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("No audit entries yet", style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    } else {
                        items(auditLog.reversed()) { entry ->
                            AuditEntryCard(entry = entry)
                        }
                    }
                }
            }
        }

        @Composable
        fun AuditEntryCard(entry: VoteViewModel.AuditEntry) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            entry.action,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = when (entry.action) {
                                "VOTE_CAST" -> Color(0xFF4CAF50)
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                        Text(
                            android.text.format.DateFormat.format("MMM dd, hh:mm a", entry.timestamp).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        entry.details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        "User: ${entry.userName} (ID: ${entry.userId})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }

// ==================== ELECTION LIST SCREEN ====================

        @OptIn(ExperimentalMaterial3Api::class)
        @Composable
        fun ElectionListScreen(
            title: String,
            elections: List<Election>,
            navController: NavController,
            isActive: Boolean,
            onElectionClick: (Election) -> Unit
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White
                        )
                    )
                }
            ) { paddingValues ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (elections.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "No ${if (isActive) "active" else "upcoming"} elections at the moment",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    } else {
                        items(elections) { election ->
                            ElectionCard(
                                election = election,
                                isActive = isActive,
                                onClick = { onElectionClick(election) }
                            )
                        }
                    }
                }
            }
        }

        @Composable
        fun ElectionCard(election: Election, isActive: Boolean, onClick: () -> Unit) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = isActive) { onClick() },
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            election.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        if (isActive) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF4CAF50).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    "LIVE",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF4CAF50),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF2196F3).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    "UPCOMING",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF2196F3),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        election.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = "Date",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                election.date.take(30),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = "Location",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                election.location.take(20),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "${election.candidates.size} Candidates Contesting",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (isActive) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onClick,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.HowToVote, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Vote Now", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

// ==================== ELECTION DETAIL SCREEN ====================

        @OptIn(ExperimentalMaterial3Api::class)
        @Composable
        fun ElectionDetailScreen(
            electionId: String,
            navController: NavController,
            electionViewModel: ElectionViewModel,
            voteViewModel: VoteViewModel,
            authViewModel: AuthViewModel
        ) {
            var selectedCandidate by remember { mutableStateOf<Candidate?>(null) }
            var showConfirmation by remember { mutableStateOf(false) }
            var showSuccess by remember { mutableStateOf(false) }
            var showAlreadyVoted by remember { mutableStateOf(false) }

            val election = electionViewModel.getElectionById(electionId)
            val hasVoted = authViewModel.hasVoted(electionId)

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(election?.title?.take(20) ?: "Election Details") },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White
                        )
                    )
                }
            ) { paddingValues ->
                if (hasVoted) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = Color(0xFF4CAF50)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "You have already voted in this election!",
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { navController.popBackStack() }) {
                                Text("Go Back")
                            }
                        }
                    }
                } else {
                    election?.let { selectedElection ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "Election Details",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        selectedElection.description,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider()
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.DateRange, contentDescription = "Date", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(selectedElection.date, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.LocationOn, contentDescription = "Location", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(selectedElection.location, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.People, contentDescription = "Voters", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Total Voters: ${selectedElection.totalVoters}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }

                            Text(
                                "Select Your Candidate",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )

                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(selectedElection.candidates) { candidate ->
                                    CandidateCard(
                                        candidate = candidate,
                                        isSelected = selectedCandidate?.id == candidate.id,
                                        onSelect = { selectedCandidate = candidate }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (selectedCandidate != null) {
                                        showConfirmation = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = selectedCandidate != null,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50),
                                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.HowToVote, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Submit Vote", fontWeight = FontWeight.Bold)
                            }
                        }
                    } ?: run {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Error, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Election not found", style = MaterialTheme.typography.headlineSmall)
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { navController.popBackStack() }) {
                                    Text("Go Back")
                                }
                            }
                        }
                    }
                }
            }

            if (showConfirmation && selectedCandidate != null && election != null) {
                AlertDialog(
                    onDismissRequest = { showConfirmation = false },
                    title = { Text("Confirm Your Vote") },
                    text = {
                        Column {
                            Text("You are voting for:")
                            Text(
                                "${selectedCandidate!!.symbol} ${selectedCandidate!!.name}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                            Text("Party: ${selectedCandidate!!.party}")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Election: ${election.title}", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("This action cannot be undone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val success = voteViewModel.castVote(
                                    voterId = authViewModel.currentUser?.id ?: 0,
                                    voterName = authViewModel.currentUser?.name ?: "",
                                    electionId = election.id,
                                    electionTitle = election.title,
                                    candidateId = selectedCandidate!!.id,
                                    candidateName = selectedCandidate!!.name,
                                    candidateParty = selectedCandidate!!.party
                                )
                                if (success) {
                                    authViewModel.markVoted(election.id)
                                    showConfirmation = false
                                    showSuccess = true
                                } else {
                                    showConfirmation = false
                                    showAlreadyVoted = true
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color(0xFF4CAF50)
                            )
                        ) {
                            Text("Confirm Vote")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirmation = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showSuccess) {
                AlertDialog(
                    onDismissRequest = {
                        showSuccess = false
                        navController.popBackStack()
                    },
                    icon = {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(48.dp))
                    },
                    title = { Text("Vote Cast Successfully!") },
                    text = { Text("Thank you for participating in the democratic process. Your vote has been recorded.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showSuccess = false
                                navController.popBackStack()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF4CAF50))
                        ) {
                            Text("Done")
                        }
                    }
                )
            }

            if (showAlreadyVoted) {
                AlertDialog(
                    onDismissRequest = {
                        showAlreadyVoted = false
                        navController.popBackStack()
                    },
                    icon = {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(48.dp))
                    },
                    title = { Text("Already Voted") },
                    text = { Text("You have already cast your vote in this election. You cannot vote again.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showAlreadyVoted = false
                                navController.popBackStack()
                            }
                        ) {
                            Text("OK")
                        }
                    }
                )
            }
        }

        @Composable
        fun CandidateCard(candidate: Candidate, isSelected: Boolean, onSelect: () -> Unit) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect() },
                elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(candidate.symbol, fontSize = 28.sp)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(candidate.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(candidate.party, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(candidate.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), maxLines = 2)
                        }
                    }

                    RadioButton(
                        selected = isSelected,
                        onClick = onSelect,
                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF4CAF50))
                    )
                }
            }
        }

// ==================== UTILITY FUNCTIONS ====================

        fun isValidEmail(email: String): Boolean {
            return Patterns.EMAIL_ADDRESS.matcher(email).matches()
        }