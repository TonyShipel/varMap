package com.example.varmap.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "points")
data class PointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double
)


data class PointDto(
    val id: Long?,
    val name: String,
    val latitude: Double,
    val longitude: Double
)

fun PointEntity.toDto() = PointDto(
    id = if (id == 0L) null else id,
    name = name,
    latitude = latitude,
    longitude = longitude
)

fun PointDto.toEntity() = PointEntity(
    id = id ?: 0,
    name = name,
    latitude = latitude,
    longitude = longitude
)
