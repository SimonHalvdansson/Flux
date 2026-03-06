package io.github.simonhalvdansson.flux;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.util.Log;

/**
 * Schedules the job that updates prices in the background.
 */
public class PriceUpdateScheduler {

    private static final String TAG = "PriceUpdateScheduler";
    private static final int JOB_ID = 1001;

    /**
     * Schedule the background job to run periodically (e.g. every hour).
     */
    public static void schedulePriceUpdateJob(Context context) {
        ComponentName component = new ComponentName(context, PriceUpdateJobService.class);

        JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, component)
                // run roughly every hour (on Android 12+, intervals can be clamped to 15min or so)
                .setPeriodic(60 * 60 * 1000)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true); // so it persists reboots if we have RECEIVE_BOOT_COMPLETED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // .setRequiresBatteryNotLow(true) etc. if desired
        }

        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        int result = scheduler.schedule(builder.build());
        if (result == JobScheduler.RESULT_SUCCESS) {
            Log.d(TAG, "Job scheduled successfully");
        } else {
            Log.w(TAG, "Job scheduling failed");
        }
    }

    public static void cancelPriceUpdateJob(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        scheduler.cancel(JOB_ID);
        Log.d(TAG, "Job canceled");
    }
}

