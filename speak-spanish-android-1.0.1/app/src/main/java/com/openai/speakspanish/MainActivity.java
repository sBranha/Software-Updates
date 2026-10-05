package com.openai.speakspanish;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
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
    private static final int REQ_SYSTEM_SPEECH = 1002;

    private EditText englishText;
    private TextView spanishText;
    private TextView statusText;
    private Button micButton;
    private Button translateButton;
    private Button replayButton;
    private Spinner voiceSpinner;
    private CheckBox preferNaturalOnline;

    private Translator translator;
    private boolean modelReady;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech tts;
    private boolean ttsReady;
    private final List<Voice> spanishVoices = new ArrayList<>();
    private String lastSpanish = "";
    private String pendingSpeechText = "";
    private boolean retriedOfflineVoice;

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
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("Speak Spanish");
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.rgb(20, 61, 89));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Tap the microphone, speak English, and the app will translate and say it in Spanish.");
        subtitle.setTextSize(16);
        subtitle.setTextColor(Color.rgb(93, 109, 126));
        LinearLayout.LayoutParams subLp = matchWrap();
        subLp.setMargins(0, dp(6), 0, dp(18));
        root.addView(subtitle, subLp);

        micButton = new Button(this);
        micButton.setText("🎤  TAP & SPEAK ENGLISH");
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

        root.addView(sectionLabel("English"));
        englishText = new EditText(this);
        englishText.setHint("What you say will appear here. You can also type English here.");
        englishText.setTextSize(18);
        englishText.setMinLines(3);
        englishText.setGravity(Gravity.TOP | Gravity.START);
        englishText.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(englishText, matchWrap());

        translateButton = new Button(this);
        translateButton.setText("Translate & Speak Spanish");
        translateButton.setTextSize(17);
        translateButton.setOnClickListener(v -> translateAndSpeak(englishText.getText().toString()));
        LinearLayout.LayoutParams translateLp = matchWrap();
        translateLp.setMargins(0, dp(12), 0, dp(18));
        root.addView(translateButton, translateLp);

        root.addView(sectionLabel("Spanish"));
        spanishText = new TextView(this);
        spanishText.setText("Spanish translation will appear here.");
        spanishText.setTextSize(22);
        spanishText.setTextColor(Color.rgb(23, 32, 42));
        spanishText.setMinHeight(dp(105));
        spanishText.setGravity(Gravity.CENTER_VERTICAL);
        spanishText.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(spanishText, matchWrap());

        replayButton = new Button(this);
        replayButton.setText("🔊  SAY SPANISH AGAIN");
        replayButton.setTextSize(17);
        replayButton.setEnabled(false);
        replayButton.setOnClickListener(v -> speakSpanish(lastSpanish));
        LinearLayout.LayoutParams replayLp = matchWrap();
        replayLp.setMargins(0, dp(12), 0, dp(22));
        root.addView(replayButton, replayLp);

        root.addView(sectionLabel("Spanish voice"));
        preferNaturalOnline = new CheckBox(this);
        preferNaturalOnline.setText("Prefer the most natural online Spanish voice when available");
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
        note.setText("The app prefers high-quality Google/Samsung Spanish voices. Low and very-low quality voices are blocked.");
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
        return new LinearLayout.LayoutParams(-1, -2);
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
                    statusText.setText("Ready — tap the microphone and speak English");
                })
                .addOnFailureListener(e -> {
                    modelReady = false;
                    statusText.setText("Translation model needs internet for its first download.");
                });
    }

    private void setupSpeechRecognizer() {
        destroyRecognizer();
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(buildRecognitionListener());
        } catch (Exception e) {
            speechRecognizer = null;
        }
    }

    private RecognitionListener buildRecognitionListener() {
        return new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                statusText.setText("Listening… speak English now");
                micButton.setText("🎤  LISTENING…");
            }
            @Override public void onBeginningOfSpeech() { statusText.setText("I hear you…"); }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() {
                statusText.setText("Got it — processing speech…");
                micButton.setText("🎤  TAP & SPEAK ENGLISH");
            }
            @Override public void onError(int error) {
                micButton.setText("🎤  TAP & SPEAK ENGLISH");
                statusText.setText(humanSpeechError(error));
                if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) setupSpeechRecognizer();
            }
            @Override public void onResults(Bundle results) {
                micButton.setText("🎤  TAP & SPEAK ENGLISH");
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
                    String heard = partial.get(0);
                    englishText.setText(heard);
                    englishText.setSelection(heard.length());
                }
            }
            @Override public void onEvent(int eventType, Bundle params) { }
        };
    }

    private Intent speechIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak English");
        return intent;
    }

    private void startListening() {
        statusText.setText("Starting microphone…");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        if (speechRecognizer == null) setupSpeechRecognizer();
        if (speechRecognizer != null) {
            try {
                speechRecognizer.cancel();
                speechRecognizer.startListening(speechIntent());
                return;
            } catch (Exception ignored) {
                setupSpeechRecognizer();
            }
        }
        launchSystemSpeechFallback();
    }

    private void launchSystemSpeechFallback() {
        try {
            statusText.setText("Opening phone speech recognition…");
            startActivityForResult(speechIntent(), REQ_SYSTEM_SPEECH);
        } catch (Exception e) {
            statusText.setText("Speech recognition could not start. Make sure Google Speech Services or Samsung voice input is enabled.");
            Toast.makeText(this, "Speech recognition is unavailable on this phone.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SYSTEM_SPEECH) {
            micButton.setText("🎤  TAP & SPEAK ENGLISH");
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> choices = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (choices != null && !choices.isEmpty()) {
                    String spoken = choices.get(0);
                    englishText.setText(spoken);
                    englishText.setSelection(spoken.length());
                    translateAndSpeak(spoken);
                    return;
                }
            }
            statusText.setText("No speech was captured. Tap the microphone and try again.");
        }
    }

    private void translateAndSpeak(String input) {
        String text = input == null ? "" : input.trim();
        if (text.isEmpty()) {
            statusText.setText("Speak or type some English first.");
            Toast.makeText(this, "Speak or type something in English first.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!modelReady) {
            statusText.setText("Getting the Spanish translator ready… first use needs internet.");
            translator.downloadModelIfNeeded()
                    .addOnSuccessListener(unused -> {
                        modelReady = true;
                        translateAndSpeak(text);
                    })
                    .addOnFailureListener(e -> statusText.setText("Couldn't download the translation model. Connect to internet and try again."));
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
                    statusText.setText("Spanish ready — speaking now…");
                    speakSpanish(translated);
                })
                .addOnFailureListener(e -> {
                    translateButton.setEnabled(true);
                    statusText.setText("Translation failed. Check your connection and try again.");
                });
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            ttsReady = false;
            statusText.setText("Spanish voice engine could not start. Tap Install / Upgrade Spanish Voices.");
            return;
        }
        ttsReady = true;
        tts.setSpeechRate(0.94f);
        tts.setPitch(1.0f);
        tts.setLanguage(new Locale("es", "US"));
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { runOnUiThread(() -> statusText.setText("Speaking Spanish…")); }
            @Override public void onDone(String utteranceId) {
                runOnUiThread(() -> {
                    pendingSpeechText = "";
                    retriedOfflineVoice = false;
                    statusText.setText("Ready");
                });
            }
            @Override public void onError(String utteranceId) { runOnUiThread(() -> retrySpeechAfterError()); }
            @Override public void onError(String utteranceId, int errorCode) { runOnUiThread(() -> retrySpeechAfterError()); }
        });
        loadSpanishVoices();
    }

    private void loadSpanishVoices() {
        spanishVoices.clear();
        Set<Voice> all = tts.getVoices();
        if (all != null) {
            for (Voice voice : all) {
                if (voice.getLocale() != null && "es".equalsIgnoreCase(voice.getLocale().getLanguage()) && isAcceptableVoice(voice)) {
                    spanishVoices.add(voice);
                }
            }
        }
        Collections.sort(spanishVoices, Comparator.comparingInt(this::voiceScore).reversed().thenComparing(Voice::getName));
        populateVoiceSpinner();
    }

    private boolean isAcceptableVoice(Voice voice) {
        return voice != null && voice.getQuality() >= Voice.QUALITY_NORMAL;
    }

    private int voiceScore(Voice voice) {
        int score = voice.getQuality() * 10;
        String tag = voice.getLocale().toLanguageTag().toLowerCase(Locale.US);
        if (tag.startsWith("es-us")) score += 500;
        else if (tag.startsWith("es-mx")) score += 450;
        else if (tag.startsWith("es-419")) score += 400;
        else if (tag.startsWith("es-es")) score += 300;
        if (voice.isNetworkConnectionRequired()) score += 250; else score += 100;
        String name = voice.getName().toLowerCase(Locale.US);
        if (name.contains("network") || name.contains("wavenet") || name.contains("neural")) score += 300;
        return score;
    }

    private String qualityLabel(Voice voice) {
        if (voice.getQuality() >= Voice.QUALITY_VERY_HIGH) return "very high";
        if (voice.getQuality() >= Voice.QUALITY_HIGH) return "high";
        return "normal / modern";
    }

    private void populateVoiceSpinner() {
        if (voiceSpinner == null) return;
        boolean allowOnline = preferNaturalOnline == null || preferNaturalOnline.isChecked();
        List<String> labels = new ArrayList<>();
        labels.add("Auto — best available Spanish voice");
        for (Voice voice : spanishVoices) {
            if (!allowOnline && voice.isNetworkConnectionRequired()) continue;
            labels.add(voice.getLocale().toLanguageTag() + " • " + qualityLabel(voice) + (voice.isNetworkConnectionRequired() ? " • online" : " • offline") + " • " + voice.getName());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        voiceSpinner.setAdapter(adapter);
    }

    private Voice selectedVoice() {
        if (!ttsReady || spanishVoices.isEmpty()) return null;
        boolean allowOnline = preferNaturalOnline == null || preferNaturalOnline.isChecked();
        int selected = voiceSpinner.getSelectedItemPosition();
        if (selected <= 0) {
            for (Voice voice : spanishVoices) if (allowOnline || !voice.isNetworkConnectionRequired()) return voice;
            return null;
        }
        int visibleIndex = 0;
        for (Voice voice : spanishVoices) {
            if (!allowOnline && voice.isNetworkConnectionRequired()) continue;
            visibleIndex++;
            if (visibleIndex == selected) return voice;
        }
        return null;
    }

    private Voice bestOfflineVoice() {
        for (Voice voice : spanishVoices) if (!voice.isNetworkConnectionRequired()) return voice;
        return null;
    }

    private void speakSpanish(String text) {
        if (text == null || text.trim().isEmpty()) {
            statusText.setText("There is no Spanish text to speak yet.");
            return;
        }
        if (!ttsReady) {
            statusText.setText("Spanish voice is still starting. Tap Say Spanish Again in a moment.");
            return;
        }
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audio != null && audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
            statusText.setText("Your media volume is muted. Turn the volume up, then tap Say Spanish Again.");
            Toast.makeText(this, "Media volume is muted.", Toast.LENGTH_LONG).show();
            return;
        }
        Voice voice = selectedVoice();
        if (voice == null) {
            int langResult = tts.setLanguage(new Locale("es", "US"));
            Voice current = tts.getVoice();
            if (langResult >= TextToSpeech.LANG_AVAILABLE && current != null && current.getLocale() != null && "es".equalsIgnoreCase(current.getLocale().getLanguage()) && isAcceptableVoice(current)) voice = current;
        }
        if (voice == null) {
            statusText.setText("No usable modern Spanish voice is installed. Tap Install / Upgrade Spanish Voices.");
            Toast.makeText(this, "Install a Google or Samsung Spanish voice, then try again.", Toast.LENGTH_LONG).show();
            return;
        }
        pendingSpeechText = text;
        retriedOfflineVoice = false;
        tts.setVoice(voice);
        tts.setSpeechRate(0.94f);
        int result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "spanish-" + System.currentTimeMillis());
        if (result == TextToSpeech.ERROR) {
            statusText.setText("The Spanish voice could not start. Trying another voice…");
            retrySpeechAfterError();
        } else {
            statusText.setText("Speaking Spanish…");
        }
    }

    private void retrySpeechAfterError() {
        if (!retriedOfflineVoice && pendingSpeechText != null && !pendingSpeechText.isEmpty()) {
            Voice offline = bestOfflineVoice();
            if (offline != null && (tts.getVoice() == null || !offline.equals(tts.getVoice()))) {
                retriedOfflineVoice = true;
                tts.setVoice(offline);
                int result = tts.speak(pendingSpeechText, TextToSpeech.QUEUE_FLUSH, null, "spanish-offline-" + System.currentTimeMillis());
                if (result != TextToSpeech.ERROR) {
                    statusText.setText("Trying the best offline Spanish voice…");
                    return;
                }
            }
        }
        statusText.setText("Spanish voice playback failed. Tap Install / Upgrade Spanish Voices, then try again.");
    }

    private void openVoiceInstall() {
        try {
            startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
        } catch (Exception ignored) {
            try {
                startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
            } catch (Exception ignoredAgain) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        }
    }

    private String humanSpeechError(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO: return "Microphone audio problem. Tap the microphone and try again.";
            case SpeechRecognizer.ERROR_CLIENT: return "Speech recognition reset. Tap the microphone and try again.";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Microphone permission is required.";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "Speech recognition needs an internet connection on this phone.";
            case SpeechRecognizer.ERROR_NO_MATCH: return "I didn't understand that. Tap the microphone and try again.";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "Speech recognizer was busy. Tap the microphone and try again.";
            case SpeechRecognizer.ERROR_SERVER: return "Speech service is unavailable right now. Tap the microphone and try again.";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "I didn't hear anything. Tap the microphone and speak again.";
            default: return "Speech recognition stopped. Tap the microphone and try again.";
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startListening();
        else if (requestCode == REQ_MIC) statusText.setText("Microphone permission is needed so you can speak English into the app.");
    }

    private void destroyRecognizer() {
        if (speechRecognizer != null) {
            try { speechRecognizer.cancel(); } catch (Exception ignored) { }
            try { speechRecognizer.destroy(); } catch (Exception ignored) { }
            speechRecognizer = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyRecognizer();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (translator != null) translator.close();
    }
}
