package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import org.json.JSONArray
import org.json.JSONObject

@IgnoreExtraProperties
data class TestResult(
    @DocumentId val id: String = "",
    val userId: String = "",
    val collegeId: String = "",
    val quizId: String = "",
    val quizTitle: String = "",
    val subject: String = "",
    val score: Double = 0.0,
    val totalMarks: Double = 0.0,
    val accuracy: Double = 0.0,
    val summary: String = "",
    val answersJson: String = "",
    val weakTopics: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class StudentAnswerRecord(
    val questionId: String = "",
    val questionText: String = "",
    val selectedIndex: Int = -1,
    val correctIndex: Int = 0,
    val isCorrect: Boolean = false,
    val explanation: String = "",
    val topic: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("questionId", questionId)
        put("questionText", questionText)
        put("selectedIndex", selectedIndex)
        put("correctIndex", correctIndex)
        put("isCorrect", isCorrect)
        put("explanation", explanation)
        put("topic", topic)
    }

    companion object {
        fun fromJson(json: JSONObject): StudentAnswerRecord {
            return StudentAnswerRecord(
                questionId = json.optString("questionId"),
                questionText = json.optString("questionText"),
                selectedIndex = json.optInt("selectedIndex", -1),
                correctIndex = json.optInt("correctIndex", 0),
                isCorrect = json.optBoolean("isCorrect", false),
                explanation = json.optString("explanation"),
                topic = json.optString("topic")
            )
        }

        fun parseList(jsonString: String): List<StudentAnswerRecord> {
            if (jsonString.isBlank()) return emptyList()
            return try {
                val array = JSONArray(jsonString)
                val list = mutableListOf<StudentAnswerRecord>()
                for (i in 0 until array.length()) {
                    list.add(fromJson(array.getJSONObject(i)))
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun serializeList(records: List<StudentAnswerRecord>): String {
            val array = JSONArray()
            records.forEach { array.put(it.toJson()) }
            return array.toString()
        }
    }
}
