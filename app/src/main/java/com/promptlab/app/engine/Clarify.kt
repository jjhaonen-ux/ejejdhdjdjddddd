package com.promptlab.app.engine

/**
 * الأسئلة التوضيحية — حتى ثلاثة أسئلة مؤثرة في جولة واحدة، كل سؤال قابل للتخطي،
 * والتخطي يعطي عنصرًا نائبًا بدل قيمة مختلقة (قواعد سلوك prompt-optimizer منقولة إلى المحرك).
 * لا استبيان: السؤال لا يُطرح إلا إذا كانت إجابته ستغيّر النتيجة، وسؤال الموضوع
 * يكفي فلا يُضاف سؤال الجمهور بعده (مثال الخطة B01: سؤال واحد فقط).
 */

data class ClarifyQuestion(
    val slot: String,
    val question: String,
    val placeholder: String,
)

object Clarifier {

    private val TOPIC_WORDS = Regex("(دورت(ي|ه)?|منتج(ي|نا|ه)?|المنتج|محل(ي|نا)?|متجر(نا)?|عرضنا|خدمت(نا|ي)?|تطبيق(نا|ي)?|موقعنا|شركة)")
    private val AUDIENCE_TASKS = Regex("(اعلان|بوست|منشور|انستغرام|سناب|ريلز|تويست|حملة)")
    private val AUDIENCE_PRESENT = Regex("(جمهور|للشباب|للاطفال|للمبتدئين|للمحترفين|للعملاء|للاعبين|للتعليم|للطلاب)")
    private val MATERIAL_TASKS = Regex("(لخص|راجع|ترجم|حلل|صحح|اختصر|اقرا)")
    /** الإشارة لمادة موجودة سيُرفقها المستخدم («هذا المقال») تعفي من سؤال المادة (حالة A01) */
    private val MATERIAL_MARKER = Regex("(هذا|هذه|هولاء|التالي|التالية|المرفق|المرفقه|اليك)")
    private val LENGTH_TASKS = Regex("(مقال|تقرير|بحث)")
    private val MATERIAL_LONG = Regex("(\\n\\n|[«\"'])")
    private val MESSAGE_TASKS = Regex("(بريد|رسالة|ايميل)")
    private val JOB_DESCRIPTION = Regex("(وصف وظيفي|وصف وظيفة)")
    private val STUDY_PLAN = Regex("(خطة دراسية|خطة تعليميه)")
    private val PRODUCT_DESCRIPTION = Regex("(وصف منتج)")
    private val LENGTH_SPECIFIED = Regex(
        "([0-9]+|ثلاثه|ثلاث|اربعه|اربع|خمسه|خمس|سته|ست|سبعه|سبع|ثمانيه|ثماني|تسعه|تسع|عشره|عشر)\\s*(كلمه|كلمات|نقطه|نقاط|فقره|فقرات|سطر|اسطر)"
    )

    fun suggest(text: String, analysis: Analysis): List<ClarifyQuestion> {
        val m = normalizeAr(text.lowercase())
        val questions = mutableListOf<ClarifyQuestion>()
        val slots = mutableSetOf<String>()

        fun add(slot: String, question: String, placeholder: String) {
            if (slots.add(slot) && questions.size < 3) {
                questions += ClarifyQuestion(slot, question, placeholder)
            }
        }

        // ١) مادة مفقودة: طلب معالجة نص بلا نص وبلا إشارة لمادة قادمة (B06/B10 — لا B01/A01)
        if (MATERIAL_TASKS.containsMatchIn(m) && analysis.wordCount < 12 &&
            !MATERIAL_LONG.containsMatchIn(text) && !MATERIAL_MARKER.containsMatchIn(m)
        ) {
            add("material", "أين النص المطلوب؟ أرسله وسنبدأ فورًا.", "[نص المادة المطلوب]")
        }

        // ٢) موضوع بلا اسم: «دورتي / منتجي / متجرنا» بلا تسمية (B01) — يمنع أسئلة لاحقة عن الجمهور
        val hasTopicGap = TOPIC_WORDS.containsMatchIn(m)
        if (hasTopicGap) {
            add("topic", "ما اسم أو موضوع ما ذكرته؟", "[اسم الموضوع]")
        }

        // ٣) جمهور مفقود لمهمة عرض/نشر — لا تُسأل إن كان سؤال الموضوع مطروحًا
        if (!hasTopicGap && AUDIENCE_TASKS.containsMatchIn(m) && !AUDIENCE_PRESENT.containsMatchIn(m)) {
            add("audience", "من الجمهور المستهدف؟", "[الجمهور]")
        }

        // ٤) طول مفقود لمقال أو تقرير (B02) — العدد اللفظي أو الرقمي يغني عنه
        if (LENGTH_TASKS.containsMatchIn(m) && !LENGTH_SPECIFIED.containsMatchIn(m)) {
            add("length", "ما الطول المطلوب أو منصة النشر؟", "[الطول أو المنصة]")
        }

        // ٥) رسالة بلا موضوع أو مطلب (B05)
        if (MESSAGE_TASKS.containsMatchIn(m) && !hasTopicGap) {
            add("message_subject", "ما موضوع الرسالة وما المطلوب فيها؟", "[موضوع الرسالة والمطلوب]")
        }

        // ٦) وصف وظيفي بلا مسمى (B03)
        if (JOB_DESCRIPTION.containsMatchIn(m)) {
            add("job_title", "ما المسمى الوظيفي والقطاع؟", "[المسمى الوظيفي]")
        }

        // ٧) خطة دراسية بلا مادة أو مدة (B08)
        if (STUDY_PLAN.containsMatchIn(m)) {
            add("plan_subject", "لأي مادة أو مستوى؟ وما المدة المتاحة؟", "[المادة والمدة]")
        }

        // ٨) وصف منتج بلا مواصفات (B09)
        if (PRODUCT_DESCRIPTION.containsMatchIn(m)) {
            add("product_specs", "ما المواصفات الأساسية؟ (السعة، المادة، الميزة المميزة)", "[المواصفات الأساسية]")
        }

        // لا استبيان: ثلاثة أسئلة كحد أقصى بجولة واحدة
        return questions.take(3)
    }

    /** العناصر النائبة الناتجة عن الأسئلة المتخطاة — لا قيم مختلقة أبدًا */
    fun placeholdersFor(skipped: List<ClarifyQuestion>, answered: Map<String, String>): List<String> =
        skipped.filter { answered[it.slot].isNullOrBlank() }.map { it.placeholder }
}
