package io.github.simonhalvdansson.flux;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;

public class PriceUpdateJobService extends JobService {

    private static final String TAG = "PriceUpdateJobService";

    public static final String KEY_SELECTED_AREA = "selected_area";
    public static final String KEY_SELECTED_COUNTRY = "selected_country";
    public static final String KEY_APPLY_STROMSTOTTE = "apply_stromstotte";
    public static final String KEY_APPLY_VAT = "apply_vat";
    public static final String KEY_CHART_MODE = "chart_mode";
    public static final String KEY_API_ERROR = "api_error";
    public static final String KEY_PRICE_DISPLAY_STYLE = "price_display_style";

    private Thread workerThread; // or use an Executor

    @Override
    public boolean onStartJob(final JobParameters params) {
        Log.d(TAG, "onStartJob: beginning background fetch");

        // Return true to tell system "we're doing work in another thread"
        workerThread = new Thread(() -> {
            doBackgroundWork();
            // Finished
            jobFinished(params, false);
        });
        workerThread.start();

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // Called if the job must be stopped prematurely
        if (workerThread != null) {
            workerThread.interrupt();
        }
        return true; // reschedule?
    }

    private void doBackgroundWork() {
        PriceRepository.refreshCachedPrices(getApplicationContext());
        Log.d(TAG, "Data fetched and stored");
    }
}
