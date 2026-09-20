package com.k.hosken.djkayldownunder.ui

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import com.k.hosken.djkayldownunder.data.SkipListRepository

class SkipListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SkipListRepository(application)

    fun isSkipped(folderUri: String, trackUri: String) = repository.isSkipped(folderUri, trackUri)

    fun setSkipped(folderUri: String, trackUri: String, skipped: Boolean) {
        repository.setSkipped(folderUri, trackUri, skipped)
    }

    /** folderUri -> set of skipped track URIs, for the Review Skip Items screen. */
    fun allSkips(): Map<String, Set<String>> = repository.allSkips()

    /**
     * Permanently deletes a skipped track's file from device storage (used by the trash
     * icon in Review Skipped Songs - distinct from unchecking, which only clears its skip
     * flag and leaves the file alone). Also clears its skip flag either way, since there's
     * no point remembering to skip a file that no longer exists.
     */
    fun deleteSkippedTrack(folderUri: String, trackUri: Uri): Boolean {
        val deleted = DocumentFile.fromSingleUri(getApplication(), trackUri)?.delete() ?: false
        repository.setSkipped(folderUri, trackUri.toString(), false)
        return deleted
    }
}
