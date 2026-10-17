package com.novelarrow.reader.data.repository

import android.content.Context
import com.novelarrow.reader.data.model.Chapter
import com.novelarrow.reader.data.model.Novel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Adversarial Challenger 1 Test Suite for Milestone M1.
 * Stress-tests:
 * 1. Sibling subfolder isolation under repeated refresh cycles & identical file names.
 * 2. Deeply nested directory hierarchies and dynamic additions.
 * 3. Safe deletion blast radius (strict preservation of parent and sibling directories).
 * 4. Special character directory names & empty directory handling.
 * 5. Concurrent / repeated refresh idempotency.
 */
class Milestone1Challenger1Test {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        LibraryRepository.clearCacheForTesting()
    }

    @After
    fun tearDown() {
        LibraryRepository.clearCacheForTesting()
    }

    private fun createMockContext(filesDir: File): Context {
        return object : android.content.ContextWrapper(null) {
            override fun getFilesDir(): File = filesDir
            override fun getExternalFilesDir(type: String?): File = filesDir
            override fun getApplicationContext(): Context = this
        }
    }

    // =========================================================================
    // 1. Sibling Subfolders Isolation Under Repeated Multi-Cycle Refreshes
    // =========================================================================

    @Test
    fun testSiblingSubfolders_repeatedMultiCycleRefreshes_absoluteIsolation() = runBlocking {
        val appDir = tempFolder.newFolder("app_data_siblings")
        val context = createMockContext(appDir)
        val repository = LibraryRepository(context)

        val rootDir = tempFolder.newFolder("MultiBookSeries")
        val vol1Dir = File(rootDir, "Volume 1").apply { mkdir() }
        val vol2Dir = File(rootDir, "Volume 2").apply { mkdir() }
        val vol3Dir = File(rootDir, "Volume 3").apply { mkdir() }

        File(vol1Dir, "01.txt").writeText("V1 C1")
        File(vol1Dir, "02.txt").writeText("V1 C2")

        File(vol2Dir, "01.txt").writeText("V2 C1")
        File(vol2Dir, "02.txt").writeText("V2 C2")

        File(vol3Dir, "01.txt").writeText("V3 C1")
        File(vol3Dir, "02.txt").writeText("V3 C2")

        File(rootDir, "root_readme.txt").writeText("Root Readme")

        // Initial scan
        val initialNovels = repository.scanRootDirectory(rootDir.absolutePath)
        assertEquals(4, initialNovels.size)

        val n1 = initialNovels.first { it.title == "Volume 1" }
        val n2 = initialNovels.first { it.title == "Volume 2" }
        val n3 = initialNovels.first { it.title == "Volume 3" }
        val nLoose = initialNovels.first { it.isLooseRootFiles }

        // Set reading progress on Volume 1 (chapter 1, paragraph 15) and Volume 2 (chapter 0, paragraph 42)
        repository.updateChapterProgress(n1.id, chapterIndex = 1, paragraphIndex = 15, scrollOffset = 0, isCompleted = false)
        repository.updateChapterProgress(n2.id, chapterIndex = 0, paragraphIndex = 42, scrollOffset = 0, isCompleted = true)

        // --- CYCLE 1: Asymmetric changes across siblings ---
        // Add 03.txt to Volume 1
        File(vol1Dir, "03.txt").writeText("V1 C3")
        // Remove 02.txt from Volume 2
        File(vol2Dir, "02.txt").delete()
        // Add 03.txt to Volume 3
        File(vol3Dir, "03.txt").writeText("V3 C3")
        // Add second loose file to root
        File(rootDir, "root_notes.txt").writeText("Root Notes")

        val refreshedC1 = repository.refreshAllLinkedFolders()
        assertEquals(4, refreshedC1.size)

        val chaps1_c1 = repository.getChapters(n1.id)
        val chaps2_c1 = repository.getChapters(n2.id)
        val chaps3_c1 = repository.getChapters(n3.id)
        val chapsLoose_c1 = repository.getChapters(nLoose.id)

        // Volume 1: 3 chapters (01, 02, 03)
        assertEquals(3, chaps1_c1.size)
        assertTrue(chaps1_c1.all { it.fileUri.contains("Volume 1") && it.novelId == n1.id })
        assertFalse(chaps1_c1.any { it.fileUri.contains("Volume 2") || it.fileUri.contains("Volume 3") || it.fileUri.contains("root_") })

        // Volume 2: 1 chapter (01)
        assertEquals(1, chaps2_c1.size)
        assertTrue(chaps2_c1.all { it.fileUri.contains("Volume 2") && it.novelId == n2.id })
        assertFalse(chaps2_c1.any { it.fileUri.contains("Volume 1") || it.fileUri.contains("Volume 3") })

        // Volume 3: 3 chapters (01, 02, 03)
        assertEquals(3, chaps3_c1.size)
        assertTrue(chaps3_c1.all { it.fileUri.contains("Volume 3") && it.novelId == n3.id })

        // Loose root: 2 chapters (root_readme, root_notes)
        assertEquals(2, chapsLoose_c1.size)
        assertTrue(chapsLoose_c1.all { it.novelId == nLoose.id && !it.fileUri.contains("Volume") })

        // Progress check after Cycle 1
        val n1Refreshed = refreshedC1.first { it.id == n1.id }
        assertEquals(1, n1Refreshed.currentChapterIndex)
        assertEquals(15, chaps1_c1[1].lastReadParagraphIndex)

        val n2Refreshed = refreshedC1.first { it.id == n2.id }
        assertEquals(0, n2Refreshed.currentChapterIndex)
        assertTrue(chaps2_c1[0].isCompleted)
        assertEquals(42, chaps2_c1[0].lastReadParagraphIndex)

        // --- CYCLE 2: Identical file name added to all subfolders simultaneously ---
        File(vol1Dir, "Interlude.txt").writeText("V1 Interlude")
        File(vol2Dir, "Interlude.txt").writeText("V2 Interlude")
        File(vol3Dir, "Interlude.txt").writeText("V3 Interlude")

        val refreshedC2 = repository.refreshAllLinkedFolders()
        assertEquals(4, refreshedC2.size)

        val ch1_interlude = repository.getChapters(n1.id).firstOrNull { it.title == "Interlude" }
        val ch2_interlude = repository.getChapters(n2.id).firstOrNull { it.title == "Interlude" }
        val ch3_interlude = repository.getChapters(n3.id).firstOrNull { it.title == "Interlude" }

        assertNotNull("Volume 1 must have Interlude", ch1_interlude)
        assertNotNull("Volume 2 must have Interlude", ch2_interlude)
        assertNotNull("Volume 3 must have Interlude", ch3_interlude)

        // Each must have distinct IDs and point strictly to their own directories
        assertEquals(n1.id, ch1_interlude!!.novelId)
        assertEquals(n2.id, ch2_interlude!!.novelId)
        assertEquals(n3.id, ch3_interlude!!.novelId)

        assertTrue(ch1_interlude.fileUri.contains("Volume 1"))
        assertTrue(ch2_interlude.fileUri.contains("Volume 2"))
        assertTrue(ch3_interlude.fileUri.contains("Volume 3"))

        assertTrue("Chapter IDs across novels must never collide", ch1_interlude.id != ch2_interlude.id)
        assertTrue("Chapter IDs across novels must never collide", ch2_interlude.id != ch3_interlude.id)
        assertTrue("Chapter IDs across novels must never collide", ch1_interlude.id != ch3_interlude.id)

        // --- CYCLE 3: Progress isolation on identical file names ---
        // Read Volume 1 Interlude to paragraph 88
        val ch1_idx = repository.getChapters(n1.id).indexOfFirst { it.title == "Interlude" }
        repository.updateChapterProgress(n1.id, chapterIndex = ch1_idx, paragraphIndex = 88, scrollOffset = 0, isCompleted = true)

        repository.refreshAllLinkedFolders()

        val reCh1_interlude = repository.getChapters(n1.id).first { it.title == "Interlude" }
        val reCh2_interlude = repository.getChapters(n2.id).first { it.title == "Interlude" }

        assertTrue(reCh1_interlude.isCompleted)
        assertEquals(88, reCh1_interlude.lastReadParagraphIndex)

        // Volume 2 Interlude must NOT be affected by Volume 1 Interlude progress!
        assertFalse("Volume 2 Interlude must remain unread", reCh2_interlude.isCompleted)
        assertEquals(0, reCh2_interlude.lastReadParagraphIndex)

        // --- CYCLE 4: Full re-scan of root directory (Idempotency) ---
        val rescanNovels = repository.scanRootDirectory(rootDir.absolutePath)
        assertEquals("Re-scan must not duplicate novels", 4, rescanNovels.size)

        val rescanN1 = rescanNovels.first { it.title == "Volume 1" }
        assertEquals(n1.id, rescanN1.id)
        val rescanChaps1 = repository.getChapters(n1.id)
        val rescanInterlude = rescanChaps1.first { it.title == "Interlude" }
        assertTrue("Reading progress preserved across re-scan", rescanInterlude.isCompleted)
        assertEquals(88, rescanInterlude.lastReadParagraphIndex)
    }

    // =========================================================================
    // 2. Deeply Nested Subfolder Hierarchies & Dynamic Additions
    // =========================================================================

    @Test
    fun testDeeplyNestedSubfolders_scopedRefreshAndIsolation() = runBlocking {
        val appDir = tempFolder.newFolder("app_data_nested")
        val context = createMockContext(appDir)
        val repository = LibraryRepository(context)

        val rootDir = tempFolder.newFolder("NestedSeries")
        val vol1 = File(rootDir, "Volume 1").apply { mkdir() }
        val vol2 = File(rootDir, "Volume 2").apply { mkdir() }

        val arc1 = File(vol1, "Arc 1").apply { mkdir() }
        val act1 = File(arc1, "Act 1").apply { mkdir() }
        val act2 = File(arc1, "Act 2").apply { mkdir() }
        val arc2 = File(vol1, "Arc 2").apply { mkdir() }

        val vol2Bonus = File(vol2, "Bonus").apply { mkdir() }

        File(act1, "001 - Awakening.txt").writeText("Act 1 Chapter 1")
        File(act2, "002 - Challenge.txt").writeText("Act 2 Chapter 2")
        File(arc2, "003 - Climax.txt").writeText("Arc 2 Chapter 3")

        File(vol2Bonus, "Bonus Story.txt").writeText("Bonus content")
        File(vol2, "001 - Prologue.txt").writeText("Volume 2 Prologue")

        // Scan root
        val novels = repository.scanRootDirectory(rootDir.absolutePath)
        assertEquals(2, novels.size)

        val novel1 = novels.first { it.title == "Volume 1" }
        val novel2 = novels.first { it.title == "Volume 2" }

        val chaps1 = repository.getChapters(novel1.id)
        val chaps2 = repository.getChapters(novel2.id)

        assertEquals("Volume 1 must contain all 3 nested chapters", 3, chaps1.size)
        assertEquals("Volume 2 must contain both chapters", 2, chaps2.size)

        // All chapters in Volume 1 must point inside Volume 1 path
        assertTrue(chaps1.all { it.fileUri.contains("Volume 1") && it.fileUri.startsWith(vol1.absolutePath) })
        assertFalse(chaps1.any { it.fileUri.contains("Volume 2") })

        // All chapters in Volume 2 must point inside Volume 2 path
        assertTrue(chaps2.all { it.fileUri.contains("Volume 2") && it.fileUri.startsWith(vol2.absolutePath) })
        assertFalse(chaps2.any { it.fileUri.contains("Volume 1") })

        // Add deeply nested files dynamically
        val act3 = File(arc1, "Act 3").apply { mkdir() }
        File(act3, "004 - Resolution.txt").writeText("Act 3 Chapter 4")

        val deepBonus = File(vol2Bonus, "SubBonus").apply { mkdir() }
        File(deepBonus, "Deep Extra.txt").writeText("Deep Extra")

        // Trigger scoped refresh
        val refreshed = repository.refreshAllLinkedFolders()
        assertEquals(2, refreshed.size)

        val refreshedChaps1 = repository.getChapters(novel1.id)
        val refreshedChaps2 = repository.getChapters(novel2.id)

        assertEquals("Volume 1 should now have 4 chapters", 4, refreshedChaps1.size)
        assertEquals("Volume 2 should now have 3 chapters", 3, refreshedChaps2.size)

        assertTrue(refreshedChaps1.any { it.title == "004 - Resolution" })
        assertTrue(refreshedChaps2.any { it.title == "Deep Extra" })

        // Verify isolation is preserved
        assertTrue(refreshedChaps1.all { it.fileUri.startsWith(vol1.absolutePath) })
        assertTrue(refreshedChaps2.all { it.fileUri.startsWith(vol2.absolutePath) })
    }

    // =========================================================================
    // 3. Safe Deletion Blast Radius: Strict Preservation of Parents & Siblings
    // =========================================================================

    @Test
    fun testSafeDeletion_strictBlastRadius_preservesParentAndSiblings() = runBlocking {
        val appDir = tempFolder.newFolder("app_data_blast_radius")
        val context = createMockContext(appDir)
        val repository = LibraryRepository(context)

        // Setup outer parent containing unrelated novel and current target series
        val topParent = tempFolder.newFolder("TopLibrary")
        val outsideSeries = File(topParent, "IndependentNovel").apply { mkdir() }
        File(outsideSeries, "standalone.txt").writeText("Standalone Novel")

        val targetSeries = File(topParent, "TargetSeries").apply { mkdir() }
        val subA = File(targetSeries, "Volume A").apply { mkdir() }
        val subB = File(targetSeries, "Volume B").apply { mkdir() }
        val subC = File(targetSeries, "Volume C").apply { mkdir() }

        File(subA, "chA1.txt").writeText("A1")
        File(subA, "chA2.txt").writeText("A2")
        File(subB, "chB1.txt").writeText("B1")
        File(subB, "chB2.txt").writeText("B2")
        File(subC, "chC1.txt").writeText("C1")

        val looseFile1 = File(targetSeries, "series_intro.txt").apply { writeText("Intro") }
        val looseFile2 = File(targetSeries, "series_outro.txt").apply { writeText("Outro") }

        // Also scan the outside series into library
        val outsideNovels = repository.scanRootDirectory(outsideSeries.absolutePath)
        assertEquals(1, outsideNovels.size)
        val outsideNovel = outsideNovels.first()

        // Scan target series (3 subfolders + 1 loose)
        val allNovelsAfterTargetScan = repository.scanRootDirectory(targetSeries.absolutePath)
        val targetNovels = allNovelsAfterTargetScan.filter { it.rootTreeUri == targetSeries.absolutePath }
        assertEquals(4, targetNovels.size)

        val novelA = targetNovels.first { it.title == "Volume A" }
        val novelB = targetNovels.first { it.title == "Volume B" }
        val novelC = targetNovels.first { it.title == "Volume C" }
        val novelLoose = targetNovels.first { it.isLooseRootFiles }

        val allBeforeDelete = repository.getNovels()
        assertEquals(5, allBeforeDelete.size)

        // STEP 1: Delete Volume A with deleteFilesFromDisk = true
        repository.deleteNovel(novelA.id, deleteFilesFromDisk = true)

        // Assert Volume A directory is deleted
        assertFalse("Volume A directory must be deleted", subA.exists())

        // Assert Sibling Volume B, Volume C, and Loose files are 100% UNTOUCHED on disk
        assertTrue("Volume B directory must exist", subB.exists())
        assertTrue("Volume B file must exist", File(subB, "chB1.txt").exists())
        assertTrue("Volume C directory must exist", subC.exists())
        assertTrue("Volume C file must exist", File(subC, "chC1.txt").exists())
        assertTrue("TargetSeries parent directory must exist", targetSeries.exists())
        assertTrue("Loose file 1 must exist", looseFile1.exists())
        assertTrue("Loose file 2 must exist", looseFile2.exists())
        assertTrue("TopLibrary must exist", topParent.exists())
        assertTrue("Outside series must exist", outsideSeries.exists())
        assertTrue("Outside chapter must exist", File(outsideSeries, "standalone.txt").exists())

        // Assert DB state after Volume A deletion
        val dbAfterA = repository.getNovels()
        assertEquals(4, dbAfterA.size)
        assertNull("Volume A must not exist in DB", dbAfterA.firstOrNull { it.id == novelA.id })
        assertTrue("Volume A chapters must be removed", repository.getChapters(novelA.id).isEmpty())

        // STEP 2: Refresh library and verify deleted novel is NOT restored
        val refreshedAfterA = repository.refreshAllLinkedFolders()
        assertEquals(4, refreshedAfterA.size)
        assertNull("Volume A must not reappear after refresh", refreshedAfterA.firstOrNull { it.id == novelA.id })

        // STEP 3: Delete Loose novel (targetSeries Chapters) with deleteFilesFromDisk = true
        repository.deleteNovel(novelLoose.id, deleteFilesFromDisk = true)

        // Assert loose files are deleted from disk
        assertFalse("Loose file 1 must be deleted", looseFile1.exists())
        assertFalse("Loose file 2 must be deleted", looseFile2.exists())

        // Assert targetSeries directory and sibling volumes MUST NOT be deleted!
        assertTrue("Parent folder TargetSeries must NOT be deleted when loose novel is removed", targetSeries.exists())
        assertTrue("Volume B must still exist", subB.exists())
        assertTrue("Volume B files must still exist", File(subB, "chB1.txt").exists())
        assertTrue("Volume C must still exist", subC.exists())
        assertTrue("Volume C files must still exist", File(subC, "chC1.txt").exists())
        assertTrue("Outside series must still exist", outsideSeries.exists())

        // Assert DB state after loose novel deletion
        val dbAfterLoose = repository.getNovels()
        assertEquals(3, dbAfterLoose.size)
        assertNull(dbAfterLoose.firstOrNull { it.id == novelLoose.id })
        assertNotNull(dbAfterLoose.firstOrNull { it.id == novelB.id })
        assertNotNull(dbAfterLoose.firstOrNull { it.id == novelC.id })
        assertNotNull(dbAfterLoose.firstOrNull { it.id == outsideNovel.id })
    }

    // =========================================================================
    // 4. Special Characters, Empty Directories & Edge Cases
    // =========================================================================

    @Test
    fun testEmptySubfolders_and_SpecialCharacterNames() = runBlocking {
        val appDir = tempFolder.newFolder("app_data_special")
        val context = createMockContext(appDir)
        val repository = LibraryRepository(context)

        val rootDir = tempFolder.newFolder("SpecialCharsTest")

        // Subfolder with non-ASCII and special punctuation
        val specialDir = File(rootDir, "[Special] Volume (1) & 異世界 #Final").apply { mkdir() }
        File(specialDir, "001 - 始まり.txt").writeText("First chapter with CJK")

        // Empty subfolder
        val emptyDir = File(rootDir, "Empty Directory").apply { mkdir() }

        // Normal subfolder
        val normalDir = File(rootDir, "Normal Volume").apply { mkdir() }
        File(normalDir, "ch1.txt").writeText("Normal content")

        val novels = repository.scanRootDirectory(rootDir.absolutePath)

        // Empty directory containing 0 supported files should NOT create a novel
        assertEquals("Empty directory should be ignored, only 2 novels created", 2, novels.size)

        val specialNovel = novels.firstOrNull { it.title == "[Special] Volume (1) & 異世界 #Final" }
        assertNotNull("Novel with special characters must be imported", specialNovel)

        val specialChapters = repository.getChapters(specialNovel!!.id)
        assertEquals(1, specialChapters.size)
        assertEquals("001 - 始まり", specialChapters[0].title)

        // Test writing and reading progress for special character directory
        val progress = NovelFolderProgress(
            novelTitle = specialNovel.title,
            currentChapterIndex = 0,
            currentChapterTitle = "001 - 始まり",
            currentParagraphIndex = 19
        )
        repository.writeFolderProgress(specialNovel.subfolderPath ?: "", specialNovel.title, progress)

        val readProgress = repository.readFolderProgress(specialNovel.subfolderPath, specialNovel.title)
        assertNotNull(readProgress)
        assertEquals("001 - 始まり", readProgress!!.currentChapterTitle)
        assertEquals(19, readProgress.currentParagraphIndex)

        // Now add a chapter into the previously empty directory and refresh
        File(emptyDir, "new_chapter.txt").writeText("New content in previously empty folder")

        // Re-scanning root should now detect 3 novels
        val rescannedNovels = repository.scanRootDirectory(rootDir.absolutePath)
        assertEquals(3, rescannedNovels.size)
        assertTrue(rescannedNovels.any { it.title == "Empty Directory" })
    }

    // =========================================================================
    // 5. Concurrent / Rapid Repeated Refreshes: Idempotency & Thread Safety
    // =========================================================================

    @Test
    fun testConcurrentRefreshes_idempotencyAndConsistency() = runBlocking(Dispatchers.IO) {
        val appDir = tempFolder.newFolder("app_data_concurrent")
        val context = createMockContext(appDir)
        val repository = LibraryRepository(context)

        val rootDir = tempFolder.newFolder("ConcurrentTest")
        val v1 = File(rootDir, "Vol 1").apply { mkdir() }
        val v2 = File(rootDir, "Vol 2").apply { mkdir() }

        for (i in 1..5) {
            File(v1, "chap_%02d.txt".format(i)).writeText("Content V1 $i")
            File(v2, "chap_%02d.txt".format(i)).writeText("Content V2 $i")
        }

        repository.scanRootDirectory(rootDir.absolutePath)

        // Launch 10 concurrent refreshes
        val jobs = (1..10).map {
            async {
                repository.refreshAllLinkedFolders()
            }
        }
        val results = jobs.awaitAll()

        // All refreshes must complete cleanly and return exactly 2 novels
        for (novels in results) {
            assertEquals(2, novels.size)
        }

        // Verify database state remains intact and uncorrupted
        val finalNovels = repository.getNovels()
        assertEquals(2, finalNovels.size)

        val novel1 = finalNovels.first { it.title == "Vol 1" }
        val novel2 = finalNovels.first { it.title == "Vol 2" }

        val ch1 = repository.getChapters(novel1.id)
        val ch2 = repository.getChapters(novel2.id)

        assertEquals(5, ch1.size)
        assertEquals(5, ch2.size)

        assertTrue(ch1.all { it.novelId == novel1.id && it.fileUri.contains("Vol 1") })
        assertTrue(ch2.all { it.novelId == novel2.id && it.fileUri.contains("Vol 2") })
    }
}
