# Lifeline: Offline Peer-to-Peer Emergency SOS

Lifeline is a robust, deeply resilient Android application built for disaster scenarios where cellular networks and internet connectivity have completely failed (e.g., earthquakes, floods, government blackouts).

Unlike traditional SOS apps that require an internet connection or SMS to function, Lifeline creates its own decentralized mesh network using **Google Nearby Connections API** (Bluetooth Low Energy + WiFi Direct).

## 🚀 Key Features

*   **100% Offline SOS Broadcasting:** Send distress signals instantly without a SIM card or WiFi connection.
*   **Decentralized Mesh Networking:** Messages automatically hop and relay through other nearby Lifeline users. If Person A is trapped, and Person B walks by, B's phone receives the SOS. When B walks near Person C, B's phone secretly passes the SOS to C, expanding the rescue radius infinitely.
*   **Voice Message Support:** Tap the microphone to record a custom audio message, which is broadcast over the offline mesh data payload.
*   **Custom Text Broadcasts:** Send customized text updates ("I am trapped on the 3rd floor") instead of generic signals.
*   **Offline Rescue Maps:** Built using `osmdroid`. Maps are downloaded and accessible offline, allowing rescuers to visually pinpoint exactly where distress signals are coming from based on GPS data in the SOS packet.
*   **Automatic Cloud Synchronization:** If *any* phone in the mesh network eventually finds an internet connection, it uses Firebase Firestore to quietly backup all collected SOS signals to the cloud, allowing remote authorities to see the disaster layout.
*   **Local Persistence:** Uses Android Room Database to store all received messages safely, even if the device restarts.

## 🛠 Tech Stack

*   **Language:** Kotlin
*   **UI:** Modern XML + Material Design 3
*   **Networking:** Google Nearby Connections API (P2P Cluster Strategy)
*   **Database:** Room (SQLite)
*   **Cloud Backend:** Firebase Firestore
*   **Mapping:** osmdroid (OpenStreetMap)
*   **Background Tasks:** WorkManager
*   **Architecture:** MVVM (Model-View-ViewModel)

## 📱 How to Run locally

1.  Clone this repository.
2.  Open the project in Android Studio.
3.  **Firebase Setup:** The project requires Firebase Firestore for cloud sync.
    *   Create a Firebase project.
    *   Register your Android app (`com.example.lifeline`).
    *   Download the `google-services.json` file.
    *   Place it in the `app/` directory of this project.
4.  Sync Gradle (using the included Gradle 8.5 wrapper).
5.  **Critical:** Because this app relies on the physical Bluetooth and WiFi Direct radios inside a phone, **you must run this on a physical Android device**. The Nearby Connections API will crash or fail to find peers if run on a standard Android Emulator.

## 🔒 Privacy & Permissions

Lifeline requires the following permissions to function:
*   `ACCESS_FINE_LOCATION` / `COARSE_LOCATION`: To embed your rescue coordinates in your SOS packet.
*   `BLUETOOTH` / `BLUETOOTH_ADVERTISE` / `NEARBY_WIFI_DEVICES`: To construct the offline mesh network.
*   `RECORD_AUDIO`: To capture voice SOS messages.

*Disclaimer: This app was built as an emergency proof-of-concept and should not solely be relied upon for life-threatening situations without extensive testing.*
