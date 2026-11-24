package com.promptlab.app.engine

/**
 * القواعد الاثنتا عشرة — نقل مطابق لـ analyzers.ts مع القوائم العربية.
 * كل قاعدة دالة نقية على نموذج النص تعيد نتائج موضعية، وإضافة قاعدة = إضافة سطر هنا.
 */

enum class Severity(val penalty: Int) { INFO(5), MINOR(14), MAJOR(30) }
enum class Dimension(val weight: Double) {
    CLARITY(0.25), SPECIFICITY(0.2), CONTEXT(0.15), CONSTRAINTS(0.15), OUTPUT_SPEC(0.15), STRUCTURE(0.1)
}

data class Finding(
    val id: String,
    val dimension: Dimension,
    val severity: Severity,
    val message: String,
    val suggestion: String,
)

data class AnalyzerRule(val id: String, val detect: (TextModel) -> List<Finding>)

private val EN_VAGUE_TERMS = listOf(
    "some", "a few", "several", "various", "stuff", "things", "better", "good", "nice",
    "improve it", "make it better", "etc", "and so on", "somehow", "kind of", "sort of",
)
/** كلمات الغموض العربية (AR) — تُقاس بمطابقة الرموز لا السلاسل الفرعية، فلا تنطلق على الحلول/الحلوة */
private val AR_VAGUE_TERMS = listOf(
    "كذا", "شيء ما", "شي ما", "اشياء", "امور", "حاجات", "وهكذا", "افضل", "حلو",
    "جميل", "بشكل افضل", "شوية", "تنوع", "جوانب",
)

private fun vagueArHits(model: TextModel): List<String> {
    val toks = model.matchTokens
    val hits = mutableListOf<String>()
    for (term in AR_VAGUE_TERMS) {
        val parts = term.split(Regex("\\s+"))
        for (i in 0..(toks.size - parts.size)) {
            if ((0 until parts.size).all { k -> toks[i + k] == parts[k] }) {
                hits.add(term); break
            }
        }
    }
    return hits
}

private val EN_CONFLICT_PAIRS: List<Triple<Regex, Regex, String>> = listOf(
    Triple(Regex("\\b(short|brief|concise)\\b"), Regex("\\b(detailed|comprehensive|in.?depth|thorough)\\b"), "short vs. detailed"),
    Triple(Regex("\\bformal\\b"), Regex("\\b(casual|informal|conversational)\\b"), "formal vs. casual"),
    Triple(Regex("\\bsimple\\b"), Regex("\\b(advanced|technical|expert)\\b"), "simple vs. technical"),
)
/** أزواج التعارض العربية + الأزواج المنطقية (منع × طلب الشيء نفسه) — AR-03
 *  تُقاس على النص المطبّع، فكل الأنماط بصيغة مطبعة (ة→ه، أ→ا، ى→ي). */
private val AR_CONFLICT_PAIRS: List<Triple<Regex, Regex, String>> = listOf(
    Triple(Regex("قصير|مختصر|موجز"), Regex("مفصل|موسع|شامل|تفصيل|تفصيلي"), "قصير × مفصل"),
    Triple(Regex("رسمي"), Regex("ودي|وديه|مريح|مرتاح|عامي|عاميه|غير رسمي"), "رسمي × ودّي"),
    Triple(Regex("بسيط|ساده"), Regex("تقني|تقنيه|متقدم|عمق تقني|احترافي"), "بسيط × تقني"),
    Triple(Regex("فصحى"), Regex("عامي|عاميه|لهجه|دارجه|مصري|سعودي|شامي|خليجي|عراقي|مغربي"), "فصحى × عامية"),
    Triple(Regex("(لا تذكر اي ارقام|بدون اي ارقام|من غير اي ارقام|ما تذكر ارقام|لا تذكر نسبه|ما تذكر نسبه|بدون نسبه)"),
           Regex("احصاييات|احصائيات|اضف ارقام|اضف نسب|نسب واضحه|نسبه الخصم|ارقام واضحه"),
           "منع الأرقام × طلب أرقام"),
    Triple(Regex("لا تذكر تاريخ|بدون تاريخ|من غير تاريخ|ما تذكر موعد|بدون موعد"),
           Regex("اضف موعد|اذكر موعد|تاريخ البدايه|موعد الانتهاء"),
           "منع التاريخ × طلب موعد"),
)

private val EN_INJECTION_PATTERNS = listOf(
    Regex("ignore (all |any )?(previous|prior|above) (instructions?|prompts?)"),
    Regex("disregard (the )?(system|previous)"),
    Regex("reveal (your|the) (system )?prompt"),
)
private val AR_INJECTION_PATTERNS = listOf(
    Regex("تجاهل (كل |جميع )?(التعليمات|ما سبق|ما قبل)"),
    Regex("اعرض (البرومبت|التعليمات) النظامي"),
    Regex("تجاهل كل ما سبق|انس كل ما سبق"),
)

private val EN_PRONOUN_START = Regex("^(it|this|that|they|these|those)\\b", RegexOption.IGNORE_CASE)
private val AR_PRONOUN_START = Regex("^(هذا|هذه|هولاء|ذلك|اللي|الذي|التي|المذكور)")

