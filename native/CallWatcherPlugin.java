package de.taxiruf25500.funk;

import android.Manifest;
import android.content.Intent;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.telecom.TelecomManager;
import android.view.WindowManager;
import java.util.ArrayList;

@CapacitorPlugin(name = "CallWatcher", permissions = {
    @Permission(alias = "phone", strings = { Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG, Manifest.permission.ANSWER_PHONE_CALLS }),
    @Permission(alias = "mic", strings = { Manifest.permission.RECORD_AUDIO }),
    @Permission(alias = "notif", strings = { "android.permission.POST_NOTIFICATIONS" })
})
public class CallWatcherPlugin extends Plugin {
    static CallWatcherPlugin inst;
    static volatile boolean foreground = false;
    static String pending = "";

    @Override public void load() { inst = this; }
    @Override protected void handleOnResume() { foreground = true; }
    @Override protected void handleOnPause() { foreground = false; }

    static boolean deliver(String num, String dir) {
        if (inst == null || !foreground) return false;
        JSObject o = new JSObject(); o.put("number", num); o.put("dir", dir);
        inst.notifyListeners("call", o, true);
        return true;
    }

    @Override protected void handleOnNewIntent(Intent intent) {
        String n = intent == null ? null : intent.getStringExtra("funk_number");
        if (n != null && !n.isEmpty()) { deliver(n, "in"); }
    }

    @PluginMethod public void requestAccess(PluginCall call) {
        if (getPermissionState("phone") != com.getcapacitor.PermissionState.GRANTED) requestPermissionForAlias("phone", call, "afterPerm");
        else afterPerm(call);
    }
    @com.getcapacitor.annotation.PermissionCallback private void afterPerm(PluginCall call) {
        JSObject r = new JSObject(); r.put("granted", getPermissionState("phone") == com.getcapacitor.PermissionState.GRANTED); call.resolve(r);
    }
    @PluginMethod public void getPending(PluginCall call) {
        String n = pending; pending = "";
        if (n.isEmpty()) { Intent i = getActivity().getIntent(); if (i != null && i.getStringExtra("funk_number") != null) { n = i.getStringExtra("funk_number"); i.removeExtra("funk_number"); } }
        JSObject r = new JSObject(); r.put("number", n); call.resolve(r);
    }

    static void ring(String num) { if (inst == null) return; JSObject o = new JSObject(); o.put("number", num); inst.notifyListeners("ring", o); }
    static void callEnd() { if (inst == null) return; inst.notifyListeners("callend", new JSObject()); }

    /* Bildschirm an lassen */
    @PluginMethod public void keepAwake(PluginCall call) {
        final boolean on = call.getBoolean("on", true);
        getActivity().runOnUiThread(() -> { if (on) getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); else getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); });
        call.resolve();
    }

    /* Anruf annehmen / beenden (ohne den Anruf-Bildschirm) */
    @PluginMethod public void acceptCall(PluginCall call) {
        try {
            TelecomManager tm = (TelecomManager) getContext().getSystemService(Context.TELECOM_SERVICE);
            tm.acceptRingingCall();
            final boolean sp = call.getBoolean("speaker", true);
            if (sp) new Handler(Looper.getMainLooper()).postDelayed(() -> { try { AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE); am.setMode(AudioManager.MODE_IN_CALL); am.setSpeakerphoneOn(true); } catch (Throwable t) { } }, 700);
            call.resolve();
        } catch (Throwable t) { call.reject("Annehmen nicht möglich: " + t.getMessage()); }
    }
    @PluginMethod public void endCall(PluginCall call) {
        try { TelecomManager tm = (TelecomManager) getContext().getSystemService(Context.TELECOM_SERVICE); if (android.os.Build.VERSION.SDK_INT >= 28) tm.endCall(); call.resolve(); }
        catch (Throwable t) { call.reject("Auflegen nicht möglich: " + t.getMessage()); }
    }

    /* Spracheingabe (Android-SpeechRecognizer – im WebView funktioniert die Web-Speech-API nicht) */
    private SpeechRecognizer sr;
    @PluginMethod public void listen(PluginCall call) {
        if (getPermissionState("mic") != com.getcapacitor.PermissionState.GRANTED) { requestPermissionForAlias("mic", call, "afterMic"); return; }
        startListen(call);
    }
    @PermissionCallback private void afterMic(PluginCall call) {
        if (getPermissionState("mic") == com.getcapacitor.PermissionState.GRANTED) startListen(call); else call.reject("Mikrofon-Zugriff nicht erlaubt");
    }
    private void startListen(final PluginCall call) {
        final String lang = call.getString("lang", "de-DE");
        getActivity().runOnUiThread(() -> {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(getContext())) { call.reject("Spracherkennung nicht verfügbar (Google-App/Spracheingabe installieren)"); return; }
                if (sr != null) { try { sr.destroy(); } catch (Throwable t) { } }
                sr = SpeechRecognizer.createSpeechRecognizer(getContext());
                Intent it = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                it.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                it.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
                it.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
                it.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
                it.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1600);
                sr.setRecognitionListener(new RecognitionListener() {
                    public void onReadyForSpeech(Bundle b) { }
                    public void onBeginningOfSpeech() { }
                    public void onRmsChanged(float v) { }
                    public void onBufferReceived(byte[] b) { }
                    public void onEndOfSpeech() { }
                    public void onPartialResults(Bundle b) { }
                    public void onEvent(int i, Bundle b) { }
                    public void onError(int e) { JSObject r = new JSObject(); r.put("text", ""); r.put("error", e); call.resolve(r); }
                    public void onResults(Bundle b) { ArrayList<String> l = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); JSObject r = new JSObject(); r.put("text", (l != null && !l.isEmpty()) ? l.get(0) : ""); call.resolve(r); }
                });
                sr.startListening(it);
            } catch (Throwable t) { call.reject("Spracheingabe: " + t.getMessage()); }
        });
    }
    @PluginMethod public void stopListening(PluginCall call) {
        getActivity().runOnUiThread(() -> { try { if (sr != null) { sr.stopListening(); } } catch (Throwable t) { } });
        call.resolve();
    }
}
