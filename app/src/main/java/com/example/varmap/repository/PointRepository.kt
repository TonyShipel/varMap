package com.example.varmap.repository

import com.example.varmap.data.*
import com.example.varmap.network.ApiService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PointRepository(
    private val dao: PointDao,
    private val api: ApiService,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {
    val pointsFlow: Flow<List<PointEntity>> = dao.getAllFlow()

    suspend fun addLocal(point: PointEntity): Long = withContext(io) {
        dao.insert(point)
    }


    suspend fun syncDown() = withContext(io) {
        val remote = api.getPoints().map { it.toEntity() }
        dao.clear()
        dao.insertAll(remote)
    }


    suspend fun syncUp() = withContext(io) {
        val local = dao.getAll().map { it.toDto() }
        val updated = api.upsertPoints(local).map { it.toEntity() }
        dao.clear()
        dao.insertAll(updated)
    }
}
