package org.videolan.vlc.viewmodels.audio

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.withContext
import org.videolan.vlc.util.ModelsHelper


class Genresprovider(private val sections: Boolean = true): AudioModel() {

    override suspend fun updateList() {
        dataset.value = withContext(CommonPool) {
            val array = medialibrary.getGenres(sort, desc)
            (if (sections) ModelsHelper.generateSections(sort, array) else array.toList()).toMutableList()
        }
    }

    class Factory(private val sections: Boolean): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return Genresprovider(sections) as T
        }
    }
}