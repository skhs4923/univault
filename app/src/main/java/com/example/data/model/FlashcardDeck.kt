package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@IgnoreExtraProperties
data class FlashcardDeck(
    @DocumentId val id: String = "",
    val userId: String = "",
    val collegeId: String = "",
    val title: String = "",
    val subject: String = "",
    val courseCode: String = "",
    val sourceNoteId: String = "",
    val sourceNoteTitle: String = "",
    val sourceExcerpt: String = "",
    val cardsCount: Int = 0,
    val masteredCount: Int = 0,
    val cardsJson: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class FlashcardItem(
    val id: String = UUID.randomUUID().toString(),
    val front: String = "",
    val back: String = "",
    val hint: String = "",
    val topic: String = "",
    val isMastered: Boolean = false,
    val reviewCount: Int = 0
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("front", front)
            put("back", back)
            put("hint", hint)
            put("topic", topic)
            put("isMastered", isMastered)
            put("reviewCount", reviewCount)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): FlashcardItem {
            return FlashcardItem(
                id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                front = json.optString("front").trim(),
                back = json.optString("back").trim(),
                hint = json.optString("hint").trim(),
                topic = json.optString("topic").trim(),
                isMastered = json.optBoolean("isMastered", false),
                reviewCount = json.optInt("reviewCount", 0)
            )
        }

        fun parseList(jsonString: String): List<FlashcardItem> {
            if (jsonString.isBlank()) return emptyList()
            return try {
                val array = JSONArray(jsonString)
                val list = mutableListOf<FlashcardItem>()
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val card = fromJson(obj)
                    if (card.front.isNotBlank() && card.back.isNotBlank()) {
                        list.add(card)
                    }
                }
                list
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun serializeList(cards: List<FlashcardItem>): String {
            val array = JSONArray()
            cards.forEach { array.put(it.toJson()) }
            return array.toString()
        }
    }
}
