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

@OptIn(ExperimentalMaterial3Api::class)
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
        composable("forgot_password") { ForgotPasswordPage(navController) }
        composable("dashboard") { DashboardPage(navController, authViewModel, electionViewModel, voteViewModel) }
        composable("active_elections") { ActiveElectionsPage(navController, electionViewModel, voteViewModel, authViewModel) }
        composable("upcoming_elections") { UpcomingElectionsPage(navController, electionViewModel) }
        composable("my_votes") { MyVotesPage(navController, voteViewModel, authViewModel) }
        composable("candidates") { CandidatesPage(navController, electionViewModel, authViewModel) }
        composable("results") { ResultsPage(navController, electionViewModel, voteViewModel, authViewModel) }
        composable("settings") { SettingsPage(navController, authViewModel) }
        composable("admin_add_candidate") { AdminAddCandidatePage(navController, electionViewModel) }
        composable("admin_manage_elections") { AdminManageElectionsPage(navController, electionViewModel) }
        composable("create_election") { CreateElectionPage(navController, electionViewModel) }
        composable("audit_log") { AuditLogPage(navController, voteViewModel) }
        composable("election_detail/{electionId}") { backStackEntry ->
            val electionId = backStackEntry.arguments?.getString("electionId") ?: ""
            ElectionDetailScreen(electionId, navController, electionViewModel, voteViewModel, authViewModel)
        }
        composable("results_detail/{electionId}") { backStackEntry ->
            val electionId = backStackEntry.arguments?.getString("electionId") ?: ""
            ResultsDetailScreen(electionId, navController, electionViewModel, voteViewModel)
        }
        composable("edit_candidate/{electionId}/{candidateId}") { backStackEntry ->
            val electionId = backStackEntry.arguments?.getString("electionId") ?: ""
            val candidateId = backStackEntry.arguments?.getString("candidateId") ?: ""
            EditCandidatePage(navController, electionViewModel, electionId, candidateId)
        }
        composable("edit_election/{electionId}") { backStackEntry ->
            val electionId = backStackEntry.arguments?.getString("electionId") ?: ""
            EditElectionPage(navController, electionViewModel, electionId)
        }
    }
}

// ==================== VIEW MODELS ====================

class VoteViewModel {
    private val _votes = mutableStateOf<Map<String, Vote>>(emptyMap())
    val votes: Map<String, Vote> get() = _votes.value

    private val _auditLog = mutableStateOf<List<AuditEntry>>(emptyList())
    val auditLog: List<AuditEntry> get() = _auditLog.value

    private val _publishedResults = mutableStateOf<Map<String, PublishedResult>>(emptyMap())
    val publishedResults: Map<String, PublishedResult> get() = _publishedResults.value

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

    data class PublishedResult(
        val electionId: String,
        val electionTitle: String,
        val winnerId: String,
        val winnerName: String,
        val winnerParty: String,
        val winnerSymbol: String,
        val totalVotes: Int,
        val turnout: Int,
        val results: Map<String, Int>,
        val publishedAt: Long
    )

    data class AuditEntry(
        val id: String,
        val action: String,
        val userId: Int,
        val userName: String,
        val details: String,
        val timestamp: Long
    )

    fun castVote(voterId: Int, voterName: String, electionId: String, electionTitle: String, candidateId: String, candidateName: String, candidateParty: String): Boolean {
        val voteKey = "${voterId}_${electionId}"
        if (_votes.value.containsKey(voteKey)) return false
        val vote = Vote(voterId, voterName, electionId, electionTitle, candidateId, candidateName, candidateParty, System.currentTimeMillis())
        _votes.value = _votes.value + (voteKey to vote)
        _auditLog.value = _auditLog.value + AuditEntry(System.currentTimeMillis().toString(), "VOTE_CAST", voterId, voterName, "Voted for $candidateName in $electionTitle", System.currentTimeMillis())
        return true
    }

    fun getUserVotes(voterId: Int): List<Vote> = _votes.value.values.filter { it.voterId == voterId }
    fun getElectionResults(electionId: String): Map<String, Int> = _votes.value.values.filter { it.electionId == electionId }.groupingBy { it.candidateId }.eachCount()
    fun getVoterTurnout(electionId: String, totalVoters: Int): Int {
        val votesCount = _votes.value.values.count { it.electionId == electionId }
        return if (totalVoters > 0) (votesCount * 100 / totalVoters) else 0
    }
    fun getTotalVotesCast(): Int = _votes.value.size
    fun getVotesCountForElection(electionId: String): Int = _votes.value.values.count { it.electionId == electionId }

    fun publishResults(electionId: String, electionTitle: String, totalVoters: Int, winnerSymbol: String): PublishedResult? {
        val results = getElectionResults(electionId)
        val totalVotes = results.values.sum()
        if (totalVotes == 0) return null
        val winnerId = results.maxByOrNull { it.value }?.key ?: return null
        val turnout = getVoterTurnout(electionId, totalVoters)
        val winnerVote = _votes.value.values.find { it.electionId == electionId && it.candidateId == winnerId }
        val publishedResult = PublishedResult(
            electionId = electionId,
            electionTitle = electionTitle,
            winnerId = winnerId,
            winnerName = winnerVote?.candidateName ?: "Unknown",
            winnerParty = winnerVote?.candidateParty ?: "Unknown",
            winnerSymbol = winnerSymbol,
            totalVotes = totalVotes,
            turnout = turnout,
            results = results,
            publishedAt = System.currentTimeMillis()
        )
        _publishedResults.value = _publishedResults.value + (electionId to publishedResult)
        _auditLog.value = _auditLog.value + AuditEntry(
            System.currentTimeMillis().toString(),
            "RESULTS_PUBLISHED",
            0,
            "Admin",
            "Published results for $electionTitle. Winner: ${winnerVote?.candidateName} with $totalVotes votes. Turnout: $turnout%",
            System.currentTimeMillis()
        )
        return publishedResult
    }

    fun getPublishedResults(electionId: String): PublishedResult? = _publishedResults.value[electionId]
    fun getAllPublishedResults(): List<PublishedResult> = _publishedResults.value.values.toList()
}

class AuthViewModel {
    private val _currentUser = mutableStateOf<User?>(null)
    val currentUser: User? get() = _currentUser.value
    val isLoggedIn: Boolean get() = _currentUser.value != null
    private val _registeredUsers = mutableStateOf<List<User>>(emptyList())

    data class User(val id: Int, val name: String, val email: String, val password: String, val hasVoted: MutableList<String>, val isAdmin: Boolean = false)

    init {
        registerUser("Admin User", "admin@demo.com", "admin123", true)
        registerUser("Test User", "user@demo.com", "user123", false)
        registerUser("John Doe", "john@demo.com", "john123", false)
        registerUser("Jane Smith", "jane@demo.com", "jane123", false)
    }

