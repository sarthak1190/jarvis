<div align="center">

# 🤖 J.A.R.V.I.S — Advanced Autonomous AI Assistant for macOS

[![Java Support](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-macOS%20%28M--Series%29-lightgrey.svg)]()

**J.A.R.V.I.S** is a fully functional, highly autonomous, voice-controlled, and context-aware artificial intelligence assistant specifically engineered for macOS. Built with cutting-edge **100% Free & Local AI** (Ollama + Whisper), it operates securely and instantly on Apple Silicon hardware with zero cloud dependencies.

[Features](#-key-features) • [Architecture](#%EF%B8%8F-system-architecture) • [Workflow](#-application-workflow) • [Installation](#-how-to-run-locally) • [Contribution Guide](#%EF%B8%8F-how-to-contribute)

</div>

---

## 🌟 Key Features

* **🎙️ Voice Recognition (100% Offline):** Powered by the Python `openai-whisper` CLI running completely locally. Instantly transcribes audio into highly accurate text.
* **🧠 Context-Aware Brain (Ollama):** Integrates natively with local LLMs (like `llama3.1` or `phi3`) to parse natural language, handling complex compound intents and conversational memory seamlessly.
* **⚡ Parallel Multi-Intent Processing:** Ask Jarvis to *"Open Spotify, check my battery, and tell me the weather"*. He interprets it into three sub-actions and flawlessly executes them **concurrently** via Java `CompletableFuture` pools.
* **👂 Acoustic Clap Detection:** Built-in Background RMS analyzer listens for double-claps `👏👏` to automatically wake the microphone array and await your command without button presses.
* **🤖 Proactive Autonomous Alerts:** Jarvis quietly monitors your system's `pmset` daemon in the background and proactively tells you to plug in your Mac when the battery falls to dangerously low levels.
* **🧩 Plugin Strategy Architecture:** Adding a new capability is as easy as dropping a new Java class implementing the `CommandStrategy` interface. It is auto-discovered by the Spring container on boot.
* **🔐 Secure macOS Sandboxing:** Native Terminal and AppleScript commands are rigorously routed through a whitelist/blacklist adapter ensuring no bad shell injections execute.

---

## 🏗️ System Architecture

Jarvis is structured around a modular, highly decoupled service-oriented architecture using Spring Boot.

### Directory Structure Overview
```text
src/main/java/com/jarvis/
├── JarvisApplication.java          # Spring Boot Main Entry Point
├── adapter/
│   ├── os/                         # Native macOS System & AppleScript bridging
│   ├── speech/                     # Microphone Capture, Whisper processing
│   └── tts/                        # macOS native 'say' command wrapper
├── config/                         # Type-safe application.yml binding properties
├── controller/                     # REST Endpoints and WebSocket message brokers
├── engine/
│   ├── decision/                   # AI Proactive Interceptor (Guards & Evaluators)
│   ├── execution/                  # Concurrent Multi-Intent Executor
│   │   └── strategies/             # 20+ plugin commands (Weather, Volume, Calendar etc.)
│   ├── intent/                     # Core NLP Prompting Engine -> Local Ollama Endpoint
│   ├── memory/                     # Rolling Conversational Context Context Buffer
│   └── scheduler/                  # Background background Daemons (Battery alerts)
├── dto/                            # Data Transfer Objects (IntentResult, CommandResult)
└── service/                        # Master Orchestrators (JarvisOrchestratorService, ClapDetector)
```

---

## 🔄 Application Workflow

When you issue a command, here is exactly what happens internally in fractions of a second:

1. **Trigger:** You click the *Voice button* OR trigger the *Double-Clap Acoustic listener*.
2. **Capture:** The `AudioCaptureAdapter` activates your Mac's microphone and writes a `.wav` file into a temp cache.
3. **Speech-to-Text:** The `WhisperSpeechAdapter` safely bypasses python SSL constraints and executes the local `whisper` CLI, yielding transcribed text.
4. **Context Building:** The Transcribed text is dumped into the `ConversationMemoryService` along with your recent historical context logic.
5. **Intent Parsing:** `IntentRecognitionEngine` sends the payload securely to `localhost:11434` (Ollama). The local LLM structures a JSON syntax mapping the intent.
6. **Execution Pipeline:** `JarvisOrchestratorService` dispatches the intent into the `CommandExecutionEngine`. If compound, it dispatches thread pools asynchronously to process mapped `CommandStrategy` classes.
7. **Synthesis:** Successful execution string payloads are formatted by `ResponseGeneratorService`.
8. **TTS Output:** `MacOsTtsAdapter` taps into the `say` binary and vocalizes the result via Samantha.

---

## 🚀 How to Run Locally

### Prerequisites
1. **Java 21+** and **Maven** installed on your Mac. 
2. **Ollama:** Installed (`brew install --cask ollama`) to provide the LLM logic cleanly. Ensure you run `ollama run llama3.1` (or whichever local model you prefer).
3. **Python & Whisper:** You must install Python and OpenAI-Whisper:
   ```bash
   brew install ffmpeg
   pip3 install -U openai-whisper
   ```

### 1. Clone & Pull The Service
To download Jarvis locally onto your device and stay up to date:
```bash
# Clone the repository
git clone https://github.com/your-username/jarvis.git

# Navigate into the project directory
cd jarvis

# If you ever need to fetch latest changes from someone else's contribution:
git pull origin main
```

### 2. Configuration (`application.yml`)
Navigate to `src/main/resources/application.yml`. Make sure your system configurations map properly.
* The `openai` block MUST have `base-url` pointing to `http://localhost:11434/v1` for Ollama.
* The `whisper.local-binary-path` MUST be set to `whisper`.

### 3. Build & Run
First, compile and build the package to verify your Java modules.
```bash
mvn clean install
```
Next, boot up the Jarvis Daemon!
```bash
mvn spring-boot:run
```

**Accessing Jarvis:**
Once you see `Jarvis AI Assistant is ONLINE` in the console:
* Open Google Chrome/Safari and visit: **http://localhost:8080**
* The stunning Arc-Reactor UI awaits you!

---

## 🛠️ How to Contribute

We love making Jarvis smarter! Since Jarvis utilizes the **Strategy Design Pattern**, adding a new custom command feature takes exactly ONE file and ZERO rewrites to the core engine.

### Step-by-Step Contribution Guide

#### 1. Fork and Branch
1. Fork the repo to your personal GitHub.
2. Clone it and create a feature branch: `git checkout -b feature/my-cool-command`.

#### 2. Create your new Integration (Strategy)
Create a new Java class inside `src/main/java/com/jarvis/engine/execution/strategies`.

```java
import com.jarvis.dto.CommandResult;
import com.jarvis.dto.IntentResult;
import com.jarvis.engine.execution.CommandStrategy;
import org.springframework.stereotype.Component;

@Component
public class OpenNetflixStrategy implements CommandStrategy {

    @Override
    public String getActionType() {
        return "OPEN_NETFLIX"; // The exact action key the LLM will provide
    }

    @Override
    public CommandResult execute(IntentResult intent) {
        // Your logic here:
        String process = "open https://netflix.com";
        return CommandResult.success("Opening Netflix for you now, sir.", getActionType());
    }
}
```

#### 3. Teach the NLP Brain (Optional but recommended)
Head to `IntentRecognitionEngine.java` and find the `SYSTEM_PROMPT` block. 
Add a quick line so the brain knows it understands your new intent:
`- OPEN_NETFLIX: Opens netflix via browser.`

#### 4. Test it!
Run `mvn spring-boot:run` and say "Hey, open Netflix!". Spring Boot's Dependency Injection instantly detects your new class dynamically and executes it!

#### 5. Submit your Pull Request
1. Keep code clean and write brief JavaDocs describing what your strategy solves.
2. Commit your code (`git commit -m "Adds OpenNetflix strategy integration"`)
3. Push to your branch (`git push origin feature/my-cool-command`).
4. Head to GitHub and click **Create Pull Request**!

---
<div align="center">
<i>"Sometimes you gotta run before you can walk."</i> 🤖
</div>