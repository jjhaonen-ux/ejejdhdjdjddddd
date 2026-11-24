package com.promptlab.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات المحرك العربي — مستمدة من حالات قبول المرحلة الأولى على مستوى المحرك
 * (حالات الواجهة والنظام تُختبر على الجهاز وفق المصفوفة). كل اختبار يحمل معرف حالته.
 */
class ArabicAcceptanceTest {

    private fun analyze(text: String) = PromptAnalyzer.analyze(text)
    private fun gen(text: String, mode: Mode = Mode.IMPROVE, answers: Map<String, String> = emptyMap()): GeneratedResult {
        val a = analyze(text)
        val cs = ConstraintExtractor.extract(text)
        val qs = Clarifier.suggest(text, a)
        return TemplateGenerator.generate(text, a, cs, qs, answers, mode)
    }

    // ===== A01: الطلب البسيط يبقى كما هو =====
    @Test
    fun a01_simpleRequestStaysAsIs() {
        val r = gen("لخّص هذا المقال في ثلاث نقاط.")
        assertEquals("لخّص هذا المقال في ثلاث نقاط.", r.prompt)
        assertTrue(r.changes.first().contains("كما هو"))
    }

    // ===== B01: سؤال واحد مؤثر + عنصر نائب + حفظ المنع واللهجة =====
    @Test
    fun b01_courseAdWithoutPrice() {
        val text = "أبي إعلان لدورتي في إنستغرام، باللهجة السعودية، لا تذكر السعر."
        val a = analyze(text)
        val qs = Clarifier.suggest(text, a)
        assertTrue(qs.isNotEmpty())
        assertEquals(1, qs.size)
        assertEquals("topic", qs.first().slot)

        val r = gen(text, answers = emptyMap())
        assertTrue(r.prompt.contains("[اسم الموضوع]"))
        assertTrue(r.prompt.contains("لا تذكر السعر"))
        assertTrue(r.prompt.contains("باللهجة السعودية"))
    }

    // ===== B06: لا اختراع ملخص لمادة غائبة =====
    @Test
    fun b06_missingMaterialIsNeverInvented() {
        val text = "لخّص التقرير."
        val qs = Clarifier.suggest(text, analyze(text))
        assertTrue(qs.isNotEmpty())
        assertEquals("material", qs.first().slot)
        val r = gen(text)
        assertTrue(r.prompt.contains("[نص المادة المطلوب]"))
        assertFalse(r.prompt.contains("ملخص التقرير يشير"))
    }

    // ===== C01: التعارض اللفظي يُلتقط ولا يُحسم بصمت =====
    @Test
    fun c01_verbalConflictDetected() {
        val a = analyze("اكتب منشورًا قصيرًا وشاملًا عن فوائد المشي.")
        assertTrue(a.findings.any { it.id == "conflicting_instructions" })
    }

    // ===== C04: التعارض المنطقي (منع أرقام × طلب إحصائيات) =====
    @Test
    fun c04_logicalConflictDetected() {
        val a = analyze("لا تذكر أي أرقام في التقرير، وأضف إحصائيات واضحة تدعم الفكرة.")
        assertTrue(a.findings.any { it.id == "conflicting_instructions" })
    }

    // ===== D02: حد ٥٠ كلمة بالضبط يُستخرج ويُحفظ =====
    @Test
    fun d02_exactWordLimitPreserved() {
        val text = "صغ رسالة من ٥٠ كلمة بالضبط لتفعيل الاشتراك."
        val cs = ConstraintExtractor.extract(text)
        val numeric = cs.filter { it.type == ConstraintType.NUMERIC }
        assertTrue(numeric.isNotEmpty())
        assertEquals(50, numeric.first().numericValue)
        assertEquals("كلمة", numeric.first().unit)
        assertEquals(NumericDirection.EXACT, numeric.first().direction)
        val r = gen(text)
        assertTrue(normalizeAr(r.prompt).contains("50"))
    }

    // ===== D03: الأرقام الثلاثة (٣ فقرات / ٤٠ كلمة / سنتان) =====
    @Test
    fun d03_multipleNumbersPreserved() {
        val text = "اكتب وصفًا للمنتج في ثلاث فقرات، لا يزيد كل فقرة عن ٤٠ كلمة، واذكر الضمان سنتين."
        val cs = ConstraintExtractor.extract(text)
        val numeric = cs.filter { it.type == ConstraintType.NUMERIC }
        assertTrue(numeric.any { it.numericValue == 40 && it.direction == NumericDirection.MAX })
        assertTrue(cs.any { it.type == ConstraintType.FACT && it.verbatim.contains("الضمان") })
        val r = gen(text)
        assertTrue(normalizeAr(r.prompt).contains("40"))
        assertTrue(r.prompt.contains("الضمان"))
    }

    // ===== D08: حد ٢٨٠ حرفًا + منع الهاشتاقات =====
    @Test
    fun d08_charLimitAndProhibition() {
        val text = "أبغى منشور عن العرض، ما يزيد ٢٨٠ حرفًا، ومن غير هاشتاقات."
        val cs = ConstraintExtractor.extract(text)
        assertTrue(cs.any { it.type == ConstraintType.NUMERIC && it.numericValue == 280 })
        assertTrue(cs.any { it.type == ConstraintType.PROHIBITION && it.verbatim.contains("هاشتاق") })
        val r = gen(text)
        assertTrue(r.prompt.contains("280") || normalizeAr(r.prompt).contains("280"))
        assertTrue(r.prompt.contains("هاشتاق"))
    }

