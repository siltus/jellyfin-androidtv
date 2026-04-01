package org.jellyfin.androidtv.ui.playback

import android.app.AlertDialog
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.post
import timber.log.Timber
import java.util.UUID

fun AudioNowPlayingFragment.recycleCurrentItem(
	api: ApiClient,
	mediaManager: MediaManager,
	navigationRepository: NavigationRepository,
) {
	val item = mediaManager.currentAudioItem ?: return
	val itemName = item.name ?: "Unknown"
	val itemId = item.id
	val hasNext = mediaManager.hasNextAudioItem()

	AlertDialog.Builder(requireContext())
		.setTitle(R.string.item_delete_confirm_title)
		.setMessage(getString(R.string.item_delete_confirm_message))
		.setPositiveButton(R.string.lbl_delete) { _, _ ->
			performRecycle(api, mediaManager, navigationRepository, itemId, itemName, hasNext)
		}
		.setNegativeButton(android.R.string.cancel, null)
		.show()
}

private fun AudioNowPlayingFragment.performRecycle(
	api: ApiClient,
	mediaManager: MediaManager,
	navigationRepository: NavigationRepository,
	itemId: UUID,
	itemName: String,
	hasNext: Boolean,
) = lifecycleScope.launch {
	// Advance to next track (or stop) before deleting to release file handle
	if (hasNext) {
		mediaManager.nextAudioItem()
	}

	// Wait for file handle release
	delay(500)

	try {
		withContext(Dispatchers.IO) {
			api.post<Unit>(
				pathTemplate = "/RecycleBin/{itemId}",
				pathParameters = mapOf("itemId" to itemId),
			)
		}
	} catch (error: ApiClientException) {
		Timber.e(error, "Failed to recycle item $itemName (id=$itemId)")
		Toast.makeText(
			context,
			getString(R.string.item_deletion_failed, itemName),
			Toast.LENGTH_LONG
		).show()
		return@launch
	}

	Toast.makeText(context, getString(R.string.item_deleted, itemName), Toast.LENGTH_SHORT).show()

	if (!hasNext) {
		if (navigationRepository.canGoBack) navigationRepository.goBack()
		else navigationRepository.navigate(Destinations.home)
	}
}
