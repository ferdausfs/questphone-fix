<div align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" style="width: 30%;" />
  <h2>QuestPhone</h2>

  <a href="https://github.com/questphone/questphone/graphs/contributors">
    <img src="https://img.shields.io/github/contributors/questphone/questphone" alt="GitHub contributors" />
  </a><!--  -->
  <a href="https://discord.gg/RGuqaMHxAw">
    <img src="https://img.shields.io/badge/Discord%20Server-white?style=flat&logo=discord" alt="Discord Server" />
  </a>
  <a href="https://t.me/questphone">
    <img src="https://img.shields.io/badge/Telegram%20Group-blue?style=flat&logo=telegram" alt="Telegram Group" />
  </a>
  <a href="https://github.com/questphone/questphone/releases">
    <img src="https://img.shields.io/github/downloads/questphone/questphone/total" alt="Total Downloads" />
  </a>
  <a href="https://github.com/questphone/questphone">
    <img src="https://img.shields.io/github/repo-size/questphone/questphone" alt="Repository Size" />
  </a>
</div>

---

**QuestPhone** is an open-source Android productivity utility designed to help users reduce screen addiction by regulating app usage through a gamified experience. The application blocks distracting apps and unlocks them only once the user completes all their work.

It was started in April 2024 under the name *[DigipawsFull Project](https://www.github.com/nethical6/digipaws)* and is now continued as *QuestPhone*.

> [!CAUTION]
> This project is experimental and not yet ready for full production. [Donate](https://digipaws.life/donate)

---

## 🚀 Features

- **Open Source**: Fully transparent, with the source code available for community contributions.
- **Productivity Enhancement**: Helps build healthier digital habits and reduce screen addiction.
- **Gamified**: Level up, make streaks, collect objects, earn rewards, and more.
- **Minimal Launcher**: Optional launcher that turns your device into a productivity beast.
- **Unlimited Possibilities**: Almost any real-life habit can be converted into a quest.
- **Strict Checking**: Not just checkboxes—uses integrations to verify your quests.

---

## 📸 Screenshots

Click on any image to enlarge.

<table>
  <tr>
    <td><img src='fastlane/metadata/android/en-US/images/phoneScreenshots/1.png' width='120'></td>
    <td><img src='fastlane/metadata/android/en-US/images/phoneScreenshots/2.png' width='120'></td>
    <td><img src='fastlane/metadata/android/en-US/images/phoneScreenshots/3.png' width='120'></td>
    <td><img src='fastlane/metadata/android/en-US/images/phoneScreenshots/4.png' width='120'></td>
    <td><img src='fastlane/metadata/android/en-US/images/phoneScreenshots/5.png' width='120'></td>
    <td><img src='fastlane/metadata/android/en-US/images/phoneScreenshots/6.png' width='120'></td>
  </tr>
</table>

---

## 🔌 Integrations

Integrations are how your quests get verified. 
Press on any title to get a detailed explanation about them

- **[Health Connect](https://github.com/QuestPhone/docs/blob/main/integration/HealthConnect.md)**: Connects to your fitness watch or reads data from other apps.
- **[AI Snap](https://github.com/QuestPhone/docs/blob/main/integration/AiSnap.md)**: Prompts users to snap a photo after completing the task.
- **[Deep Focus](https://github.com/QuestPhone/docs/blob/main/integration/DeepFocus.md)**: Blocks all but selected apps for focused time.
- **[Swift Mark](https://github.com/QuestPhone/docs/blob/main/integration/HealthConnect.md)**: Simple tap-to-complete mode with no strict verification.
- **External Integration**: Connect external apps to questphone

---

## 📝 Todo

- [x] Add an API for other developers to turn their apps into quests.
- [ ] Add friends and social features.
- [x] AI-generated motivational reminders.

## Features
- Full Blown minimal launcher with support for widgets 
- Track Steps, Distance covered, Hydration, Sleep, and Calories burned via Health Connect
- Habit tracker cum Habit former
- On Device AI model for Ai Quest Verification (doesn't work properly rn, need help :sob:)
- Reminders from our on device ai 
- Supports external integrations via the questphone api
- Screentime statistics
- Quest Statistics
- Shareable profiles [example](https://questphone.app/@nethical)
- Cool Animations

## FAQ

### Q: Is QuestPhone privacy friendly?

**A:** Yes, way more than any closed-source app blockers or habit trackers.

### Q: Why is Questphone a launcher?
**A:** The only way for us to make app blocker service always run without using accessibility service was through this

### Q: Can I use it as a simple habit tracker?
**A:** Yes! you can simply select no apps as distractive and enjoy your habit tracker

## Tech Stack
- Compose
- Room 
- Supabase
- Dagger Hilt

## Building locally

> [!IMPORTANT]
> This repo ships the `ai/src/main/sentencepiece` dependency as a **git submodule** —
> clone with `git clone --recurse-submodules` or run `git submodule update --init`
> first, otherwise the `fdroid` flavor fails to build.

1. **Prerequisites**: JDK 17+, Android SDK (platform 36, build-tools 35), git
2. **Clone with submodules**:
   ```bash
   git clone --recurse-submodules https://github.com/<you>/questphone-fix.git
   ```
3. **Create `local.properties`** (copy from `local.properties.example` and fill in):
   ```properties
   sdk.dir=/absolute/path/to/Android/sdk
   SUPABASE_URL=https://your-project.supabase.co
   SUPABASE_API_KEY=your-publishable-key
   API_URL=https://api.questphone.app
   ```
   The build **fails fast with a clear error** if the Supabase keys are missing
   (previously it silently baked the string `"null"` into the APK).
4. **(Optional) Signing** — create a `keystore.properties` in the repo root:
   ```properties
   storeFile=/absolute/path/to/release.keystore
   storePassword=...
   keyAlias=...
   keyPassword=...
   ```
   Without it, `release` builds fall back to the debug keystore so the APK stays
   installable. Never commit either file.
5. **Build**:
   ```bash
   ./gradlew :app:assemblePlayRelease     # Play flavor (Firebase)
   ./gradlew :app:assembleFdroidRelease   # F-Droid flavor (on-device AI)
   ```
   Output: `app/build/outputs/apk/play/release/` (universal APK included).

CI: GitHub Actions workflow [`.github/workflows/build-release.yml`](.github/workflows/build-release.yml)
builds signed APKs on every `v*` tag (keystore via `KEYSTORE_BASE64` secret, optional).
