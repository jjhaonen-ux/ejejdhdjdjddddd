package com.promptlab.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.promptlab.app.PromptLabApp
import com.promptlab.app.R
import com.promptlab.app.data.Draft
import com.promptlab.app.data.HistoryItem
import com.promptlab.app.engine.Analysis
import com.promptlab.app.engine.ClarifyQuestion
import com.promptlab.app.engine.Constraint
import com.promptlab.app.engine.ConstraintExtractor
import com.promptlab.app.engine.Clarifier
import com.promptlab.app.engine.Complexity
import com.promptlab.app.engine.Diff
import com.promptlab.app.engine.DiffOpType
import com.promptlab.app.engine.FindingCatalog
import com.promptlab.app.engine.Mode
import com.promptlab.app.engine.PromptAnalyzer
import com.promptlab.app.engine.TemplateGenerator
import com.promptlab.app.engine.TaskType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Screen { INPUT, REVIEW, RESULT, HISTORY }

/** الجذر: آلة حالات بسيطة — إدخال ← مراجعة الفهم ← النتيجة ← السجل */
@Composable
fun PromptLabRoot(initialSharedText: String?) {
    val context = LocalContext.current
    val app = context.applicationContext as PromptLabApp
    val db = app.db
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var screen by remember { mutableStateOf(Screen.INPUT) }
    var inputText by remember { mutableStateOf(initialSharedText ?: "") }
    var sharedNotice by remember { mutableStateOf(initialSharedText != null) }
    var draftNotice by remember { mutableStateOf(false) }
    var analysis by remember { mutableStateOf<Analysis?>(null) }
    var constraints by remember { mutableStateOf<List<Constraint>>(emptyList()) }
    var questions by remember { mutableStateOf<List<ClarifyQuestion>>(emptyList()) }
    var answers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var mode by remember { mutableStateOf(Mode.IMPROVE) }
    var result by remember { mutableStateOf<com.promptlab.app.engine.GeneratedResult?>(null) }
    var editedResult by remember { mutableStateOf<String?>(null) }
    var originalAtGeneration by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }

    // استعادة المسودة بعد القتل أو التدوير (NFR-04) — ما لم يكن ثمة نص مشارك جديد
    LaunchedEffect(Unit) {
        db.draftDao().get()?.let { d ->
            if (d.text.isNotBlank() && inputText.isBlank()) {
                inputText = d.text
                draftNotice = true
            }
        }
    }
    // حفظ تلقائي للمسودة بتأخير بسيط
    LaunchedEffect(inputText) {
        delay(500)
        if (inputText.isNotBlank()) {
            db.draftDao().upsert(Draft(text = inputText, updatedAt = System.currentTimeMillis()))
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) { delay(1800); toast = null }
    }

    fun regenerate(text: String, a: Analysis, cs: List<Constraint>, qs: List<ClarifyQuestion>, ans: Map<String, String>, m: Mode) {
        result = TemplateGenerator.generate(text, a, cs, qs, ans, m)
        editedResult = null
        originalAtGeneration = text
        screen = Screen.RESULT
    }

    when (screen) {
        Screen.INPUT -> InputScreen(
            text = inputText,
            onTextChange = { inputText = it },
            sharedNotice = sharedNotice,
            draftNotice = draftNotice,
            onAnalyze = {
                val a = PromptAnalyzer.analyze(inputText)
                analysis = a
                constraints = ConstraintExtractor.extract(inputText)
                questions = Clarifier.suggest(inputText, a)
                answers = emptyMap()
                screen = Screen.REVIEW
            },
            onHistory = { screen = Screen.HISTORY },
        )

        Screen.REVIEW -> ReviewScreen(
            analysis = analysis,
            constraints = constraints,
            questions = questions,
            answers = answers,
            onAnswer = { slot, v -> answers = answers + (slot to v) },
            onBack = { screen = Screen.INPUT },
            onGenerate = {
                val a = analysis ?: return@ReviewScreen
                regenerate(inputText, a, constraints, questions, answers, mode)
            },
        )

        Screen.RESULT -> ResultScreen(
            original = originalAtGeneration,
            generated = result,
            editedResult = editedResult,
            onEdited = { editedResult = it },
            mode = mode,
            constraints = constraints,
            onMode = { m ->
                mode = m
                val a = analysis ?: return@ResultScreen
                regenerate(originalAtGeneration, a, constraints, questions, answers, m)
            },
            onCopy = {
                clipboard.setText(AnnotatedString(editedResult ?: result?.prompt ?: ""))
                toast = context.getString(R.string.copied)
            },
            onShare = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, editedResult ?: result?.prompt ?: "")
                }
                context.startActivity(Intent.createChooser(send, null))
            },
            onSave = {
                val r = result ?: return@ResultScreen
                scope.launch {
                    db.historyDao().insert(
                        HistoryItem(
                            original = originalAtGeneration,
                            result = editedResult ?: r.prompt,
                            mode = r.mode.name,
                            taskType = analysis?.taskType?.name ?: "GENERAL",
                            score = analysis?.score?.overall ?: 0,
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                    toast = context.getString(R.string.saved)
                }
            },
            onBack = { screen = Screen.REVIEW },
            onNew = { screen = Screen.INPUT },
        )

        Screen.HISTORY -> HistoryScreen(
            db = db,
            onBack = { screen = Screen.INPUT },
            onCopy = { text -> clipboard.setText(AnnotatedString(text)); toast = context.getString(R.string.copied) },
            onShare = { text ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(send, null))
            },
        )
    }

    toast?.let { t ->
        // إشعار مؤقت أسفل الشاشة
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = t,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .padding(24.dp)
                    .background(MaterialTheme.colorScheme.inverseSurface, MaterialTheme.shapes.small)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
    }
}

