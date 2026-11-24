package com.promptlab.app.engine

/**
 * نموذج النص المسبق الاستخراج — نقل مطابق لـ src/core/analysis/text-model.ts
 * في محرك Prompt Rerank (MIT)، مع طبقة عربية فوقه (AR-01..AR-06 من تقرير المرحلة الأولى).
 *
 * قاعدة التكافؤ: الأنماط الإنجليزية تعمل على النص الأصلي بحروفه كما في المحرك المرجعي حرفيًا،
 * والأنماط العربية تعمل على نسخة مطبعة (normalizeAr) — فلا يتأثر السلوك الإنجليزي إطلاقًا.
 */

data class TextModel(
    val raw: String,
    val words: List<String>,
    val wordCount: Int,
    val sentences: List<String>,
    val lines: List<String>,
    val lower: String,
    /** نسخة مطبعة للعربية: بلا تشكيل/تطويل، أشكال الألف موحدة، خانة الأرقام لاتينية */
    val match: String,
    val matchTokens: List<String>,
    val hasQuestion: Boolean,
    val hasCodeFence: Boolean,
    val hasList: Boolean,
    val hasRole: Boolean,
    val hasOutputFormat: Boolean,
    val hasExamples: Boolean,
    val hasConstraints: Boolean,
    val imperativeVerbs: List<String>,
)

/** توحيد النص العربي للمطابقة (AR-01) + تحويل الخانة العربية-الهندية إلى لاتينية (AR-02) */
fun normalizeAr(text: String): String {
    val sb = StringBuilder(text.length)
    for (c in text) {
        when (c) {
            '\u0640' -> {}                                   // التطويل ـ
            in '\u064B'..'\u0655', '\u0670', '\u0656' -> {}  // التشكيل
            'أ', 'إ', 'آ', 'ٱ' -> sb.append('ا')
            'ى' -> sb.append('ي')
            'ة' -> sb.append('ه')
            'ؤ' -> sb.append('و')
            'ئ' -> sb.append('ي')
            'گ', 'چ', 'پ' -> sb.append(c)
            in '٠'..'٩' -> sb.append(('0' + (c - '٠')))
            else -> sb.append(c)
        }
    }
    return sb.toString()
}

private val EN_ROLE_PATTERNS = listOf(
    Regex("\\bact as\\b"),
    Regex("\\byou are an?\\b"),
    Regex("\\bas an? (expert|senior|professional)\\b"),
    Regex("\\brole\\s*:"),
)
private val AR_ROLE_PATTERNS = listOf(
    Regex("تصرف كانك|تصرفي كانك"),
    Regex("انت (خبير|كاتب|مطور|مختص|مساعد|مدرب|مستشار|مصمم|محلل|صانع محتوى)"),
    Regex("بصفه|بصفتك|باعتبارك"),
    Regex("الدور:"),
)

private val EN_OUTPUT_PATTERNS = listOf(
    Regex("\\bjson\\b"), Regex("\\bmarkdown\\b"), Regex("\\btable\\b"),
    Regex("\\bbullet(ed)?\\s*(points?|list)?\\b"), Regex("\\bnumbered list\\b"),
    Regex("\\bformat\\s*:"), Regex("\\boutput\\s*(format|as|in)\\b"),
    Regex("\\brespond (in|with|using)\\b"), Regex("\\breturn (a|an|only|the)\\b"),
    Regex("\\bcsv\\b"), Regex("\\byaml\\b"),
)
private val AR_OUTPUT_PATTERNS = listOf(
    Regex("جدول"), Regex("قائمه"), Regex("نقاط مرقمه"), Regex("خطوات مرقمه"),
    Regex("بصيغه"), Regex("تنسيق:"), Regex("على شكل"), Regex("سطر واحد"), Regex("عمودين"),
)

private val EN_EXAMPLE_PATTERNS = listOf(
    Regex("\\bfor example\\b"), Regex("\\be\\.g\\."), Regex("\\bexample\\s*:"),
    Regex("\\bsuch as\\b"), Regex("\\blike this\\b"),
)
private val AR_EXAMPLE_PATTERNS = listOf(
    Regex("مثلا"), Regex("على سبيل المثال"), Regex("مثال:"), Regex("مثل هذا"),
)

