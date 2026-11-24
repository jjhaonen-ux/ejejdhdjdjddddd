package com.promptlab.app.engine

/**
 * استخراج القيود الصريحة من الطلب — دليل المرحلة الأولى §٦ (AR-02/AR-03):
 * الأرقام بالخانتين، التواريخ والأوقات، المنع، اللهجة، التنسيق، الدعوة الختامية، والحقائق الواجب ذكرها.
 * كل الالتقاطات تُقرأ من نسخة واحدة نظيفة من التطويل والتشكيل فقط (الهمزات باقية وأنماطها
 * مدرجة بصيغتيها)، فالفهارس متوافقة وما يظهر في الواجهة يطابق ما كتبه المستخدم.
 */

enum class ConstraintType { PROHIBITION, NUMERIC, DATE_TIME, DIALECT, FORMAT, CTA, FACT }
enum class NumericDirection { MAX, MIN, EXACT }

data class Constraint(
    val type: ConstraintType,
    val verbatim: String,
    val numericValue: Int? = null,
    val unit: String? = null,
    val direction: NumericDirection? = null,
)

private val DIACRITICS = Regex("[\\u0640\\u064B-\\u0655\\u0670]")

/** النسخة الوحيدة للالتقاط: بلا تشكيل/تطويل فقط — كل ما عدا ذلك حرفي */
private fun clean(text: String) = text.replace(DIACRITICS, "")

private val PROHIBITION = Regex(
    "(?:ولا?\\s*تذكر|ولا?\\s*تضيف|ما\\s*تذكر|بدون\\s*(?:ذكر\\s*)?|من\\s*غير\\s*(?:ذكر\\s*)?|ألا\\s*نعترف|لا\\s*نعترف|لا\\s*توعد|ولا\\s*توعد|لا\\s*تعطي|لا\\s*تختلق)\\s*([^،.!؟\\n]{1,45})"
)
private val NUMERIC_LIMIT = Regex(
    "([0-9٠-٩]+)\\s*(كلمة|كلمات|حرف|أحرف|سطر|أسطر|فقرة|فقرات|نقطة|نقاط|ثانية|ثواني|دقيقة|دقائق|يوم|أيام|أسبوع|أسابيع|ساعة|ساعات|ريال|درهم|جنيه|دولار|خطوات?|مميزات?|جمل)"
)
private val WORD_NUMBER_LIMIT = Regex(
    "(اثنين|اثنتين|ثلاثة|ثلاث|أربعة|أربع|خمسة|خمس|ستة|ست|سبعة|سبع|ثمانية|ثماني|تسعة|تسع|عشرة|عشر)\\s*(خطوات?|نقاط?|فقرات?|أفكار|مميزات?|أسئلة|جمل|مزايا)"
)
private val WORD_NUMBERS = mapOf(
    "اثنين" to 2, "اثنتين" to 2, "ثلاثة" to 3, "ثلاث" to 3, "أربعة" to 4, "أربع" to 4,
    "خمسة" to 5, "خمس" to 5, "ستة" to 6, "ست" to 6, "سبعة" to 7, "سبع" to 7,
    "ثمانية" to 8, "ثماني" to 8, "تسعة" to 9, "تسع" to 9, "عشرة" to 10, "عشر" to 10,
)
private val MAX_CONTEXT = Regex(
    "(لا يزيد|ما يزيد|بحد أقصى|بحد اقصى|كحد أقصى|كحد اقصى|أقصى عدد|اقصى عدد|أكثر من|اكثر من|لا تتجاوز|ما تتجاوز|حد أقصى|حد اقصى)"
)
private val MIN_CONTEXT = Regex("(لا يقل|على الأقل|على الاقل|أقل عدد)")
private val EXACT_CONTEXT = Regex("(بالضبط|نفس العدد|الترتيب نفسه)")
private val DATE_TIME = Regex(
    "((?:يوم\\s+)?(?:الاثنين|الثلاثاء|الأربعاء|الاربعاء|الخميس|الجمعة|السبت|الأحد|الاحد)|الساعة\\s*\\S+|\\d{1,2}\\s*(?:يناير|فبراير|مارس|أبريل|ابريل|مايو|يونيو|يوليو|أغسطس|اغسطس|سبتمبر|أكتوبر|اكتوبر|نوفمبر|ديسمبر)|صباحا|مساء)"
)
private val DIALECT_REQ = Regex(
    "(باللهجة\\s*\\S+|بالعامية\\s*\\S*|بالفصحى|بلهجتنا|بالدارجة|بالمصري|بالسعودية|بالشامي|بالعراقي|بالمغربي|بلهجتنا وبطريقة)"
)
private val FORMAT_REQ = Regex(
    "(جدول من عمودين|سطر واحد|خطوات مرقمة|نقاط مرقمة|جدول|قائمة|عمودين|json|csv)"
)
private val CTA_REQ = Regex("(?:و?اختم|و?اقفل|و?انته)\\s*(?:بال|بـ|ب)([^،.!؟\\n]{1,50})")
private val FACT_REQ = Regex("(?:اذكر أن|اذكر|توضح أن|وضح أن)\\s*([^،.!؟\\n]{1,60})")

