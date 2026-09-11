# Lifeline Project Presentation Script (7th Semester)

> **Instructions for the Team:** 
> Print this document out or keep it on your phone during the presentation. Each section is broken down simply so you can speak naturally. **Do not read it like a robot**—use these points as a guide to talk to your professors.

---

## 1. Introduction (To be spoken by anyone)

"Good morning respected professors. We are Team 6, and we are presenting our major project: **Lifeline**. 

As you may remember, in our 6th-semester mini-project, we built the foundation of Lifeline—an app that can send SOS messages offline using Bluetooth and WiFi Direct when there is no internet. 

However, last semester's version was just a basic proof-of-concept. It worked, but it wasn't ready for a real-world disaster. This semester, our goal was to upgrade Lifeline into a **military-grade, real-world rescue application**. We call this the 'Lifeline Elite' upgrade. 

Today, we want to walk you through the 5 major real-world features we added this semester to make this app actually save lives."

---

## 2. Hardware Triggers (Speak about the 'Shake' & 'Flashlight')
**Who should speak:** Arun

"The first major problem we tackled this semester was usability. In a real disaster, like an earthquake, a victim might be trapped under rubble, injured, or panicked. They cannot easily unlock their phone, open an app, and type a message.

To solve this, we integrated the phone's hardware sensors directly into our app. 
1. **Shake-to-SOS:** We wrote a background algorithm that reads the phone's accelerometer. If the user shakes their phone violently 4 times in 2 seconds, the app automatically wakes up and broadcasts their GPS location and an SOS to the offline mesh network.
2. **Morse Code Flashlight:** We also added an automatic hardware beacon. With one tap, the phone's camera flashlight starts flashing the universal S-O-S pattern in Morse code. This helps physical rescue teams find victims in the dark."

---

## 3. Medical Profiles & Smart Priority (Speak about the new data)
**Who should speak:** Prashik & Prateek

"The second problem we solved was information sorting. During a disaster, rescuers are flooded with hundreds of SOS signals. How do they know who needs help the most?

This semester, we introduced two solutions for this:
1. **Medical Profiles:** Users can now pre-save their Blood Type, Allergies, and existing Medical Conditions in the app. When an SOS is sent, this data is secretly attached to the signal. When an ambulance arrives, the paramedics already know what blood to bring and what medicines to avoid.
2. **Smart Priority Scanner:** We wrote a smart keyword scanner. If a victim manages to type a message like *'I am bleeding heavily'* or *'fire'*, our app automatically detects these critical words and flags their SOS in **Red** as a Priority-5 emergency, pushing them to the top of the rescuer's list."

---

## 4. Two-Way Rescue Communication
**Who should speak:** Mayank

"Last semester, our app was a one-way street: victims broadcasted an SOS and just had to pray someone heard it. This caused panic.

This semester, we created a **Two-Way Rescue System**. We added a 'Rescuer Mode' for authorities. When a rescuer receives an SOS, they can click an 'Acknowledge' button. 
This sends a confirmation signal back through the offline mesh network directly to the victim's phone. The victim gets a notification saying *'Help is on the way'*, which is crucial for keeping victims calm and preventing panic during a crisis."

---

## 5. Security & Mesh Stability (Speak about Encryption)
**Who should speak:** Eshant

"Finally, we had to address Security and Stability. Last semester, we were sending people's exact GPS locations over open Bluetooth. In the real world, this is dangerous because bad actors could intercept the signal.

This semester, we completely rebuilt the core networking engine in Java. 
1. **Military-Grade Security:** We added AES-256 Encryption. Now, every single SOS payload is encrypted before it leaves the phone. Only verified Lifeline apps can decrypt and read the distress signals.
2. **Stability:** We added GZIP compression to make the messages smaller, so they send faster over Bluetooth. We also wrote nearly 100 automated unit tests to ensure that our mesh engine will never crash, even if 100 phones connect to it at the exact same time."

---

## 6. Conclusion (To be spoken by anyone)

"To summarize, last semester we proved that offline SOS networking was possible. This semester, we made it secure, automated, and practical for real doctors and rescue teams to use. 

Thank you. We would now love to show you a live demonstration of the app working across our devices without any internet connection."

---

### ❓ Q&A Cheat Sheet (If the teachers ask tough questions)

**Q: "Did you guys actually write this or just download it?"**
*Answer:* "We wrote it entirely from scratch. You can look at our GitHub commit history. We specifically had to rewrite the networking engine in Java this semester because Kotlin coroutines were struggling to handle the Bluetooth thread management efficiently."

**Q: "How does the app know you shook it 4 times and didn't just drop it?"**
*Answer:* "Arun implemented a 'Low-Pass Filter' algorithm. It subtracts normal gravity from the accelerometer data, leaving only linear acceleration. It then checks if the acceleration crosses a threshold of 15.0 magnitude exactly 4 times within a strict 2-second window. Dropping it only creates one spike, not four."

**Q: "Why AES-256 encryption?"**
*Answer:* "Because we are transmitting highly sensitive data (exact GPS coordinates and medical history) over open public airwaves (Bluetooth). AES-256 is the global standard for secure data and it is fast enough that it doesn't drain the phone's battery during an emergency."
