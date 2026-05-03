package com.michael.simplemusic.data

import kotlinx.coroutines.flow.Flow

class ChannelRepository(private val dao: AudioChannelDao) {

    val allChannels: Flow<List<AudioChannel>> = dao.getAllChannels()

    suspend fun getChannelById(id: Int): AudioChannel? = dao.getChannelById(id)

    suspend fun insertChannel(channel: AudioChannel): Long = dao.insertChannel(channel)

    suspend fun updateChannel(channel: AudioChannel) = dao.updateChannel(channel)

    suspend fun deleteChannel(channel: AudioChannel) = dao.deleteChannel(channel)

    suspend fun getChannelCount(): Int = dao.getChannelCount()
}
