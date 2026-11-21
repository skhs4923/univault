package com.example.util

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.data.model.ScheduleItem
import com.example.data.model.StudyMaterial
import com.example.data.repository.StudyMaterialRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "VaultMaterialAnalyzer"
private const val MAX_INLINE_FILE_BYTES = 3 * 1024 * 1024L
private const val MAX_TOTAL_INLINE_BYTES = 9 * 1024 * 1024L
private const val MAX_INLINE_ATTACHMENTS = 3

data class VaultInlineAttachment(
    val title: String,
    val materialType: String,
    val mimeType: String,
    val bytes: ByteArray
)

data class VaultQuizContext(
    val promptText: String,
    val inlineAttachments: List<VaultInlineAttachment> = emptyList(),
    val analyzedCount: Int = 0,
    val fileAttachmentsIncluded: Int = 0
)

object VaultMaterialAnalyzer {

    fun extractSelectableTopics(
        materials: List<StudyMaterial>,
        schedules: List<ScheduleItem> = emptyList(),
        weakTopics: List<String> = emptyList()
    ): List<String> {
        val topics = LinkedHashSet<String>()
        weakTopics.forEach { t -> if (t.isNotBlank()) topics.add(t.trim()) }
        materials.forEach { mat ->
            mat.tags.forEach { tag -> if (tag.isNotBlank()) topics.add(tag.trim()) }
            if (mat.title.isNotBlank()) topics.add(mat.title.trim())
            if (mat.subject.isNotBlank()) topics.add(mat.subject.trim())
        }
        schedules.forEach { item ->
            if (item.title.isNotBlank()) topics.add(item.title.trim())
        }
        return topics.take(18)
    }

    suspend fun buildVaultQuizContext(
        context: Context?,
        repository: StudyMaterialRepository?,
        materials: List<StudyMaterial>,
        selectedTopic: String
    ): VaultQuizContext = withContext(Dispatchers.IO) {
        if (materials.isEmpty()) {
            return@withContext VaultQuizContext(
                promptText = "No Vault study materials were provided; generate rigorous academic questions based on the selected topic and subject.",
                inlineAttachments = emptyList(),
                analyzedCount = 0,
                fileAttachmentsIncluded = 0
            )
        }

        val topicLower = selectedTopic.trim().lowercase()
        val prioritized = materials.sortedByDescending { mat ->
            var score = 0
            if (topicLower.isNotBlank()) {
                if (mat.title.lowercase().contains(topicLower)) score += 5
                if (mat.subject.lowercase().contains(topicLower)) score += 4
                if (mat.tags.any { it.lowercase().contains(topicLower) }) score += 4
                if (mat.content.lowercase().contains(topicLower)) score += 3
            }
            when (mat.type) {
                "QUESTION_PAPER" -> score += 3
                "QUESTION_BANK" -> score += 3
                else -> score += 2
            }
            if (mat.fileName.isNotBlank() || mat.fileBase64.isNotBlank() || mat.hasChunks) score += 2
            score
        }

        val sb = StringBuilder()
        val notes = prioritized.filter { it.type == "LECTURE_NOTE" || (it.type != "QUESTION_BANK" && it.type != "QUESTION_PAPER") }
        val questionBanks = prioritized.filter { it.type == "QUESTION_BANK" }
        val questionPapers = prioritized.filter { it.type == "QUESTION_PAPER" }

        sb.appendLine("=== VAULT SOURCE MATERIALS SUMMARY (${prioritized.size} items) ===")
        sb.appendLine("Lecture Notes: ${notes.size} | Question Banks: ${questionBanks.size} | Question Papers: ${questionPapers.size}")
        sb.appendLine()

        val inlineDocs = mutableListOf<VaultInlineAttachment>()
        var totalInlineBytes = 0L

        suspend fun appendMaterialDetails(mat: StudyMaterial, index: Int) {
            val typeLabel = when (mat.type) {
                "QUESTION_PAPER" -> "QUESTION PAPER"
                "QUESTION_BANK" -> "QUESTION BANK"
                else -> "LECTURE NOTE"
            }
            sb.appendLine("[${index + 1}] [$typeLabel] ${mat.title} | Subject: ${mat.subject} (${mat.courseCode}, ${mat.semester})")
            if (mat.professor.isNotBlank()) sb.appendLine("    Professor: ${mat.professor}")
            if (mat.tags.isNotEmpty()) sb.appendLine("    Topics/Tags: ${mat.tags.joinToString(", ")}")
            if (mat.fileName.isNotBlank()) sb.appendLine("    Attached File: ${mat.fileName} (${mat.mimeType.ifBlank { "unknown mime" }})")

            if (mat.content.isNotBlank()) {
                val excerpt = mat.content.trim().take(2400)
                sb.appendLine("    Notes/Content:")
                sb.appendLine("    $excerpt")
            }

            // Try to extract text or inline PDF/image binary from uploaded files
            val resolvedMime = DocumentStorageUtil.resolveMimeType(mat.fileName, mat.mimeType).lowercase()
            val hasRealUploadedFile = mat.fileName.isNotBlank() || mat.fileBase64.isNotBlank() || mat.hasChunks ||
                (mat.localPath.isNotBlank() && File(mat.localPath).exists())

            if (context != null && hasRealUploadedFile) {
                val fileBytes = loadUploadedFileBytes(context, repository, mat)
                if (fileBytes != null && fileBytes.isNotEmpty()) {
                    when {
                        isPlainTextFile(mat.fileName, resolvedMime) -> {
                            val decodedText = String(fileBytes, Charsets.UTF_8).trim().take(3500)
                            if (decodedText.isNotBlank()) {
                                sb.appendLine("    Extracted File Text (${mat.fileName}):")
                                sb.appendLine("    $decodedText")
                            }
                        }
                        isGeminiSupportedBinaryMime(resolvedMime) &&
                            fileBytes.size <= MAX_INLINE_FILE_BYTES &&
                            inlineDocs.size < MAX_INLINE_ATTACHMENTS &&
                            (totalInlineBytes + fileBytes.size) <= MAX_TOTAL_INLINE_BYTES -> {
                            inlineDocs.add(
                                VaultInlineAttachment(
                                    title = mat.title,
                                    materialType = typeLabel,
                                    mimeType = normalizeInlineMime(resolvedMime),
                                    bytes = fileBytes
                                )
                            )
                            totalInlineBytes += fileBytes.size
                            sb.appendLine("    [Full ${mat.fileName} ($resolvedMime) attached directly for multimodal AI analysis]")
                        }
                    }
                }
            }
            sb.appendLine()
        }

        prioritized.take(20).forEachIndexed { idx, mat ->
            appendMaterialDetails(mat, idx)
        }

        return@withContext VaultQuizContext(
            promptText = sb.toString().trim(),
            inlineAttachments = inlineDocs,
            analyzedCount = prioritized.size,
            fileAttachmentsIncluded = inlineDocs.size
        )
    }

