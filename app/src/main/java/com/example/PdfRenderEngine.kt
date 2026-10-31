package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PdfRenderEngine(private val file: File) {

    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    var pageCount: Int = 0
        private set

    // Cache up to 4 rendered page bitmaps
    private val pageCache = object : LruCache<Int, Bitmap>(4) {
        override fun entryRemoved(evicted: Boolean, key: Int?, oldValue: Bitmap?, newValue: Bitmap?) {
            // Let Garbage Collector handle recycling naturally to prevent race conditions during draw
        }
    }

    init {
        try {
            if (file.exists() && file.length() > 0) {
                pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd!!)
                pageCount = renderer!!.pageCount
            }
        } catch (e: Exception) {
            Log.e("PdfRenderEngine", "Failed to initialize renderer for ${file.name}", e)
            close()
        }
    }

    val isValid: Boolean
        get() = renderer != null && pageCount > 0

    suspend fun getPageBitmap(pageIndex: Int, targetWidthPx: Int = 1080): Bitmap? = withContext(Dispatchers.IO) {
        if (!isValid || pageIndex < 0 || pageIndex >= pageCount) return@withContext null

        // Check in-memory cache first
        synchronized(pageCache) {
            val cached = pageCache.get(pageIndex)
            if (cached != null && !cached.isRecycled) {
                return@withContext cached
            }
        }

        // Render synchronously to comply with PdfRenderer's 1-page-at-a-time rule
        val rendered = renderPageInternal(pageIndex, targetWidthPx)
        if (rendered != null) {
            synchronized(pageCache) {
                pageCache.put(pageIndex, rendered)
            }
        }
        rendered
    }

    @Synchronized
    private fun renderPageInternal(pageIndex: Int, targetWidthPx: Int): Bitmap? {
        val curRenderer = renderer ?: return null
        if (pageIndex < 0 || pageIndex >= pageCount) return null

        var page: PdfRenderer.Page? = null
        return try {
            page = curRenderer.openPage(pageIndex)
            val origW = page.width
            val origH = page.height

            // Calculate scale: target crisp reading without excessive memory
            val safeWidth = targetWidthPx.coerceIn(720, 1600)
            val scale = (safeWidth.toFloat() / origW.toFloat()).coerceIn(1.0f, 3.0f)

            val width = (origW * scale).toInt().coerceIn(100, 2400)
            val height = (origH * scale).toInt().coerceIn(100, 3600)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE) // PDF backgrounds are transparent by default

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } catch (e: Exception) {
            Log.e("PdfRenderEngine", "Error rendering page $pageIndex", e)
            null
        } finally {
            try {
                page?.close()
            } catch (e: Exception) {}
        }
    }

    fun close() {
        synchronized(pageCache) {
            pageCache.evictAll()
        }
        try {
            renderer?.close()
        } catch (e: Exception) {}
        try {
            pfd?.close()
        } catch (e: Exception) {}
        renderer = null
        pfd = null
    }
}
