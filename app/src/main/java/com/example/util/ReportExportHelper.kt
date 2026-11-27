package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.SessionTimelineEventEntity
import com.example.data.local.WorkSessionEntity
import com.example.engine.RiderWorkspaceEngine
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExportHelper {

    fun buildSessionCsv(
        sessions: List<WorkSessionEntity>,
        timelineEvents: List<SessionTimelineEventEntity>,
        nowMs: Long = System.currentTimeMillis()
    ): String {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("# Ultimate Rider Booster - Device Readiness & Work Session Report")
        sb.appendLine("# Disclaimer: Measures local device GNSS/network/battery readiness only; does not access Talabat order or earnings data.")
        sb.appendLine("session_id,start_time,end_time,status,active_minutes,total_samples,gps_fixed_samples,avg_gps_accuracy_m,network_ok_samples,network_losses,overheat_events,start_battery_pct,end_battery_pct,max_battery_temp_c")
        for (s in sessions) {
            val activeMin = RiderWorkspaceEngine.computeEffectiveDurationMs(s, nowMs) / 60_000L
            val avgAcc = if (s.accuracySampleCount > 0) {
                String.format(Locale.US, "%.1f", s.totalAccuracySumMeters / s.accuracySampleCount)
            } else ""
            sb.appendLine(
                listOf(
                    s.id,
                    df.format(Date(s.startTimeMs)),
                    s.endTimeMs?.let { df.format(Date(it)) } ?: "Active",
                    s.status,
                    activeMin,
                    s.totalSamples,
                    s.gpsFixedSamples,
                    avgAcc,
                    s.networkOkSamples,
                    s.networkLossCount,
                    s.overheatEventCount,
                    s.startBatteryPct,
                    s.endBatteryPct ?: "",
                    s.maxBatteryTempC?.let { String.format(Locale.US, "%.1f", it) } ?: ""
                ).joinToString(",")
            )
        }
        if (timelineEvents.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("event_id,session_id,timestamp,category,severity,title,detail")
            for (ev in timelineEvents) {
                val safeTitle = "\"${ev.title.replace("\"", "\"\"")}\""
                val safeDetail = "\"${ev.detail.replace("\"", "\"\"")}\""
                sb.appendLine("${ev.id},${ev.sessionId},${df.format(Date(ev.timestampMs))},${ev.category},${ev.severity},$safeTitle,$safeDetail")
            }
        }
        return sb.toString()
    }

    fun writeSessionPdfToStream(
        outputStream: OutputStream,
        sessions: List<WorkSessionEntity>,
        timelineEvents: List<SessionTimelineEventEntity>,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val nativeWorked = runCatching {
            val doc = PdfDocument()
            val titlePaint = Paint().apply {
                color = Color.rgb(11, 18, 32)
                textSize = 18f
                isFakeBoldText = true
            }
            val subtitlePaint = Paint().apply {
                color = Color.rgb(0, 120, 180)
                textSize = 11f
                isFakeBoldText = true
            }
            val bodyPaint = Paint().apply {
                color = Color.rgb(30, 41, 59)
                textSize = 10f
            }
            val mutedPaint = Paint().apply {
                color = Color.rgb(100, 116, 139)
                textSize = 9f
            }

            var pageNum = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create())
            var canvas = page.canvas
            var y = 44f

            canvas.drawText("ULTIMATE RIDER BOOSTER — WORKSPACE REPORT", 36f, y, titlePaint)
            y += 18f
            canvas.drawText("Generated: ${df.format(Date(nowMs))}", 36f, y, mutedPaint)
            y += 14f
            canvas.drawText("Note: Reflects local device GNSS, network, battery & thermal readiness only.", 36f, y, mutedPaint)
            y += 22f

            canvas.drawText("WORK SESSIONS (${sessions.size})", 36f, y, subtitlePaint)
            y += 16f

            if (sessions.isEmpty()) {
                canvas.drawText("No work sessions recorded yet.", 36f, y, bodyPaint)
                y += 16f
            } else {
                for (s in sessions.take(25)) {
                    if (y > 780f) {
                        doc.finishPage(page)
                        pageNum++
                        page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create())
                        canvas = page.canvas
                        y = 44f
                    }
                    val mins = RiderWorkspaceEngine.computeEffectiveDurationMs(s, nowMs) / 60_000L
                    val acc = if (s.accuracySampleCount > 0) String.format(Locale.US, "±%.1fm", s.totalAccuracySumMeters / s.accuracySampleCount) else "N/A"
                    val line1 = "#${s.id} [${s.status}] ${df.format(Date(s.startTimeMs))} • Active: ${mins}m • GPS Acc: $acc"
                    val line2 = "   Samples: ${s.totalSamples} • Net Drops: ${s.networkLossCount} • Overheats: ${s.overheatEventCount} • Batt: ${s.startBatteryPct}% -> ${s.endBatteryPct ?: "--"}%"
                    canvas.drawText(line1, 36f, y, bodyPaint)
                    y += 13f
                    canvas.drawText(line2, 36f, y, mutedPaint)
                    y += 16f
                }
            }

            if (timelineEvents.isNotEmpty()) {
                if (y > 740f) {
                    doc.finishPage(page)
                    pageNum++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create())
                    canvas = page.canvas
                    y = 44f
                }
                y += 8f
                canvas.drawText("RECENT TELEMETRY TIMELINE EVENTS", 36f, y, subtitlePaint)
                y += 16f
                for (ev in timelineEvents.take(40)) {
                    if (y > 785f) {
                        doc.finishPage(page)
                        pageNum++
                        page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNum).create())
                        canvas = page.canvas
                        y = 44f
                    }
                    val line = "${df.format(Date(ev.timestampMs))} [${ev.category}/${ev.severity}] ${ev.title}: ${ev.detail}".take(96)
                    canvas.drawText(line, 36f, y, bodyPaint)
                    y += 13f
                }
            }

            doc.finishPage(page)
            doc.writeTo(outputStream)
            doc.close()
        }.isSuccess

        if (!nativeWorked) {
            writeFallbackPdf14(outputStream, sessions, timelineEvents, df, nowMs)
        }
    }

    private fun writeFallbackPdf14(
        outputStream: OutputStream,
        sessions: List<WorkSessionEntity>,
        timelineEvents: List<SessionTimelineEventEntity>,
        df: SimpleDateFormat,
        nowMs: Long
    ) {
        val lines = buildList {
            add("ULTIMATE RIDER BOOSTER - WORKSPACE REPORT")
            add("Generated: ${df.format(Date(nowMs))}")
            add("WORK SESSIONS (${sessions.size})")
            sessions.take(25).forEach { s ->
                val mins = RiderWorkspaceEngine.computeEffectiveDurationMs(s, nowMs) / 60_000L
                add("#${s.id} [${s.status}] ${df.format(Date(s.startTimeMs))} Active: ${mins}m Samples: ${s.totalSamples}")
            }
            timelineEvents.take(25).forEach { ev ->
                add("${df.format(Date(ev.timestampMs))} [${ev.category}] ${ev.title}: ${ev.detail}")
            }
        }
        val contentStream = buildString {
            append("BT /F1 10 Tf 36 780 Td 14 TL ")
            lines.forEach { raw ->
                val esc = raw.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
                append("($esc) Tj T* ")
            }
            append("ET")
        }
        val pdf = buildString {
            append("%PDF-1.4\n")
            append("1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n")
            append("2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n")
            append("3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj\n")
            append("4 0 obj << /Length ${contentStream.length} >> stream\n$contentStream\nendstream endobj\n")
            append("5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n")
            append("trailer << /Root 1 0 R >>\n%%EOF\n")
        }
        outputStream.write(pdf.toByteArray(Charsets.UTF_8))
    }

    fun exportAndShareCsv(
        context: Context,
        sessions: List<WorkSessionEntity>,
        events: List<SessionTimelineEventEntity>
    ): Boolean = runCatching {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "rider_workspace_report.csv")
        file.writeText(buildSessionCsv(sessions, events))
        shareReportFile(context, file, "text/csv", "Share Rider CSV Report")
        true
    }.getOrDefault(false)

    fun exportAndSharePdf(
        context: Context,
        sessions: List<WorkSessionEntity>,
        events: List<SessionTimelineEventEntity>
    ): Boolean = runCatching {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "rider_workspace_report.pdf")
        file.outputStream().use { out ->
            writeSessionPdfToStream(out, sessions, events)
        }
        shareReportFile(context, file, "application/pdf", "Share Rider PDF Report")
        true
    }.getOrDefault(false)

    private fun shareReportFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