    // ===== D11: خمس خطوات بالترتيب نفسه — النقل الحرفي =====
    @Test
    fun d11_fiveStepsVerbatimOrder() {
        val text = "اكتب تعليمات قصيرة لتشغيل الجهاز بخمس خطوات مرقمة، بالترتيب نفسه: افتح الغطاء، وصّل الكابل، اضغط زر التشغيل، انتظر إشارة الضوء، اختر البرنامج."
        val cs = ConstraintExtractor.extract(text)
        assertTrue(cs.any { it.type == ConstraintType.NUMERIC && it.numericValue == 5 })
        val r = gen(text)
        assertTrue(r.prompt.contains("الترتيب نفسه"))
        assertTrue(r.prompt.indexOf("افتح الغطاء") < r.prompt.indexOf("اختر البرنامج"))
    }

    // ===== D12: ١٤ يومًا + لا وعود تعويض =====
    @Test
    fun d12_daysAndNoPromise() {
        val text = "اكتب ردًا لطلب إرجاع، واذكر أن الاسترجاع خلال ١٤ يومًا فقط، ولا توعد بأي تعويض إضافي."
        val cs = ConstraintExtractor.extract(text)
        assertTrue(cs.any { it.type == ConstraintType.NUMERIC && it.numericValue == 14 })
        assertTrue(cs.any { it.type == ConstraintType.PROHIBITION && it.verbatim.contains("توعد") || it.type == ConstraintType.FACT })
        val r = gen(text)
        assertTrue(normalizeAr(r.prompt).contains("14"))
        assertTrue(r.prompt.contains("تعويض"))
    }

    // ===== F01: المصطلح اللاتيني يبقى حرفيًا والرقم محفوظًا =====
    @Test
    fun f01_latinTermKeptVerbatim() {
        val text = "اكتب إعلانًا عن كورس Data Analysis باللهجة السعودية، واذكر أن المدة ٦ أسابيع."
        val cs = ConstraintExtractor.extract(text)
        assertTrue(cs.any { it.type == ConstraintType.NUMERIC && it.numericValue == 6 })
        val r = gen(text)
        assertTrue(r.prompt.contains("Data Analysis"))
        assertTrue(normalizeAr(r.prompt).contains("6"))
    }

    // ===== G01: «خلّه أقصر» يبقي منع السعر واللهجة (وضع الاختصار على النص الحالي) =====
    @Test
    fun g01_shortenKeepsConstraintAndDialect() {
        val text = "أبي إعلان لدورة التسويق باللهجة السعودية، لا تذكر السعر."
        val r = gen(text, mode = Mode.SHORTEN)
        assertTrue(r.prompt.contains("لا تذكر السعر"))
        assertTrue(r.prompt.contains("باللهجة السعودية"))
        assertTrue(r.prompt.contains("اختصر"))
    }

    // ===== A05: الجدول من عمودين مع الأيام الثلاثة =====
    @Test
    fun a05_tableColumnsAndDays() {
        val text = "حوّل هذه القائمة إلى جدول من عمودين: المهمة والموعد.\n١) مراجعة التصميم — الأحد\n٢) تسليم النموذج — الثلاثاء\n٣) اختبار المستخدم — الخميس"
        val cs = ConstraintExtractor.extract(text)
        assertTrue(cs.any { it.type == ConstraintType.FORMAT && it.verbatim.contains("عمودين") })
        assertTrue(cs.filter { it.type == ConstraintType.DATE_TIME }.size >= 2)
        val r = gen(text)
        assertTrue(r.prompt.contains("عمودين"))
    }

    // ===== فحص الانتهاك: مضمون المنع داخل الناتج =====
    @Test
    fun prohibitionViolationDetector() {
        val text = "أبغى إعلان سناب شات لدورة التصوير، بدون ذكر تاريخ البداية."
        val cs = ConstraintExtractor.extract(text)
        val violation = ConstraintExtractor.violations("أعلننا أن الدورة تبدأ بتاريخ ٥ يناير", cs)
        assertTrue(violation.isNotEmpty())
        val clean = ConstraintExtractor.violations("دورة التصوير قادمة — سجلوا الآن!", cs)
        assertTrue(clean.isEmpty())
    }

    // ===== كشف اللهجات للعرض (AR-04) =====
    @Test
    fun dialectDetection() {
        assertEquals(Dialect.SAUDI, DialectDetector.detect("أبغى رسالة للزبائن عن فرعنا الجديد"))
        assertEquals(Dialect.EGYPTIAN, DialectDetector.detect("عايز بوست لصفحة المطعم عن العزومات"))
        assertEquals(Dialect.LEVANTINE, DialectDetector.detect("بدي بوست انستغرام عن افتتاح محل الحلويات"))
        assertEquals(Dialect.IRAQI, DialectDetector.detect("شلون أكتب إعلان عن دورة كمبيوتر؟"))
        assertEquals(Dialect.MOROCCAN, DialectDetector.detect("بغيت نص إعلاني على المنتوج الجديد ديالنا"))
    }

    // ===== التطبيع (AR-01): التشكيل والتطويل لا يعطّلان المطابقة =====
    @Test
    fun normalizationMatchesDiacriticsAndTatweel() {
        val a = analyze("اكــتـب إعــلانًا مُشَوَّقًا لدورة تسويق.")
        assertEquals(Dialect.MSA, a.dialect)
        assertEquals(TaskType.BUSINESS, a.taskType)
    }

    // ===== الفرق النصي: تعديل نقطي واحد (G05) =====
    @Test
    fun g05_pointEditDiff() {
        val a = "اكتب دعوة مؤتمر لفريق المبيعات يوم الأحد."
        val b = "اكتب دعوة مؤتمر لفريق المبيعات يوم الاثنين."
        val ops = Diff.wordDiff(a, b)
        assertEquals(1, ops.count { it.type == DiffOpType.DELETE })
        assertEquals(1, ops.count { it.type == DiffOpType.INSERT })
    }
}
