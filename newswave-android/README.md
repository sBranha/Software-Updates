# NewsWire 0.4.5 — private Android Auto test
Display name: NewsWire. Android applicationId: `com.newswire.live`.
This is a new Android application ID distinct from the previous `com.newswave.live` phone APK. Existing Java namespace `com.newswave.live` remains as a code namespace.
This branch creates **debug-signed** test APK and AAB. For the simpler Google Play **Internal app sharing** method, upload the APK. Google re-signs it with an internal test certificate. Do not upload the old NewsWave APK or this debug AAB into the standard Internal testing release form, which requires a production-upload-signed bundle.
To distribute through the standard internal testing track later, first configure a persistent upload signing key, then generate a release AAB. Keep private keys out of GitHub. Android Auto weather availability requires real-vehicle verification; an installed phone APK alone is not proof that it will appear on the Explorer screen.
