package com.michael.simplemusic.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromChannelType(value: ChannelType): String {
        return value.name
    }

    @TypeConverter
    fun toChannelType(value: String): ChannelType {
        return ChannelType.valueOf(value)
    }
}
