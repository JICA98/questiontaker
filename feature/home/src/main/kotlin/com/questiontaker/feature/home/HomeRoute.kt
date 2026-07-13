package com.questiontaker.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.questiontaker.data.QuestionRepository
import com.questiontaker.data.UpdateManager
import com.questiontaker.data.model.Question
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface Screen {
    object Dashboard : Screen
    data class Practice(val source: String?, val initialIndex: Int = 0) : Screen
    data class MockExam(val questionCount: Int, val durationMinutes: Int) : Screen
    data class MockExamResults(
        val examQuestions: List<Question>,
        val userAnswers: Map<Int, String>,
        val score: Int,
        val timeSpentSeconds: Int,
        val totalTimeSeconds: Int
    ) : Screen
    object Bookmarks : Screen
    data class ViewQuestions(val source: String?) : Screen
}

data class UpdateInfo(
    val tagName: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val isForceCheck: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeRoute(repository: QuestionRepository) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
    var isDarkTheme by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    var updateDialogInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var checkingForUpdates by remember { mutableStateOf(false) }
    var showNoUpdateDialog by remember { mutableStateOf(false) }

    // Auto-check for updates at startup
    LaunchedEffect(Unit) {
        UpdateManager.checkForUpdate(
            context = context,
            forceCheck = false,
            onUpdateAvailable = { tagName, downloadUrl, releaseNotes ->
                updateDialogInfo = UpdateInfo(tagName, downloadUrl, releaseNotes, isForceCheck = false)
            }
        )
    }

    // Dynamic color container wrapper for interactive theme support
    val customThemeColors = if (isDarkTheme) {
        darkColorScheme(
            primary = Color(0xFF4DB6AC),
            onPrimary = Color(0xFF003730),
            secondary = Color(0xFF80CBC4),
            background = Color(0xFF0B131A),
            surface = Color(0xFF15222E),
            onSurface = Color(0xFFE2E8F0),
            error = Color(0xFFCF6679)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF00796B),
            onPrimary = Color(0xFFFFFFFF),
            secondary = Color(0xFF009688),
            background = Color(0xFFF4F6F6),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF1C1B1F),
            error = Color(0xFFB00020)
        )
    }

    MaterialTheme(colorScheme = customThemeColors) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (val screen = currentScreen) {
                is Screen.Dashboard -> DashboardScreen(
                    repository = repository,
                    isDarkTheme = isDarkTheme,
                    onThemeToggle = { isDarkTheme = !isDarkTheme },
                    onNavigateToPractice = { source -> currentScreen = Screen.Practice(source) },
                    onNavigateToViewQuestions = { source -> currentScreen = Screen.ViewQuestions(source) },
                    onNavigateToMock = { currentScreen = Screen.MockExam(30, 30) },
                    onNavigateToBookmarks = { currentScreen = Screen.Bookmarks },
                    onCheckForUpdates = {
                        checkingForUpdates = true
                        UpdateManager.checkForUpdate(
                            context = context,
                            forceCheck = true,
                            onUpdateAvailable = { tagName, downloadUrl, releaseNotes ->
                                checkingForUpdates = false
                                updateDialogInfo = UpdateInfo(tagName, downloadUrl, releaseNotes, isForceCheck = true)
                            },
                            onNoUpdate = {
                                checkingForUpdates = false
                                showNoUpdateDialog = true
                            }
                        )
                    },
                    onOpenGitHub = {
                        UpdateManager.openGitHub(context)
                    }
                )
                is Screen.Practice -> PracticeScreen(
                    repository = repository,
                    source = screen.source,
                    initialIndex = screen.initialIndex,
                    onBack = { currentScreen = Screen.Dashboard }
                )
                is Screen.ViewQuestions -> ViewQuestionsScreen(
                    repository = repository,
                    source = screen.source,
                    onBack = { currentScreen = Screen.Dashboard },
                    onNavigateToPractice = { source, index ->
                        currentScreen = Screen.Practice(source, index)
                    }
                )
                is Screen.MockExam -> MockExamScreen(
                    repository = repository,
                    questionCount = screen.questionCount,
                    durationMinutes = screen.durationMinutes,
                    onSubmit = { examQs, userAns, score, timeSpent, totalTime ->
                        currentScreen = Screen.MockExamResults(examQs, userAns, score, timeSpent, totalTime)
                    },
                    onBack = { currentScreen = Screen.Dashboard }
                )
                is Screen.MockExamResults -> MockExamResultsScreen(
                    results = screen,
                    onBack = { currentScreen = Screen.Dashboard }
                )
                is Screen.Bookmarks -> BookmarksScreen(
                    repository = repository,
                    onBack = { currentScreen = Screen.Dashboard }
                )
            }
        }

        // Dialogs for Updates
        updateDialogInfo?.let { info ->
            AlertDialog(
                onDismissRequest = { updateDialogInfo = null },
                title = { Text("New Update Available (${info.tagName})") },
                text = {
                    Column {
                        Text("A new version of QuestionTaker is available. Would you like to update?")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Release Notes:\n${info.releaseNotes}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            UpdateManager.startDownload(context, info.downloadUrl, info.tagName)
                            updateDialogInfo = null
                        }
                    ) {
                        Text("Update Now")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                UpdateManager.skipVersion(context, info.tagName)
                                updateDialogInfo = null
                            }
                        ) {
                            Text("Skip this version")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { updateDialogInfo = null }) {
                            Text("Later")
                        }
                    }
                }
            )
        }

        if (checkingForUpdates) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Checking for Updates") },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text("Fetching latest release info...")
                    }
                },
                confirmButton = {}
            )
        }

        if (showNoUpdateDialog) {
            AlertDialog(
                onDismissRequest = { showNoUpdateDialog = false },
                title = { Text("Up to Date") },
                text = { Text("You are already on the latest version of QuestionTaker (${UpdateManager.getCurrentVersion(context)}).") },
                confirmButton = {
                    Button(onClick = { showNoUpdateDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

// -------------------------------------------------------------
// DASHBOARD SCREEN
// -------------------------------------------------------------
@Composable
fun DashboardScreen(
    repository: QuestionRepository,
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    onNavigateToPractice: (String?) -> Unit,
    onNavigateToViewQuestions: (String?) -> Unit,
    onNavigateToMock: () -> Unit,
    onNavigateToBookmarks: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onOpenGitHub: () -> Unit
) {
    val totalQuestions = repository.getAllQuestions().size
    val completedCount = repository.getCompletedQuestionsCount()
    val accuracy = repository.getAccuracy()
    val starredCount = repository.getBookmarkedQuestions().size
    val rawSources = repository.getAllSources()
    val sources = remember(rawSources) {
        val predefinedOrder = listOf(
            "AIIMS CRE Anesthesia MCQs",
            "AIIMS CRE Complete 115 MCQ",
            "AIIMS CRE Anesthesia MCQs Complete",
            "AIIMS CRE OT Technician PYQ-v2",
            "AIIMS CRE OT Technician PYQ Part5",
            "OT Technician Exam Prep",
            "AIIMS CRE OT Technician Questions",
            "AIIMS CRE OT Technician Prep",
            "anesthesia ot technician exam part 10",
            "Anesthesia OT Technician MCQs",
            "AIIMS CRE OT Technician Part 12",
            "anesthesia ot technician questions",
            "AIIMS CRE OT Anaesthesia Technician 100 Practice Questions",
            "AIIMS CRE OT Anaesthesia Practice Paper 100Q",
            "AIIMS CRE OT Technician Previous Year Question Paper Solution Part#2",
            "AIIMS CRE OT Technician Previous Year Question Paper Solution Part 15",
            "anesthesia ot technician exam part 17",
            "anesthesia ot technician exam part 18",
            "Anesthesia technician 1st year Question paper Paper -A",
            "anesthesia ot technician exam part 20"
        )
        rawSources.sortedBy { predefinedOrder.indexOf(it) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome Header Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.align(Alignment.CenterStart)) {
                        Text(
                            text = "AIIMS CRE Exam Prep",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontFamily = FontFamily.SansSerif
                            )
                        )
                        Text(
                            text = "Anesthesia & OT Technician (Post Code 11)",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        )
                    }

                    // Theme Toggle Button
                    IconButton(
                        onClick = onThemeToggle,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .background(
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f),
                                CircleShape
                            )
                    ) {
                        Text(
                            text = if (isDarkTheme) "☀️" else "🌙",
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }

        // Statistics Overview Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Your Progress Statistics",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Circular Progress Indicator
                        val progressFraction = if (totalQuestions > 0) completedCount.toFloat() / totalQuestions else 0f
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(80.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 8.dp,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${(progressFraction * 100).toInt()}%",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(text = "done", fontSize = 10.sp, color = Color.Gray)
                            }
                        }

                        // Statistics Grid Columns
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 24.dp)
                        ) {
                            StatRow("Completed Questions", "$completedCount / $totalQuestions")
                            StatRow("Overall Accuracy", if (completedCount > 0) "$accuracy%" else "-")
                            StatRow("Starred Questions", "$starredCount saved")
                        }
                    }
                }
            }
        }

        // Quick Launch Actions Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Launch Study Mode",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DashboardActionButton(
                        title = "Practice Mode",
                        subtitle = "Instant explanations",
                        emoji = "📖",
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        textColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToPractice(null) }
                    )

                    DashboardActionButton(
                        title = "Mock Exam",
                        subtitle = "Timed simulation",
                        emoji = "⏱️",
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                        textColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToMock
                    )
                }

                DashboardActionButton(
                    title = "Starred Review Room",
                    subtitle = "Review questions saved by you",
                    emoji = "⭐",
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                    textColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onNavigateToBookmarks
                )
            }
        }

        // Practice Sets Categories Section
        item {
            Text(
                text = "Practice by Topic & Paper",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        items(sources) { source ->
            val count = repository.getQuestionsBySource(source).size
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToPractice(source) }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = getDisplaySourceName(source),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            LazyRow(
                                modifier = Modifier.padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(getDisplaySourceDescription(source)) { tag ->
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    ) {
                                        Text(
                                            text = tag,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "$count MCQs available",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        Text(
                            text = "➡️",
                            fontSize = 16.sp,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }

                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { onNavigateToViewQuestions(source) }
                        ) {
                            Text("👁️ View Questions", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        }

                        TextButton(
                            onClick = { onNavigateToPractice(source) }
                        ) {
                            Text("📖 Practice Mode", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        // Check for updates, Open GitHub, Reset progress at bottom
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onCheckForUpdates,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Check for Updates", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onOpenGitHub,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Open GitHub", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                
                TextButton(
                    onClick = { repository.resetStats() },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Reset Study Progress", color = Color.Red.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 13.sp, color = Color.Gray)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DashboardActionButton(
    title: String,
    subtitle: String,
    emoji: String,
    color: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = emoji, fontSize = 24.sp)
            Text(text = title, fontWeight = FontWeight.Bold, color = textColor, fontSize = 15.sp)
            Text(text = subtitle, fontSize = 12.sp, color = textColor.copy(alpha = 0.7f))
        }
    }
}

// -------------------------------------------------------------
// PRACTICE MODE SCREEN
// -------------------------------------------------------------
@Composable
fun PracticeScreen(
    repository: QuestionRepository,
    source: String?,
    initialIndex: Int,
    onBack: () -> Unit
) {
    val questions = remember {
        when (source) {
            null -> repository.getAllQuestions()
            "Starred Questions Review" -> repository.getBookmarkedQuestions()
            else -> repository.getQuestionsBySource(source)
        }
    }

    var currentIndex by rememberSaveable { mutableStateOf(initialIndex) }
    
    if (questions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("No questions found.")
                Button(onClick = onBack) { Text("Back") }
            }
        }
        return
    }

    val currentQuestion = questions[currentIndex]
    val shuffledOptions = remember(currentQuestion) { currentQuestion.options.shuffled() }
    var selectedOption by remember(currentQuestion) { mutableStateOf<String?>(null) }
    var isStarred by remember(currentQuestion) { mutableStateOf(repository.isBookmarked(currentQuestion.id)) }

    BackHandler(enabled = true) {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Practice Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(onClick = onBack) {
                    Text("⬅️", fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = getDisplaySourceName(source),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(text = "Study Study Mode", fontSize = 11.sp, color = Color.Gray)
                }
            }

            // Bookmark Star Button
            IconButton(
                onClick = {
                    isStarred = repository.toggleBookmark(currentQuestion.id)
                }
            ) {
                Text(
                    text = if (isStarred) "⭐" else "☆",
                    fontSize = 24.sp,
                    color = if (isStarred) Color(0xFFFFD700) else Color.Gray
                )
            }
        }

        // Progress Bar
        val progress = (currentIndex + 1).toFloat() / questions.size
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Question ${currentIndex + 1} of ${questions.size}", fontSize = 12.sp, color = Color.Gray)
                Text(text = "${((progress) * 100).toInt()}% Done", fontSize = 12.sp, color = Color.Gray)
            }
        }

        // LazyColumn for scrollable question text, options and explanation
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Question Text
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                ) {
                    Text(
                        text = currentQuestion.question,
                        style = MaterialTheme.typography.titleMedium.copy(
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }

            // Options List
            if (shuffledOptions.isNotEmpty()) {
                itemsIndexed(shuffledOptions) { idx, option ->
                    val displayKey = ('A' + idx).toString()
                    val isCorrectOption = option.key.equals(currentQuestion.answer, ignoreCase = true)
                    val isSelectedOption = option.key.equals(selectedOption, ignoreCase = true)
                    
                    val cardBgColor = when {
                        selectedOption == null -> MaterialTheme.colorScheme.surface
                        isSelectedOption && isCorrectOption -> Color(0xFFE8F5E9) // Soft Green
                        isSelectedOption && !isCorrectOption -> Color(0xFFFFEBEE) // Soft Red
                        isCorrectOption -> Color(0xFFE8F5E9) // Highlight correct if user was wrong
                        else -> MaterialTheme.colorScheme.surface
                    }

                    val borderColor = when {
                        selectedOption == null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        isSelectedOption && isCorrectOption -> Color(0xFF4CAF50)
                        isSelectedOption && !isCorrectOption -> Color(0xFFF44336)
                        isCorrectOption -> Color(0xFF4CAF50)
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                    }

                    val keyBgColor = when {
                        selectedOption == null -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        isSelectedOption && isCorrectOption -> Color(0xFF2E7D32)
                        isSelectedOption && !isCorrectOption -> Color(0xFFC62828)
                        isCorrectOption -> Color(0xFF2E7D32)
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    }

                    val keyTextColor = if (selectedOption == null || (!isSelectedOption && !isCorrectOption)) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.white()
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = selectedOption == null) {
                                selectedOption = option.key
                                val isCorrect = option.key.equals(currentQuestion.answer, ignoreCase = true)
                                repository.recordAttempt(currentQuestion.id, isCorrect)
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                        border = BorderStroke(1.5.dp, borderColor)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(keyBgColor, CircleShape)
                            ) {
                                Text(
                                    text = displayKey,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = keyTextColor
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = option.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            } else {
                // Direct Q&A fallback (no options) - Show Text box for User to think and Reveal button
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "This is a direct question without multiple choices. Think about your answer, then tap below to reveal.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        )
                        
                        if (selectedOption == null) {
                            Button(
                                onClick = {
                                    selectedOption = "REVEALED"
                                    repository.recordAttempt(currentQuestion.id, true)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Reveal Answer")
                            }
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                border = BorderStroke(1.5.dp, Color(0xFF4CAF50))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Correct Answer:",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32),
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = currentQuestion.answer,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Explanation panel reveal
            item {
                AnimatedVisibility(
                    visible = selectedOption != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "💡", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Explanation",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            val exp = currentQuestion.explanation
                            Text(
                                text = if (exp.isNotEmpty()) exp else "No detailed explanation available for this question. Standard AIIMS CRE syllabus facts apply.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Navigation Footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { currentIndex-- },
                enabled = currentIndex > 0,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), contentColor = MaterialTheme.colorScheme.onSurface),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Previous")
            }

            Button(
                onClick = { currentIndex++ },
                enabled = currentIndex < questions.size - 1 && selectedOption != null,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Next Question")
            }
        }
    }
}

// -------------------------------------------------------------
// MOCK EXAM SCREEN
// -------------------------------------------------------------
@Composable
fun MockExamScreen(
    repository: QuestionRepository,
    questionCount: Int,
    durationMinutes: Int,
    onSubmit: (List<Question>, Map<Int, String>, Int, Int, Int) -> Unit,
    onBack: () -> Unit
) {
    // Select random questions from the repository
    val examQuestions = remember {
        repository.getAllQuestions().shuffled().take(questionCount)
    }

    var currentIndex by rememberSaveable { mutableStateOf(0) }
    val userAnswers = remember { mutableStateMapOf<Int, String>() }
    var timeRemainingSeconds by remember { mutableStateOf(durationMinutes * 60) }
    var showGridDialog by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        showExitConfirmDialog = true
    }

    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            title = { Text("Exit Exam?") },
            text = { Text("Are you sure you want to exit? Your exam progress will be lost.") },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmDialog = false
                        onBack()
                    }
                ) {
                    Text("Exit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Start timer coroutine
    LaunchedEffect(key1 = timeRemainingSeconds) {
        if (timeRemainingSeconds > 0) {
            delay(1000L)
            timeRemainingSeconds--
        } else {
            // Auto submit when timer runs out
            var score = 0
            examQuestions.forEachIndexed { idx, q ->
                if (userAnswers[idx].equals(q.answer, ignoreCase = true)) {
                    score++
                }
            }
            onSubmit(examQuestions, userAnswers.toMap(), score, durationMinutes * 60, durationMinutes * 60)
        }
    }

    val currentQuestion = examQuestions[currentIndex]
    val shuffledOpts = remember(currentQuestion) { currentQuestion.options.shuffled() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Mock Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Text("🚪", fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Mock Exam Simulation",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    val minutes = timeRemainingSeconds / 60
                    val seconds = timeRemainingSeconds % 60
                    val timerText = String.format("%02d:%02d", minutes, seconds)
                    Text(
                        text = "Timer: $timerText",
                        fontWeight = FontWeight.Bold,
                        color = if (timeRemainingSeconds < 180) Color.Red else MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Questions Grid Icon Button
                IconButton(
                    onClick = { showGridDialog = !showGridDialog },
                    modifier = Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape)
                ) {
                    Text("📊", fontSize = 18.sp)
                }

                // Submit Button
                Button(
                    onClick = {
                        // Calculate score
                        var score = 0
                        examQuestions.forEachIndexed { idx, q ->
                            val userAns = userAnswers[idx]
                            if (userAns != null && userAns.equals(q.answer, ignoreCase = true)) {
                                score++
                            }
                        }
                        val timeSpent = (durationMinutes * 60) - timeRemainingSeconds
                        onSubmit(examQuestions, userAnswers.toMap(), score, timeSpent, durationMinutes * 60)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Submit", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Progress Bar
        val progress = (currentIndex + 1).toFloat() / examQuestions.size
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Question ${currentIndex + 1} of ${examQuestions.size}", fontSize = 12.sp, color = Color.Gray)
                Text(text = "${userAnswers.size} of ${examQuestions.size} answered", fontSize = 12.sp, color = Color.Gray)
            }
        }

        // Dialog for grid navigator
        if (showGridDialog) {
            AlertDialog(
                onDismissRequest = { showGridDialog = false },
                title = { Text("Question Navigator") },
                text = {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.height(200.dp)
                    ) {
                        itemsIndexed(examQuestions) { idx, _ ->
                            val isAnswered = userAnswers.containsKey(idx)
                            val isCurrent = idx == currentIndex
                            val cellColor = when {
                                isCurrent -> MaterialTheme.colorScheme.primary
                                isAnswered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                            }
                            val cellTextColor = if (isCurrent) Color.white() else MaterialTheme.colorScheme.onSurface

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(cellColor)
                                    .clickable {
                                        currentIndex = idx
                                        showGridDialog = false
                                    }
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = cellTextColor
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showGridDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        // Active Question Area
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Question Text
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                ) {
                    Text(
                        text = currentQuestion.question,
                        style = MaterialTheme.typography.titleMedium.copy(
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }

            // Options
            if (shuffledOpts.isNotEmpty()) {
                itemsIndexed(shuffledOpts) { idx, option ->
                    val displayKey = ('A' + idx).toString()
                    val isSelected = userAnswers[currentIndex] == option.key
                    val cardBgColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                    val borderColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                userAnswers[currentIndex] = option.key
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                        border = BorderStroke(1.5.dp, borderColor)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        CircleShape
                                    )
                            ) {
                                Text(
                                    text = displayKey,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (isSelected) Color.white() else MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = option.text,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            } else {
                // Direct Q-A in Mock Exam: input or simple text reveal selection
                // In mock exam, since typing is complex, we allow selecting A (Correct) / B (Incorrect) or skip
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "This is a direct question. Select whether you know the answer or want to mark it as complete.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            val isMarkedKnow = userAnswers[currentIndex] == "KNOW"
                            val isMarkedUnsure = userAnswers[currentIndex] == "UNSURE"
                            
                            Button(
                                onClick = { userAnswers[currentIndex] = "KNOW" },
                                colors = ButtonDefaults.buttonColors(containerColor = if (isMarkedKnow) Color(0xFF4CAF50) else Color.Gray),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("I Know This")
                            }

                            Button(
                                onClick = { userAnswers[currentIndex] = "UNSURE" },
                                colors = ButtonDefaults.buttonColors(containerColor = if (isMarkedUnsure) Color(0xFFF44336) else Color.Gray),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Unsure/Skip")
                            }
                        }
                    }
                }
            }
        }

        // Navigation Footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { currentIndex-- },
                enabled = currentIndex > 0,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), contentColor = MaterialTheme.colorScheme.onSurface),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Previous")
            }

            Button(
                onClick = { currentIndex++ },
                enabled = currentIndex < examQuestions.size - 1,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Next")
            }
        }
    }
}

// -------------------------------------------------------------
// MOCK EXAM RESULTS SCREEN
// -------------------------------------------------------------
@Composable
fun MockExamResultsScreen(
    results: Screen.MockExamResults,
    onBack: () -> Unit
) {
    BackHandler(enabled = true) {
        onBack()
    }

    val totalQs = results.examQuestions.size
    val score = results.score
    val percentage = (score * 100) / totalQs
    val minutesSpent = results.timeSpentSeconds / 60
    val secondsSpent = results.timeSpentSeconds % 60

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Results Summary Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Exam Results",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "$score / $totalQs",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 48.sp
                            )
                        )
                        Text(
                            text = "$percentage% Correct Accuracy",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                        Text(
                            text = String.format("Time Spent: %02d:%02d", minutesSpent, secondsSpent),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onPrimary, contentColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Back to Dashboard", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Review list header
        item {
            Text(
                text = "Detailed Review",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Loop and render review items
        results.examQuestions.forEachIndexed { index, q ->
            item {
                val userAns = results.userAnswers[index]
                val isCorrect = userAns != null && userAns.equals(q.answer, ignoreCase = true)
                
                val statusColor = if (isCorrect) Color(0xFF4CAF50) else Color(0xFFF44336)
                val statusLabel = if (isCorrect) "✓ Correct" else "✗ Incorrect"

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Question ${index + 1}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = statusLabel,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = statusColor
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = q.question,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (q.options.isNotEmpty()) {
                            q.options.forEach { opt ->
                                val isCorrectOpt = opt.key.equals(q.answer, ignoreCase = true)
                                val isUserSelectedOpt = opt.key.equals(userAns, ignoreCase = true)

                                val optBg = when {
                                    isCorrectOpt -> Color(0xFFE8F5E9)
                                    isUserSelectedOpt -> Color(0xFFFFEBEE)
                                    else -> Color.Transparent
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .background(optBg, RoundedCornerShape(4.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(
                                                if (isCorrectOpt) Color(0xFF2E7D32) else if (isUserSelectedOpt) Color(0xFFC62828) else Color.Gray.copy(alpha = 0.15f),
                                                CircleShape
                                            )
                                    ) {
                                        Text(
                                            text = opt.key,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCorrectOpt || isUserSelectedOpt) Color.white() else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = opt.text, fontSize = 13.sp)
                                }
                            }
                        } else {
                            // Direct Q-A results
                            Text(text = "Your attempt state: $userAns", fontSize = 13.sp)
                            Text(text = "Correct Answer: ${q.answer}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        val exp = q.explanation
                        Text(
                            text = "💡 Explanation: " + (if (exp.isNotEmpty()) exp else "Standard medical exam curriculum facts apply."),
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier
                                .background(Color.Gray.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// BOOKMARKS REVIEW SCREEN
// -------------------------------------------------------------
@Composable
fun BookmarksScreen(
    repository: QuestionRepository,
    onBack: () -> Unit
) {
    val starredQuestions = remember { repository.getBookmarkedQuestions() }

    if (starredQuestions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "⭐",
                    fontSize = 64.sp
                )
                Text(
                    text = "No Starred Questions Yet",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "Star questions during practice or exams to save them here for quick review.",
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
                Button(onClick = onBack) {
                    Text("Return to Dashboard")
                }
            }
        }
    } else {
        // Reuse Practice Screen logic but bind to bookmarked items
        PracticeScreen(
            repository = repository,
            source = "Starred Questions Review",
            initialIndex = 0,
            onBack = onBack
        )
    }
}

// Helper color extension for compose
private fun Color.Companion.white(): Color = Color(0xFFFFFFFF)

fun getDisplaySourceName(rawSource: String?): String {
    if (rawSource == null) return "General Practice"
    return when (rawSource) {
        "AIIMS CRE Anesthesia MCQs" -> "Part 1"
        "AIIMS CRE Complete 115 MCQ" -> "Part 2"
        "AIIMS CRE Anesthesia MCQs Complete" -> "Part 3"
        "AIIMS CRE OT Technician PYQ-v2" -> "Part 4"
        "AIIMS CRE OT Technician PYQ Part5" -> "Part 5"
        "OT Technician Exam Prep" -> "Part 6"
        "AIIMS CRE OT Technician Questions" -> "Part 7"
        "AIIMS CRE OT Technician Prep" -> "Part 8"
        "anesthesia ot technician exam part 10" -> "Part 9"
        "Anesthesia OT Technician MCQs" -> "Part 10"
        "AIIMS CRE OT Technician Part 12" -> "Part 11"
        "anesthesia ot technician questions" -> "Part 12"
        "AIIMS CRE OT Anaesthesia Technician 100 Practice Questions" -> "Part 13"
        "AIIMS CRE OT Anaesthesia Practice Paper 100Q" -> "Part 14"
        "AIIMS CRE OT Technician Previous Year Question Paper Solution Part#2" -> "Part 15"
        "AIIMS CRE OT Technician Previous Year Question Paper Solution Part 15" -> "Part 16"
        "anesthesia ot technician exam part 17" -> "Part 17"
        "anesthesia ot technician exam part 18" -> "Part 18"
        "Anesthesia technician 1st year Question paper Paper -A" -> "Part 19"
        "anesthesia ot technician exam part 20" -> "Part 20"
        else -> rawSource
    }
}

fun getDisplaySourceDescription(rawSource: String?): List<String> {
    if (rawSource == null) return listOf("Mixed Topics", "Random", "Comprehensive")
    return when (rawSource) {
        "AIIMS CRE Anesthesia MCQs" -> listOf("Capnography", "Airway Devices", "Medical Gases", "Pharmacology")
        "AIIMS CRE Complete 115 MCQ" -> listOf("Airway Management", "Intubation", "Laryngoscopy", "Supraglottic Devices")
        "AIIMS CRE Anesthesia MCQs Complete" -> listOf("Pharmacology", "Capnography", "Fluid Therapy", "Airway Complications")
        "AIIMS CRE OT Technician PYQ-v2" -> listOf("Preoxygenation", "Pre-op Fasting", "Anaphylaxis", "Induction Agents")
        "AIIMS CRE OT Technician PYQ Part5" -> listOf("General Knowledge", "Mathematics", "Aptitude", "Logical Reasoning")
        "OT Technician Exam Prep" -> listOf("Gas Cylinders", "Physics in Anesthesia", "Airway Equipment", "Vitals")
        "AIIMS CRE OT Technician Questions" -> listOf("General Anatomy", "Waste Management", "Breathing Circuits", "Critical Care")
        "AIIMS CRE OT Technician Prep" -> listOf("Anesthesia Equipment", "Pharmacology", "Monitoring", "Physics")
        "anesthesia ot technician exam part 10" -> listOf("Anesthesia Equipment", "Pharmacology", "Monitoring", "Physics")
        "Anesthesia OT Technician MCQs" -> listOf("Anesthesia Machine", "Capnography", "Airway Complications", "Surgical Setup")
        "AIIMS CRE OT Technician Part 12" -> listOf("Gas Cylinders", "Muscle Relaxants", "Sterilization", "OT Environment")
        "anesthesia ot technician questions" -> listOf("Human Anatomy", "Physiology", "Blood & Circulation", "Nervous System")
        "AIIMS CRE OT Anaesthesia Technician 100 Practice Questions" -> listOf("OT Design & Zones", "Sterilization", "Biomedical Waste", "Infection Control")
        "AIIMS CRE OT Anaesthesia Practice Paper 100Q" -> listOf("Sterilization", "Surgical Instruments", "Fumigation", "Waste Management")
        "AIIMS CRE OT Technician Previous Year Question Paper Solution Part#2" -> listOf("Biomedical Waste", "ECG", "Sterilization", "CPR")
        "AIIMS CRE OT Technician Previous Year Question Paper Solution Part 15" -> listOf("Cranial Nerves", "Salivary Glands", "Hemophilia", "Blood Testing")
        "anesthesia ot technician exam part 17" -> listOf("Anesthesia Machine", "IV Lines", "Airway Management", "PPE")
        "anesthesia ot technician exam part 18" -> listOf("Pulse Oximetry", "Pre-oxygenation", "Ventilation", "Propofol")
        "Anesthesia technician 1st year Question paper Paper -A" -> listOf("Cell Biology", "Skeletal System", "Respiratory System", "Heart Structure")
        "anesthesia ot technician exam part 20" -> listOf("Gas Cylinder Color", "Laryngoscope Blades", "CVP", "ASA Class")
        else -> listOf("Practice Paper")
    }
}

// -------------------------------------------------------------
// VIEW QUESTIONS SCREEN (READ-ONLY VERTICAL SCROLL)
// -------------------------------------------------------------
@Composable
fun ViewQuestionsScreen(
    repository: QuestionRepository,
    source: String?,
    onBack: () -> Unit,
    onNavigateToPractice: (String?, Int) -> Unit
) {
    val questions = remember {
        when (source) {
            null -> repository.getAllQuestions()
            "Starred Questions Review" -> repository.getBookmarkedQuestions()
            else -> repository.getQuestionsBySource(source)
        }
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    if (questions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("No questions found.")
                Button(onClick = onBack) { Text("Back") }
            }
        }
        return
    }

    BackHandler(enabled = true) {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(onClick = onBack) {
                    Text("⬅️", fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = getDisplaySourceName(source),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(text = "View Mode (Read-Only)", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }

        // Horizontal Quick-Jump bar
        Text(
            text = "Jump to Question:",
            fontSize = 11.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(questions) { index, _ ->
                val isSelected = firstVisibleIndex == index
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                        .clickable {
                            coroutineScope.launch {
                                listState.animateScrollToItem(index)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Q${index + 1}",
                        color = if (isSelected) Color.white() else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // LazyColumn for the questions list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            itemsIndexed(questions) { index, question ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Card Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Question ${index + 1} of ${questions.size}",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium
                            )
                            
                            // Practice from here button
                            Button(
                                onClick = { onNavigateToPractice(source, index) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.filledTonalButtonColors()
                            ) {
                                Text("📖 Practice", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Question Text
                        Text(
                            text = question.question,
                            style = MaterialTheme.typography.titleMedium.copy(
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )

                        // Options
                        if (question.options.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                question.options.forEach { option ->
                                    val isCorrect = option.key.equals(question.answer, ignoreCase = true)
                                    
                                    val cardBgColor = if (isCorrect) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surface
                                    val borderColor = if (isCorrect) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                    val keyBgColor = if (isCorrect) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    val keyTextColor = if (isCorrect) Color.white() else MaterialTheme.colorScheme.primary

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                        border = BorderStroke(1.2.dp, borderColor)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .background(keyBgColor, CircleShape)
                                            ) {
                                                Text(
                                                    text = option.key,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = keyTextColor
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = option.text,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Direct Q&A type fallback
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                border = BorderStroke(1.2.dp, Color(0xFF4CAF50))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Answer:",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32),
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = question.answer,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }

                        // Explanation
                        if (question.explanation.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text("💡", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Explanation",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = question.explanation,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

