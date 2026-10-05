package com.openai.speakspanish;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    private static final int REQ_MIC = 1001;

    private EditText englishText;
    private TextView spanishText;
    private TextView statusText;
    private Button micButton;
    private Button translateButton;
    private Button replayButton;
    private Spinner voiceSpinner;
    private CheckBox preferNaturalOnline;

    private Translator translator;
    private boolean modelReady = false;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private final List<Voice> spanishVoices = new ArrayList<>();
    private String lastSpanish = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        setupTranslator();
        setupSpeechRecognizer();
        tts = new TextToSpeech(this, this);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(247, 249, 251));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("Speak Spanish");
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.rgb(20, 61, 89));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Speak in English. The app translates it and says it naturally in Spanish.");
        subtitle.setTextSize(16);
        subtitle.setTextColor(Color.rgb(93, 109, 126));
        LinearLayout.LayoutParams subLp = matchWrap();
        subLp.setMargins(0, dp(6), 0, dp(18));
        root.addView(subtitle, subLp);

        micButton = new Button(this);
        micButton.setText("🎤  SPEAK ENGLISH");
        micButton.setTextSize(20);
        micButton.setAllCaps(false);
        micButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        micButton.setMinHeight(dp(72));
        micButton.setOnClickListener(v -> startListening());
        root.addView(micButton, matchWrap());

        statusText = new TextView(this);
        statusText.setText("Preparing translator…");
        statusText.setGravity(Gravity.CENTER);
        statusText.setTextSize(14);
        statusText.setTextColor(Color.rgb(93, 109, 126));
        LinearLayout.LayoutParams statusLp = matchWrap();
        statusLp.setMargins(0, dp(8), 0, dp(18));
        root.addView(statusText, statusLp);

        TextView englishLabel = sectionLabel("English");
        root.addView(englishLabel);

        englishText = new EditText(this);
        englishText.setHint("What you say will appear here. You can also type English here.");
        englishText.setTextSize(18);
        englishText.setMinLines(3);
        englishText.setGravity(Gravity.TOP | Gravity.START);
        englishText.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(englishText, boxParams());

        translateButton = new Button(this);
        translateButton.setText("Translate & Speak Spanish");
        translateButton.setTextSize(17);
        translateButton.setOnClickListener(v -> translateAndSpeak(englishText.getText().toString()));
        LinearLayout.LayoutParams translateLp = matchWrap();
        translateLp.setMargins(0, dp(12), 0, dp(18));
        root.addView(translateButton, translateLp);

        TextView spanishLabel = sectionLabel("Spanish");
        root.addView(spanishLabel);

        spanishText = new TextView(this);
        spanishText.setText("Spanish translation will appear here.");
        spanishText.setTextSize(22);
        spanishText.setTextColor(Color.rgb(23, 32, 42));
        spanishText.setMinHeight(dp(105));
        spanishText.setGravity(Gravity.CENTER_VERTICAL);
        spanishText.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(spanishText, boxParams());

        replayButton = new Button(this);
        replayButton.setText("🔊  Say Spanish Again");
        replayButton.setTextSize(17);
        replayButton.setEnabled(false);
        replayButton.setOnClickListener(v -> speakSpanish(lastSpanish));
        LinearLayout.LayoutParams replayLp = matchWrap();
        replayLp.setMargins(0, dp(12), 0, dp(22));
        root.addView(replayButton, replayLp);

        TextView voiceTitle = sectionLabel("Spanish voice");
        root.addView(voiceTitle);

        preferNaturalOnline = new CheckBox(this);
        preferNaturalOnline.setText("Prefer the most natural high-quality voice (may use internet)");
        preferNaturalOnline.setChecked(true);
        preferNaturalOnline.setTextSize(15);
        preferNaturalOnline.setOnCheckedChangeListener((buttonView, isChecked) -> populateVoiceSpinner());
        root.addView(preferNaturalOnline, matchWrap());

        voiceSpinner = new Spinner(this);
        LinearLayout.LayoutParams spinnerLp = matchWrap();
        spinnerLp.setMargins(0, dp(6), 0, dp(10));
        root.addView(voiceSpinner, spinnerLp);

        Button previewVoice = new Button(this);
        previewVoice.setText("Preview Selected Spanish Voice");
        previewVoice.setOnClickListener(v -> speakSpanish("Hola. Esta es mi voz en español."));
        LinearLayout.LayoutParams previewLp = matchWrap();
        previewLp.setMargins(0, 0, 0, dp(8));
        root.addView(previewVoice, previewLp);

        Button voiceSettings = new Button(this);
        voiceSettings.setText("Install / Upgrade Spanish Voices");
        voiceSettings.setOnClickListener(v -> openVoiceInstall());
        root.addView(voiceSettings, matchWrap());

        TextView note = new TextView(this);
        note.setText("Tip: The app only auto-speaks with high-quality Spanish voices. Low-quality robotic fallback voices are intentionally blocked. Install a Google or Samsung high-quality Spanish voice if none appears here.");
        note.setTextSize(13);
        note.setTextColor(Color.rgb(93, 109, 126));
        LinearLayout.LayoutParams noteLp = matchWrap();
        noteLp.setMargins(0, dp(10), 0, 0);
        root.addView(note, noteLp);

        setContentView(scroll);
    }

    private TextView sectionLabel(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(16);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setTextColor(Color.rgb(20, 61, 89));
        v.setPadding(0, 0, 0, dp(6));
        return v;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams boxParams() {
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, 0, 0, 0);
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void setupTranslator() {
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.SPANISH)
                .build();
        translator = Translation.getClient(options);

        statusText.setText("Downloading / checking Spanish translation model…");
        translator.downloadModelIfNeeded()
                .addOnSuccessListener(unused -> {
                    modelReady = true;
                    statusText.setText("Ready — tap Speak English");
                })
                .addOnFailureListener(e -> {
                    modelReady = false;
                    statusText.setText("Translation model needs internet for its first download.");
                });
    }

    private void setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText.setText("Speech recognition is not available on this phone.");
            return;
        }
        createRecognizer();
    }

    private void createRecognizer() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            } else {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            }
        } catch (Exception e) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        }

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                statusText.setText("Listening… speak English now");
                micButton.setText("Listening…");
            }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() {
                statusText.setText("Translating…");
                micButton.setText("🎤  SPEAK ENGLISH");
            }
            @Override public void onError(int error) {
                micButton.setText("🎤  SPEAK ENGLISH");
                statusText.setText(humanSpeechError(error));
            }
            @Override public void onResults(Bundle results) {
                micButton.setText("🎤  SPEAK ENGLISH");
                ArrayList<String> choices = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (choices != null && !choices.isEmpty()) {
                    String spoken = choices.get(0);
                    englishText.setText(spoken);
                    englishText.setSelection(spoken.length());
                    translateAndSpeak(spoken);
                } else {
                    statusText.setText("I didn't catch that. Tap the microphone and try again.");
                }
            }
            @Override public void onPartialResults(Bundle partialResults) {
                ArrayList<String> partial = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (partial != null && !partial.isEmpty()) {
                    englishText.setText(partial.get(0));
                    englishText.setSelection(englishText.length());
                }
            }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
    }

    private void startListening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        if (speechRecognizer == null) {
            setupSpeechRecognizer();
            if (speechRecognizer == null) return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak English");
        speechRecognizer.startListening(intent);
    }

    private void translateAndSpeak(String input) {
        String text = input == null ? "" : input.trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "Speak or type something in English first.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!modelReady) {
            statusText.setText("Spanish translation model is still downloading. Check your internet connection.");
            translator.downloadModelIfNeeded()
                    .addOnSuccessListener(unused -> {
                        modelReady = true;
                        translateAndSpeak(text);
                    })
                    .addOnFailureListener(e -> statusText.setText("Couldn't download translation model. Connect to internet and try again."));
            return;
        }

        statusText.setText("Translating to Spanish…");
        translateButton.setEnabled(false);
        translator.translate(text)
                .addOnSuccessListener(translated -> {
                    translateButton.setEnabled(true);
                    lastSpanish = translated;
                    spanishText.setText(translated);
                    replayButton.setEnabled(true);
                    statusText.setText("Spanish ready");
                    speakSpanish(translated);
                })
                .addOnFailureListener(e -> {
                    translateButton.setEnabled(true);
                    statusText.setText("Translation failed. Please try again.");
                });
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            ttsReady = false;
            statusText.setText("Spanish voice engine could not start.");
            return;
        }
        ttsReady = true;
        tts.setSpeechRate(0.94f);
        tts.setPitch(1.0f);
        tts.setLanguage(new Locale("es", "US"));
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {
                runOnUiThread(() -> statusText.setText("Speaking Spanish…"));
            }
            @Override public void onDone(String utteranceId) {
                runOnUiThread(() -> statusText.setText("Ready"));
            }
            @Override public void onError(String utteranceId) {
                runOnUiThread(() -> statusText.setText("Voice playback failed. Try another Spanish voice."));
            }
        });
        loadSpanishVoices();
    }

    private void loadSpanishVoices() {
        spanishVoices.clear();
        Set<Voice> all = tts.getVoices();
        if (all != null) {
            for (Voice voice : all) {
                if (voice.getLocale() != null && "es".equalsIgnoreCase(voice.getLocale().getLanguage())) {
                    spanishVoices.add(voice);
                }
            }
        }
        Collections.sort(spanishVoices, Comparator
                .comparingInt(this::voiceScore).reversed()
                .thenComparing(Voice::getName));
        populateVoiceSpinner();
    }

    private int voiceScore(Voice voice) {
        int score = voice.getQuality() * 10;
        String tag = voice.getLocale().toLanguageTag().toLowerCase(Locale.US);
        if (tag.startsWith("es-us")) score += 400;
        else if (tag.startsWith("es-mx")) score += 350;
        else if (tag.startsWith("es-419")) score += 300;
        else if (tag.startsWith("es-es")) score += 250;
        if (voice.isNetworkConnectionRequired()) score += 100;
        else score += 50;
        return score;
    }

    private void populateVoiceSpinner() {
        if (voiceSpinner == null) return;
        boolean allowOnline = preferNaturalOnline == null || preferNaturalOnline.isChecked();
        List<String> labels = new ArrayList<>();
        labels.add("Auto — best natural Spanish voice");
        for (Voice voice : spanishVoices) {
            if (voice.getQuality() < 400) continue;
            if (!allowOnline && voice.isNetworkConnectionRequired()) continue;
            String quality;
            if (voice.getQuality() >= 500) quality = "very high";
            else if (voice.getQuality() >= 400) quality = "high";
            else quality = "standard";
            labels.add(voice.getLocale().toLanguageTag() + " • " + quality +
                    (voice.isNetworkConnectionRequired() ? " • online" : " • offline") +
                    " • " + voice.getName());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        voiceSpinner.setAdapter(adapter);
    }

    private Voice selectedVoice() {
        if (!ttsReady || spanishVoices.isEmpty()) return null;
        boolean allowOnline = preferNaturalOnline == null || preferNaturalOnline.isChecked();
        int selected = voiceSpinner.getSelectedItemPosition();
        if (selected <= 0) {
            for (Voice voice : spanishVoices) {
                if (voice.getQuality() < 400) continue;
                if (allowOnline || !voice.isNetworkConnectionRequired()) return voice;
            }
            return null;
        }
        int visibleIndex = 0;
        for (Voice voice : spanishVoices) {
            if (voice.getQuality() < 400) continue;
            if (!allowOnline && voice.isNetworkConnectionRequired()) continue;
            visibleIndex++;
            if (visibleIndex == selected) return voice;
        }
        return null;
    }

    private void speakSpanish(String text) {
        if (text == null || text.trim().isEmpty()) return;
        if (!ttsReady) {
            statusText.setText("Spanish voice is still starting. Tap Say Spanish Again in a moment.");
            return;
        }

        Voice voice = selectedVoice();
        if (voice == null) {
            statusText.setText("No high-quality Spanish voice is installed. Tap Install / Upgrade Spanish Voices — robotic fallback is disabled.");
            Toast.makeText(this, "Install a high-quality Spanish voice first.", Toast.LENGTH_LONG).show();
            return;
        }
        tts.setVoice(voice);
        tts.setSpeechRate(0.94f);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "spanish-" + System.currentTimeMillis());
    }

    private void openVoiceInstall() {
        try {
            Intent install = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
            startActivity(install);
        } catch (Exception ignored) {
            try {
                Intent settings = new Intent("com.android.settings.TTS_SETTINGS");
                startActivity(settings);
            } catch (Exception ignoredAgain) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        }
    }

    private String humanSpeechError(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO: return "Microphone audio problem. Try again.";
            case SpeechRecognizer.ERROR_CLIENT: return "Speech recognition stopped. Tap the microphone to retry.";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Microphone permission is required.";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "Speech recognition needs a connection on this phone.";
            case SpeechRecognizer.ERROR_NO_MATCH: return "I didn't understand that. Tap the microphone and try again.";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "Speech recognizer is busy. Try again.";
            case SpeechRecognizer.ERROR_SERVER: return "Speech service is unavailable right now. Try again.";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "I didn't hear anything. Tap the microphone and speak again.";
            default: return "Speech recognition error. Tap the microphone and try again.";
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else if (requestCode == REQ_MIC) {
            statusText.setText("Microphone permission is needed so you can speak English into the app.");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.cancel();
            speechRecognizer.destroy();
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (translator != null) {
            translator.close();
        }
    }
}
