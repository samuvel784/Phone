/*
 * Copyright (C) 2026 MovStore
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.R
import com.example.model.Contact
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale

object ContactShareUtils {

    fun shareAsVCard(context: Context, contact: Contact) {
        try {
            val vCardString = buildVCard(contact)
            val sanitizedName = contact.name.ifBlank { "Contact" }.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            val vCardFile = File(context.cacheDir, "exports").apply { mkdirs() }.let { dir ->
                File(dir, "$sanitizedName.vcf")
            }
            FileOutputStream(vCardFile).use { fos ->
                fos.write(vCardString.toByteArray(StandardCharsets.UTF_8))
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                vCardFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/x-vcard"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, contact.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            val chooser = Intent.createChooser(intent, context.getString(R.string.share_recording)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.share_recording), Toast.LENGTH_SHORT).show()
        }
    }

    fun shareAsText(context: Context, contact: Contact) {
        try {
            val textBuilder = StringBuilder()
            textBuilder.append("Name: ").append(contact.name).append("\n")
            val numbers = contact.getAllNumbers()
            for (num in numbers) {
                textBuilder.append("${num.label}: ").append(num.number).append("\n")
            }
            val emails = contact.getAllEmails()
            for (email in emails) {
                textBuilder.append("${email.label} Email: ").append(email.email).append("\n")
            }
            val addresses = contact.getAllAddresses()
            for (addr in addresses) {
                textBuilder.append("${addr.label} Address: ").append(addr.address).append("\n")
            }
            if (contact.note.isNotBlank()) {
                textBuilder.append("Note: ").append(contact.note).append("\n")
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, contact.name)
                putExtra(Intent.EXTRA_TEXT, textBuilder.toString().trim())
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            val chooser = Intent.createChooser(intent, context.getString(R.string.share_recording)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.share_recording), Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildVCard(contact: Contact): String {
        val sb = StringBuilder()
        sb.append("BEGIN:VCARD\n")
        sb.append("VERSION:3.0\n")
        val fn = escapeVCard(contact.name)
        sb.append("FN:").append(fn).append("\n")
        sb.append("N:").append(fn).append(";;;\n")

        for (num in contact.getAllNumbers()) {
            val type = num.label.uppercase(Locale.ROOT)
            val vCardType = when {
                type.contains("WORK") -> "WORK"
                type.contains("HOME") -> "HOME"
                type.contains("FAX") -> "FAX"
                else -> "CELL"
            }
            sb.append("TEL;TYPE=").append(vCardType).append(":").append(escapeVCard(num.number)).append("\n")
        }

        for (email in contact.getAllEmails()) {
            val type = email.label.uppercase(Locale.ROOT)
            val vCardType = if (type.contains("WORK")) "WORK" else "HOME"
            sb.append("EMAIL;TYPE=").append(vCardType).append(":").append(escapeVCard(email.email)).append("\n")
        }

        for (addr in contact.getAllAddresses()) {
            sb.append("ADR;TYPE=").append(addr.label.uppercase(Locale.ROOT)).append(":;;").append(escapeVCard(addr.address)).append(";;;\n")
        }

        if (contact.note.isNotBlank()) {
            sb.append("NOTE:").append(escapeVCard(contact.note)).append("\n")
        }

        sb.append("END:VCARD\n")
        return sb.toString()
    }

    private fun escapeVCard(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\n", "\\n")
    }
}
