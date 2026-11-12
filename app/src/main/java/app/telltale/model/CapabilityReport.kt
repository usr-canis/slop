package app.telltale.model

/** Builds the shareable plain-text capability report. Pure, unit-tested. */
object CapabilityReport {
    fun carTitle(fields: Map<String, FieldReading>): String {
        val make = fields[Fields.MAKE]?.text
        val name = fields[Fields.MODEL_NAME]?.text
        val year = fields[Fields.YEAR]?.text
        val t = listOfNotNull(year, make, name).joinToString(" ")
        return t.ifBlank { "Unknown car" }
    }

    fun statusOf(fields: Map<String, FieldReading>, key: String): FieldStatus = fields[key]?.status ?: FieldStatus.WAITING

    fun summary(fields: Map<String, FieldReading>): Pair<Int, Int> =
        Fields.carFields.count { statusOf(fields, it.key) == FieldStatus.AVAILABLE } to Fields.carFields.size

    fun text(fields: Map<String, FieldReading>, appVersion: String): String = buildString {
        appendLine("Telltale capability report")
        appendLine(carTitle(fields))
        val (a, t) = summary(fields)
        appendLine("$a of $t car fields available · app $appVersion")
        Fields.all.groupBy { it.group }.forEach { (group, defs) ->
            appendLine()
            appendLine("[$group]")
            defs.forEach { d ->
                val r = fields[d.key]
                val st = r?.status ?: FieldStatus.WAITING
                val mark = when (st) { FieldStatus.AVAILABLE -> "✓"; FieldStatus.WAITING -> "…"; else -> "✗" }
                append("$mark ${d.label}: ${st.label}")
                if (st == FieldStatus.AVAILABLE && r?.text != null) append(" — ${r.text}")
                appendLine()
            }
        }
    }
}
