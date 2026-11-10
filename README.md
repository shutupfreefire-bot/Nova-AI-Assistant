# Nova AI Assistant

A lightweight Android AI chat and voice assistant built with Kotlin and Jetpack Compose.

- Minimal dark chat interface with microphone and text input.
- OpenAI-compatible provider settings (NVIDIA NIM, OpenRouter and other compatible endpoints).
- Android system text-to-speech.
- Android speech recognition, preferring the on-device recognizer on Android 12+ when available.
- GitHub Actions builds a debug APK on every push to main and via manual workflow dispatch.

## Configure
In Settings, enter an OpenAI-compatible base URL, model ID and API key. NVIDIA NIM example:
- Base URL: https://integrate.api.nvidia.com/v1
- Model: nvidia/nemotron-3.5-lightning-30b-a3b

Nova does not bundle an LLM or speech model. Cloud LLM calls require your own provider key. Speech recognition is supplied by Android's installed speech service; local recognition is used only when Android reports an on-device recognizer available. Text-to-speech uses Android's system engine.

## Build
GitHub Actions builds a debug APK. Open Actions → Build Nova Android APK → latest run → Artifacts → nova-ai-debug-apk.

## Privacy
Prompts are sent to the API endpoint you configure. API keys are stored in app-private preferences on the device.

## Open-source acknowledgements
The modular voice-assistant architecture was informed by [Aria Voice Assistant](https://github.com/BigWizard94/aria-voice-assistant). No Aria source files are copied in this initial implementation. See upstream repository for its MIT license and notices.
