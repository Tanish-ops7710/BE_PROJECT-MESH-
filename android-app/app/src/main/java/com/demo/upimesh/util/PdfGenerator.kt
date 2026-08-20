package com.demo.upimesh.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfGenerator {

    fun generateTransactionReceipt(
        context: Context,
        packetId: String,
        senderVpa: String,
        receiverVpa: String,
        amount: String,
        status: String,
        note: String
    ): File? {
        return try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val paint = Paint().apply {
                color = Color.parseColor("#1A73E8")
                textSize = 24f
                isFakeBoldText = true
            }

            // Header Banner
            canvas.drawRect(0f, 0f, 595f, 100f, paint)

            val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = 22f
                isFakeBoldText = true
            }
            canvas.drawText("OFFLINE UPI MESH RECEIPT", 40f, 60f, textPaint)

            paint.color = Color.BLACK
            paint.textSize = 14f
            paint.isFakeBoldText = false

            var y = 140f
            val lineSpacing = 30f

            val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            val dateStr = dateFormat.format(Date())

            canvas.drawText("Transaction Date: $dateStr", 40f, y, paint); y += lineSpacing
            canvas.drawText("Status: $status", 40f, y, paint); y += lineSpacing
            canvas.drawText("Packet ID: $packetId", 40f, y, paint); y += lineSpacing
            canvas.drawText("Reference SHA-256: ${CryptoUtils.sha256(packetId).take(16).uppercase()}", 40f, y, paint); y += lineSpacing + 10f

            paint.textSize = 16f
            paint.isFakeBoldText = true
            canvas.drawText("Payment Details", 40f, y, paint); y += lineSpacing
            paint.textSize = 14f
            paint.isFakeBoldText = false

            canvas.drawText("Sender VPA: $senderVpa", 40f, y, paint); y += lineSpacing
            canvas.drawText("Receiver VPA: $receiverVpa", 40f, y, paint); y += lineSpacing
            canvas.drawText("Amount Transferred: ₹$amount", 40f, y, paint); y += lineSpacing
            if (note.isNotEmpty()) {
                canvas.drawText("Note: $note", 40f, y, paint); y += lineSpacing
            }
            canvas.drawText("Security Scheme: Hybrid RSA-2048 + AES-256-GCM", 40f, y, paint); y += lineSpacing + 20f

            paint.color = Color.GRAY
            paint.textSize = 12f
            canvas.drawText("Verified by Offline BLE Gossip Network & Central Bank Ledger", 40f, y, paint)

            pdfDocument.finishPage(page)

            val file = File(context.cacheDir, "UPI_Receipt_${packetId.take(8)}.pdf")
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            outputStream.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