val analyzerRules: List<AnalyzerRule> = listOf(
    AnalyzerRule("unclear_objective") { m ->
        if (m.imperativeVerbs.isEmpty() && !m.hasQuestion) listOf(
            Finding(
                "unclear_objective", Dimension.CLARITY, Severity.MAJOR,
                "No clear task or question is stated.",
                "State the task explicitly, e.g. start with a verb: \"Summarize…\", \"Write…\".",
            )
        ) else emptyList()
    },
    AnalyzerRule("missing_context") { m ->
        if (m.wordCount < 8) listOf(
            Finding(
                "missing_context", Dimension.CONTEXT, Severity.MAJOR,
                "The prompt is very short and gives the model almost nothing to work with.",
                "Add background: who this is for, what you already have, and what outcome you need.",
            )
        ) else emptyList()
    },
    AnalyzerRule("no_role") { m ->
        if (!m.hasRole && m.wordCount >= 15) listOf(
            Finding(
                "no_role", Dimension.STRUCTURE, Severity.MINOR,
                "No role or persona is assigned to the model.",
                "Open with a role, e.g. \"You are a senior data engineer…\" to anchor tone and expertise.",
            )
        ) else emptyList()
    },
    AnalyzerRule("missing_output_format") { m ->
        if (!m.hasOutputFormat) listOf(
            Finding(
                "missing_output_format", Dimension.OUTPUT_SPEC,
                if (m.wordCount >= 15) Severity.MAJOR else Severity.MINOR,
                "The expected output format is unspecified.",
                "Say what the answer should look like: length, structure (list/table/JSON), tone.",
            )
        ) else emptyList()
    },
    AnalyzerRule("no_constraints") { m ->
        if (!m.hasConstraints && m.wordCount >= 12) listOf(
            Finding(
                "no_constraints", Dimension.CONSTRAINTS, Severity.MINOR,
                "No constraints bound the answer (length, scope, style, exclusions).",
                "Add limits such as \"no more than 300 words\" or \"only use the provided data\".",
            )
        ) else emptyList()
    },
    AnalyzerRule("no_examples") { m ->
        if (!m.hasExamples && m.wordCount >= 40) listOf(
            Finding(
                "no_examples", Dimension.CONTEXT, Severity.INFO,
                "A prompt of this complexity usually benefits from an example.",
                "Show one input/output example of what a good answer looks like (few-shot).",
            )
        ) else emptyList()
    },
    AnalyzerRule("vague_quantifier") { m ->
        val enHits = EN_VAGUE_TERMS.filter { m.lower.contains(it) }
        val arHits = vagueArHits(m)
        val hits = enHits + arHits
        if (hits.isNotEmpty()) listOf(
            Finding(
                "vague_quantifier", Dimension.SPECIFICITY,
                if (hits.size > 2) Severity.MAJOR else Severity.MINOR,
                "Vague wording weakens the request: ${hits.take(4).joinToString(", ")}.",
                "Replace vague terms with concrete numbers, names, or criteria.",
            )
        ) else emptyList()
    },
    AnalyzerRule("ambiguous_reference") { m ->
        val starts = m.sentences.filter { EN_PRONOUN_START.containsMatchIn(it) || AR_PRONOUN_START.containsMatchIn(normalizeAr(it.lowercase())) }
        if (starts.isNotEmpty() && m.sentences.size > 1) listOf(
            Finding(
                "ambiguous_reference", Dimension.CLARITY, Severity.MINOR,
                "Sentences start with pronouns whose referent may be unclear to the model.",
                "Name the thing explicitly instead of using pronouns.",
            )
        ) else emptyList()
    },
    AnalyzerRule("wall_of_text") { m ->
        if (m.wordCount >= 80 && !m.hasList && m.lines.size <= 2) listOf(
            Finding(
                "wall_of_text", Dimension.STRUCTURE, Severity.MINOR,
                "Long unstructured text is harder for models to parse reliably.",
                "Break the prompt into labeled sections or a bulleted list of requirements.",
            )
        ) else emptyList()
    },
    AnalyzerRule("mixed_tasks") { m ->
        val distinct = m.imperativeVerbs.toSet()
        if (distinct.size >= 3) listOf(
            Finding(
                "mixed_tasks", Dimension.STRUCTURE, Severity.MINOR,
                "Several distinct tasks are bundled together (${distinct.take(4).joinToString(", ")}).",
                "Number the sub-tasks, or split them into separate prompts for better results.",
            )
        ) else emptyList()
    },
    AnalyzerRule("conflicting_instructions") { m ->
        val enConflicts = EN_CONFLICT_PAIRS.filter { (a, b, _) -> a.containsMatchIn(m.lower) && b.containsMatchIn(m.lower) }
        val arConflicts = AR_CONFLICT_PAIRS.filter { (a, b, _) -> a.containsMatchIn(m.match) && b.containsMatchIn(m.match) }
        (enConflicts + arConflicts).map { (_, _, label) ->
            Finding(
                "conflicting_instructions", Dimension.CLARITY, Severity.MAJOR,
                "The prompt asks for contradictory qualities ($label).",
                "Pick one, or state which applies to which part of the answer.",
            )
        }
    },
    AnalyzerRule("injection_suspect") { m ->
        val hit = EN_INJECTION_PATTERNS.any { it.containsMatchIn(m.lower) } ||
            AR_INJECTION_PATTERNS.any { it.containsMatchIn(m.match) }
        if (hit) listOf(
            Finding(
                "injection_suspect", Dimension.CLARITY, Severity.INFO,
                "The text contains instruction-override phrasing, which the rewriter will treat as data.",
                "If this is intentional prompt content, keep it; otherwise remove it.",
            )
        ) else emptyList()
    },
)