    fun login(email: String, password: String): Boolean {
        val user = _registeredUsers.value.find { it.email.equals(email, ignoreCase = true) && it.password == password }
        return if (user != null) {
            _currentUser.value = user
            true
        } else false
    }

    private fun registerUser(name: String, email: String, password: String, isAdmin: Boolean): Boolean {
        if (_registeredUsers.value.any { it.email.equals(email, ignoreCase = true) }) return false
        if (password.length < 6) return false
        val newUser = User(_registeredUsers.value.size + 1, name, email, password, mutableListOf(), isAdmin)
        _registeredUsers.value = _registeredUsers.value + newUser
        if (isAdmin) _currentUser.value = newUser
        return true
    }

    fun signUp(name: String, email: String, password: String): Boolean = registerUser(name, email, password, false)
    fun logout() { _currentUser.value = null }
    fun isAdmin(): Boolean = _currentUser.value?.isAdmin == true
    fun markVoted(electionId: String) { _currentUser.value?.hasVoted?.add(electionId) }
    fun hasVoted(electionId: String): Boolean = _currentUser.value?.hasVoted?.contains(electionId) == true
    fun changePassword(oldPassword: String, newPassword: String): Boolean {
        val currentUser = _currentUser.value ?: return false
        if (currentUser.password != oldPassword || newPassword.length < 6) return false
        val index = _registeredUsers.value.indexOfFirst { it.id == currentUser.id }
        val updated = currentUser.copy(password = newPassword)
        val list = _registeredUsers.value.toMutableList()
        list[index] = updated
        _registeredUsers.value = list
        _currentUser.value = updated
        return true
    }
}

class ElectionViewModel {
    private val _elections = mutableStateOf<List<Election>>(emptyList())
    val elections: List<Election> get() = _elections.value

    init {
        _elections.value = listOf(
            Election("1", "Presidential Election 2024", "Vote for the next President", "March 15, 2024", "Main Auditorium", ElectionStatus.ACTIVE, mutableListOf(
                Candidate("c1", "Sarah Johnson", "Progressive Union", "🌿", "Improving campus facilities and student services"),
                Candidate("c2", "Michael Chen", "Future Leaders", "⚡", "Career development and tech innovation"),
                Candidate("c3", "Emily Rodriguez", "Student First", "📚", "Student rights and academic excellence")
            ), 5000),
            Election("2", "CS Department Representative", "Elect your representative", "March 15-17, 2024", "Online", ElectionStatus.ACTIVE, mutableListOf(
                Candidate("c4", "Alex Thompson", "Tech Innovators", "💻", "AI and machine learning focus"),
                Candidate("c5", "Jessica Lee", "CS Excellence", "🎯", "Industry bridge and internship programs"),
                Candidate("c6", "David Kim", "Code for All", "🌐", "Inclusive coding and diversity")
            ), 800),
            Election("3", "Vice President Election", "Choose the Vice President", "March 25, 2024", "Student Center", ElectionStatus.UPCOMING, mutableListOf(
                Candidate("c7", "Olivia Martinez", "Unity Coalition", "🤝", "Event management and student engagement"),
                Candidate("c8", "James Wilson", "Action Party", "⚡", "Digital transformation and modernization"),
                Candidate("c9", "Sophia Brown", "Bridge Builders", "🌉", "Collaboration and communication")
            ), 5000),
            Election("4", "Class Representative 2023", "Annual election", "December 10, 2023", "Online", ElectionStatus.COMPLETED, mutableListOf(
                Candidate("c10", "Thomas Anderson", "Student Voice", "🎓", "Student welfare and advocacy"),
                Candidate("c11", "Nina Williams", "Change Makers", "⭐", "Curriculum improvement and feedback")
            ), 1200)
        )
    }

    fun addCandidate(electionId: String, candidate: Candidate): Boolean {
        val updatedElections = _elections.value.toMutableList()
        val electionIndex = updatedElections.indexOfFirst { it.id == electionId }
        if (electionIndex == -1) return false
        val election = updatedElections[electionIndex]
        election.candidates.add(candidate)
        updatedElections[electionIndex] = election
        _elections.value = updatedElections
        return true
    }

    fun updateCandidate(electionId: String, candidateId: String, updatedCandidate: Candidate): Boolean {
        val updatedElections = _elections.value.toMutableList()
        val electionIndex = updatedElections.indexOfFirst { it.id == electionId }
        if (electionIndex == -1) return false
        val election = updatedElections[electionIndex]
        val candidateIndex = election.candidates.indexOfFirst { it.id == candidateId }
        if (candidateIndex == -1) return false
        election.candidates[candidateIndex] = updatedCandidate
        updatedElections[electionIndex] = election
        _elections.value = updatedElections
        return true
    }

    fun removeCandidate(electionId: String, candidateId: String): Boolean {
        val updatedElections = _elections.value.toMutableList()
        val electionIndex = updatedElections.indexOfFirst { it.id == electionId }
        if (electionIndex == -1) return false
        val election = updatedElections[electionIndex]
        val updatedCandidates = election.candidates.filter { it.id != candidateId }.toMutableList()
        val updatedElection = election.copy(candidates = updatedCandidates)
        updatedElections[electionIndex] = updatedElection
        _elections.value = updatedElections
        return true
    }

    fun addElection(election: Election): Boolean {
        if (_elections.value.any { it.title.equals(election.title, ignoreCase = true) }) return false
        _elections.value = _elections.value + election
        return true
    }

    fun updateElection(electionId: String, updatedElection: Election): Boolean {
        val updatedElections = _elections.value.toMutableList()
        val electionIndex = updatedElections.indexOfFirst { it.id == electionId }
        if (electionIndex == -1) return false
        updatedElections[electionIndex] = updatedElection
        _elections.value = updatedElections
        return true
    }

    fun removeElection(electionId: String): Boolean {
        _elections.value = _elections.value.filter { it.id != electionId }
        return true
    }

    fun getElectionById(electionId: String): Election? {
        return _elections.value.find { it.id == electionId }
    }
}

// ==================== DATA MODELS ====================

data class DashboardItem(val title: String, val description: String, val icon: ImageVector, val route: String, val color: Color)
data class Election(val id: String, val title: String, val description: String, val date: String, val location: String, val status: ElectionStatus, val candidates: MutableList<Candidate>, val totalVoters: Int)
data class Candidate(val id: String, val name: String, val party: String, val symbol: String, val description: String, val imageUrl: String? = null)
enum class ElectionStatus { ACTIVE, UPCOMING, COMPLETED }
data class CandidateWithDetails(val electionTitle: String, val candidate: Candidate, val electionId: String, val electionStatus: ElectionStatus)

fun isValidEmail(email: String): Boolean = Patterns.EMAIL_ADDRESS.matcher(email).matches()

