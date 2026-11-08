# These constructors are reached through framework reflection in release builds.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class io.github.professorengineergit.cadence.PracticeViewModel {
    public <init>(android.app.Application);
}
-keep class io.github.professorengineergit.cadence.ReminderWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
