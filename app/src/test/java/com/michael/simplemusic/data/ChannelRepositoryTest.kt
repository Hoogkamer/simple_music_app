package com.michael.simplemusic.data

import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

class ChannelRepositoryTest {

    @Mock
    private lateinit var dao: AudioChannelDao
    
    private lateinit var repository: ChannelRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = ChannelRepository(dao)
    }

    @Test
    fun getChannelByIdDelegatesToDao() = runBlocking {
        repository.getChannelById(1)
        verify(dao).getChannelById(1)
    }

    @Test
    fun insertChannelDelegatesToDao() = runBlocking {
        val channel = AudioChannel(name = "Test")
        repository.insertChannel(channel)
        verify(dao).insertChannel(channel)
    }

    @Test
    fun updateChannelDelegatesToDao() = runBlocking {
        val channel = AudioChannel(id = 1, name = "Test")
        repository.updateChannel(channel)
        verify(dao).updateChannel(channel)
    }

    @Test
    fun deleteChannelDelegatesToDao() = runBlocking {
        val channel = AudioChannel(id = 1, name = "Test")
        repository.deleteChannel(channel)
        verify(dao).deleteChannel(channel)
    }

    @Test
    fun getChannelCountDelegatesToDao() = runBlocking {
        repository.getChannelCount()
        verify(dao).getChannelCount()
    }
}