// ==================== LOGIN PAGE ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginPage(navController: NavController, authViewModel: AuthViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(8.dp)) {
            Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.HowToVote, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Smart Ballot", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Secure Digital Voting Platform", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(value = email, onValueChange = { newEmail -> email = newEmail; showError = false }, label = { Text("Email") }, leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), isError = showError, shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = password, onValueChange = { newPassword -> password = newPassword; showError = false }, label = { Text("Password") }, leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), isError = showError, shape = RoundedCornerShape(12.dp))

                if (showError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Invalid email or password", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(24.dp))

                Button(onClick = {
                    scope.launch {
                        isLoading = true
                        delay(500)
                        if (authViewModel.login(email, password)) {
                            navController.navigate("dashboard") { popUpTo("login") { inclusive = true } }
                        } else {
                            showError = true
                        }
                        isLoading = false
                    }
                }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading, shape = RoundedCornerShape(12.dp)) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    else Text("Login", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { navController.navigate("signup") }) { Text("Sign Up", color = MaterialTheme.colorScheme.primary) }
                    TextButton(onClick = { navController.navigate("forgot_password") }) { Text("Forgot Password?", color = MaterialTheme.colorScheme.primary) }
                }

                Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)), shape = RoundedCornerShape(8.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Demo Credentials:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("Admin: admin@demo.com / admin123", style = MaterialTheme.typography.bodySmall)
                        Text("User: user@demo.com / user123", style = MaterialTheme.typography.bodySmall)
                        Text("John: john@demo.com / john123", style = MaterialTheme.typography.bodySmall)
                        Text("Jane: jane@demo.com / jane123", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

// ==================== SIGN UP PAGE ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpPage(navController: NavController, authViewModel: AuthViewModel) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(8.dp)) {
            Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Create Account", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Join the future of voting", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(value = name, onValueChange = { newName -> name = newName }, label = { Text("Full Name") }, leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(value = email, onValueChange = { newEmail -> email = newEmail }, label = { Text("Email") }, leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(value = password, onValueChange = { newPassword -> password = newPassword }, label = { Text("Password") }, leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(value = confirmPassword, onValueChange = { newConfirm -> confirmPassword = newConfirm }, label = { Text("Confirm Password") }, leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), isError = password != confirmPassword && confirmPassword.isNotBlank(), shape = RoundedCornerShape(12.dp))

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(24.dp))

                Button(onClick = {
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
                                    navController.navigate("dashboard") { popUpTo("login") { inclusive = true } }
                                } else {
                                    errorMessage = "Email already registered"
                                }
                                isLoading = false
                            }
                        }
                    }
                }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading, shape = RoundedCornerShape(12.dp)) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    else Text("Register", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = { navController.popBackStack() }) { Text("Already have an account? Login") }
            }
        }
    }
}

