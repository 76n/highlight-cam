package com.highlightcam.app.ui.setup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.highlightcam.app.data.SessionRepository
import com.highlightcam.app.data.UserPreferencesRepository
import com.highlightcam.app.domain.GoalZoneSet
import com.highlightcam.app.domain.NormalizedPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var userPrefsRepo: UserPreferencesRepository
    private lateinit var sessionRepo: SessionRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        dataStore = PreferenceDataStoreFactory.create(scope = testScope, produceFile = { tempFolder.newFile("test.preferences_pb") })
        userPrefsRepo = UserPreferencesRepository(dataStore)
        sessionRepo = SessionRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial step is PLACING_A`() {
        val vm = SetupViewModel(userPrefsRepo, sessionRepo)
        assertEquals(SetupStep.PLACING_A, vm.uiState.value.step)
    }

    @Test
    fun `4 taps advances from PLACING_A to PLACING_B`() {
        val vm = SetupViewModel(userPrefsRepo, sessionRepo)
        vm.onCanvasTap(0.1f, 0.1f)
        vm.onCanvasTap(0.3f, 0.1f)
        vm.onCanvasTap(0.3f, 0.3f)
        assertEquals(SetupStep.PLACING_A, vm.uiState.value.step)
        vm.onCanvasTap(0.1f, 0.3f)
        assertEquals(SetupStep.PLACING_B, vm.uiState.value.step)
        assertEquals(4, vm.uiState.value.goalAPoints.size)
    }

    @Test
    fun `4 more taps advances from PLACING_B to FINE_TUNING`() {
        val vm = SetupViewModel(userPrefsRepo, sessionRepo)
        repeat(4) { vm.onCanvasTap(0.1f, 0.1f + it * 0.1f) }
        assertEquals(SetupStep.PLACING_B, vm.uiState.value.step)
        repeat(3) { vm.onCanvasTap(0.7f, 0.1f + it * 0.1f) }
        assertEquals(SetupStep.PLACING_B, vm.uiState.value.step)
        vm.onCanvasTap(0.7f, 0.4f)
        assertEquals(SetupStep.FINE_TUNING, vm.uiState.value.step)
        assertEquals(4, vm.uiState.value.goalBPoints.size)
    }

    @Test
    fun `onHandleDrag updates specific point`() {
        val vm = SetupViewModel(userPrefsRepo, sessionRepo)
        repeat(4) { vm.onCanvasTap(0.1f + it * 0.05f, 0.1f + it * 0.05f) }
        repeat(4) { vm.onCanvasTap(0.6f + it * 0.05f, 0.1f + it * 0.05f) }
        vm.onHandleDrag("a", 0, 0.2f, 0.2f)
        assertEquals(NormalizedPoint(0.2f, 0.2f), vm.uiState.value.goalAPoints[0])
    }

    @Test
    fun `redraw resets to PLACING_A`() {
        val vm = SetupViewModel(userPrefsRepo, sessionRepo)
        repeat(4) { vm.onCanvasTap(0.1f, 0.1f) }
        vm.redraw()
        assertEquals(SetupStep.PLACING_A, vm.uiState.value.step)
        assertTrue(vm.uiState.value.goalAPoints.isEmpty())
    }

    @Test
    fun `isReconfiguring is true when goalZoneSet exists`() =
        testScope.runTest {
            userPrefsRepo.updateGoalZoneSet(GoalZoneSet.DEFAULT)
            val vm = SetupViewModel(userPrefsRepo, sessionRepo)
            assertTrue(vm.uiState.value.isReconfiguring)
        }

    @Test
    fun `isReconfiguring is false on first launch`() {
        val vm = SetupViewModel(userPrefsRepo, sessionRepo)
        assertFalse(vm.uiState.value.isReconfiguring)
    }

    @Test
    fun `confirmZones saves and emits nav event`() =
        testScope.runTest {
            val vm = SetupViewModel(userPrefsRepo, sessionRepo)
            repeat(4) { vm.onCanvasTap(0.1f + it * 0.05f, 0.1f + it * 0.05f) }
            repeat(4) { vm.onCanvasTap(0.6f + it * 0.05f, 0.1f + it * 0.05f) }
            vm.advanceToConfirm()
            vm.confirmZones()

            val saved = userPrefsRepo.goalZoneSet.first()
            assertTrue(saved != null)
            assertEquals("a", saved!!.goalA.id)
            assertEquals("b", saved.goalB.id)

            val event = vm.navEvents.first()
            assertEquals(SetupNavEvent.NavigateToRecording, event)
        }

    @Test
    fun `useDefaults saves DEFAULT and emits nav event`() =
        testScope.runTest {
            val vm = SetupViewModel(userPrefsRepo, sessionRepo)
            vm.useDefaults()

            val saved = userPrefsRepo.goalZoneSet.first()
            assertEquals(GoalZoneSet.DEFAULT, saved)

            val event = vm.navEvents.first()
            assertEquals(SetupNavEvent.NavigateToRecording, event)
        }

    @Test
    fun `buildGoalZoneSet produces correct zones`() {
        val a = listOf(NormalizedPoint(0.1f, 0.2f), NormalizedPoint(0.3f, 0.2f), NormalizedPoint(0.3f, 0.6f), NormalizedPoint(0.1f, 0.6f))
        val b = listOf(NormalizedPoint(0.7f, 0.2f), NormalizedPoint(0.9f, 0.2f), NormalizedPoint(0.9f, 0.6f), NormalizedPoint(0.7f, 0.6f))
        val set = SetupViewModel.buildGoalZoneSet(a, b)
        assertEquals("a", set.goalA.id)
        assertEquals("b", set.goalB.id)
        assertEquals(NormalizedPoint(0.1f, 0.2f), set.goalA.p1)
        assertEquals(NormalizedPoint(0.9f, 0.6f), set.goalB.p3)
    }
}
