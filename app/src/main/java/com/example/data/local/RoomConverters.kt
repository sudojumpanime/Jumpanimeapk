package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.Season
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RoomConverters {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true; coerceInputValues = true }

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromSeasonList(value: List<Season>): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toSeasonList(value: String): List<Season> {
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