@Composable
private fun InputScreen(
    text: String,
    onTextChange: (String) -> Unit,
    sharedNotice: Boolean,
    draftNotice: Boolean,
    onAnalyze: () -> Unit,
    onHistory: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        TextButton(onClick = onHistory) { Text(stringResource(R.string.history)) }

        if (sharedNotice) {
            Notice(stringResource(R.string.shared_text_received))
        }
        if (draftNotice) {
            Notice(stringResource(R.string.draft_restored))
        }

        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            placeholder = { Text(stringResource(R.string.input_hint), maxLines = 3, overflow = TextOverflow.Ellipsis) },
        )
        Button(onClick = onAnalyze, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.analyze))
        }
        Text(
            stringResource(R.string.offline_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReviewScreen(
    analysis: Analysis?,
    constraints: List<Constraint>,
    questions: List<ClarifyQuestion>,
    answers: Map<String, String>,
    onAnswer: (String, String) -> Unit,
    onBack: () -> Unit,
    onGenerate: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.review_title), style = MaterialTheme.typography.headlineSmall)
        if (analysis == null) { Text("—"); return }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoCell(stringResource(R.string.task_type), taskLabel(analysis.taskType))
            InfoCell(stringResource(R.string.complexity), complexityLabel(analysis.complexity))
            InfoCell(stringResource(R.string.quality_score), "${analysis.score.overall}/100")
        }

        if (analysis.findings.isNotEmpty()) {
            Section(stringResource(R.string.findings_title)) {
                analysis.findings.forEach { f ->
                    val d = FindingCatalog.display(f)
                    Text("• ${d.title}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "   ${d.suggestion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (constraints.isNotEmpty()) {
            Section(stringResource(R.string.constraints_title)) {
                constraints.forEach { Text("• ${it.verbatim}", style = MaterialTheme.typography.bodyMedium) }
            }
        }

        if (questions.isNotEmpty()) {
            Section(stringResource(R.string.questions_title)) {
                questions.forEach { q ->
                    Text(q.question, style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = answers[q.slot] ?: "",
                        onValueChange = { onAnswer(q.slot, it) },
                        placeholder = { Text(stringResource(R.string.answer_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    TextButton(onClick = { onAnswer(q.slot, "") }) { Text(stringResource(R.string.skip)) }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Button(onClick = onGenerate) { Text(stringResource(R.string.generate)) }
        }
    }
}

@Composable
private fun ResultScreen(
    original: String,
    generated: com.promptlab.app.engine.GeneratedResult?,
    editedResult: String?,
    onEdited: (String?) -> Unit,
    mode: Mode,
    constraints: List<Constraint>,
    onMode: (Mode) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onNew: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var showDiff by remember { mutableStateOf(false) }
    val currentText = editedResult ?: generated?.prompt ?: ""
    val coverage = ConstraintExtractor.coverage(currentText, constraints)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.result_title), style = MaterialTheme.typography.headlineSmall)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeButton(Mode.IMPROVE, mode, stringResource(R.string.mode_improve), onMode)
            ModeButton(Mode.SHORTEN, mode, stringResource(R.string.mode_shorten), onMode)
            ModeButton(Mode.DETAILED, mode, stringResource(R.string.mode_detailed), onMode)
        }

        SelectionContainer {
            Text(
                text = currentText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                    .padding(12.dp),
            )
        }

        if (editing) {
            OutlinedTextField(
                value = currentText,
                onValueChange = { onEdited(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            )
            Text(stringResource(R.string.edit_done), style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = { editing = !editing }) {
            Text(if (editing) stringResource(R.string.edit_done) else stringResource(R.string.edit_result))
        }

        if (coverage.isNotEmpty()) {
            Section(stringResource(R.string.constraints_title)) {
                coverage.forEach { (c, ok) ->
                    Text(
                        "${if (ok) "✓" else "✗"} ${c.verbatim}",
                        color = if (ok) Color.Unspecified else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        generated?.changes?.takeIf { it.isNotEmpty() }?.let { changes ->
            Section(stringResource(R.string.what_changed)) {
                changes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
        }

        TextButton(onClick = { showDiff = !showDiff }) {
            Text(if (showDiff) stringResource(R.string.hide_diff) else stringResource(R.string.show_diff))
        }
        if (showDiff && generated != null) {
            Diff.wordDiff(original, currentText).forEach { op ->
                when (op.type) {
                    DiffOpType.EQUAL -> Text(op.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DiffOpType.INSERT -> Text("+ ${op.text}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    DiffOpType.DELETE -> Text("− ${op.text}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCopy) { Text(stringResource(R.string.copy)) }
            OutlinedButton(onClick = onShare) { Text(stringResource(R.string.share)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSave) { Text(stringResource(R.string.save_history)) }
            OutlinedButton(onClick = { onEdited(null); onNew() }) { Text(stringResource(R.string.start_over)) }
        }
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(
            stringResource(R.string.offline_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistoryScreen(
    db: com.promptlab.app.data.PromptLabDb,
    onBack: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    val items by db.historyDao().all().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var favOnly by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.history), style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { favOnly = !favOnly }) {
                    Text(if (favOnly) "★ " + stringResource(R.string.favorites_only) else stringResource(R.string.favorites_only))
                }
            }
        }

        val shown = if (favOnly) items.filter { it.favorite } else items
        if (shown.isEmpty()) {
            Text(stringResource(R.string.empty_history), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { item ->
                Card(colors = CardDefaults.cardColors()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "${modeLabel(item.mode)} • ${item.taskType} • ${item.score}/100" + if (item.favorite) " ★" else "",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(item.original, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(item.result, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { onCopy(item.result) }) { Text(stringResource(R.string.copy)) }
                            TextButton(onClick = { onShare(item.result) }) { Text(stringResource(R.string.share)) }
                            TextButton(onClick = { scope.launch { db.historyDao().update(item.copy(favorite = !item.favorite)) } }) { Text("★") }
                            TextButton(onClick = { scope.launch { db.historyDao().deleteById(item.id) } }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            if (items.isNotEmpty()) {
                OutlinedButton(onClick = { scope.launch { db.historyDao().deleteAll() } }) {
                    Text(stringResource(R.string.delete_all), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ===== عناصر صغيرة مشتركة =====

@Composable
private fun Notice(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun InfoCell(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ModeButton(current: Mode, selected: Mode, label: String, onClick: (Mode) -> Unit) {
    if (current == selected) Button(onClick = { onClick(current) }) { Text(label) }
    else OutlinedButton(onClick = { onClick(current) }) { Text(label) }
}

private fun taskLabel(t: TaskType): String = when (t) {
    TaskType.CODING -> "برمجة"
    TaskType.RESEARCH -> "بحثي"
    TaskType.BUSINESS -> "تسويق/أعمال"
    TaskType.EDUCATION -> "تعليمي"
    TaskType.CREATIVE -> "إبداعي"
    TaskType.WRITING -> "كتابة"
    TaskType.GENERAL -> "عام"
}

private fun complexityLabel(c: Complexity): String = when (c) {
    Complexity.SIMPLE -> "بسيط"
    Complexity.MODERATE -> "متوسط"
    Complexity.COMPLEX -> "معقد"
}

private fun modeLabel(m: String): String = when (m) {
    "IMPROVE" -> "تحسين"
    "SHORTEN" -> "اختصار"
    "DETAILED" -> "مفصلة"
    else -> m
}
