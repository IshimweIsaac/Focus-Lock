import android.app.ActivityOptions
fun test() {
    val options = ActivityOptions.makeBasic()
    options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
}