object ConstraintExtractor {

    fun extract(text: String): List<Constraint> {
        val src = clean(text.trim())
        val low = src.lowercase()
        val out = mutableListOf<Constraint>()

        // ١) المنع — بالتقاط الحرفي، ونحفظ المضمون المنفي في unit لفحص الانتهاك لاحقًا
        for (m in PROHIBITION.findAll(low)) {
            val captured = m.groupValues[1].trim()
            if (captured.isNotBlank()) {
                out += Constraint(ConstraintType.PROHIBITION, src.substring(m.range).trim(), unit = captured.take(30))
            }
        }

        // ٢) الحدود الرقمية (بالأرقام ثم بالكلمات العددية) مع قراءة الاتجاه من السياق السابق
        for (m in NUMERIC_LIMIT.findAll(low)) {
            val before = low.substring(0, m.range.first).takeLast(80)
            val direction = when {
                MAX_CONTEXT.containsMatchIn(before) -> NumericDirection.MAX
                MIN_CONTEXT.containsMatchIn(before) -> NumericDirection.MIN
                else -> NumericDirection.EXACT
            }
            out += Constraint(
                ConstraintType.NUMERIC,
                src.substring(m.range).trim(),
                numericValue = normalizeAr(m.groupValues[1]).toIntOrNull(),
                unit = m.groupValues[2],
                direction = direction,
            )
        }
        for (m in WORD_NUMBER_LIMIT.findAll(low)) {
            val before = low.substring(0, m.range.first).takeLast(60)
            val direction = when {
                MAX_CONTEXT.containsMatchIn(before) -> NumericDirection.MAX
                MIN_CONTEXT.containsMatchIn(before) -> NumericDirection.MIN
                else -> NumericDirection.EXACT
            }
            out += Constraint(
                ConstraintType.NUMERIC,
                src.substring(m.range).trim(),
                numericValue = WORD_NUMBERS[m.groupValues[1]],
                unit = m.groupValues[2],
                direction = direction,
            )
        }

        // ٣) التواريخ والأوقات
        for (m in DATE_TIME.findAll(low)) out += Constraint(ConstraintType.DATE_TIME, src.substring(m.range).trim())

        // ٤) اللهجة المطلوبة
        for (m in DIALECT_REQ.findAll(low)) out += Constraint(ConstraintType.DIALECT, src.substring(m.range).trim())

        // ٥) التنسيق
        for (m in FORMAT_REQ.findAll(low)) out += Constraint(ConstraintType.FORMAT, src.substring(m.range).trim())

        // ٦) الدعوة الختامية
        for (m in CTA_REQ.findAll(low)) out += Constraint(ConstraintType.CTA, src.substring(m.range).trim())

        // ٧) الحقائق الواجب ذكرها
        for (m in FACT_REQ.findAll(low)) out += Constraint(ConstraintType.FACT, src.substring(m.range).trim())

        return out.distinctBy { it.type to it.verbatim }.sortedBy { it.type.ordinal }
    }

