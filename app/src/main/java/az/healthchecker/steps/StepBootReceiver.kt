package az.healthchecker.steps
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts step counting after the phone restarts or the app is updated, if the user left it on. */
class StepBootReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action==Intent.ACTION_BOOT_COMPLETED||intent.action==Intent.ACTION_MY_PACKAGE_REPLACED)StepTracking.resume(context)
    }
}
