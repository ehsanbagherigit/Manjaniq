<div align="center">

<img src="https://raw.githubusercontent.com/ehsanbagherigit/Manjaniq/master/assets/logos/manjaniq_logo.png" alt="Manjaniq Logo" width="120" height="120">

# Manjaniq

**An Advanced Android SOCKS5 & Loopback Proxy Client powered by the Aether Core.**

[![CI/CD Status](https://github.com/ehsanbagherigit/manjaniq/actions/workflows/build-debug.yml/badge.svg)](https://github.com/ehsanbagherigit/manjaniq/actions)
[![Android Min SDK](https://img.shields.io/badge/Min%20SDK-29%28Android%2010%29-blue.svg)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

</div>

---

## 📖 About the Project

**Manjaniq** is a high-performance Android proxy client engineered for local SOCKS5 and loopback traffic routing (operating strictly without VPN/TUN layers). Built around the native **Aether Core** framework and a modern **Jetpack Compose** interface, Manjaniq provides a stable, secure, and lightweight proxy management experience.

---

## ✨ Key Features

* **Pure SOCKS5 / Loopback Architecture:** Binds directly to a local loopback port (`127.0.0.1:1819`) without invoking system-wide VPN services.
* **Aether Core Integration:** Fully ported core modules, identity managers (`AetherIdentityManager`), key parsers, and command renderers matching the reference design.
* **Psiphon Support with Conditional UI:** Integrated Psiphon tunneling capabilities featuring dynamically conditioned **Exit Country Codes** (visible and active exclusively when Psiphon is toggled ON).
* **Advanced Proxy Settings:** Comprehensive scan modes, IP version selector, and UDP obfuscation controls.
* **Clean UI & Modern Design:** Built with Jetpack Compose and Material Design 3, completely stripped of deprecated fields (such as remote "remark" inputs) for a streamlined user experience.

---

## 🛠️ Tech Stack

* **Language:** Kotlin (Android UI & Application Logic) / Rust (`libaether.so` JNI Native Core).
* **UI Framework:** Jetpack Compose & Material 3.
* **Interfacing:** JNI (Java Native Interface) bridge for high-speed native execution.
* **CI/CD & Automation:** GitHub Actions (automated compilation, artifact packaging, and emulator smoke tests).

---

## 📱 Download & Installation

You can download the latest pre-compiled debug APKs directly from the **GitHub Actions** workflow:

1. Navigate to the [Actions](https://github.com/ehsanbagherigit/manjaniq/actions) tab in the repository.
2. Select the latest successful workflow run (`Build Debug APK`).
3. Download the architecture-specific artifacts:
   * **`manjaniq-arm64-debug-apk`** (For modern ARM64 devices)
   * **`manjaniq-x86_64-debug-apk`** (For emulators and x86_64 systems)

---

## 🏗️ Project Structure

```text
com.manjaniq/
├── core/           # Aether core management, identity handlers, and argument renderers
├── ui/             # Jetpack Compose screens, view models, and UI components
├── native/         # JNI bridge implementations and native library bindings
└── data/           # Storage, preferences, and co
nfiguration handlers
🤝 Contributing
Contributions, feature requests, and bug reports are welcome! Feel free to open an issue or submit a pull request.
📄 License
This project is licensed under the MIT License. See the LICENSE file for details.
