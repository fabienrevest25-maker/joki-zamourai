package com.jokizamurai.game;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.util.ArrayList;
import java.util.Locale;
import org.json.JSONArray;

public class MainActivity extends Activity {
    private static final int REQ_MIC = 71;
    private WebView web;
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    private SpeechRecognizer stt;
    private String pendingLang = null;

    /** Voix japonaise : window.AndroidTTS dans la page. */
    public class TtsBridge {
        @JavascriptInterface
        public void speak(String text) { speakRate(text, 1f); }

        @JavascriptInterface
        public void speakRate(String text, float rate) {
            if (!ttsReady || text == null) return;
            tts.setPitch(1f);
            tts.setSpeechRate(Math.max(0.3f, Math.min(1.5f, rate)));
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jz");
        }

        @JavascriptInterface
        public void speakPitch(String text, float rate, float pitch) {
            if (!ttsReady || text == null) return;
            tts.setPitch(Math.max(0.3f, Math.min(2f, pitch)));
            tts.setSpeechRate(Math.max(0.3f, Math.min(1.5f, rate)));
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jz");
        }

        @JavascriptInterface
        public boolean ready() { return ttsReady; }

        @JavascriptInterface
        public void stop() { if (tts != null) tts.stop(); }
    }

    /** Reconnaissance vocale : window.AndroidSTT dans la page. */
    public class SttBridge {
        @JavascriptInterface
        public void start(final String lang) { runOnUiThread(() -> startListening(lang)); }

        @JavascriptInterface
        public void stop() { runOnUiThread(() -> { if (stt != null) stt.stopListening(); }); }

        @JavascriptInterface
        public void abort() { runOnUiThread(() -> { if (stt != null) stt.cancel(); }); }
    }

    private void js(final String kind, final String jsonArg) {
        runOnUiThread(() -> web.evaluateJavascript("window.__stt&&window.__stt('" + kind + "'," + jsonArg + ")", null));
    }

    private void startListening(String lang) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingLang = lang;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            js("error", "'service-not-allowed'");
            js("end", "null");
            return;
        }
        if (tts != null) tts.stop();
        if (stt == null) {
            stt = SpeechRecognizer.createSpeechRecognizer(this);
            stt.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle p) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float v) {}
                @Override public void onBufferReceived(byte[] b) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onPartialResults(Bundle b) {}
                @Override public void onEvent(int t, Bundle b) {}

                @Override
                public void onResults(Bundle b) {
                    ArrayList<String> list = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    JSONArray arr = new JSONArray();
                    if (list != null) for (String s : list) arr.put(s);
                    if (arr.length() > 0) js("result", arr.toString());
                    js("end", "null");
                }

                @Override
                public void onError(int code) {
                    String e;
                    switch (code) {
                        case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: e = "not-allowed"; break;
                        case SpeechRecognizer.ERROR_AUDIO: e = "audio-capture"; break;
                        case SpeechRecognizer.ERROR_NETWORK:
                        case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                        case SpeechRecognizer.ERROR_SERVER: e = "network"; break;
                        case 12: /* ERROR_LANGUAGE_NOT_SUPPORTED */
                        case 13: /* ERROR_LANGUAGE_UNAVAILABLE */ e = "language-not-supported"; break;
                        case SpeechRecognizer.ERROR_NO_MATCH:
                        case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: e = "no-speech"; break;
                        default: e = "aborted";
                    }
                    js("error", "'" + e + "'");
                    js("end", "null");
                }
            });
        }
        Intent in = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        in.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        in.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang == null ? "ja-JP" : lang);
        in.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang == null ? "ja-JP" : lang);
        in.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        in.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        try {
            stt.startListening(in);
        } catch (Exception ex) {
            js("error", "'service-not-allowed'");
            js("end", "null");
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code != REQ_MIC) return;
        String lang = pendingLang;
        pendingLang = null;
        if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) startListening(lang);
        else { js("error", "'not-allowed'"); js("end", "null"); }
    }

    @SuppressWarnings("deprecation")
    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(Locale.JAPAN);
                ttsReady = (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED);
            }
        });
        web = new WebView(this);
        web.setBackgroundColor(0xFF161B30);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        s.setSupportZoom(false);
        s.setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new TtsBridge(), "AndroidTTS");
        web.addJavascriptInterface(new SttBridge(), "AndroidSTT");
        setContentView(web);
        immersive();
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onWindowFocusChanged(boolean has) {
        super.onWindowFocusChanged(has);
        if (has) immersive();
    }

    /** Retour : ferme d'abord le menu ou la boîte ouverte dans le jeu ; sinon met l'appli en arrière-plan sans la tuer. */
    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(window.__androidBack?window.__androidBack():false)", v -> {
            if (!"true".equals(v)) moveTaskToBack(true);
        });
    }

    @Override
    protected void onPause() {
        if (tts != null) tts.stop();
        if (stt != null) stt.cancel();
        web.onPause();
        web.pauseTimers();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        web.resumeTimers();
        immersive();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        if (stt != null) stt.destroy();
        web.destroy();
        super.onDestroy();
    }
}
