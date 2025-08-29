# FraudGuard

## Introduction

Modern fraud detection is typically server-based, relying on centralized data aggregation that can compromise user privacy, introduce latency, and fail to adapt to individual behaviors. **FraudGuard** revolutionizes this approach by delivering robust, real-time fraud detection **entirely on-device**. By continuously learning from each user’s unique touch, typing, app usage, and movement patterns, FraudGuard creates a personalized model of “normal” behavior. This ensures sensitive data never leaves the device, enabling immediate, privacy-preserving detection of unauthorized access, spoofing, and bot activity.

FraudGuard’s unique architecture leverages a multi-agent fusion system, combining behavioral monitoring, honeypot decoy traps, and an adversarial “Challenger” loop. This enables adaptive, lifelong defense against evolving fraud tactics—without ever depending on the cloud.

## Key Features

- **On-Device Behavioral Learning:** Models user’s touch, typing rhythm, app usage, and movement patterns locally for real-time, privacy-first defense.
- **Multi-Agent System:** Specialized agents collaborate to monitor behaviors, detect anomalies, and aggregate risk scores.
- **Honeypot Decoys:** Embedded fake elements bait attackers and trigger early fraud warnings.
- **Adversarial Challenger Loop:** Continuously tests and strengthens models against new fraud techniques using a built-in adversarial agent.
- **No Cloud Dependency:** All data and models stay on the device; nothing is uploaded.
- **Immediate Response:** Detects and responds to threats instantly with actions like biometric checks, account locking, or user alerts.
- **Lightweight & Battery-Efficient:** Designed for mobile devices with minimal performance impact.

## Problem Statement

Fraud detection today is largely server-driven, relying on centralized data aggregation that risks privacy, introduces latency, and often struggles to adapt to individual user behavior. FraudGuard rethinks this paradigm by bringing anomaly detection **fully on-device**, ensuring sensitive behavioral signals never leave the user’s phone. By continuously learning touch patterns, typing rhythm, app usage habits, and movement signatures, it builds a lifelong personalized model of “normal” behavior. This enables real-time detection of unauthorized access, spoofing, and bot-like activity with no cloud dependency, guaranteeing both **low-latency defense and strong user privacy**.

What sets FraudGuard apart is its **multi-agent fusion system**, where specialized agents collaborate: a behavioral monitor, a honeypot decoy trap to bait attackers, and an adversarial “Challenger” loop that constantly tests the system’s robustness against evolving fraud tactics. Unlike one-size-fits-all solutions, FraudGuard personalizes security continuously while staying lightweight and privacy-preserving. This unique blend of **on-device learning, decoy intelligence, and adversarial resilience** makes it a next-gen fraud shield that is both hackathon-viable and industry-relevant.

## System Architecture

![FraudGuard Architecture](assets/architecture.png)

- **Agents:**  
  - *Touch Agent*: Monitors swipe/tap pressure, velocity, curvature, and bot patterns  
  - *Typing Agent*: Observes keystroke dynamics and rhythm anomalies  
  - *Usage Agent*: Tracks app usage  
  - *Movement Agent*: Detects motion signatures  
- **Fusion Agent:** Aggregates signals and computes fraud risk.
- **Decision Engine:** Determines if an anomaly requires a response.
- **Response Manager:** Executes actions (biometric check, lockout, activate honeypot).
- **Honeypot Agent:** Sets up decoy elements to trap attackers.
- **Learner & Challenger:** Feedback loop updates models and tests their robustness.

See [`docs/architecture.md`](docs/architecture.md) for detailed diagrams and explanations.

## Installation & Setup

### Prerequisites

- **Android Studio** (latest version recommended)
- **Android device** (API 26+)
- **TensorFlow Lite** for on-device ML inference

### Steps

1. **Clone the repository:**
    ```sh
    git clone https://github.com/<your-org>/FraudGuard.git
    cd FraudGuard
    ```

2. **Open in Android Studio:**
    - Go to `File > Open` and select the `FraudGuard` project folder.

3. **Configure TensorFlow Lite:**
    - Ensure TensorFlow Lite libraries are included in your `build.gradle`.
    - Place pre-trained or starter models under `app/src/main/assets/`.

4. **Build & Deploy:**
    - Connect your Android device via USB.
    - Click "Run" in Android Studio to install and launch the app.

## Usage

- **Demo Mode:**  
  Launch the app and select "Demo". The UI displays:
    - Live behavioral signals (touch, typing, usage, movement scores)
    - Current fraud risk score (real-time)
    - Alerts or actions triggered (e.g., biometric check, lockout)
    - Honeypot activity status

- **What You'll See:**  
  The dashboard visualizes ongoing monitoring. If abnormal behavior is detected, you may be prompted for additional verification or see a simulated lockout. The honeypot will record any interaction with decoy elements.

