# Lifeline Elite: Offline Peer-to-Peer Emergency SOS

Lifeline Elite is a robust, military-grade Android application built for disaster scenarios where cellular networks and internet connectivity have completely failed (e.g., earthquakes, floods, government blackouts).

Unlike traditional SOS apps that require an internet connection or SMS to function, Lifeline Elite creates its own decentralized mesh network using **Google Nearby Connections API** (Bluetooth Low Energy + WiFi Direct).

## 🚀 Elite Features

*   **100% Offline SOS Broadcasting:** Send distress signals instantly without a SIM card or WiFi connection.
*   **Decentralized DTN Mesh Networking:** Advanced Store-and-Forward Delay Tolerant Networking with gossip protocol routing. Messages automatically hop and relay through other nearby devices, expanding the rescue radius infinitely. 
*   **AES-256-GCM Encryption & GZIP Compression:** Military-grade encryption protects all SOS payloads and locations. Payloads are GZIP compressed to optimize BLE bandwidth.
*   **Medical Profiles:** Attach critical medical data (Blood Type, Allergies, Pre-existing Conditions) directly to your offline SOS broadcast.
*   **Smart NLP Priority Classification:** Automatically parses message content and upgrades priority based on critical keywords (e.g., "bleeding", "trapped", "fire") to categorize signals from P1 to P5.
*   **Hardware SOS Triggers:**
    *   **Shake-to-SOS:** Background accelerometer monitoring automatically triggers an SOS if the device is violently shaken 4 times in 2 seconds.
    *   **Morse Code Flashlight:** Built-in hardware beacon flashes the universal SOS pattern continuously.
*   **Role Management & Rescue Coordination:** Toggle between "Victim" and "Rescuer". Rescuers can send Delivery ACKs ("Help is on the way") and update Victim status ("EN_ROUTE", "REACHED", "EVACUATED").
*   **Voice Message Support:** Tap the microphone to record a custom audio message, which is encrypted and broadcast over the offline mesh data payload.
*   **Automatic Cloud Synchronization:** If *any* phone in the mesh network eventually finds an internet connection, it uses Firebase Firestore to quietly backup all collected SOS signals.

## 🛠 Tech Stack

*   **Languages:** Java (Core Engine/Security) + Kotlin (UI/Business Logic)
*   **UI:** Modern XML + Material Design 3
*   **Networking:** Google Nearby Connections API (P2P Cluster Strategy) + DTN
*   **Security:** `javax.crypto` (AES-256-GCM, PBKDF2)
*   **Database:** Room (SQLite)
*   **Cloud Backend:** Firebase Firestore
*   **Mapping:** osmdroid (OpenStreetMap)
*   **Background Tasks:** Foreground Services (WakeLocks) + WorkManager
*   **Architecture:** MVVM (Model-View-ViewModel)
*   **Testing:** Robolectric, Mockito, Truth (97 Comprehensive Unit Tests)

## 📱 How to Run and Test Locally

1.  Clone this repository.
2.  Open the project in **Android Studio**.
3.  **Firebase Setup:** The project requires Firebase Firestore for cloud sync.
    *   Create a Firebase project.
    *   Register your Android app (`com.example.lifeline`).
    *   Download the `google-services.json` file.
    *   Place it in the `app/` directory of this project.
4.  Sync Gradle (using the included Gradle wrapper).
5.  **Running Tests:** Right-click the `app/src/test/` folder and select **Run 'Tests in lifeline'** to execute the 97 unit tests.
6.  **Running the App:** Because this app relies on the physical Bluetooth, WiFi Direct radios, and hardware sensors, **you must run this on physical Android devices**. The Nearby Connections API will crash or fail to find peers on a standard emulator. Enable USB debugging on two physical phones and run the app from Android Studio.

## 🔒 Privacy & Permissions

Lifeline Elite requires the following permissions to function:
*   `ACCESS_FINE_LOCATION` / `COARSE_LOCATION`: To embed your rescue coordinates in your SOS packet.
*   `BLUETOOTH` / `BLUETOOTH_ADVERTISE` / `NEARBY_WIFI_DEVICES`: To construct the offline mesh network.
*   `RECORD_AUDIO`: To capture voice SOS messages.
*   `CAMERA`: For the Morse code flashlight beacon.
*   `POST_NOTIFICATIONS`: To keep the background mesh engine alive.

___

## 👨‍💻 Team

Developed by a 5-member team:
* **Eshant Guta** – Backend & Mesh Networking Specialist (Core Engine, Google Nearby Connections API)
* **Prashik Humane** – UI/UX & Mapping Engineer (Frontend, Material Design 3, osmdroid)
* **Mayank** – Local Architecture Lead (State & Storage, MVVM, Room Database)
* **Prateek Mishra** – Cloud & Background Services Engineer (Backend Sync, Firebase Firestore, WorkManager)
* **Arun Kumar Swami** – QA, GPS & Permissions Lead (Testing, Hardware Execution, Android Permissions)

___

**⚠️Disclaimer**: *This app was built as an emergency proof-of-concept and should not solely be relied upon for life-threatening situations without extensive real-world testing.*
