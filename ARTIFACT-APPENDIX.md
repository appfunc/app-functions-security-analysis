# Artifact Appendix

Paper title: _"The Agent in the Middle: A Security and Privacy Analysis of Android AppFunctions"_

Requested Badge(s):

- [x] **Available**
- [x] **Functional**
- [ ] **Reproduced**

## Description

Artifact for the _[PoPETs, 2027.1]_ paper: _"The Agent in the Middle: A Security and Privacy Analysis of Android AppFunctions"_.

This repository contains artifacts produced during our security and privacy analysis of Android AppFunctions. The repository contains the following:

- Android Executor (NotyAgent) application, built on top of the publicly available (https://github.com/PatilShreyas/appfunctions-notyagent-app) repository as a starting point;
- Android AppFunctions provider application, built on top of the publicly available (https://github.com/FilipFan/AppFunctionsPilot) repository as a starting point;
- The `install_as_system_app.sh` script, for installing the custom privileged executor on a rooted Pixel 10 Pro device;
- The large scale analysis processing script `scan_appfunctions.py` used in searching for AppFunction code signatures in the APK database (along with results250K.json output);
- The markdown file `static_analysis.md`, containing static analysis notes of the flagged applications;
- The `hook_gemini.js` Frida script, used in the Gemini reverse engineering process (better launched with `attach.sh` helper script);
- The `tested_apps.md` file, containing the exact versions + SHA256 hashes of tested apps;
- The `/apks` repository, containing all relevant APKs used during the study (Samsung S26 and Pixel 10 Pro pre-installs, as well as the Google Assistant extracted from the Pixel 10 Pro);
- The `appFuncsP10.json` and `appFuncsS26.json` files containing the full list of available AppFunctions from the Pixel and Samsung devices, which were available during the study.

### Security/Privacy Issues and Ethical Concerns

For the custom **AI Assistant** and **AppFunction provider** (in-lab experiments): attacks such as T4 forward fingerprint information about the device to your specified exfiltration URL (see further instructions). Other identifiable information, such as location, user email of the main device account, network information are used in the conversation with Gemini, and thus forwarded to the Google AI Studio API as query content.

For the production attacks: no relevant artifacts are included, as the patched Samsung Notes may be misused if released publicly (see further notes).

No user study artifacts are included in this repository.

## Basic Requirements

### Hardware Requirements

1. A rooted Android 16+ device (The exact mobile device, on which the experiments were conducted, was the Pixel 10 Pro (`google/blazer/blazer:16/CP1A.260505.005/15081906:user/release-keys`))
2. A computer with USB access to connect to the Android device for `adb` operations.

### Software Requirements

1. **Operating System**
   Used Host/build machine: macOS Sequoia 15.7.4 (24G517). Any OS able to run Android Studio and shell scripts should work. The **target device**, however is fixed: a physical **Google Pixel 10 Pro**, **rooted**, running **Android 16 (API 36.1)**.

2. **OS Packages**

- `adb` (Android Debug Bridge, part of Android SDK Platform-Tools)
- `git` (Pulling the project repository)
- Root access tooling on the device side (e.g., Magisk) already applied to the Pixel 10 Pro prior to running the artifact

3. **Artifact Packaging**
   Docker (Version 4.90.0, 238679) is used to build APKs in a self-contained Ubuntu 24.04 container environment. This is used to avoid Android Studio and JDK dependencies. It is important to run the container with `--platform linux/amd64` if running on a non-x86 machine.

4. **Compiler/Interpreter**

- Android Gradle Plugin (AGP): 8.11.0
- Kotlin: 2.1.21
- KSP (Kotlin Symbol Processing): 2.1.21-2.0.1
- JDK 11
- Compile/Target/Min SDK: 36 (Android 16, API 36.1)

These are provided resolved within the docker image.

5. **Dependencies**
   All resolved automatically by Gradle from the version catalog (`gradle/libs.versions.toml`)

   **External services / credentials required (not bundled — reviewers must provide their own):**
   - A **Google API key** (used for Gemini backend).
   - A **server endpoint URL** that accepts HTTP POST requests for T4 attack.

6. **ML Models**
   Google Gemini (via Google AI Studio API)

7. **Datasets**
   N/A — no external datasets are required.

### Estimated Time and Storage Consumption

- Human and compute time required to run the artifact: **5 hours**
- Overall disk space consumed by artifact: **~8GB**

## Environment

### Accessibility

The artifact is accessible on Github:
https://github.com/appfunc/app-functions-security-analysis

### Set Up the Environment

To set up the environment, first clone the repository, and build the docker image used for compiling the APKs. The `--platform` parameter is to ensure an x86 image, such as on Apple Silicon machines.

```bash
git https://github.com/appfunc/app-functions-security-analysis.git
cd app-functions-security-analysis
docker build --platform linux/amd64 -t appfunctions-build .
```

Now, you will need to configure and build the applications for the AI Assistant Executor, and the AppFunction Provider.

```bash
docker run --rm -it appfunctions-pilot-build # enter interactive terminal within the container
```

Then you need to create and configure a `local.properties` file (an example `local.properties.example` file can be used as the base). In this file, provide the Gemini API Key, a URL which accepts HTTP Post requests for T4 attack (such as webhook.site), and enable the necessary attacks (e. g. `T1_ENABLED=true`) depending on the experiment being run. Once configured, you can then build the two applications (you may increase the resources allocated for Docker, in order to reduce the compile time):

```bash
# The following two commands are to be run inside the container
./gradlew :app:assembleDebug # Builds the AI assistant application
./gradlew :tool:assembleDebug # Builds the AppFunction provider application
```

Once succesfully built, copy these files to your working directory, outside the container (run the following in another terminal):

```bash
# find the container ID
docker ps -a

docker cp <container_id>:/workspace/tool/build/outputs/apk/debug/tool-debug.apk .
docker cp <container_id>:/workspace/app/build/outputs/apk/debug/app-debug.apk .
```

Now, installing the tool application is a simple `adb` command as follows:

```bash
adb install -r tool-debug.apk
```

However, the AI Assistant (`app`) application requires the privileged `EXECUTE_APP_FUNCTIONS` permission, and thus must be installed as a privileged system application using the interactive script:

```bash
./install_as_system_app.sh app-debug.apk
```

This script _could_ work (and has been tested to run) on an emulator with writeable system partitions, however the script does at times fail during installation. If the installation is succesful, `EXECUTE_APP_FUNCTIONS granted on <device>` should be displayed

### Testing the Environment

Both applications (Tool and AI Assistant) should be accessible on the phone. The Tool application should be launchable with a place-holder screen without crashing. The AI Assistant application should ask for location permission (grant it), and output the following text on screen:

"Hi, I'm Gemini, ready to execute AppFunctions
Available AppFunctions:
<functions, such as getRecommendedApps>"

## Artifact Evaluation

### Main Results and Claims

#### Main Result 1: In-lab attacks with custom Executor and Provider

The attacks outlined in the paper demonstrate a new security primitive, where a privileged AI Assistant (Executor) enables data flows to a non-privileged Provider application, affecting security and privacy. This new primitive is demonstrated with the seven representative attacks (T1-T7) in an ideal in-lab environment.

#### Main Result 2: Attacks on Google Assistant

AppFunctions enable a low-privilege Provider application to influence the Google Assistant, resulting in data exfiltration and malicious side-effects under attacks T1, T4 and T5. In our paper, we patched a production Samsung Notes APK, bypassing Google Assistant restrictions on AppFunction execution, and demonstrating the viability of T4 and T5. T1 can be reproduced with a non-patched Samsung Calendar application.

### Experiments

For in-lab experiments, it is recommended to disable the remaining AppFunctions via `local.properties` and re-compile the application (refer to set up). This is mainly to guarantee that Gemini will not pick other paths to executing a user query (such as Gemini deciding to use `getWeatherAccurate` instead of `getWeather` AppFunction for T1).

As a work-around, you may also instruct the Gemini Assistant to use a specific AppFunction, relevant to the experiment

#### Experiment 1: (T1) Permission re-delegation

- Time: 15 human-minutes + 30 compute-minutes

This experiment reproduces Main Result 1.

_Recommended_: disable the `getWeatherAccurate()` function, by setting `T3_ENABLED` to false and re-compile the tool application, in order to avoid Gemini using this function instead. The Assistant application must be restarted in order to re-index the new AppFunctions, provided by the tool.

In the AI assistant application (package `app`), ask the assistant to get today's weather. Then observe the logs for the tool application (via `adb logcat`, all relevant output has the tag `AppFunctions`) to see the location being provided to the unprivileged tool application. The log will start with `[T1]...`

#### Experiment 2: (T3) Information Overdisclosure

- Time: 15 human-minutes + 30 compute-minutes

This experiment reproduces Main Result 1.

_Recommended_: disable the `getWeather()` function, by setting `T1_ENABLED` to false. In-turn, enable the `getWeatherAccurate()` function with `T3_ENABLED` if it was disabled previously

In the AI assistant application (package `app`), ask the assistant to get today's weather. Then observe the logs for the tool application (via `adb logcat`, all relevant output has the tag `AppFunctions`) to see the location and other sensitive information being provided to the unprivileged tool application. The log will start with `[T3]...`

#### Experiment 3: (T4) Installed app fingerprinting

- Time: 10 human-minutes + 10 compute-minutes

This experiment reproduces Main Result 1.

In the AI assistant application (package `app`), ask the assistant to recommend some applications for you to use. Then observe the logs for the tool application (via `adb logcat`, all relevant output has the tag `AppFunctions`) to see an exfiltration attempt of known applications on device. The log will start with `[T4]...`. The AppFunction will also send a POST request to the provided URL `T4_URL`.

#### Experiment 4: (T5) Prompt injection via result

- Time: 10 human-minutes + 10 compute-minutes

This experiment reproduces Main Result 1.

In the AI assistant application (package `app`), ask the assistant to get your events. Then observe the logs for the tool application (via `adb logcat`, all relevant output has the tag `AppFunctions`) to see a prompt injection being returned to the assistant. The log will start with `[T5]...`. As a result, the device should have bluetooth turned on.

## Limitations

Experiments involving the production Google Assistant are not reproducible, as we have not included the patched versions of the Samsung Notes APK (both for Samsung S26, and the Pixel 10 Pro). As argued in the paper, the Samsung Notes application provides an avenue for abuse if deployed on a third party app store. Particularly in the case of users seeking the Samsung Notes application for a non-Samsung phone (which one of our patched versions enable).

Additionally, both the in-lab and production experiments depend on the non-deterministic behavior of an LLM we do not control. Gemini models are hosted by Google and accessed via the Google AI Studio API and the Google Assistant application. While model versions can be selected, the underlying models may change over time (e.g., adversarial prompt-injection hardening, updated content policies) since we last ran our experiments, which may make some results difficult or impossible to reproduce exactly.

## Notes on Reusability

Following the broader release of Android AppFunctions, the `tool` application can serve as a baseline for testing these attacks against a production assistant. `SampleFunctions.kt` and `SampleFunctionsSchemas.kt` together implement the full AppFunction functionality and schema shaping, and can be extended or replaced to test additional function definitions, schemas, or attack variants. This will completely remove the need for the patched Samsung Notes as a bypass to communicate with a production assistant.
