package com.android.car.carlauncher.calmmode;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.android.car.carlauncher.R;
import com.android.car.qc.QCItem;
import com.android.car.qc.QCList;
import com.android.car.qc.QCRow;
import com.android.car.qc.provider.BaseQCProvider;

import java.util.Set;

/** Quick-control entry used by CarSystemUI's remote QC view. */
public final class CalmModeQCProvider extends BaseQCProvider {
    public static final Uri CALM_MODE_URI = new Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority("com.android.car.carlauncher.calmmode")
            .appendPath("calm_mode")
            .build();

    @Override
    @SuppressWarnings("deprecation")
    protected QCItem onBind(@NonNull Uri uri) {
        if (!CALM_MODE_URI.equals(uri.buildUpon().clearQuery().build())) {
            throw new IllegalArgumentException("No calm-mode item for " + uri);
        }
        Intent intent = new Intent(getContext(), CalmModeActivity.class);
        ActivityOptions options = ActivityOptions.makeBasic()
                .setPendingIntentCreatorBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        PendingIntent action = PendingIntent.getActivity(
                getContext(),
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT,
                options.toBundle());
        QCRow row = new QCRow.Builder()
                .setTitle(getContext().getString(R.string.calm_mode_title))
                .setPrimaryAction(action)
                .build();
        return new QCList.Builder().addRow(row).build();
    }

    @NonNull
    @Override
    protected Set<String> getAllowlistedPackages() {
        return Set.of("com.android.systemui");
    }
}
