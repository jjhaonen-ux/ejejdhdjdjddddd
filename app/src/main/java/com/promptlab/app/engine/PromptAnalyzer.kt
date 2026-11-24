package com.promptlab.app.engine

/**
 * واجهة المحرك الموحدة — نقل مطابق لـ analyzePrompt في engine.ts.
 * التحليل كله محلي ونقي: بلا شبكة ولا نموذج لغوي (افتراض A6 المرجعي نفسه).
 */

data class Analysis(
    val taskType: TaskType,
    val complexity: Complexity,
    val wordCount: Int,
    val score: Scoring.Score,
    val findings: List<Finding>,
    val dialect: Dialect,
)

object PromptAnalyzer {
    fun analyze(text: String): Analysis {
        val model = buildTextModel(text)
        val findings = analyzerRules.flatMap { it.detect(model) }
        return Analysis(
            taskType = detectTaskType(model),
            complexity = detectComplexity(model),
            wordCount = model.wordCount,
            score = Scoring.compute(findings),
            findings = findings,
            dialect = DialectDetector.detect(text),
        )
    }
}

/** عناوين النتائج للعرض بالعربية — المعرفات مستقرة والنص الإنجليزي يبقى في engine فقط */
object FindingCatalog {
    data class Display(val title: String, val suggestion: String)

    private val map = mapOf(
        "unclear_objective" to Display("لا يظهر بوضوح ما المطلوب", "ابدأ بفعل مهمة مثل: اكتب…، لخّص…، صمّم…"),
        "missing_context" to Display("الطلب قصير جدًا", "أضف خلفية: لمن هذا؟ وما نتيجة تريدها؟"),
        "no_role" to Display("لا يوجد دور للنموذج", "افتح بدور مثل: أنت خبير تسويق…"),
        "missing_output_format" to Display("شكل الناتج غير محدد", "حدد: طول، قائمة أو جدول أو فقرات، نبرة"),
        "no_constraints" to Display("لا توجد قيود تحدّ الإجابة", "أضف حدودًا مثل: لا يزيد عن ٣٠٠ كلمة، فقط من المعطيات"),
        "no_examples" to Display("الطلب طويل ويستفيد من مثال", "أعطِ مثالًا واحدًا على شكل الإجابة الجيدة"),
        "vague_quantifier" to Display("كلمات غامضة تضعف الطلب", "استبدل الغامض بأرقام وأسماء ومعايير واضحة"),
        "ambiguous_reference" to Display("جمل تبدأ بضمائر قد يغيب مرجعها", "سمِّ الشيء صراحة بدل «هذا/ذلك»"),
        "wall_of_text" to Display("نص طويل بلا بنية", "قسّمه إلى أقسام معنونة أو قائمة متطلبات"),
        "mixed_tasks" to Display("عدة مهام مدموجة في طلب واحد", "رقّم المهام الفرعية أو افصلها في طلبات"),
        "conflicting_instructions" to Display("تعارض صريح داخل الطلب", "اختر أحد الطرفين أو حدد ما ينطبق على أي جزء"),
        "injection_suspect" to Display("عبارات تجاوز تعليمات — ستُعامل بيانًا لا أمرًا", "إن كانت مقصودة فاتركها، وإلا فاحذفها"),
    )

    fun display(finding: Finding): Display =
        map[finding.id] ?: Display(finding.message, finding.suggestion)
}
