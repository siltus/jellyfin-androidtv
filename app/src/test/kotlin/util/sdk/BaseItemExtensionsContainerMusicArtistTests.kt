package org.jellyfin.androidtv.util.sdk

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.UUID

/**
 * Regression tests for [isContainerMusicArtist].
 *
 * Container artists are filesystem directories that Jellyfin auto-classifies
 * as MusicArtist because they live directly under a music-type library, but
 * whose children are sub-folders rather than albums. The classic example:
 * `E:\Music\Festivals 2026\<per-artist-sub-folders>`.
 *
 * Such items have `childCount == 0` and zero tracks credited to them, so
 * routing them through artist-specific code paths (artist details page,
 * shuffle by `artistIds`) yields an empty page or a "no media source" error.
 * [isContainerMusicArtist] is the predicate every such code path uses to
 * decide whether to treat the item as a folder instead.
 */
class BaseItemExtensionsContainerMusicArtistTests : FunSpec({

	fun item(
		type: BaseItemKind,
		isFolder: Boolean? = null,
		childCount: Int? = null,
	) = BaseItemDto(
		id = UUID.randomUUID(),
		type = type,
		isFolder = isFolder,
		childCount = childCount,
	)

	test("returns true for a MusicArtist with childCount = 0 (container directory)") {
		item(BaseItemKind.MUSIC_ARTIST, isFolder = true, childCount = 0)
			.isContainerMusicArtist() shouldBe true
	}

	test("returns true for a MusicArtist with null childCount (server omitted the field)") {
		item(BaseItemKind.MUSIC_ARTIST, isFolder = true, childCount = null)
			.isContainerMusicArtist() shouldBe true
	}

	test("returns false for a real MusicArtist with album credits (childCount > 0)") {
		// e.g. 70000 Tons Of Metal with 65 albums - must keep using artistIds
		item(BaseItemKind.MUSIC_ARTIST, isFolder = true, childCount = 65)
			.isContainerMusicArtist() shouldBe false
	}

	test("returns false for a non-folder MusicArtist") {
		// Defensive: real artists are folders, but if the server ever returns
		// isFolder=false on a MusicArtist treat it as a real artist.
		item(BaseItemKind.MUSIC_ARTIST, isFolder = false, childCount = 0)
			.isContainerMusicArtist() shouldBe false
	}

	test("returns false for a MusicAlbum even with childCount = 0") {
		// Albums route through their own dedicated code paths; never apply the
		// container-artist fallback to them.
		item(BaseItemKind.MUSIC_ALBUM, isFolder = true, childCount = 0)
			.isContainerMusicArtist() shouldBe false
	}

	test("returns false for a generic Folder") {
		item(BaseItemKind.FOLDER, isFolder = true, childCount = 0)
			.isContainerMusicArtist() shouldBe false
	}

	test("returns false for a CollectionFolder (a top-level library)") {
		item(BaseItemKind.COLLECTION_FOLDER, isFolder = true, childCount = 0)
			.isContainerMusicArtist() shouldBe false
	}
})
