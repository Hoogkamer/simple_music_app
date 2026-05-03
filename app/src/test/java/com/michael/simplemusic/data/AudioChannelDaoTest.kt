package com.michael.simplemusic.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AudioChannelDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: AudioChannelDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.audioChannelDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndGetChannel() = runBlocking {
        val channel = AudioChannel(name = "Test Channel", type = ChannelType.FOLDER)
        val id = dao.insertChannel(channel)
        
        val retrieved = dao.getChannelById(id.toInt())
        assertNotNull(retrieved)
        assertEquals("Test Channel", retrieved?.name)
        assertEquals(ChannelType.FOLDER, retrieved?.type)
    }

    @Test
    fun updateChannel() = runBlocking {
        val channel = AudioChannel(name = "Original Name")
        val id = dao.insertChannel(channel)
        
        val toUpdate = dao.getChannelById(id.toInt())!!.copy(name = "Updated Name")
        dao.updateChannel(toUpdate)
        
        val retrieved = dao.getChannelById(id.toInt())
        assertEquals("Updated Name", retrieved?.name)
    }

    @Test
    fun deleteChannel() = runBlocking {
        val channel = AudioChannel(name = "To Delete")
        val id = dao.insertChannel(channel)
        
        dao.deleteChannel(dao.getChannelById(id.toInt())!!)
        
        val retrieved = dao.getChannelById(id.toInt())
        assertNull(retrieved)
    }

    @Test
    fun getAllChannelsSortedByLastPlayed() = runBlocking {
        val c1 = AudioChannel(name = "Old", lastPlayedTime = 1000)
        val c2 = AudioChannel(name = "New", lastPlayedTime = 2000)
        
        dao.insertChannel(c1)
        dao.insertChannel(c2)
        
        val all = dao.getAllChannels().first()
        assertEquals(2, all.size)
        assertEquals("New", all[0].name) // Should be first because of DESC sort
        assertEquals("Old", all[1].name)
    }
}
