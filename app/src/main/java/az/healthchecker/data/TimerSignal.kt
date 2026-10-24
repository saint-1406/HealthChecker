package az.healthchecker.data
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Sound and vibration of the workout timers. The beep goes to the media stream, so it is heard in headphones over a
 * training video; a phone without a vibrator or a busy audio system simply stays silent.
 */
class TimerSignal(private val context:Context) {
    fun tick()=beep(ToneGenerator.TONE_PROP_BEEP,150)
    fun done(){beep(ToneGenerator.TONE_PROP_BEEP2,700);vibrate()}
    private fun beep(tone:Int,ms:Int) {
        val generator=try{ToneGenerator(AudioManager.STREAM_MUSIC,90)}catch(e:RuntimeException){return}
        generator.startTone(tone,ms)
        Handler(Looper.getMainLooper()).postDelayed({generator.release()},ms+300L)
    }
    private fun vibrate() {
        val vibrator=if(Build.VERSION.SDK_INT>=31)context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
        if(vibrator?.hasVibrator()==true)vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0,350,150,350),-1))
    }
}
