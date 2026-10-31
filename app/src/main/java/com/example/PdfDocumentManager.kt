package com.example

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class FocusPdfDoc(
    val id: String,
    val title: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val pageCount: Int,
    val lastReadPage: Int = 0,
    val addedTimestamp: Long = System.currentTimeMillis()
)

object PdfDocumentManager {
    private const val PREFS_NAME = "FocusLockDocManager"
    private const val KEY_DOCS_JSON = "saved_docs_json"
    private const val DOCS_DIR = "focus_docs"

    fun getDocsDir(context: Context): File {
        val dir = File(context.filesDir, DOCS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getFileForDoc(context: Context, doc: FocusPdfDoc): File {
        return File(getDocsDir(context), doc.fileName)
    }

    @Synchronized
    fun getDocs(context: Context): List<FocusPdfDoc> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_DOCS_JSON, null) ?: return emptyList()
        val list = mutableListOf<FocusPdfDoc>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id")
                val title = obj.optString("title", "Document")
                val fileName = obj.optString("fileName", "$id.pdf")
                val size = obj.optLong("fileSizeBytes", 0L)
                val pageCount = obj.optInt("pageCount", 1)
                val lastPage = obj.optInt("lastReadPage", 0)
                val added = obj.optLong("addedTimestamp", System.currentTimeMillis())
                if (id.isNotEmpty()) {
                    list.add(
                        FocusPdfDoc(
                            id = id,
                            title = title,
                            fileName = fileName,
                            fileSizeBytes = size,
                            pageCount = pageCount,
                            lastReadPage = lastPage,
                            addedTimestamp = added
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("PdfDocManager", "Error parsing docs JSON", e)
        }
        return list
    }

    @Synchronized
    fun saveDoc(context: Context, doc: FocusPdfDoc) {
        val current = getDocs(context).toMutableList()
        val index = current.indexOfFirst { it.id == doc.id }
        if (index != -1) {
            current[index] = doc
        } else {
            current.add(0, doc) // newest first
        }
        saveAllDocs(context, current)
    }

    @Synchronized
    fun updateLastReadPage(context: Context, docId: String, page: Int) {
        val current = getDocs(context).toMutableList()
        val index = current.indexOfFirst { it.id == docId }
        if (index != -1) {
            val doc = current[index]
            val validPage = page.coerceIn(0, (doc.pageCount - 1).coerceAtLeast(0))
            current[index] = doc.copy(lastReadPage = validPage)
            saveAllDocs(context, current)
        }
    }

    @Synchronized
    fun deleteDoc(context: Context, docId: String): Boolean {
        val current = getDocs(context).toMutableList()
        val doc = current.firstOrNull { it.id == docId } ?: return false
        val file = getFileForDoc(context, doc)
        try {
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e("PdfDocManager", "Error deleting file for doc $docId", e)
        }
        current.removeAll { it.id == docId }
        saveAllDocs(context, current)
        return true
    }

    private fun saveAllDocs(context: Context, list: List<FocusPdfDoc>) {
        val jsonArray = JSONArray()
        for (doc in list) {
            val obj = JSONObject().apply {
                put("id", doc.id)
                put("title", doc.title)
                put("fileName", doc.fileName)
                put("fileSizeBytes", doc.fileSizeBytes)
                put("pageCount", doc.pageCount)
                put("lastReadPage", doc.lastReadPage)
                put("addedTimestamp", doc.addedTimestamp)
            }
            jsonArray.put(obj)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DOCS_JSON, jsonArray.toString()).apply()
    }

    fun importPdfFromUri(context: Context, uri: Uri): FocusPdfDoc? {
        return try {
            var displayName = "Imported_Document.pdf"
            var sizeBytes = 0L

            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) {
                            val name = cursor.getString(nameIndex)
                            if (!name.isNullOrBlank()) displayName = name
                        }
                        if (sizeIndex != -1) {
                            sizeBytes = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("PdfDocManager", "Could not query document metadata", e)
            }

            val id = UUID.randomUUID().toString().take(8)
            val docsDir = getDocsDir(context)
            val targetFile = File(docsDir, "$id.pdf")

            context.contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            if (sizeBytes <= 0L) {
                sizeBytes = targetFile.length()
            }

            // Determine page count safely using native PdfRenderer
            var pageCount = 1
            var pfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            try {
                pfd = ParcelFileDescriptor.open(targetFile, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd)
                pageCount = renderer.pageCount.coerceAtLeast(1)
            } catch (e: Exception) {
                Log.w("PdfDocManager", "Could not count pages via PdfRenderer", e)
            } finally {
                try { renderer?.close() } catch (e: Exception) {}
                try { pfd?.close() } catch (e: Exception) {}
            }

            val cleanTitle = displayName
                .replace(Regex("\\.[pP][dD][fF]$"), "")
                .replace('_', ' ')
                .trim()

            val doc = FocusPdfDoc(
                id = id,
                title = if (cleanTitle.isNotBlank()) cleanTitle else "Document $id",
                fileName = targetFile.name,
                fileSizeBytes = sizeBytes,
                pageCount = pageCount,
                lastReadPage = 0,
                addedTimestamp = System.currentTimeMillis()
            )
            saveDoc(context, doc)
            doc
        } catch (e: Exception) {
            Log.e("PdfDocManager", "Failed to import PDF from $uri", e)
            null
        }
    }

    fun ensureSampleDocExists(context: Context) {
        val docs = getDocs(context)
        if (docs.isNotEmpty()) return

        val docsDir = getDocsDir(context)
        val id = "focus_starter"
        val targetFile = File(docsDir, "$id.pdf")
        if (!targetFile.exists()) {
            try {
                val pdfDoc = PdfDocument()

                // Page 1: Focus Reader Welcome
                val pageInfo1 = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 (72 dpi)
                val page1 = pdfDoc.startPage(pageInfo1)
                val canvas1 = page1.canvas

                // Background
                val bgPaint = Paint().apply { color = android.graphics.Color.rgb(250, 250, 248) }
                canvas1.drawRect(0f, 0f, 595f, 842f, bgPaint)

                // Header bar accent
                val accentPaint = Paint().apply { color = android.graphics.Color.rgb(79, 70, 229) }
                canvas1.drawRect(45f, 40f, 550f, 44f, accentPaint)

                // Header
                val titlePaint = Paint().apply {
                    color = android.graphics.Color.rgb(15, 23, 42)
                    textSize = 24f
                    isFakeBoldText = true
                    isAntiAlias = true
                }
                canvas1.drawText("Focus Lock: Document Reader", 45f, 75f, titlePaint)

                val subPaint = Paint().apply {
                    color = android.graphics.Color.rgb(100, 116, 139)
                    textSize = 13f
                    isAntiAlias = true
                }
                canvas1.drawText("Your distraction-free reading companion during lockdown.", 45f, 98f, subPaint)

                val dividerPaint = Paint().apply {
                    color = android.graphics.Color.rgb(226, 232, 240)
                    strokeWidth = 1.5f
                }
                canvas1.drawLine(45f, 115f, 550f, 115f, dividerPaint)

                val textPaint = Paint().apply {
                    color = android.graphics.Color.rgb(51, 65, 85)
                    textSize = 13.5f
                    isAntiAlias = true
                }

                val welcomeLines = listOf(
                    "Welcome to your Focus Reader!",
                    "",
                    "When your phone is locked down, all distracting social media, games, and",
                    "browsers are strictly blocked. But your study and growth should never stop.",
                    "",
                    "This built-in reader operates entirely inside Focus Lock:",
                    "• 100% Offline & Isolated: Never triggers app blocking or exits lockdown.",
                    "• Easy Import: Tap the '+ Import PDF' button anytime to add study notes,",
                    "   holy books, textbooks, articles, or work documents from your device.",
                    "• Complete Privacy: Your documents stay completely local on your device.",
                    "• Reading Comfort: Pinch to zoom, swipe pages, jump to any page, and",
                    "   toggle between Light, Dark, and Warm Sepia reading modes.",
                    "• Auto-Save Progress: Pick up right where you left off when you return.",
                    "",
                    "Turn to Page 2 for Daily Focus & Reflection guidelines."
                )

                var y = 145f
                for (line in welcomeLines) {
                    if (line.startsWith("Welcome to your") || line.startsWith("This built-in reader")) {
                        textPaint.isFakeBoldText = true
                        textPaint.color = android.graphics.Color.rgb(15, 23, 42)
                        textPaint.textSize = 14.5f
                    } else if (line.startsWith("•")) {
                        textPaint.isFakeBoldText = false
                        textPaint.color = android.graphics.Color.rgb(67, 56, 202)
                        textPaint.textSize = 13f
                    } else {
                        textPaint.isFakeBoldText = false
                        textPaint.color = android.graphics.Color.rgb(51, 65, 85)
                        textPaint.textSize = 13.5f
                    }
                    canvas1.drawText(line, 45f, y, textPaint)
                    y += 24f
                }

                // Footer
                val footerPaint = Paint().apply {
                    color = android.graphics.Color.rgb(148, 163, 184)
                    textSize = 11f
                    isAntiAlias = true
                }
                canvas1.drawText("Page 1 of 2  •  Focus Lock Guide", 45f, 810f, footerPaint)

                pdfDoc.finishPage(page1)

                // Page 2: Reflection & Deep Study
                val pageInfo2 = PdfDocument.PageInfo.Builder(595, 842, 2).create()
                val page2 = pdfDoc.startPage(pageInfo2)
                val canvas2 = page2.canvas
                canvas2.drawRect(0f, 0f, 595f, 842f, bgPaint)
                canvas2.drawRect(45f, 40f, 550f, 44f, accentPaint)

                canvas2.drawText("Daily Focus & Reflection", 45f, 75f, titlePaint)
                canvas2.drawText("Cultivating mental stillness, prayer, and deep study habits.", 45f, 98f, subPaint)
                canvas2.drawLine(45f, 115f, 550f, 115f, dividerPaint)

                val tips = listOf(
                    "1. The Gift of Undivided Attention",
                    "Modern life constantly fragments our focus. By choosing lockdown,",
                    "you reclaim hours of pure cognitive depth. Embrace the quietness.",
                    "",
                    "2. Sacred Reading & Meditation",
                    "Before opening a chapter, take 3 deep, steady breaths.",
                    "Disconnect from urgent thoughts. Let words sink in deeply rather than skimming.",
                    "",
                    "3. Build Your Personal Focus Library",
                    "You can import any PDF into this reader:",
                    "• Devotional texts, scriptures, and spiritual books",
                    "• School textbooks, lecture summaries, and revision notes",
                    "• Deep literature, history, and philosophical essays",
                    "• Work manuals and technical documentation",
                    "",
                    "\"He who conquers himself is mightier than he who conquers a city.\"",
                    "",
                    "Happy reading! Tap '+ Import PDF' on the library screen anytime to add your own books."
                )

                y = 145f
                for (tip in tips) {
                    if (tip.startsWith("1.") || tip.startsWith("2.") || tip.startsWith("3.")) {
                        textPaint.isFakeBoldText = true
                        textPaint.color = android.graphics.Color.rgb(15, 23, 42)
                        textPaint.textSize = 15f
                    } else if (tip.startsWith("•")) {
                        textPaint.isFakeBoldText = false
                        textPaint.color = android.graphics.Color.rgb(79, 70, 229)
                        textPaint.textSize = 13f
                    } else if (tip.startsWith("\"")) {
                        textPaint.isFakeBoldText = true
                        textPaint.color = android.graphics.Color.rgb(124, 58, 237)
                        textPaint.textSize = 14f
                    } else {
                        textPaint.isFakeBoldText = false
                        textPaint.color = android.graphics.Color.rgb(51, 65, 85)
                        textPaint.textSize = 13.5f
                    }
                    canvas2.drawText(tip, 45f, y, textPaint)
                    y += 24f
                }

                canvas2.drawText("Page 2 of 2  •  Focus Lock Guide", 45f, 810f, footerPaint)

                pdfDoc.finishPage(page2)

                targetFile.outputStream().use { pdfDoc.writeTo(it) }
                pdfDoc.close()

                val sample = FocusPdfDoc(
                    id = id,
                    title = "Focus & Calm Reader Guide",
                    fileName = targetFile.name,
                    fileSizeBytes = targetFile.length(),
                    pageCount = 2,
                    lastReadPage = 0,
                    addedTimestamp = System.currentTimeMillis()
                )
                saveDoc(context, sample)
            } catch (e: Exception) {
                Log.e("PdfDocManager", "Error generating starter sample PDF", e)
            }
        }
    }
}
