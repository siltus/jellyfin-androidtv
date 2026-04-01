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
import org.jellyfin.sdk.api.client.extensions.post
import timber.log.Timber
import java.util.UUID

sealed class RecycleResult {
	data object Success : RecycleResult()
	data class Failure(val error: Exception) : RecycleResult()
}

/**
 * Core recycle logic extracted for testability.
 * Advances to next track if available, waits for file handle release, then calls the API.
 */
internal suspend fun executeRecycle(
	apiCall: suspend (UUID) -> Unit,
	mediaManager: MediaManager,
	itemId: UUID,
	hasNext: Boolean,
	delayMs: Long = 500,
): RecycleResult {
	if (hasNext) {
		mediaManager.nextAudioItem()
	}

	delay(delayMs)

	return try {
		apiCall(itemId)
		RecycleResult.Success
	} catch (error: Exception) {
		RecycleResult.Failure(error)
	}
}

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
	val result = executeRecycle(
		apiCall = { id ->
			withContext(Dispatchers.IO) {
				api.post<Unit>(
					pathTemplate = "/RecycleBin/{itemId}",
					pathParameters = mapOf("itemId" to id),
				)
			}
		},
		mediaManager = mediaManager,
		itemId = itemId,
		hasNext = hasNext,
	)

	when (result) {
		is RecycleResult.Success -> {
			Toast.makeText(context, getString(R.string.item_deleted, itemName), Toast.LENGTH_SHORT).show()
			if (!hasNext) {
				if (navigationRepository.canGoBack) navigationRepository.goBack()
				else navigationRepository.navigate(Destinations.home)
			}
		}
		is RecycleResult.Failure -> {
			Timber.e(result.error, "Failed to recycle item $itemName (id=$itemId)")
			Toast.makeText(context, getString(R.string.item_deletion_failed, itemName), Toast.LENGTH_LONG).show()
		}
	}
}

fun AudioNowPlayingFragment.moveCurrentItemToFolder(
	api: ApiClient,
	mediaManager: MediaManager,
	navigationRepository: NavigationRepository,
) {
	val item = mediaManager.currentAudioItem ?: return
	val itemName = item.name ?: "Unknown"
	val itemId = item.id
	val hasNext = mediaManager.hasNextAudioItem()

	AlertDialog.Builder(requireContext())
		.setTitle("2nd Round")
		.setMessage("Move this item to the 2nd Round folder?")
		.setPositiveButton("2nd Round") { _, _ ->
			performMoveToFolder(api, mediaManager, navigationRepository, itemId, itemName, hasNext)
		}
		.setNegativeButton(android.R.string.cancel, null)
		.show()
}

private fun AudioNowPlayingFragment.performMoveToFolder(
	api: ApiClient,
	mediaManager: MediaManager,
	navigationRepository: NavigationRepository,
	itemId: UUID,
	itemName: String,
	hasNext: Boolean,
) = lifecycleScope.launch {
	val result = executeRecycle(
		apiCall = { id ->
			withContext(Dispatchers.IO) {
				api.post<Unit>(
					pathTemplate = "/MoveToFolder/{itemId}",
					pathParameters = mapOf("itemId" to id),
				)
			}
		},
		mediaManager = mediaManager,
		itemId = itemId,
		hasNext = hasNext,
	)

	when (result) {
		is RecycleResult.Success -> {
			Toast.makeText(context, "Moved $itemName to 2nd Round", Toast.LENGTH_SHORT).show()
			if (!hasNext) {
				if (navigationRepository.canGoBack) navigationRepository.goBack()
				else navigationRepository.navigate(Destinations.home)
			}
		}
		is RecycleResult.Failure -> {
			Timber.e(result.error, "Failed to move item $itemName (id=$itemId)")
			Toast.makeText(context, "Failed to move $itemName", Toast.LENGTH_LONG).show()
		}
	}
}
