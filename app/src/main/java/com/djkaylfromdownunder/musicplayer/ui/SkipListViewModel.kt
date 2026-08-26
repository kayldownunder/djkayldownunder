package com.djkaylfromdownunder.musicplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.djkaylfromdownunder.musicplayer.data.SkipListRepository

class SkipListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SkipListRepository(application)

    fun isSkipped(folderUri: String, trackUri: String) = repository.isSkipped(folderUri, trackUri)

    fun setSkipped(folderUri: String, trackUri: String, skipped: Boolean) {
        repository.setSkipped(folderUri, trackUri, skipped)
    }

    /** folderUri -> set of skipped track URIs, for the Review Skip Items screen. */
    fun allSkips(): Map<String, Set<String>> = repository.allSkips()
}