private val EN_CONSTRAINT_PATTERNS = listOf(
    Regex("\\bmust\\b"), Regex("\\bshould\\b"), Regex("\\bdo not\\b"), Regex("\\bdon't\\b"),
    Regex("\\bavoid\\b"), Regex("\\bno more than\\b"), Regex("\\bat (least|most)\\b"),
    Regex("\\bwithin\\b"), Regex("\\bmax(imum)?\\b"), Regex("\\bmin(imum)?\\b"),
    Regex("\\bonly\\b"), Regex("\\bexactly\\b"),
    Regex("\\b\\d+\\s*(words?|sentences?|paragraphs?|items?|lines?|characters?)\\b"),
)
private val AR_CONSTRAINT_PATTERNS = listOf(
    Regex("يجب|لا يجب|لا تذكر|لا تضيف|بدون|من غير|تجنب|ما تذكر"),
    Regex("لا يزيد عن|لا يزيد|لا يقل عن|على الاقل|على الاكثر|كحد اقصى|بحد اقصى"),
    Regex("فقط|حصرا|بالضبط|الترتيب نفسه"),
    Regex("[0-9]+\\s*(كلمة|كلمات|حرف|احرف|سطر|اسطر|فقرة|فقره|فقرات|نقطة|نقطه|نقاط|ثانية|ثانيه|ثواني|دقيقة|دقيقه|دقائق|يوم|ايام|أسبوع|اسبوع|أسابيع|اسابيع|ساعة|ساعه|ساعات|ريال|درهم|جنيه|دولار|طلب|خطوات?|مميزات?)"),
)

/** أفعال المهام الإنجليزية — كما في TASK_VERBS المرجعية */
val TASK_VERBS_EN = listOf(
    "write", "create", "generate", "make", "build", "explain", "summarize", "summarise",
    "analyze", "analyse", "translate", "rewrite", "refactor", "fix", "debug", "implement",
    "design", "draft", "compose", "list", "compare", "review", "describe", "develop",
    "optimize", "optimise", "improve", "convert", "extract", "classify", "plan", "outline",
    "brainstorm", "research", "find", "calculate", "solve", "teach", "help",
)

/** أفعال المهام العربية واللهجية (AR-06) — تُقاس على النص المطبّع */
val TASK_VERBS_AR = listOf(
    "اكتب", "اكتبي", "صمم", "صممي", "انشئ", "حلل", "لخص", "اشرح", "ترجم",
    "اعد صياغه", "احسب", "خطط", "قارن", "اذكر", "راجع", "اختصر", "وسع", "اقترح",
    "صحح", "فسر", "علق", "حضر", "صغ", "ارسم", "اختبر", "علمني", "ساعدني",
    "سو", "سوي", "اعمل", "اعمللي", "ابعت", "ارسل", "جيب", "ودي", "ابغي", "نخلي",
    "خليني", "شغل", "عدل", "فصغ", "اضف", "حول", "صلح",
)

/** كلمات المهاد العربية التي يأتي بعدها فعل المهمة (AR-06) */
val AR_POLITE_PARTICLES = listOf(
    "ممكن", "لو سمحت", "لو سمحتي", "ارجو", "ابي", "ابغى", "اريد", "عايز", "عاوز",
    "بدي", "ودي", "ناوي", "ساعدني", "خليني",
)

private val AR_PUNCT = charArrayOf(',', '.', '؟', '!', '؛', ':', '"', '«', '»', '،', '-')

private fun firstArWord(sentence: String): String? =
    normalizeAr(sentence.lowercase())
        .split(WS_SPLIT).firstOrNull { it.isNotEmpty() }
        ?.trim { it in AR_PUNCT }

private val SENTENCE_SPLIT = Regex("(?<=[.!?؟])\\s+|\\n+")
private val WS_SPLIT = Regex("\\s+")
private val LIST_LINE = Regex("^([-*•]|\\d+[.)]|[٠-٩]+[.)])\\s")
private val POLITE_EN = Regex("\\b(?:please|can you|could you|i want you to|i need you to|help me)\\s+([a-z]+)")
private val NON_ASCII_TOKEN = Regex("[^a-z']")

