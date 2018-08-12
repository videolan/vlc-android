package org.videolan.vlc.database.models

import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey

@Entity()
data class CustomDirectory(
        @PrimaryKey
        val path: String
)

