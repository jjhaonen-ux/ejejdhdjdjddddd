package com.promptlab.app.engine

/**
 * فرق نصي بمستوى الكلمة (LCS) — لعرض المقارنة بين النصين في وضع المقارنة
 * وبين النتيجة المولدة ونسختها المعدلة يدويًا.
 */

enum class DiffOpType { EQUAL, INSERT, DELETE }

data class DiffOp(val type: DiffOpType, val text: String)

object Diff {
    fun wordDiff(a: String, b: String): List<DiffOp> {
        val aw = a.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val bw = b.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (aw.isEmpty() && bw.isEmpty()) return emptyList()
        if (aw.isEmpty()) return listOf(DiffOp(DiffOpType.INSERT, bw.joinToString(" ")))
        if (bw.isEmpty()) return listOf(DiffOp(DiffOpType.DELETE, aw.joinToString(" ")))

        // LCS طولي ثم مسار
        val n = aw.size
        val m = bw.size
        val lcs = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                lcs[i][j] = if (aw[i] == bw[j]) lcs[i + 1][j + 1] + 1
                else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }
        val ops = mutableListOf<DiffOp>()
        var i = 0
        var j = 0
        fun flush(list: MutableList<DiffOp>, type: DiffOpType, buf: StringBuilder) {
            if (buf.isNotEmpty()) { list += DiffOp(type, buf.toString().trim()); buf.clear() }
        }
        val ins = StringBuilder()
        val del = StringBuilder()
        while (i < n && j < m) {
            when {
                aw[i] == bw[j] -> {
                    flush(ops, DiffOpType.DELETE, del); flush(ops, DiffOpType.INSERT, ins)
                    ops += DiffOp(DiffOpType.EQUAL, aw[i]); i++; j++
                }
                lcs[i + 1][j] >= lcs[i][j + 1] -> { del.append(aw[i]).append(' '); i++ }
                else -> { ins.append(bw[j]).append(' '); j++ }
            }
        }
        while (i < n) { del.append(aw[i]).append(' '); i++ }
        while (j < m) { ins.append(bw[j]).append(' '); j++ }
        flush(ops, DiffOpType.DELETE, del)
        flush(ops, DiffOpType.INSERT, ins)
        return ops
    }
}
