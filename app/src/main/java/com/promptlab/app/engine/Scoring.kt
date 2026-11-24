package com.promptlab.app.engine

/**
 * التنقيط — نقل مطابق لـ scoring.ts: إعداد مركزي واحد، عقوبات الشدات وأوزان الأبعاد كما هي.
 * الأرقام تُعاير لاحقًا من مجموعة القبول العربية (المرحلة الثانية لا تغيّرها).
 */
object Scoring {
    val penalties = mapOf(Severity.INFO to 5, Severity.MINOR to 14, Severity.MAJOR to 30)

    data class Score(val overall: Int, val byDimension: Map<Dimension, Int>)

    fun compute(findings: List<Finding>): Score {
        val byDimension = Dimension.entries.associateWith { 100 }.toMutableMap()
        for (f in findings) {
            byDimension[f.dimension] = maxOf(0, (byDimension[f.dimension] ?: 100) - penalties.getValue(f.severity))
        }
        val overall = Math.round(Dimension.entries.sumOf { dim ->
            (byDimension[dim] ?: 0) * dim.weight
        }).toInt()
        return Score(overall, byDimension)
    }
}