// ==================== FORGOT PASSWORD PAGE ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordPage(navController: NavController) {
    var email by remember { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(8.dp)) {
            Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Reset Password", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Enter your email to receive a reset link", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.padding(vertical = 8.dp))

                if (showSuccess) {
                    Card(colors = CardDefaults.cardColors(Color(0xFF4CAF50).copy(alpha = 0.1f)), shape = RoundedCornerShape(12.dp)) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reset link sent to your email!", color = Color(0xFF4CAF50))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                OutlinedTextField(value = email, onValueChange = { newEmail -> email = newEmail }, label = { Text("Email") }, leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(24.dp))

                Button(onClick = {
                    scope.launch {
                        isLoading = true
                        delay(1000)
                        showSuccess = true
                        isLoading = false
                        delay(2000)
                        navController.popBackStack()
                    }
                }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading && email.isNotBlank(), shape = RoundedCornerShape(12.dp)) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    else Text("Send Reset Link")
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = { navController.popBackStack() }) { Text("Back to Login") }
            }
        }
    }
}
// ==================== DASHBOARD PAGE ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardPage(navController: NavController, authViewModel: AuthViewModel, electionViewModel: ElectionViewModel, voteViewModel: VoteViewModel) {
    val isAdmin = authViewModel.isAdmin()
    val items = mutableListOf(
        DashboardItem("Active Elections", "Cast your vote", Icons.Default.HowToVote, "active_elections", Color(0xFF4CAF50)),
        DashboardItem("Upcoming Elections", "Coming soon", Icons.Default.Event, "upcoming_elections", Color(0xFF2196F3)),
        DashboardItem("My Votes", "History", Icons.Default.History, "my_votes", Color(0xFFFF9800)),
        DashboardItem("Candidates", "Profiles", Icons.Default.Person, "candidates", Color(0xFF9C27B0)),
        DashboardItem("Results", "View winners", Icons.AutoMirrored.Filled.ShowChart, "results", Color(0xFFF44336)),
        DashboardItem("Settings", "Account", Icons.Default.Settings, "settings", Color(0xFF607D8B))
    )
    if (isAdmin) {
        items.add(DashboardItem("Add Candidate", "Add new", Icons.Default.PersonAdd, "admin_add_candidate", Color(0xFFE91E63)))
        items.add(DashboardItem("Manage Elections", "Create/Edit/Delete", Icons.Default.Edit, "admin_manage_elections", Color(0xFF673AB7)))
        items.add(DashboardItem("Audit Log", "View logs", Icons.Default.History, "audit_log", Color(0xFF795548)))
    }

    val totalVotesCast = voteViewModel.getTotalVotesCast()
    val totalPossibleVotes = electionViewModel.elections.sumOf { it.totalVoters }
    val overallTurnout = if (totalPossibleVotes > 0) (totalVotesCast * 100 / totalPossibleVotes) else 0
    val publishedResults = voteViewModel.getAllPublishedResults()
    val totalPublishedResults = publishedResults.size

    Scaffold(topBar = {
        CenterAlignedTopAppBar(title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Smart Ballot", fontWeight = FontWeight.Bold)
                Text("Welcome, ${authViewModel.currentUser?.name?.split(" ")?.first() ?: "Voter"}${if (isAdmin) " (Admin)" else ""}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }, actions = {
            IconButton(onClick = { authViewModel.logout(); navController.navigate("login") { popUpTo("dashboard") { inclusive = true } } }) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
            }
        })
    }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.HowToVote, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(totalVotesCast.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                            Text("Total Votes", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                    Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.People, contentDescription = null, tint = Color(0xFF2196F3), modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$overallTurnout%", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2196F3))
                            Text("Overall Turnout", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                    Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$totalPublishedResults", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                            Text("Results Published", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(electionViewModel.elections.count { it.status == ElectionStatus.ACTIVE }.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                            Text("Active", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(electionViewModel.elections.count { it.status == ElectionStatus.UPCOMING }.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2196F3))
                            Text("Upcoming", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(electionViewModel.elections.count { it.status == ElectionStatus.COMPLETED }.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9E9E9E))
                            Text("Completed", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(voteViewModel.votes.size.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                            Text("Votes Cast", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
            }
            items(items.chunked(2)) { rowItems ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    rowItems.forEach { item ->
                        Card(modifier = Modifier.weight(1f).aspectRatio(1f).clickable { navController.navigate(item.route) }, elevation = CardDefaults.cardElevation(4.dp), shape = RoundedCornerShape(16.dp)) {
                            Column(modifier = Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Box(modifier = Modifier.size(48.dp).background(item.color.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(item.icon, contentDescription = null, tint = item.color, modifier = Modifier.size(28.dp))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                    }
                    if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ==================== ELECTION LIST SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElectionListScreen(title: String, elections: List<Election>, navController: NavController, isActive: Boolean, onElectionClick: (Election) -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, navigationIconContentColor = Color.White))
    }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (elections.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No ${if (isActive) "active" else "upcoming"} elections", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
            } else {
                items(elections) { election ->
                    Card(modifier = Modifier.fillMaxWidth().clickable(enabled = isActive) { onElectionClick(election) }, elevation = CardDefaults.cardElevation(4.dp), shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(election.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Surface(shape = RoundedCornerShape(8.dp), color = if (isActive) Color(0xFF4CAF50).copy(alpha = 0.2f) else Color(0xFF2196F3).copy(alpha = 0.2f)) {
                                    Text(if (isActive) "LIVE" else "UPCOMING", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = if (isActive) Color(0xFF4CAF50) else Color(0xFF2196F3), fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(election.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), maxLines = 2)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary); Spacer(modifier = Modifier.width(4.dp)); Text(election.date.take(30), style = MaterialTheme.typography.bodySmall) }
                                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary); Spacer(modifier = Modifier.width(4.dp)); Text(election.location.take(20), style = MaterialTheme.typography.bodySmall) }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("${election.candidates.size} Candidates", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            if (isActive) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(onClick = { onElectionClick(election) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(Color(0xFF4CAF50)), shape = RoundedCornerShape(8.dp)) {
                                    Icon(Icons.Default.HowToVote, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Vote Now", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveElectionsPage(navController: NavController, electionViewModel: ElectionViewModel, voteViewModel: VoteViewModel, authViewModel: AuthViewModel) {
    ElectionListScreen("Active Elections", electionViewModel.elections.filter { it.status == ElectionStatus.ACTIVE }, navController, true) { navController.navigate("election_detail/${it.id}") }
}

@Composable
fun UpcomingElectionsPage(navController: NavController, electionViewModel: ElectionViewModel) {
    ElectionListScreen("Upcoming Elections", electionViewModel.elections.filter { it.status == ElectionStatus.UPCOMING }, navController, false) {}
}

// ==================== MY VOTES PAGE ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyVotesPage(navController: NavController, voteViewModel: VoteViewModel, authViewModel: AuthViewModel) {
    val userVotes = voteViewModel.getUserVotes(authViewModel.currentUser?.id ?: 0)
    Scaffold(topBar = {
        TopAppBar(title = { Text("My Voting History") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, navigationIconContentColor = Color.White))
    }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (userVotes.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.HowToVote, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No voting history yet", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text("Your voted elections will appear here", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                }
            } else {
                items(userVotes) { vote ->
                    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp), shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(vote.electionTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF4CAF50).copy(alpha = 0.2f)) {
                                    Text("VOTED", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Voted for: ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                Text("${vote.candidateName} (${vote.candidateParty})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Cast on: ${android.text.format.DateFormat.format("MMM dd, yyyy 'at' hh:mm a", vote.timestamp)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}

// ==================== CANDIDATES PAGE ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CandidatesPage(navController: NavController, electionViewModel: ElectionViewModel, authViewModel: AuthViewModel) {
    val allCandidates = electionViewModel.elections.flatMap { election ->
        election.candidates.map { candidate ->
            CandidateWithDetails(electionTitle = election.title, candidate = candidate, electionId = election.id, electionStatus = election.status)
        }
    }

    var selectedElection by remember { mutableStateOf<String?>(null) }
    val filtered = if (selectedElection == null) allCandidates else allCandidates.filter { it.electionTitle == selectedElection }
    val elections = electionViewModel.elections.map { it.title }.distinct()
    val isAdmin = authViewModel.isAdmin()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var candidateToDelete by remember { mutableStateOf<Pair<String, String>?>(null) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Candidate Profiles") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, navigationIconContentColor = Color.White))
    }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyRow(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = selectedElection == null, onClick = { selectedElection = null }, label = { Text("All Elections") }) }
                items(elections) { electionName -> FilterChip(selected = selectedElection == electionName, onClick = { selectedElection = electionName }, label = { Text(electionName.take(20)) }) }
            }
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(16.dp)) {
                if (filtered.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("No candidates found", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                    }
                } else {
                    items(filtered) { candidateDetails ->
                        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp), shape = RoundedCornerShape(12.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Text(candidateDetails.candidate.symbol, fontSize = 32.sp) }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(candidateDetails.candidate.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                            Text("${candidateDetails.candidate.party} • ${candidateDetails.electionTitle}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                            val statusColor = when (candidateDetails.electionStatus) {
                                                ElectionStatus.ACTIVE -> Color(0xFF4CAF50)
                                                ElectionStatus.UPCOMING -> Color(0xFF2196F3)
                                                else -> Color(0xFF9E9E9E)
                                            }
                                            Text("Status: ${candidateDetails.electionStatus.name}", style = MaterialTheme.typography.bodySmall, color = statusColor)
                                        }
                                    }
                                    if (isAdmin && candidateDetails.electionStatus != ElectionStatus.COMPLETED) {
                                        Row {
                                            IconButton(onClick = { navController.navigate("edit_candidate/${candidateDetails.electionId}/${candidateDetails.candidate.id}") }) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF2196F3))
                                            }
                                            IconButton(onClick = { candidateToDelete = Pair(candidateDetails.electionId, candidateDetails.candidate.id); showDeleteDialog = true }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(candidateDetails.candidate.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), maxLines = 3)
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(onClick = { navController.navigate("election_detail/${candidateDetails.electionId}") }, modifier = Modifier.fillMaxWidth().height(36.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) {
                                    Icon(Icons.Default.HowToVote, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Vote Now", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showDeleteDialog && candidateToDelete != null) {
        val (electionId, candidateId) = candidateToDelete!!
        val candidate = electionViewModel.elections.find { it.id == electionId }?.candidates?.find { it.id == candidateId }
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; candidateToDelete = null },
            confirmButton = {
                TextButton(onClick = {
                    electionViewModel.removeCandidate(electionId, candidateId)
                    showDeleteDialog = false
                    candidateToDelete = null
                }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
            },
            dismissButton = { TextButton({ showDeleteDialog = false; candidateToDelete = null }) { Text("Cancel") } },
            title = { Text("Delete Candidate") },
            text = { Text("Delete ${candidate?.name ?: "this candidate"}? This action cannot be undone!") }
        )
    }
}

// ==================== RESULTS PAGE ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsPage(navController: NavController, electionViewModel: ElectionViewModel, voteViewModel: VoteViewModel, authViewModel: AuthViewModel) {
    val isAdmin = authViewModel.isAdmin()
    val activeElections = electionViewModel.elections.filter { it.status == ElectionStatus.ACTIVE }
    val publishedResults = voteViewModel.getAllPublishedResults()
    var showPublishDialog by remember { mutableStateOf<Election?>(null) }
    var showSuccessMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Election Results") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, navigationIconContentColor = Color.White))
    }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (isAdmin && activeElections.isNotEmpty()) {
                item {
                    Text("Publish Results", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Select an election to publish final results", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                items(activeElections) { election ->
                    val votesCast = voteViewModel.getVotesCountForElection(election.id)
                    val currentTurnout = if (election.totalVoters > 0) (votesCast * 100 / election.totalVoters) else 0
                    val hasVotes = votesCast > 0
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(if (hasVotes) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(election.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Votes Cast: $votesCast / ${election.totalVoters}", style = MaterialTheme.typography.bodySmall, color = if (hasVotes) Color(0xFF4CAF50) else Color(0xFFF44336))
                                Text("Current Turnout: $currentTurnout%", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2196F3), fontWeight = FontWeight.Bold)
                                if (!hasVotes) Text("No votes to publish", style = MaterialTheme.typography.bodySmall, color = Color(0xFFF44336))
                            }
                            Button(onClick = { if (hasVotes) showPublishDialog = election }, enabled = hasVotes, colors = ButtonDefaults.buttonColors(containerColor = if (hasVotes) Color(0xFF4CAF50) else Color.Gray)) {
                                Icon(Icons.Default.Publish, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Publish", fontSize = 12.sp)
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            if (publishedResults.isNotEmpty()) {
                item {
                    Text("Published Results", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Official election winners and vote counts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                items(publishedResults) { result ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { navController.navigate("results_detail/${result.electionId}") }, elevation = CardDefaults.cardElevation(4.dp), shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(result.electionTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF4CAF50).copy(alpha = 0.2f)) {
                                    Text("OFFICIAL", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFF4CAF50).copy(alpha = 0.1f)), shape = RoundedCornerShape(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("🏆", fontSize = 32.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("WINNER", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                        Text(result.winnerName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(result.winnerParty, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("${result.totalVotes} votes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                        Text("${result.turnout}% turnout", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Top Candidates", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            val sortedResults = result.results.entries.sortedByDescending { it.value }.take(3)
                            sortedResults.forEachIndexed { index, (candidateId, voteCount) ->
                                val candidate = electionViewModel.getElectionById(result.electionId)?.candidates?.find { it.id == candidateId }
                                if (candidate != null) {
                                    val percentage = if (result.totalVotes > 0) (voteCount * 100 / result.totalVotes) else 0
                                    Column {
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("${index + 1}.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
                                            Text(candidate.symbol, fontSize = 16.sp, modifier = Modifier.width(36.dp))
                                            Text(candidate.name.take(20), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                            Text("$voteCount votes ($percentage%)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                        }
                                        LinearProgressIndicator(progress = { percentage.toFloat() / 100f }, modifier = Modifier.fillMaxWidth(), color = Color(0xFF4CAF50), trackColor = Color(0xFFE0E0E0))
                                    }
                                }
                            }
                            Text("Published: ${android.text.format.DateFormat.format("MMM dd, yyyy 'at' hh:mm a", result.publishedAt)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                }
            }

            if (publishedResults.isEmpty() && activeElections.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No Results Available", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text("Published election results will appear here", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }

    if (showPublishDialog != null) {
        val election = showPublishDialog!!
        val results = voteViewModel.getElectionResults(election.id)
        val totalVotes = results.values.sum()
        val winnerId = results.maxByOrNull { it.value }?.key
        val winner = election.candidates.find { it.id == winnerId }
        val votesCast = voteViewModel.getVotesCountForElection(election.id)
        val currentTurnout = if (election.totalVoters > 0) (votesCast * 100 / election.totalVoters) else 0

        AlertDialog(
            onDismissRequest = { showPublishDialog = null },
            confirmButton = {
                TextButton(onClick = {
                    val publishedResult = voteViewModel.publishResults(election.id, election.title, election.totalVoters, winner?.symbol ?: "")
                    if (publishedResult != null) {
                        showSuccessMessage = "Results for \"${election.title}\" have been published!\n\nWinner: ${publishedResult.winnerName} with ${publishedResult.totalVotes} votes\nTurnout: ${publishedResult.turnout}%"
                    } else {
                        showSuccessMessage = "Failed to publish results. No votes were cast."
                    }
                    showPublishDialog = null
                }) { Text("Publish") }
            },
            dismissButton = { TextButton({ showPublishDialog = null }) { Text("Cancel") } },
            title = { Text("Publish Final Results?") },
            text = {
                Column {
                    Text("Are you sure you want to publish final results for:")
                    Text(election.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                    Spacer(modifier = Modifier.height(12.dp))
                    if (winner != null && totalVotes > 0) {
                        Card(colors = CardDefaults.cardColors(Color(0xFF4CAF50).copy(alpha = 0.1f)), shape = RoundedCornerShape(8.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("🏆 WINNER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                Text("${winner.name} (${winner.party})", fontWeight = FontWeight.Bold)
                                Text("Total Votes: $totalVotes")
                                Text("Turnout: $currentTurnout%")
                            }
                        }
                    } else {
                        Text("⚠️ No votes have been cast yet!", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Once published, results will be visible to all users and cannot be changed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    if (showSuccessMessage != null) {
        AlertDialog(
            onDismissRequest = { showSuccessMessage = null },
            confirmButton = { TextButton({ showSuccessMessage = null }) { Text("OK") } },
            title = { Text("Success!") },
            text = { Text(showSuccessMessage!!) }
        )
    }
}
// ==================== RESULTS DETAIL SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsDetailScreen(electionId: String, navController: NavController, electionViewModel: ElectionViewModel, voteViewModel: VoteViewModel) {
    val election = electionViewModel.getElectionById(electionId)
    val publishedResult = voteViewModel.getPublishedResults(electionId)

    Scaffold(topBar = {
        TopAppBar(title = { Text(publishedResult?.electionTitle?.take(20) ?: "Election Results") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF4CAF50), titleContentColor = Color.White, navigationIconContentColor = Color.White))
    }) { padding ->
        if (publishedResult != null) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("OFFICIAL RESULTS", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                            Text(publishedResult.electionTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Text("Published: ${android.text.format.DateFormat.format("MMM dd, yyyy 'at' hh:mm a", publishedResult.publishedAt)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFFFFD700).copy(alpha = 0.15f)), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(4.dp)) {
                        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🏆", fontSize = 48.sp)
                            Text("WINNER", style = MaterialTheme.typography.labelMedium, color = Color(0xFFFFD700), fontWeight = FontWeight.Bold)
                            Text(publishedResult.winnerName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text(publishedResult.winnerParty, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF4CAF50)) {
                                Text("${publishedResult.totalVotes} TOTAL VOTES", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${publishedResult.totalVotes}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                Text("Total Votes", style = MaterialTheme.typography.bodySmall)
                            }
                            VerticalDivider(modifier = Modifier.height(40.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${publishedResult.turnout}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF2196F3))
                                Text("Turnout", style = MaterialTheme.typography.bodySmall)
                            }
                            VerticalDivider(modifier = Modifier.height(40.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${publishedResult.results.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                                Text("Candidates", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                item { Text("Vote Distribution", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                items(election?.candidates ?: emptyList()) { candidate ->
                    val votes = publishedResult.results[candidate.id] ?: 0
                    val percentage = if (publishedResult.totalVotes > 0) (votes * 100 / publishedResult.totalVotes) else 0
                    val isWinner = candidate.id == publishedResult.winnerId
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(if (isWinner) Color(0xFF4CAF50).copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp), elevation = if (isWinner) CardDefaults.cardElevation(4.dp) else CardDefaults.cardElevation(1.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(candidate.symbol, fontSize = 32.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(candidate.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            if (isWinner) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF4CAF50)) {
                                                    Text("WINNER", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        Text(candidate.party, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("$votes votes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (isWinner) Color(0xFF4CAF50) else Color(0xFF2196F3))
                                    Text("$percentage%", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            LinearProgressIndicator(progress = { percentage.toFloat() / 100f }, modifier = Modifier.fillMaxWidth().height(8.dp), color = if (isWinner) Color(0xFF4CAF50) else Color(0xFF2196F3), trackColor = Color(0xFFE0E0E0))
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    Text("Results Not Published Yet", style = MaterialTheme.typography.headlineSmall)
                    Text("The admin will publish results after the election concludes", style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Button(onClick = { navController.popBackStack() }) { Text("Go Back") }
                }
            }
        }
    }
}

// ==================== SETTINGS PAGE ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(navController: NavController, authViewModel: AuthViewModel) {
    var showPasswordDialog by remember { mutableStateOf(false) }
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, navigationIconContentColor = Color.White))
    }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary); Spacer(modifier = Modifier.width(8.dp)); Text("Account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) { Row { Icon(Icons.Default.PersonOutline, contentDescription = null, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(12.dp)); Text("Name") }; Text(authViewModel.currentUser?.name ?: "Not set", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) { Row { Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(12.dp)); Text("Email") }; Text(authViewModel.currentUser?.email ?: "Not set", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
                        if (authViewModel.isAdmin()) { Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) { Row { Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(12.dp)); Text("Role") }; Text("Administrator", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) } }
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary); Spacer(modifier = Modifier.width(8.dp)); Text("Security", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth().clickable { showPasswordDialog = true }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Row { Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(12.dp)); Text("Change Password") }; Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
            item {
                Button(onClick = { authViewModel.logout(); navController.navigate("login") { popUpTo("dashboard") { inclusive = true } } }, modifier = Modifier.fillMaxWidth().padding(16.dp), colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Logout")
                }
            }
        }
    }
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false; errorMessage = null; oldPassword = ""; newPassword = ""; confirmPassword = "" },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        oldPassword.isBlank() -> errorMessage = "Enter current password"
                        newPassword.length < 6 -> errorMessage = "Password must be 6+ characters"
                        newPassword != confirmPassword -> errorMessage = "Passwords don't match"
                        else -> if (authViewModel.changePassword(oldPassword, newPassword)) showPasswordDialog = false else errorMessage = "Incorrect password"
                    }
                }) { Text("Change") }
            },
            dismissButton = { TextButton({ showPasswordDialog = false }) { Text("Cancel") } },
            title = { Text("Change Password") },
            text = {
                Column {
                    OutlinedTextField(value = oldPassword, onValueChange = { newOld -> oldPassword = newOld; errorMessage = null }, label = { Text("Current Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), isError = errorMessage != null)
                    OutlinedTextField(value = newPassword, onValueChange = { newPass -> newPassword = newPass; errorMessage = null }, label = { Text("New Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), isError = errorMessage != null)
                    OutlinedTextField(value = confirmPassword, onValueChange = { newConfirm -> confirmPassword = newConfirm; errorMessage = null }, label = { Text("Confirm Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), isError = errorMessage != null)
                    if (errorMessage != null) { Text(errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
            }
        )
    }
}

// ==================== ADMIN PAGES ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAddCandidatePage(navController: NavController, electionViewModel: ElectionViewModel) {
    var selectedElection by remember { mutableStateOf<Election?>(null) }
    var candidateName by remember { mutableStateOf("") }
    var candidateParty by remember { mutableStateOf("") }
    var candidateSymbol by remember { mutableStateOf("") }
    var candidateDescription by remember { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val elections = electionViewModel.elections

    Scaffold(topBar = { TopAppBar(title = { Text("Add New Candidate") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, navigationIconContentColor = Color.White)) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("Select Election", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(elections) { election ->
                Card(modifier = Modifier.fillMaxWidth().clickable { selectedElection = election; candidateName = ""; candidateParty = ""; candidateSymbol = ""; candidateDescription = ""; errorMessage = null }, colors = CardDefaults.cardColors(containerColor = if (selectedElection?.id == election.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Text(election.title, fontWeight = FontWeight.Bold); Text("${election.candidates.size} candidates", style = MaterialTheme.typography.bodySmall) }
                        if (selectedElection?.id == election.id) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                    }
                }
            }
            if (selectedElection != null) {
                item { Text("Candidate Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            OutlinedTextField(value = candidateName, onValueChange = { candidateName = it; errorMessage = null }, label = { Text("Candidate Name") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = candidateParty, onValueChange = { candidateParty = it; errorMessage = null }, label = { Text("Party") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = candidateSymbol, onValueChange = { candidateSymbol = it }, label = { Text("Symbol (Emoji)") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = candidateDescription, onValueChange = { candidateDescription = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                        }
                    }
                }
                if (errorMessage != null) item { Text(errorMessage!!, color = MaterialTheme.colorScheme.error) }
                item {
                    Button(onClick = {
                        if (candidateName.isBlank() || candidateParty.isBlank()) errorMessage = "Name and Party required"
                        else {
                            val newCandidate = Candidate("c${System.currentTimeMillis()}", candidateName, candidateParty, candidateSymbol.ifEmpty { "👤" }, candidateDescription.ifEmpty { "No description" })
                            if (electionViewModel.addCandidate(selectedElection!!.id, newCandidate)) showSuccess = true else errorMessage = "Failed"
                        }
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(Color(0xFF4CAF50))) { Text("Add Candidate") }
                }
            }
        }
    }
    if (showSuccess) AlertDialog(onDismissRequest = { showSuccess = false; navController.popBackStack() }, confirmButton = { TextButton({ showSuccess = false; navController.popBackStack() }) { Text("OK") } }, title = { Text("Success!") }, text = { Text("Candidate added successfully!") })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminManageElectionsPage(navController: NavController, electionViewModel: ElectionViewModel) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var electionToDelete by remember { mutableStateOf<Election?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Manage Elections") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }, actions = { IconButton(onClick = { navController.navigate("create_election") }) { Icon(Icons.Default.Add, contentDescription = "Create Election") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White)) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(electionViewModel.elections) { election ->
                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(election.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("${election.candidates.size} candidates • ${election.totalVoters} voters", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            Surface(shape = RoundedCornerShape(8.dp), color = when (election.status) { ElectionStatus.ACTIVE -> Color(0xFF4CAF50).copy(alpha = 0.2f); ElectionStatus.UPCOMING -> Color(0xFF2196F3).copy(alpha = 0.2f); ElectionStatus.COMPLETED -> Color(0xFF9E9E9E).copy(alpha = 0.2f) }) {
                                Text(election.status.name, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = when (election.status) { ElectionStatus.ACTIVE -> Color(0xFF4CAF50); ElectionStatus.UPCOMING -> Color(0xFF2196F3); ElectionStatus.COMPLETED -> Color(0xFF757575) }, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(election.description.take(100), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { navController.navigate("edit_election/${election.id}") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Edit", fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = { navController.navigate("admin_add_candidate") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2196F3))) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Add Candidate", fontSize = 12.sp)
                            }
                            if (election.status != ElectionStatus.COMPLETED) {
                                OutlinedButton(onClick = { electionToDelete = election; showDeleteDialog = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("Delete", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth().clickable { navController.navigate("create_election") }, elevation = CardDefaults.cardElevation(2.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Create New Election", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    if (showDeleteDialog && electionToDelete != null) {
        AlertDialog(onDismissRequest = { showDeleteDialog = false; electionToDelete = null }, confirmButton = { TextButton(onClick = { electionViewModel.removeElection(electionToDelete!!.id); showDeleteDialog = false; electionToDelete = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") } }, dismissButton = { TextButton({ showDeleteDialog = false; electionToDelete = null }) { Text("Cancel") } }, title = { Text("Delete Election") }, text = { Text("Delete \"${electionToDelete?.title}\"? This will also delete all associated votes. This action cannot be undone!") })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditElectionPage(navController: NavController, electionViewModel: ElectionViewModel, electionId: String) {
    val election = electionViewModel.getElectionById(electionId)
    var title by remember { mutableStateOf(election?.title ?: "") }
    var desc by remember { mutableStateOf(election?.description ?: "") }
    var date by remember { mutableStateOf(election?.date ?: "") }
    var location by remember { mutableStateOf(election?.location ?: "") }
    var voters by remember { mutableStateOf(election?.totalVoters.toString() ?: "") }
    var status by remember { mutableStateOf(election?.status ?: ElectionStatus.UPCOMING) }
    var showSuccess by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Edit Election") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, navigationIconContentColor = Color.White)) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Edit Election Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = title, onValueChange = { title = it; errorMsg = null }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                        OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Location") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = voters, onValueChange = { voters = it; errorMsg = null }, label = { Text("Total Voters") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Text("Status", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = status == ElectionStatus.UPCOMING, onClick = { status = ElectionStatus.UPCOMING }, label = { Text("Upcoming") })
                            FilterChip(selected = status == ElectionStatus.ACTIVE, onClick = { status = ElectionStatus.ACTIVE }, label = { Text("Active") })
                            FilterChip(selected = status == ElectionStatus.COMPLETED, onClick = { status = ElectionStatus.COMPLETED }, label = { Text("Completed") })
                        }
                    }
                }
            }
            if (errorMsg != null) item { Text(errorMsg!!, color = MaterialTheme.colorScheme.error) }
            item {
                Button(onClick = {
                    when {
                        title.isBlank() -> errorMsg = "Enter title"
                        desc.isBlank() -> errorMsg = "Enter description"
                        date.isBlank() -> errorMsg = "Enter date"
                        location.isBlank() -> errorMsg = "Enter location"
                        voters.isBlank() -> errorMsg = "Enter voters"
                        voters.toIntOrNull() == null -> errorMsg = "Invalid number"
                        voters.toInt() <= 0 -> errorMsg = "Voters must be > 0"
                        else -> {
                            val updatedElection = Election(electionId, title, desc, date, location, status, election?.candidates ?: mutableListOf(), voters.toInt())
                            if (electionViewModel.updateElection(electionId, updatedElection)) showSuccess = true else errorMsg = "Update failed"
                        }
                    }
                }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(Color(0xFF4CAF50))) { Text("Save Changes") }
            }
        }
    }
    if (showSuccess) AlertDialog(onDismissRequest = { showSuccess = false; navController.popBackStack() }, confirmButton = { TextButton({ showSuccess = false; navController.popBackStack() }) { Text("OK") } }, title = { Text("Success!") }, text = { Text("Election updated successfully!") })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateElectionPage(navController: NavController, electionViewModel: ElectionViewModel) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var voters by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(ElectionStatus.UPCOMING) }
    var showSuccess by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Create Election") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, navigationIconContentColor = Color.White)) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Election Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = title, onValueChange = { title = it; errorMsg = null }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                        OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Location") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = voters, onValueChange = { voters = it; errorMsg = null }, label = { Text("Total Voters") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Text("Status", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = status == ElectionStatus.UPCOMING, onClick = { status = ElectionStatus.UPCOMING }, label = { Text("Upcoming") })
                            FilterChip(selected = status == ElectionStatus.ACTIVE, onClick = { status = ElectionStatus.ACTIVE }, label = { Text("Active") })
                            FilterChip(selected = status == ElectionStatus.COMPLETED, onClick = { status = ElectionStatus.COMPLETED }, label = { Text("Completed") })
                        }
                    }
                }
            }
            if (errorMsg != null) item { Text(errorMsg!!, color = MaterialTheme.colorScheme.error) }
            item {
                Button(onClick = {
                    when {
                        title.isBlank() -> errorMsg = "Enter title"
                        desc.isBlank() -> errorMsg = "Enter description"
                        date.isBlank() -> errorMsg = "Enter date"
                        location.isBlank() -> errorMsg = "Enter location"
                        voters.isBlank() -> errorMsg = "Enter voters"
                        voters.toIntOrNull() == null -> errorMsg = "Invalid number"
                        voters.toInt() <= 0 -> errorMsg = "Voters must be > 0"
                        else -> {
                            val newElection = Election(System.currentTimeMillis().toString(), title, desc, date, location, status, mutableListOf(), voters.toInt())
                            if (electionViewModel.addElection(newElection)) showSuccess = true else errorMsg = "Duplicate title"
                        }
                    }
                }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(Color(0xFF4CAF50))) { Text("Create Election") }
            }
        }
    }
    if (showSuccess) AlertDialog(onDismissRequest = { showSuccess = false; navController.popBackStack() }, confirmButton = { TextButton({ showSuccess = false; navController.popBackStack() }) { Text("OK") } }, title = { Text("Success!") }, text = { Text("Election created successfully!") })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCandidatePage(navController: NavController, electionViewModel: ElectionViewModel, electionId: String, candidateId: String) {
    val election = electionViewModel.getElectionById(electionId)
    val candidate = election?.candidates?.find { it.id == candidateId }
    var name by remember { mutableStateOf(candidate?.name ?: "") }
    var party by remember { mutableStateOf(candidate?.party ?: "") }
    var symbol by remember { mutableStateOf(candidate?.symbol ?: "") }
    var desc by remember { mutableStateOf(candidate?.description ?: "") }
    var showSuccess by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Edit Candidate") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = Color.White, navigationIconContentColor = Color.White)) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Edit Candidate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Election: ${election?.title}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = party, onValueChange = { party = it }, label = { Text("Party") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = symbol, onValueChange = { symbol = it }, label = { Text("Symbol") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    if (candidate != null) {
                        val updated = candidate.copy(name = name, party = party, symbol = symbol.ifEmpty { candidate.symbol }, description = desc.ifEmpty { candidate.description })
                        if (electionViewModel.updateCandidate(electionId, candidateId, updated)) showSuccess = true
                    }
                }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(Color(0xFF4CAF50))) { Text("Save Changes") }
                OutlinedButton(onClick = { showDelete = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
            }
        }
    }
    if (showSuccess) AlertDialog(onDismissRequest = { showSuccess = false; navController.popBackStack() }, confirmButton = { TextButton({ showSuccess = false; navController.popBackStack() }) { Text("OK") } }, title = { Text("Success!") }, text = { Text("Candidate updated successfully!") })
    if (showDelete) AlertDialog(onDismissRequest = { showDelete = false }, confirmButton = { TextButton(onClick = { electionViewModel.removeCandidate(electionId, candidateId); showDelete = false; navController.popBackStack() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") } }, dismissButton = { TextButton({ showDelete = false }) { Text("Cancel") } }, title = { Text("Delete Candidate") }, text = { Text("Delete ${candidate?.name ?: "this candidate"}?") })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogPage(navController: NavController, voteViewModel: VoteViewModel) {
    val entries = voteViewModel.auditLog
    Scaffold(topBar = { TopAppBar(title = { Text("Audit Log") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (entries.isEmpty()) {
                item { Text("No audit entries") }
            } else {
                items(entries.reversed()) { entry ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(entry.action, fontWeight = FontWeight.Bold)
                            Text(entry.details, fontSize = 12.sp)
                            Text("${entry.userName} - ${android.text.format.DateFormat.format("MMM dd, hh:mm a", entry.timestamp)}", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

// ==================== ELECTION DETAIL SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElectionDetailScreen(electionId: String, navController: NavController, electionViewModel: ElectionViewModel, voteViewModel: VoteViewModel, authViewModel: AuthViewModel) {
    var selectedCandidate by remember { mutableStateOf<Candidate?>(null) }
    var showConfirm by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    val election = electionViewModel.getElectionById(electionId)
    val hasVoted = authViewModel.hasVoted(electionId)
    val isPublished = voteViewModel.getPublishedResults(electionId) != null

    Scaffold(topBar = { TopAppBar(title = { Text(election?.title ?: "Election Details") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        if (isPublished) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null, modifier = Modifier.size(80.dp), tint = Color(0xFF4CAF50))
                    Text("Results Published!", style = MaterialTheme.typography.headlineSmall)
                    Text("View results in the Results section.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { navController.navigate("results") }) { Text("View Results") }
                }
            }
        } else if (hasVoted) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(80.dp), tint = Color(0xFF4CAF50))
                    Text("You have already voted!", style = MaterialTheme.typography.headlineSmall)
                    Button(onClick = { navController.popBackStack() }) { Text("Go Back") }
                }
            }
        } else if (election != null && election.status == ElectionStatus.ACTIVE) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(election.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(election.description, style = MaterialTheme.typography.bodyMedium)
                        Text("Date: ${election.date}", style = MaterialTheme.typography.bodySmall)
                        Text("Location: ${election.location}", style = MaterialTheme.typography.bodySmall)
                        Text("Total Voters: ${election.totalVoters}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("Select Your Candidate", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                election.candidates.forEach { candidate ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { selectedCandidate = candidate }) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(candidate.symbol, fontSize = 32.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(candidate.name, fontWeight = FontWeight.Bold)
                                    Text(candidate.party, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    Text(candidate.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                }
                            }
                            RadioButton(selected = selectedCandidate?.id == candidate.id, onClick = { selectedCandidate = candidate })
                        }
                    }
                }
                Button(onClick = { if (selectedCandidate != null) showConfirm = true }, modifier = Modifier.fillMaxWidth(), enabled = selectedCandidate != null, colors = ButtonDefaults.buttonColors(Color(0xFF4CAF50))) {
                    Icon(Icons.Default.HowToVote, contentDescription = null)
                    Text("Submit Vote", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (showConfirm && selectedCandidate != null && election != null) {
        AlertDialog(onDismissRequest = { showConfirm = false }, confirmButton = { TextButton(onClick = { if (voteViewModel.castVote(authViewModel.currentUser?.id ?: 0, authViewModel.currentUser?.name ?: "", election.id, election.title, selectedCandidate!!.id, selectedCandidate!!.name, selectedCandidate!!.party)) { authViewModel.markVoted(election.id); showConfirm = false; showSuccess = true } }) { Text("Confirm") } }, dismissButton = { TextButton({ showConfirm = false }) { Text("Cancel") } }, title = { Text("Confirm Vote") }, text = { Text("Vote for ${selectedCandidate!!.name} (${selectedCandidate!!.party})?") })
    }
    if (showSuccess) { AlertDialog(onDismissRequest = { showSuccess = false; navController.popBackStack() }, confirmButton = { TextButton({ showSuccess = false; navController.popBackStack() }) { Text("Done") } }, title = { Text("Thank You!") }, text = { Text("Your vote has been recorded.") }) }
}