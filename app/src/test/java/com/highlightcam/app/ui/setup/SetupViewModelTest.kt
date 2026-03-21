package com.highlightcam.app.ui.setup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.highlightcam.app.data.SessionRepository
import com.highlightcam.app.data.UserPreferencesRepository
import com.highlightcam.app.domain.GoalZone
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        dataStore =
            PreferenceDataStoreFactory.create(
                scope = testScope,
                produceFile = { tempFolder.newFile("test.preferences_pb") },
            )
        userPrefsRepo = UserPreferencesRepository(dataStore)
        sessionRepo = SessionRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has no rect and is not reconfiguring`() {
        val vm = SetupViewModel(userPrefsRepo, sessionRepo)
        val state = vm.uiState.value
        assertNull(state.currentDragRect)
        assertFalse(state.isRectFinalized)
        assertFalse(state.isReconfiguring)
    }

    @Test
    fun `drag produces correct rect`() {
        val vm = SetupViewModel(userPrefsRepo, sessionRepo)
        vm.onDragStart(100f, 200f)
        vm.onDragUpdate(300f, 400f)

        val rect = vm.uiState.value.currentDragRect!!
        assertEquals(100f, rect.left, EPSILON)
        assertEquals(200f, rect.top, EPSILON)
        assertEquals(300f, rect.right, EPSILON)
        assertEquals(400f, rect.bottom, EPSILON)
    }

    @Test
    fun `reverse drag normalizes rect`() {
        val vm = SetupViewModel(userPrefsRepo, sessionRepo)
        vm.onDragStart(300f, 400f)
        vm.onDragUpdate(100f, 200f)

        val rect = vm.uiState.value.currentDragRect!!
        assertEquals(100f, rect.left, EPSILON)
        assertEquals(200f, rect.top, EPSILON)
        assertEquals(300f, rect.right, EPSILON)
        assertEquals(400f, rect.bottom, EPSILON)
    }

    @Test
    fun `onDragEnd finalizes rect`() {
        val vm = SetupViewModel(userPrefsRepo, sessionRepo)
        vm.onDragStart(50f, 50f)
        vm.onDragUpdate(250f, 350f)
        vm.onDragEnd()

        val state = vm.uiState.value
        assertNotNull(state.currentDragRect)
        assertTrue(state.isRectFinalized)
    }

    @Test
    fun `clearRect resets state`() {
        val vm = SetupViewModel(userPrefsRepo, sessionRepo)
        vm.onDragStart(50f, 50f)
        vm.onDragUpdate(250f, 350f)
        vm.onDragEnd()
        vm.clearRect()

        val state = vm.uiState.value
        assertNull(state.currentDragRect)
        assertFalse(state.isRectFinalized)
    }

    @Test
    fun `new drag clears previous rect`() {
        val vm = SetupViewModel(userPrefsRepo, sessionRepo)
        vm.onDragStart(0f, 0f)
        vm.onDragUpdate(100f, 100f)
        vm.onDragEnd()

        vm.onDragStart(200f, 200f)
        val state = vm.uiState.value
        assertNull(state.currentDragRect)
        assertFalse(state.isRectFinalized)
    }

    @Test
    fun `isReconfiguring is true when goalZone already exists`() =
        testScope.runTest {
            userPrefsRepo.updateGoalZone(GoalZone(0.1f, 0.2f, 0.3f, 0.4f))
            val vm = SetupViewModel(userPrefsRepo, sessionRepo)
            assertTrue(vm.uiState.value.isReconfiguring)
        }

    @Test
    fun `confirmZone saves to repositories and emits nav event`() =
        testScope.runTest {
            val vm = SetupViewModel(userPrefsRepo, sessionRepo)
            val zone = GoalZone(0.1f, 0.2f, 0.5f, 0.5f)
            vm.confirmZone(zone)

            assertEquals(zone, userPrefsRepo.goalZone.first())
            assertEquals(zone, sessionRepo.goalZone.value)

            val event = vm.navEvents.first()
            assertEquals(SetupNavEvent.NavigateToRecording, event)
        }

    @Test
    fun `skipZone saves FULL_FRAME and emits nav event`() =
        testScope.runTest {
            val vm = SetupViewModel(userPrefsRepo, sessionRepo)
            vm.skipZone()

            assertEquals(GoalZone.FULL_FRAME, userPrefsRepo.goalZone.first())
            assertEquals(GoalZone.FULL_FRAME, sessionRepo.goalZone.value)

            val event = vm.navEvents.first()
            assertEquals(SetupNavEvent.NavigateToRecording, event)
        }

    companion object {
        private const val EPSILON = 0.01f
    }
}