    private suspend fun loadUploadedFileBytes(
        context: Context,
        repository: StudyMaterialRepository?,
        material: StudyMaterial
    ): ByteArray? {
        return try {
            if (material.localPath.isNotBlank()) {
                val localFile = File(material.localPath)
                if (localFile.exists() && localFile.length() in 1..MAX_INLINE_FILE_BYTES) {
                    return localFile.readBytes()
                }
            }

            if (material.id.isNotBlank() && material.fileName.isNotBlank()) {
                val exportName = DocumentStorageUtil.sanitizeFileName(material.fileName, material.mimeType)
                val cached = File(DocumentStorageUtil.getDocumentsDir(context), "${material.id}_$exportName")
                if (cached.exists() && cached.length() in 1..MAX_INLINE_FILE_BYTES) {
                    return cached.readBytes()
                }
            }

            if (material.fileBase64.isNotBlank()) {
                val decoded = Base64.decode(material.fileBase64, Base64.DEFAULT)
                if (decoded.size in 1..MAX_INLINE_FILE_BYTES.toInt()) {
                    return decoded
                }
            }

            if (repository != null && material.id.isNotBlank() && (material.hasChunks || material.totalChunks > 0)) {
                val chunks = repository.fetchFileChunks(material.id)
                if (chunks.isNotEmpty()) {
                    val out = java.io.ByteArrayOutputStream()
                    for (chunk in chunks) {
                        out.write(Base64.decode(chunk, Base64.DEFAULT))
                        if (out.size() > MAX_INLINE_FILE_BYTES) {
                            return null
                        }
                    }
                    return out.toByteArray()
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Skipping unreadable attachment for ${material.title}: ${e.localizedMessage}")
            null
        }
    }

    internal fun isPlainTextFile(fileName: String, mimeType: String): Boolean {
        val lowerName = fileName.lowercase()
        val lowerMime = mimeType.lowercase()
        return lowerMime.startsWith("text/") ||
            lowerName.endsWith(".txt") ||
            lowerName.endsWith(".md") ||
            lowerName.endsWith(".csv") ||
            lowerName.endsWith(".json")
    }

    internal fun isGeminiSupportedBinaryMime(mimeType: String): Boolean {
        val lower = mimeType.lowercase()
        return lower == "application/pdf" ||
            lower == "image/jpeg" ||
            lower == "image/jpg" ||
            lower == "image/png" ||
            lower == "image/webp"
    }

    private fun normalizeInlineMime(mimeType: String): String {
        return when (mimeType.lowercase()) {
            "image/jpg" -> "image/jpeg"
            else -> mimeType.lowercase()
        }
    }
}
