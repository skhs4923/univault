package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import org.json.JSONArray
import org.json.JSONObject

@IgnoreExtraProperties
data class Quiz(
    @DocumentId val id: String = "",
    val userId: String = "",
    val collegeId: String = "",
    val title: String = "",
    val subject: String = "",
    val courseCode: String = "",
    val positiveMarks: Int = 4,
    val negativeMarks: Double = 1.0,
    val durationMinutes: Int = 20,
    val questionsCount: Int = 5,
    val questionsJson: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class QuizQuestion(
    val id: String = "",
    val questionText: String = "",
    val options: List<String> = emptyList(),
    val correctIndex: Int = 0,
    val explanation: String = "",
    val topic: String = ""
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("questionText", questionText)
            put("options", JSONArray(options))
            put("correctIndex", correctIndex)
            put("explanation", explanation)
            put("topic", topic)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): QuizQuestion {
            val optsArray = json.optJSONArray("options") ?: JSONArray()
            val opts = mutableListOf<String>()
            for (i in 0 until optsArray.length()) {
                opts.add(optsArray.optString(i))
            }
            return QuizQuestion(
                id = json.optString("id"),
                questionText = json.optString("questionText"),
                options = opts,
                correctIndex = json.optInt("correctIndex", 0),
                explanation = json.optString("explanation"),
                topic = json.optString("topic")
            )
        }

        fun parseList(jsonString: String): List<QuizQuestion> {
            if (jsonString.isBlank()) return emptyList()
            return try {
                val array = JSONArray(jsonString)
                val list = mutableListOf<QuizQuestion>()
                for (i in 0 until array.length()) {
                    list.add(fromJson(array.getJSONObject(i)))
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun serializeList(questions: List<QuizQuestion>): String {
            val array = JSONArray()
            questions.forEach { array.put(it.toJson()) }
            return array.toString()
        }
    }
}
