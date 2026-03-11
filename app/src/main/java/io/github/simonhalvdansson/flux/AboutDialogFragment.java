package io.github.simonhalvdansson.flux;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class AboutDialogFragment extends DialogFragment {

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View contentView = inflater.inflate(R.layout.dialog_about, null);
        TextView versionView = contentView.findViewById(R.id.about_version);
        String versionText = getString(R.string.about_version_format, BuildConfig.VERSION_NAME);
        if (BuildConfig.DEBUG) {
            versionText += " (" + BuildConfig.BUILD_TYPE + ")";
        }
        versionView.setText(versionText);
        contentView.findViewById(R.id.about_button_flux_github)
                .setOnClickListener(v -> openUrl(getString(R.string.about_flux_github_url)));
        contentView.findViewById(R.id.about_button_entsoe_mirror_github)
                .setOnClickListener(v -> openUrl(getString(R.string.about_entsoe_mirror_github_url)));

        return new MaterialAlertDialogBuilder(requireContext())
                .setView(contentView)
                .create();
    }

    private void openUrl(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }
}
