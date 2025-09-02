package com.example.varmap.network

import com.example.varmap.data.PointDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {
    @GET("points")
    suspend fun getPoints(): List<PointDto>

    @POST("points")
    suspend fun upsertPoints(@Body points: List<PointDto>): List<PointDto>
}
