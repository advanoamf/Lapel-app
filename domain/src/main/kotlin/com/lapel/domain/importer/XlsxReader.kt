package com.lapel.domain.importer

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** One cell: its cached value (String, Double or Boolean) and its formula text, if any. */
data class XlsxCell(val value: Any?, val formula: String?) {
    val number: Double? get() = when (value) {
        is Double -> value
        is String -> value.trim().toDoubleOrNull()
        else -> null
    }
    val text: String? get() = when (value) {
        null -> null
        is Double -> if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
        else -> value.toString().trim().ifEmpty { null }
    }
    val bool: Boolean? get() = value as? Boolean
}

class XlsxSheet(val name: String, private val cells: Map<String, XlsxCell>) {
    operator fun get(column: String, row: Int): XlsxCell? = cells["$column$row"]
    val maxRow: Int = cells.keys.maxOfOrNull { ref -> ref.dropWhile { it.isLetter() }.toInt() } ?: 0
}

class XlsxWorkbook(val sheets: List<XlsxSheet>) {
    fun sheet(name: String): XlsxSheet? = sheets.firstOrNull { it.name.trim() == name.trim() }
}

/**
 * Minimal reader for .xlsx files: reads cell values as Excel last calculated them, plus formula text.
 * Uses only the JDK (zip + DOM), so it runs on Android and in plain JVM tests.
 */
object XlsxReader {

    private const val MAX_ENTRY_BYTES = 50L * 1024 * 1024

    fun read(input: InputStream): XlsxWorkbook {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || !entry.name.endsWith(".xml") && !entry.name.endsWith(".rels")) continue
                entries[entry.name.removePrefix("/")] = zip.readLimited(MAX_ENTRY_BYTES)
            }
        }

        val shared = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val workbook = entries["xl/workbook.xml"]?.let(::parse) ?: error("Not an .xlsx file: xl/workbook.xml missing")
        val rels = entries["xl/_rels/workbook.xml.rels"]?.let(::parse)
            ?.elements("Relationship")
            ?.associate { it.getAttribute("Id") to it.getAttribute("Target") }
            .orEmpty()

        val sheets = workbook.elements("sheet").mapNotNull { sheet ->
            val target = rels[sheet.getAttribute("r:id")] ?: return@mapNotNull null
            val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
            val xml = entries[path] ?: return@mapNotNull null
            XlsxSheet(sheet.getAttribute("name"), parseCells(xml, shared))
        }
        return XlsxWorkbook(sheets)
    }

    private fun parseCells(xml: ByteArray, shared: List<String>): Map<String, XlsxCell> {
        val cells = mutableMapOf<String, XlsxCell>()
        parse(xml).elements("c").forEach { c ->
            val ref = c.getAttribute("r").ifEmpty { return@forEach }
            val raw = c.firstChild("v")?.textContent
            val formula = c.firstChild("f")?.textContent?.takeIf { it.isNotBlank() }
            val value: Any? = when (c.getAttribute("t")) {
                "s" -> raw?.toIntOrNull()?.let { shared.getOrNull(it) }
                "str", "e" -> raw
                "inlineStr" -> c.elements("t").joinToString("") { it.textContent }
                "b" -> raw == "1"
                else -> raw?.toDoubleOrNull()
            }
            if (value != null || formula != null) cells[ref] = XlsxCell(value, formula)
        }
        return cells
    }

    private fun parseSharedStrings(xml: ByteArray): List<String> =
        parse(xml).elements("si").map { si -> si.elements("t").joinToString("") { it.textContent } }

    private fun parse(xml: ByteArray): Element {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isExpandEntityReferences = false
        // No DTDs or external entities in untrusted files.
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(xml)).documentElement
    }

    private fun Element.elements(tag: String): List<Element> {
        val list = getElementsByTagName(tag)
        return (0 until list.length).map { list.item(it) as Element }
    }

    private fun Element.firstChild(tag: String): Element? {
        var node: Node? = firstChild
        while (node != null) {
            if (node is Element && node.tagName == tag) return node
            node = node.nextSibling
        }
        return null
    }

    private fun ZipInputStream.readLimited(limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            total += n
            require(total <= limit) { "Spreadsheet part too large" }
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
