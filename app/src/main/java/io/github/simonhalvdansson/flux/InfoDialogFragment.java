package io.github.simonhalvdansson.flux;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class InfoDialogFragment extends DialogFragment {
    private static final String ARG_TITLE = "title";
    private static final String ARG_MESSAGE = "message";

    public static InfoDialogFragment newInstance(String title, String message) {
        InfoDialogFragment fragment = new InfoDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_MESSAGE, message);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View contentView = inflater.inflate(R.layout.dialog_info, null);

        Bundle args = requireArguments();
        TextView titleView = contentView.findViewById(R.id.info_dialog_title);
        TextView messageView = contentView.findViewById(R.id.info_dialog_message);
        MaterialButton closeButton = contentView.findViewById(R.id.info_dialog_close_button);

        titleView.setText(args.getString(ARG_TITLE));
        messageView.setText(args.getString(ARG_MESSAGE));
        closeButton.setOnClickListener(v -> dismiss());

        return new MaterialAlertDialogBuilder(requireContext())
                .setView(contentView)
                .create();
    }
}
