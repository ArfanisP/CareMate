# CareMate 🩺

**CareMate** is an Android health-assistant application designed to support elderly users and caregivers in managing daily healthcare routines. Developed as part of a University Thesis, CareMate simplifies tracking medications, medical appointments, and health examinations with an intuitive, accessible UI and an interactive bilingual voice command assistant.

---

## 🌟 Key Features

* **💊 Medication Tracking**: Easily record, manage, and view prescribed medications, including dosages, scheduled administration times, and personalized notes.
* **📅 Appointment Scheduling**: Keep track of doctor visits, specialist appointments, locations, and doctor details.
* **🔬 Medical Examination Reminders**: Log upcoming blood tests, imaging, and routine health check-ups.
* **🎙️ Interactive Voice Assistant**: Built-in bilingual (**Greek** & **English**) voice interface that uses Speech-to-Text and Text-to-Speech to guide users step-by-step through adding, editing, or removing entries hands-free.
* **⏰ Scheduled Exact Alarms & Notifications**: Integrated with Android `AlarmManager` and `NotificationManagerCompat` to deliver timely exact reminders prior to scheduled medication and appointment times.
* **🌍 Bilingual Support (EN / EL)**: Native support for both **English** and **Greek** locales, dynamically adapting UI text and voice prompts.
* **⚙️ Voice Speed & Accessibility Settings**: Adjustable Text-to-Speech playback speed and permission management for seamless accessibility.
* **🔥 Cloud Synchronization**: Real-time cloud persistence backed by **Firebase Firestore** and anonymous authentication via **Firebase Auth**.

---

## 📱 Tech Stack & Architecture

* **Language**: Java 11 / Android SDK
* **Architecture**: MVVM (Model-View-ViewModel) pattern
* **UI & Component Library**: Google Material Design 3 (`com.google.android.material`), AndroidX AppCompat, ConstraintLayout
* **Database & Auth**: Firebase Firestore & Firebase Anonymous Authentication
* **Voice & Audio**: Android Speech Recognizer API & TextToSpeech Engine
* **Notifications & Alarms**: Android `AlarmManager`, `BroadcastReceiver`, `NotificationManagerCompat`
* **Min SDK**: API 26 (Android 8.0) | **Target SDK**: API 37 (Android 15)

---

## 📂 Project Structure

```
CareMate/
├── app/
│   └── src/main/java/com/caremate/app/
│       ├── model/          # Data entities (Medication, Appointment, Examination)
│       ├── repository/     # Firestore & Auth data sources
│       ├── viewmodel/      # MVVM ViewModels managing UI state
│       ├── ui/             # Activities (Main, Medication, Appointment, Examination, Settings)
│       ├── adapter/        # RecyclerView Adapters for list components
│       ├── voice/          # Bilingual voice command parser and state handlers
│       ├── notification/   # Alarm receivers & notification helpers
│       └── util/           # Date/Time pickers, locale helpers, and utilities
└── app/src/main/res/
    ├── layout/             # Material 3 XML UI layouts
    ├── values/             # App styles, themes, and base strings
    ├── values-el/          # Greek language localization
    └── values-en/          # English language localization
```

---

## 🚀 Getting Started

### Prerequisites

1. **Android Studio** (Ladybug / 2024.2+ recommended)
2. **JDK 11** or higher
3. An Android device or Emulator running **Android 8.0 (API level 26)** or higher

### Installation & Setup

1. **Clone the repository**:
   ```bash
   git clone https://github.com/YOUR_GITHUB_USERNAME/CareMate.git
   cd CareMate
   ```

2. **Add Firebase Configuration**:
   * Ensure your `app/google-services.json` file is present in the `app/` directory for Firebase Firestore and Authentication services.

3. **Open and Build in Android Studio**:
   * Open Android Studio and select **Open** ➔ Navigate to the `CareMate` folder.
   * Let Gradle sync dependencies.
   * Run the app on an emulator or connected Android device using **Run 'app'** (`Shift + F10`).

---

## 🛡️ License

This project was developed for educational and academic research purposes as part of a University Thesis.

---

**Developed with care for accessible digital healthcare.**
