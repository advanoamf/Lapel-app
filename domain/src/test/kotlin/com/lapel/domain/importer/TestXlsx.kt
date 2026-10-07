package com.lapel.domain.importer

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds a tiny .xlsx in memory. A cell is a value (String, Number, Boolean) and optional formula. */
object TestXlsx {
    data class C(val value: Any?, val formula: String? = null)

    fun build(sheets: Map<String, Map<String, C>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, xml: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(xml.toByteArray()); zip.closeEntry()
            }
            val names = sheets.keys.toList()
            put(
                "xl/workbook.xml",
                """<workbook xmlns:r="r"><sheets>${names.mapIndexed { i, n -> """<sheet name="${esc(n)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""" }.joinToString("")}</sheets></workbook>""",
            )
            put(
                "xl/_rels/workbook.xml.rels",
                """<Relationships>${names.indices.joinToString("") { """<Relationship Id="rId${it + 1}" Target="worksheets/sheet${it + 1}.xml"/>""" }}</Relationships>""",
            )
            // First string cell goes through sharedStrings to exercise that path.
            put("xl/sharedStrings.xml", """<sst><si><t>shared</t></si></sst>""")
            names.forEachIndexed { i, n ->
                val cells = sheets.getValue(n).entries.joinToString("") { (ref, c) -> cell(ref, c) }
                put("xl/worksheets/sheet${i + 1}.xml", """<worksheet><sheetData><row>$cells</row></sheetData></worksheet>""")
            }
        }
        return out.toByteArray()
    }

    private fun cell(ref: String, c: C): String {
        val f = c.formula?.let { "<f>${esc(it.removePrefix("="))}</f>" }.orEmpty()
        return when (val v = c.value) {
            null -> """<c r="$ref">$f</c>"""
            "shared" -> """<c r="$ref" t="s">$f<v>0</v></c>"""
            is String -> if (f.isEmpty()) """<c r="$ref" t="inlineStr"><is><t>${esc(v)}</t></is></c>""" else """<c r="$ref" t="str">$f<v>${esc(v)}</v></c>"""
            is Boolean -> """<c r="$ref" t="b">$f<v>${if (v) 1 else 0}</v></c>"""
            is Number -> """<c r="$ref">$f<v>$v</v></c>"""
            else -> error("unsupported $v")
        }
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
