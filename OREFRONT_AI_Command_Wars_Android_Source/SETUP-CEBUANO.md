# OREFRONT — dali nga GitHub APK setup (Cebuano)

1. Download `OREFRONT_AI_Command_Wars_Android_Source.zip` ug extract sa MacBook.
2. Create bag-ong GitHub repo: `OREFRONT-AI-Command-Wars`.
3. Upload **mga sulod sa folder**, dili ZIP. Dapat sa root ang `app/`, `tests/`, `build.gradle`, `settings.gradle` ug `.github/workflows/build-apk.yml`.
4. Kung dili makita ang `.github` sa Finder, press **Command + Shift + .**. Sa GitHub, pwede pud **Add file → Create new file → `.github/workflows/build-apk.yml`** unya i-paste ang code sa included workflow.
5. Click **Commit changes** ug **Actions** → `Build OREFRONT Android APK`.
6. Kung green check: **Summary → Artifacts → OREFRONT-Android-APK**. Download ug extract ang ZIP, makuha `OREFRONT-AI-Command-Wars.apk`.
7. Sa **Releases**, create `v1.0.0`, title `OREFRONT AI Command Wars v1.0`, attach APK, **Publish release**. Copy link sa Messenger.
8. Sa multiplayer: usa ka Android phone ang **host**, pwede siyang mag-hotspot. Uban 1–5 phones mo-connect. Sa app: **Create LAN Room** → 6-digit code; uban **Join by Code + IP**. No MacBook server, no internet needed while playing.
9. Dapat **six actual Android phones** ang inyong i-test sa classroom. Ang 6-socket computer test dili parehas sa 6-phone verification.

Kung red X sa GitHub Actions, click ang failed `android-apk` job, ablihi ang red step ug tan-awa ang detailed error.
