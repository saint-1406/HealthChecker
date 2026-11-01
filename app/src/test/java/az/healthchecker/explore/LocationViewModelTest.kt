package az.healthchecker.explore

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import az.healthchecker.ui.explore.ExploreViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class LocationViewModelTest {
    @After fun resetMain()=Dispatchers.resetMain()

    @Test fun cancellationBeforeWorkerRunsDoesNotLeaveExploreBusy()=runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm=ExploreViewModel(ApplicationProvider.getApplicationContext<Application>())
        // A late permission callback can target a VM whose scope has already been cleared.
        // The worker then never enters its body, so a finally inside that body cannot clean up.
        vm.viewModelScope.cancel()
        var completed=false
        vm.locate { completed=true }
        runCurrent()
        assertFalse("A cancelled search must release the UI even when its worker never started",vm.busy.value)
        assertFalse(completed)
        assertNull(vm.locating.value)
        assertNull(vm.error.value)
    }
}
