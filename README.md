# Takeaway T-Connect Terminal V2 Printer App

Small app for printing on the Takeaway T-Connect Terminal V2. The internal printer is connected via USB and uses a proprietary protocol.

I have a blog post on the device, however it does not talk about printing: [Playing Half-Life on a Takeaway payment terminal](https://blog.nikolan.net/posts/takeaway-terminal/)

If printing with a bigger font size, faster speed, higher contrast, or similarly intensive settings, the terminal may reboot. I'm running mine with a 30W power adapter, and printing on regular font size caused reboots. The stable settings for me are small font, contrast `50`, speed `1`, with a delay between lines.

The protocol was reverse engineered by Codex/GPT-5.5 Medium in about 20 minutes. Having adb access, it pulled the vendor APKs, decompiled the printer-related classes, inspected the USB descriptors, and figured out the protocol.

## Build

This build script is a minimal one meant to work on my machine. It does not require a full Android Studio/Gradle project.

Required on PATH:

- `javac`
- `jarsigner`
- `keytool`
- `apktool`

```sh
./build.sh
```

Output:

```text
build/TConnectPrinter.apk
```

Install:

```sh
adb install build/TConnectPrinter.apk
```