fun buildTextModel(rawText: String): TextModel {
    val trimmed = rawText.trim()
    val lower = trimmed.lowercase()
    val match = normalizeAr(lower)
    val words = trimmed.split(WS_SPLIT).filter { it.isNotEmpty() }
    val lines = trimmed.split("\n").map { it.trim() }
    val sentences = trimmed.split(SENTENCE_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }
    val matchTokens = match.split(WS_SPLIT).filter { it.isNotEmpty() }

    val imperativeVerbs = mutableListOf<String>()

    // فعل المهمة في بداية الجملة (أول رمزين) — منطق المحرك المرجعي نفسه
    for (sentence in sentences) {
        val tokens = sentence.lowercase().split(WS_SPLIT).take(2)
            .map { it.replace(NON_ASCII_TOKEN, "") }
        val verb = tokens.firstOrNull { TASK_VERBS_EN.contains(it) }
        if (verb != null) imperativeVerbs.add(verb)

        // العربية: أول كلمة مطبعة في الجملة (AR-06) مع سقوط واو العطف
        val firstAr = firstArWord(sentence)
        if (firstAr != null) {
            if (TASK_VERBS_AR.contains(firstAr)) {
                imperativeVerbs.add(firstAr)
            } else if (firstAr.startsWith("و") && TASK_VERBS_AR.contains(firstAr.substring(1))) {
                imperativeVerbs.add(firstAr.substring(1))
            }
        }
    }
    // أفعال عربية بعد فاصلة أو في بداية سطر — بحرف حدود لا يسمح بالتقاطع داخل كلمة
    for (verb in TASK_VERBS_AR.distinct()) {
        val boundary = Regex("(?:^|[،؛\\n])\\s*و?$verb(?=\\s|$|[^\\u0600-\\u06FF])")
        if (boundary.containsMatchIn(match)) imperativeVerbs.add(verb)
    }
    // المهاد الإنجليزي المهذب — كما في المرجع
    POLITE_EN.findAll(lower).forEach { m ->
        val verb = m.groupValues[1]
        if (TASK_VERBS_EN.contains(verb)) imperativeVerbs.add(verb)
    }
    // المهاد العربي (AR-06): ممكن / لو سمحت / أبغى / عايز… + فعل عربي بعده
    val arParticle = Regex("(ممكن|لو سمحت|لو سمحتي|ارجو|ابي|ابغى|اريد|عايز|عاوز|بدي|ودي|ناوي|ساعدني|خليني)\\s+(\\S+)")
    arParticle.findAll(match).forEach { m ->
        val next = m.groupValues[2].trim(',', '.', '؟', '!', '؛', ':', '"', '«', '»')
        if (TASK_VERBS_AR.contains(next)) imperativeVerbs.add(next)
    }

    val hasRole = EN_ROLE_PATTERNS.any { it.containsMatchIn(lower) } ||
        AR_ROLE_PATTERNS.any { it.containsMatchIn(match) }
    val hasOutputFormat = EN_OUTPUT_PATTERNS.any { it.containsMatchIn(lower) } ||
        AR_OUTPUT_PATTERNS.any { it.containsMatchIn(match) }
    val hasExamples = EN_EXAMPLE_PATTERNS.any { it.containsMatchIn(lower) } ||
        AR_EXAMPLE_PATTERNS.any { it.containsMatchIn(match) }
    val hasConstraints = EN_CONSTRAINT_PATTERNS.any { it.containsMatchIn(lower) } ||
        AR_CONSTRAINT_PATTERNS.any { it.containsMatchIn(match) }

    return TextModel(
        raw = trimmed,
        words = words,
        wordCount = words.size,
        sentences = sentences,
        lines = lines,
        lower = lower,
        match = match,
        matchTokens = matchTokens,
        hasQuestion = trimmed.contains("?") || trimmed.contains("؟"),
        hasCodeFence = trimmed.contains("```"),
        hasList = lines.any { LIST_LINE.containsMatchIn(it) },
        hasRole = hasRole,
        hasOutputFormat = hasOutputFormat,
        hasExamples = hasExamples,
        hasConstraints = hasConstraints,
        imperativeVerbs = imperativeVerbs,
    )
}
