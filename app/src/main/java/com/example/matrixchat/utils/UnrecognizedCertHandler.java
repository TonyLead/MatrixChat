package com.example.matrixchat.utils;

import android.app.Activity;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.matrixchat.MyApplication;
import com.example.matrixchat.R;
import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.ssl.CertUtil;
import org.matrix.androidsdk.ssl.Fingerprint;
import org.matrix.androidsdk.ssl.UnrecognizedCertificateException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class UnrecognizedCertHandler {
    private static final String LOG_TAG = UnrecognizedCertHandler.class.getSimpleName();

    private static final Map<String, Set<Fingerprint>> ignoredFingerprints = new HashMap<>();
    private static final Set<String> openDialogIds = new HashSet<>();

    /**
     * Handle a network exception and display a dialog box if it's a certificate exception
     *
     * @param e        the exception
     * @param callback callback to fire when the user makes a decision
     * @return true if an exception was handled, false otherwise
     */
    public static boolean handle(final HomeServerConnectionConfig hsConfig, final Exception e, final Callback callback) {
        UnrecognizedCertificateException unrecCertEx = CertUtil.getCertificateException(e);
        if (unrecCertEx != null) {
            final Fingerprint fingerprint = unrecCertEx.getFingerprint();
            UnrecognizedCertHandler.show(hsConfig, fingerprint, false, callback);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Display a certificate dialog box, asking the user about an unknown certificate
     *
     * @param hsConfig                the homeserver configuration
     * @param unrecognizedFingerprint the fingerprint for the unknown certificate
     * @param existing                the certificate is existing
     * @param callback                callback to fire when the user makes a decision
     */
    public static void show(final HomeServerConnectionConfig hsConfig, final Fingerprint unrecognizedFingerprint, boolean existing, final Callback callback) {
        final Activity activity = MyApplication.getCurrentActivity();

        if (activity == null) {
            return;
        }

        final String dialogId;
        if (hsConfig.getCredentials() != null) {
            dialogId = hsConfig.getCredentials().userId;
        } else {
            dialogId = hsConfig.getHomeserverUri().toString() + unrecognizedFingerprint.getBytesAsHexString();
        }

        if (openDialogIds.contains(dialogId)) {
            Log.i(LOG_TAG, "Not opening dialog " + dialogId + " as one is already open.");
            return;
        }

        if (hsConfig.getCredentials() != null) {
            Set<Fingerprint> f = ignoredFingerprints.get(hsConfig.getCredentials().userId);
            if (f != null && f.contains(unrecognizedFingerprint)) {
                callback.onIgnore();
                return;
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);

        LayoutInflater inflater = activity.getLayoutInflater();

        View layout = inflater.inflate(R.layout.dialog_ssl_fingerprint, null);

       TextView sslFingerprintTitle = layout.findViewById(R.id.ssl_fingerprint_title);
        sslFingerprintTitle.setText(activity.getString(R.string.ssl_fingerprint_hash, unrecognizedFingerprint.getType().toString()));

        TextView sslFingerprint = layout.findViewById(R.id.ssl_fingerprint);
        sslFingerprint.setText(unrecognizedFingerprint.getBytesAsHexString());

        TextView sslUserId = layout.findViewById(R.id.ssl_user_id);
        if (hsConfig.getCredentials() != null) {
            sslUserId.setText(activity.getString(R.string.generic_label_and_value,
                    activity.getString(R.string.username),
                    hsConfig.getCredentials().userId));
        } else {
            sslUserId.setText(activity.getString(R.string.generic_label_and_value,
                    activity.getString(R.string.hs_url),
                    hsConfig.getHomeserverUri().toString()));
        }

        TextView sslExpl = layout.findViewById(R.id.ssl_explanation);
        if (existing) {
            if (hsConfig.getAllowedFingerprints().size() > 0) {
                sslExpl.setText(activity.getString(R.string.ssl_expected_existing_expl));
            } else {
                sslExpl.setText(activity.getString(R.string.ssl_unexpected_existing_expl));
            }
        } else {
            sslExpl.setText(activity.getString(R.string.ssl_cert_new_account_expl));
        }

        builder.setView(layout);
        builder.setTitle(R.string.ssl_could_not_verify);

        builder.setPositiveButton(R.string.ssl_trust, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                hsConfig.getAllowedFingerprints().add(unrecognizedFingerprint);
                callback.onAccept();
            }
        });

        if (existing) {
            builder.setNegativeButton(R.string.ssl_remain_offline, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    if (hsConfig.getCredentials() != null) {
                        Set<Fingerprint> f = ignoredFingerprints.get(hsConfig.getCredentials().userId);
                        if (f == null) {
                            f = new HashSet<>();
                            ignoredFingerprints.put(hsConfig.getCredentials().userId, f);
                        }

                        f.add(unrecognizedFingerprint);
                    }
                    callback.onIgnore();
                }
            });

            builder.setNeutralButton(R.string.ssl_logout_account, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    callback.onReject();
                }
            });
        } else {
            builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    callback.onReject();
                }
            });
        }

        final AlertDialog dialog = builder.create();

        final EventEmitter.Listener<Activity> destroyListener = new EventEmitter.Listener<Activity>() {
            @Override
            public void onEventFired(EventEmitter<Activity> emitter, Activity destroyedActivity) {
                if (activity == destroyedActivity) {
                    Log.e(LOG_TAG, "Dismissed!");
                    openDialogIds.remove(dialogId);
                    dialog.dismiss();
                    emitter.unregister(this);
                }
            }
        };

        final EventEmitter<Activity> emitter = MyApplication.getInstance().getOnActivityDestroyedListener();
        emitter.register(destroyListener);

        dialog.setOnDismissListener(new AlertDialog.OnDismissListener() {
            public void onDismiss(DialogInterface dialog) {
                Log.d(LOG_TAG, "Dismissed!");
                openDialogIds.remove(dialogId);
                emitter.unregister(destroyListener);
            }
        });

        dialog.show();
        openDialogIds.add(dialogId);
    }

    public interface Callback {
        /**
         * The certificate was explicitly accepted
         */
        void onAccept();

        /**
         * The warning was ignored by the user
         */
        void onIgnore();

        /**
         * The unknown certificate was explicitly rejected
         */
        void onReject();
    }
}