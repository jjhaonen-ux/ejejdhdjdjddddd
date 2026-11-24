package com.promptlab.app.engine

/**
 * كشف نوع المهمة والتعقيد — نقل مطابق لـ engine.ts مع مفاتيح عربية إضافية.
 * الترتيب يحمل المعنى: البرمجة أولًا («اشرح هذا الكود» تُقرأ برمجة لا تعليمية).
 */

enum class TaskType { CODING, RESEARCH, BUSINESS, EDUCATION, CREATIVE, WRITING, GENERAL }
enum class Complexity { SIMPLE, MODERATE, COMPLEX }

/** أول ملف يصيب ≥ ١ من مفاتيحه يفوز */
private val TASK_PROFILES: List<Pair<TaskType, List<Regex>>> = listOf(
    TaskType.CODING to listOf(
        Regex("\\b(code|function|bug|refactor|api|typescript|python|javascript|sql|regex|compile|debug|implement|class|script)\\b"),
        Regex("```"),
        Regex("\\b(error|exception|stack trace)\\b"),
        Regex("كود|برمجة|دالة|دوال|سكريبت|تصحيح|خطأ في الكود|داتا"),
    ),
    TaskType.RESEARCH to listOf(
        Regex("\\b(research|sources|citations?|literature|study|studies|evidence|compare .* approaches|analy[sz]e)\\b"),
        Regex("\\b(pros and cons|trade-?offs)\\b"),
        Regex("مصادر|دراسات|بحث علمي|مراجع"),
    ),
    TaskType.BUSINESS to listOf(
        Regex("\\b(business|marketing|sales|strategy|customers?|revenue|pitch|okrs?|kpis?|stakeholders?|meeting|email to)\\b"),
        Regex("اعلان|تسويق|حملة|منشور|جمهور|عملاء|مبيعات|عرض تخفيض|عرض تسويقي|علامة تجارية|سناب|انستغرام|سناب شات|واتساب|تيك توك|ريلز"),
    ),
    TaskType.EDUCATION to listOf(
        Regex("\\b(explain|teach|lesson|student|beginner|eli5|step[- ]by[- ]step|tutorial|quiz|learn)\\b"),
        Regex("اشرح|درس|طالب|مبتدئ|خطوة بخطوة|تعليمي|علمني|شرح"),
    ),
    TaskType.CREATIVE to listOf(
        Regex("\\b(story|poem|song|fiction|character|plot|creative|imagine|novel)\\b"),
        Regex("قصة|قصيدة|شعر|اغنية|شخصية|حبكة|رواية|ابداع"),
    ),
    TaskType.WRITING to listOf(
        Regex("\\b(write|draft|essay|article|blog|rewrite|edit|proofread|summar[iy][sz]e|paragraph|letter)\\b"),
        Regex("مقال|مجموعة كلمات|رسالة|تلخيص|لخص|اعد صياغة|بوست|وصف وظيفي|وصف منتج|نص اعلاني|سكريبت"),
    ),
)

fun detectTaskType(model: TextModel): TaskType {
    for ((taskType, patterns) in TASK_PROFILES) {
        if (patterns.any { it.containsMatchIn(model.lower) || it.containsMatchIn(model.match) }) {
            return taskType
        }
    }
    return TaskType.GENERAL
}

fun detectComplexity(model: TextModel): Complexity {
    if (model.wordCount < 20) return Complexity.SIMPLE
    if (model.wordCount < 80 && model.imperativeVerbs.toSet().size <= 2) return Complexity.MODERATE
    return Complexity.COMPLEX
}
