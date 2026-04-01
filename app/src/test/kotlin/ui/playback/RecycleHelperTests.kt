package org.jellyfin.androidtv.ui.playback

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID

class RecycleHelperTests : FunSpec({

	test("executeRecycle advances to next track when hasNext is true") {
		val mediaManager = mockk<MediaManager>(relaxed = true)
		val itemId = UUID.randomUUID()

		val result = executeRecycle(
			apiCall = { },
			mediaManager = mediaManager,
			itemId = itemId,
			hasNext = true,
			delayMs = 0,
		)

		result.shouldBeInstanceOf<RecycleResult.Success>()
		verify(exactly = 1) { mediaManager.nextAudioItem() }
	}

	test("executeRecycle does not advance track when hasNext is false") {
		val mediaManager = mockk<MediaManager>(relaxed = true)
		val itemId = UUID.randomUUID()

		val result = executeRecycle(
			apiCall = { },
			mediaManager = mediaManager,
			itemId = itemId,
			hasNext = false,
			delayMs = 0,
		)

		result.shouldBeInstanceOf<RecycleResult.Success>()
		verify(exactly = 0) { mediaManager.nextAudioItem() }
	}

	test("executeRecycle calls API with correct itemId") {
		val mediaManager = mockk<MediaManager>(relaxed = true)
		val itemId = UUID.randomUUID()
		var receivedId: UUID? = null

		val result = executeRecycle(
			apiCall = { id -> receivedId = id },
			mediaManager = mediaManager,
			itemId = itemId,
			hasNext = false,
			delayMs = 0,
		)

		result.shouldBeInstanceOf<RecycleResult.Success>()
		receivedId shouldBe itemId
	}

	test("executeRecycle returns Failure when API call throws") {
		val mediaManager = mockk<MediaManager>(relaxed = true)
		val itemId = UUID.randomUUID()
		val exception = RuntimeException("Network error")

		val result = executeRecycle(
			apiCall = { throw exception },
			mediaManager = mediaManager,
			itemId = itemId,
			hasNext = false,
			delayMs = 0,
		)

		val failure = result.shouldBeInstanceOf<RecycleResult.Failure>()
		failure.error shouldBe exception
	}

	test("executeRecycle advances track before calling API even when API fails") {
		val mediaManager = mockk<MediaManager>(relaxed = true)
		val itemId = UUID.randomUUID()
		var apiCalled = false

		val result = executeRecycle(
			apiCall = {
				apiCalled = true
				throw RuntimeException("Server error")
			},
			mediaManager = mediaManager,
			itemId = itemId,
			hasNext = true,
			delayMs = 0,
		)

		result.shouldBeInstanceOf<RecycleResult.Failure>()
		verify(exactly = 1) { mediaManager.nextAudioItem() }
		apiCalled shouldBe true
	}

	test("executeRecycle returns Success when API call succeeds") {
		val mediaManager = mockk<MediaManager>(relaxed = true)
		val itemId = UUID.randomUUID()

		val result = executeRecycle(
			apiCall = { },
			mediaManager = mediaManager,
			itemId = itemId,
			hasNext = true,
			delayMs = 0,
		)

		result shouldBe RecycleResult.Success
	}

	test("executeRecycle calls nextAudioItem before apiCall") {
		val mediaManager = mockk<MediaManager>(relaxed = true)
		val callOrder = mutableListOf<String>()

		every { mediaManager.nextAudioItem() } answers {
			callOrder.add("next")
			0
		}

		executeRecycle(
			apiCall = { callOrder.add("api") },
			mediaManager = mediaManager,
			itemId = UUID.randomUUID(),
			hasNext = true,
			delayMs = 0,
		)

		callOrder shouldBe listOf("next", "api")
	}

	test("executeRecycle handles multiple sequential calls independently") {
		val mediaManager = mockk<MediaManager>(relaxed = true)
		val id1 = UUID.randomUUID()
		val id2 = UUID.randomUUID()
		val receivedIds = mutableListOf<UUID>()

		executeRecycle(
			apiCall = { receivedIds.add(it) },
			mediaManager = mediaManager,
			itemId = id1,
			hasNext = true,
			delayMs = 0,
		)

		executeRecycle(
			apiCall = { receivedIds.add(it) },
			mediaManager = mediaManager,
			itemId = id2,
			hasNext = false,
			delayMs = 0,
		)

		receivedIds shouldBe listOf(id1, id2)
		verify(exactly = 1) { mediaManager.nextAudioItem() }
	}
})
