package com.michael.simplemusic.data

import kotlinx.coroutines.flow.Flow

class StateRepository(private val dao: ListeningStateDao) {

    val allStates: Flow<List<ListeningState>> = dao.getAllStates()

    suspend fun getStateById(id: Int): ListeningState? = dao.getStateById(id)

    suspend fun insertState(state: ListeningState): Long = dao.insertState(state)

    suspend fun updateState(state: ListeningState) = dao.updateState(state)

    suspend fun deleteState(state: ListeningState) = dao.deleteState(state)

    suspend fun getStateCount(): Int = dao.getStateCount()
}