    /** فحص التغطية: هل القيد (أو قيمته الرقمية بالخانة التي كانت) ظاهر داخل البرومبت الناتج؟ (FR-06) */
    fun coverage(prompt: String, constraints: List<Constraint>): List<Pair<Constraint, Boolean>> {
        val norm = normalizeAr(prompt.lowercase())
        return constraints.map { c ->
            val verbatimNorm = normalizeAr(clean(c.verbatim).lowercase()).trim()
            val ok = when (c.type) {
                ConstraintType.NUMERIC -> {
                    val v = c.numericValue
                    (v != null && norm.contains(v.toString())) || norm.contains(verbatimNorm)
                }
                else -> norm.contains(verbatimNorm) ||
                    (!c.unit.isNullOrBlank() && norm.contains(normalizeAr(clean(c.unit!!).lowercase()).trim()))
            }
            c to ok
        }
    }

    private val VIOLATION_STOPWORDS = setOf("ذكر", "الذكر", "بدون", "غير", "من", "أي", "اي", "ألا", "الا")

    /** هل ظهر مضمون المنع داخل نص الناتج نفسه؟ (فحص انتهاك، لا فحص تغطية)
     *  يفحص كلمات المضمون المنفي الدالّة (السعر، تاريخ، توعد…) لا العبارة الحرفية. */
    fun violations(resultText: String, constraints: List<Constraint>): List<Constraint> {
        val norm = normalizeAr(resultText.lowercase())
        return constraints.filter { c ->
            c.type == ConstraintType.PROHIBITION && !c.unit.isNullOrBlank() &&
                normalizeAr(c.unit!!.lowercase())
                    .split(Regex("\\s+"))
                    .any { tok ->
                        val t = tok.trim(',', '.', '؛', ':', '"', '«', '»')
                        t.length >= 4 && t !in VIOLATION_STOPWORDS && norm.contains(t)
                    }
        }
    }
}

/** كشف اللهجة للعرض والتكييف فقط — لا للإنفاذ (AR-04). أول إصابة تفوز والترتيب مقصود. */
enum class Dialect(val display: String, val instruction: String) {
    SAUDI("سعودية", "باللهجة السعودية"),
    EGYPTIAN("مصرية", "باللهجة المصرية"),
    LEVANTINE("شامية", "باللهجة الشامية"),
    GULF("خليجية", "باللهجة الخليجية"),
    YEMENI("يمنية", "باللهجة اليمنية"),
    IRAQI("عراقية", "باللهجة العراقية"),
    MOROCCAN("مغربية (دارجة)", "بالدارجة المغربية"),
    MSA("فصحى", "بالفصحى"),
}

object DialectDetector {
    fun detect(text: String): Dialect {
        val m = normalizeAr(text.lowercase())
        return when {
            Regex("شلون|هواية|شكد").containsMatchIn(m) -> Dialect.IRAQI
            Regex("بغيت|ديال|دابا|المنتوج|بالمنتوج").containsMatchIn(m) -> Dialect.MOROCCAN
            Regex("عايز|عاوز|ازيك|بتاع|كده|دلوقتي").containsMatchIn(m) -> Dialect.EGYPTIAN
            Regex("بدي|هلق|منيح").containsMatchIn(m) -> Dialect.LEVANTINE
            Regex("ناوي").containsMatchIn(m) -> Dialect.YEMENI
            Regex("ابغي|ابي |سوي |سو لي|وش |الحين|دورتي|خليني").containsMatchIn(m) -> Dialect.SAUDI
            Regex("ودي |عساك|شخبارك").containsMatchIn(m) -> Dialect.GULF
            else -> Dialect.MSA
        }
    }
}
