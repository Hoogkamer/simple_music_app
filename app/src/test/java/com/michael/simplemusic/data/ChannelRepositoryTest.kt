package com.michael.simplemusic.data

import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class ChannelRepositoryTest {

    @Mock
    private lateinit var dao: AudioChannelDao
    
    private lateinit var repository: ChannelRepository

    @Before
    fun setup() {
        repository = ChannelRepository(dao)
    }

    @Test
    fun getChannelByIdDelegatesToDao() = runTest {
        repository.getChannelById(1)
        verify(dao).getChannelById(1)
    }

    @Test
    fun insertChannelDelegatesToDao() = runTest {
        val channel = AudioChannel(name = "Test")
        repository.insertChannel(channel)
        verify(dao).insertChannel(channel)
    }

    @Test
    fun updateChannelDelegatesToDao() = runTest {
        val channel = AudioChannel(id = 1, name = "Test")
        repository.updateChannel(channel)
        verify(dao).updateChannel(channel)
    }

    @Test
    fun deleteChannelDelegatesToDao() = runTest {
        val channel = AudioChannel(id = 1, name = "Test")
        repository.deleteChannel(channel)
        verify(dao).deleteChannel(channel)
    }

    @Test
    fun getChannelCountDelegatesToDao() = runTest {
        repository.getChannelCount()
        verify(dao).getChannelCount()
    }
}