- **Logs:**  
  Anomaly events are saved to local CSV files using the `Logger` utility for further analysis.


## Novelty of Approach
- Unlike existing fraud detection systems, FraudGuard fuses *all* user behavior signals (touch, typing, app usage, movement) **on-device**.
- It uniquely integrates **honeypot decoys** within behavioral detection (rarely seen in mobile fraud defense).
- It introduces an **adversarial challenger loop** that continuously strengthens models against evolving fraud tactics.



## Project Structure

📂 FraudGuard
 ├── README.md                  → Problem, solution, installation, usage
 ├── docs/
 │    ├── architecture.md        → Diagrams + explanation
 │    ├── novelty.md             → Why unique
 │    ├── scalability.md        → Fairness, privacy, scalability
 │    ├── ethical.md
 │    ├── fusion.md
 │    ├── contributing.md
 │    └── demo_script.md         → 10-min demo plan
 ├── app/
 │    ├── TouchAgent.java        → Swipe/tap monitoring
 │    ├── UsageAgent.java        → App usage pattern monitor
 │    ├── HoneypotAgent.java     → Fake button/screen trap
 │    ├── FusionEngine.java      → Score aggregator
 │    ├── ResponseLayer.java     → Lock screen / biometric check
 │    └── Logger.java            → Logs anomalies to CSV
 ├── demo/
 │    ├── app.py
 └── assets/
      └── architecture.png       → Visual diagram

## Quick start (local demos)

### Run the Java demos (Windows cmd)

1. Compile all Java sources

```bat
javac -d . app\*.java
```

2. System demo (Touch + Typing + Fusion)

```bat
java app.TouchAgent system
```

3. Export touch features to CSV (defaults to folder "data" and prefix "normal")

```bat
java app.TouchAgent data normal
```

The exporter prints the generated CSV path when done.

### Use the TouchAgent add() API

```java
TouchAgent agent = new TouchAgent();
agent.start();

// pointerId, x, y, pressure, size
agent.add(TouchAgent.EventType.DOWN, 0, 100f, 100f, 0.6f, 22f);
agent.add(TouchAgent.EventType.MOVE, 0, 150f, 110f, 0.55f, 21f);
agent.add(TouchAgent.EventType.UP,   0, 200f, 120f, 0.0f,  0.0f);

Agent.AgentResult r = agent.getResult();
System.out.println(r.score + " " + r.explanations);

agent.stop();
```
### Fusion Score Calculation

The **FusionEngine** combines agent scores using weighted averaging and risk logic:

```
fusion_score = w1 * touch_score + w2 * typing_score + w3 * usage_score
```
- `w1`, `w2`, `w3` are agent weights (configurable).
- The fusion score is mapped to risk levels:
  - **LOW**: fusion_score > 0.7
  - **MEDIUM**: 0.4 < fusion_score ≤ 0.7
  - **HIGH**: fusion_score ≤ 0.4

Fusion scores are updated in real time and visualized in the dashboard.
### Optional: Python Streamlit demo
 ### Honeypot (Honeytrap) Mechanism

- The honeypot screen contains decoy elements (fake password field, submit button, suspicious link).
- Interactions with these elements are logged as honeypot events.
- If repeated suspicious events are detected, a notification is triggered.
- Honeypot logs are stored locally and can be exported for analysis.
- **Note:** Honeypot scores do NOT affect the fusion score; they are tracked independently.

---

## App Flow

1. **User opens the app.**
2. **Agents start monitoring behavior.**
3. **Scores are calculated and fused in real time.**
4. **Dashboard displays live scores and risk level.**
5. **Honeypot events are logged if decoy elements are triggered.**
6. 
```bat
python -m venv venv
venv\Scripts\activate
pip install -r demo\requirements.txt
cd demo
streamlit run app.py
```


## Contributing

Contributions are welcome! See [contributing.md](docs/contributing.md) for guidelines.

## Roadmap

- [ ] Expand to iOS  
- [ ] Add federated learning option  
- [ ] Integration with banking SDKs  

### Documentation

- [Architecture](docs/architecture.md)
- [Contibution](docs/contributing.md)
- [Demo Script](docs/demo_script.md)
- [Ethical Guidelines](docs/ethical.md)
- [Fusion Engine](docs/fusion.md)
- [Novelty Details](docs/novelty.md)
- [Scalability](docs/scalability.md)
## Security & Privacy

- All behavioral data is processed locally.
- No personal data is sent to external servers.
- Honeypot logs are stored securely and can be exported by authorized users.

---  

## Troubleshooting

- If dashboard charts do not appear, ensure all required views are present in `activity_dashboard.xml`.
- For build errors, verify all dependencies and view IDs.
- For honeypot issues, check event logging and notification settings.