package com.asphalt.app.data

import androidx.room.*
import com.asphalt.app.model.ZoneItem
import com.asphalt.app.model.ZoneType
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "zones")
data class ZoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val governorate: String,
    val type: String, // PREFERRED or AVOIDED
    val notes: String = "",
    val radiusKm: Double = 3.0
) {
    fun toDomain(): ZoneItem = ZoneItem(
        id = id,
        name = name,
        governorate = governorate,
        type = if (type == "PREFERRED") ZoneType.PREFERRED else ZoneType.AVOIDED,
        notes = notes,
        radiusKm = radiusKm
    )

    companion object {
        fun fromDomain(item: ZoneItem): ZoneEntity = ZoneEntity(
            id = item.id,
            name = item.name,
            governorate = item.governorate,
            type = item.type.name,
            notes = item.notes,
            radiusKm = item.radiusKm
        )
    }
}

@Dao
interface ZoneDao {
    @Query("SELECT * FROM zones ORDER BY id DESC")
    fun getAllZones(): Flow<List<ZoneEntity>>

    @Query("SELECT * FROM zones WHERE type = :type ORDER BY id DESC")
    fun getZonesByType(type: String): Flow<List<ZoneEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertZone(zone: ZoneEntity): Long

    @Delete
    suspend fun deleteZone(zone: ZoneEntity)
}
