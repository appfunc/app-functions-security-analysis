# Artifact Appendix (Required for all badges)

Paper title: _"The Agent in the Middle: A Security and Privacy Analysis of Android AppFunctions"_

Requested Badge(s):

- [x] **Available**
- [x] **Functional**
- [ ] **Reproduced**

<!-- Authors can provide this content _either_ as a separate file in their artifact
_or_ as part of their existing documentation (e.g., `README.md`). In the latter
case, you should have the same section titles as in this template.

This template includes several placeholders. When filling in this template for
their artifact, the authors should:

1. Remove this note.
2. Delete the sections that are _not_ required for the badge(s) they are
   applying for.
3. Omit suffixes of the form "(required/encouraged for badge ...)" from the
   section titles.
4. Authors should not leave the placeholder descriptions initially provided with
   this file into the submitted version with their artifact.

While this template is provided for artifact review, you should write your
instructions for someone trying to reuse your artifact in the future (i.e., not
an artifact reviewer). -->

## Description (Required for all badges)

Artifact for the _[PoPETs, 2027.1]_ paper: _"The Agent in the Middle: A Security and Privacy Analysis of Android AppFunctions"_.

This repository contains artifacts produced during our security and privacy analysis of Android AppFunctions. The repository contains the following:

- Android Executor (NotyAgent) application, built on top of the publicly available (https://github.com/PatilShreyas/appfunctions-notyagent-app) repository as a starting point;
- Android AppFunctions provider application, built on top of the publicly available (https://github.com/FilipFan/AppFunctionsPilot) repository as a starting point;
- The `install_as_system_app.sh` script, for installing the custom privileged executor on a rooted Pixel 10 Pro device;
- The large scale analysis processing script `scan_appfunctions.py` used in searching for AppFunction code signatures in the APK database (along with results250K.json output);
- The PDF `static_analysis.md`, containing static analysis notes of the flagged applications;
- The `hook_gemini.js` Frida script, used in the Gemini reverse engineering process (better launched with `attach.sh` helper script);
- The `tested_apps.md` file, containing the exact versions + SHA256 hashes of tested apps;
- The `/apks` repository, containing all relevant APKs used during the study (Samsung S26 and Pixel 10 Pro pre-installs, as well as the Google Assistant extracted from the Pixel 10 Pro);
- The `appFuncsP10.json` and `appFuncsS26.json` files containing the full list of available AppFunctions from the Pixel and Samsung devices, which were available during the study.

### Security/Privacy Issues and Ethical Concerns (Required for all badges)

For the custom **AI Assistant** and **AppFunction provider** (in-lab experiments): attacks such as T4 forward fingerprint information about the device to your specified exfiltration URL (see further instructions). Other identifiable information, such as location, user email of the main device account, network information are used in the conversation with Gemini, and thus forwarded to the Google AI Studio API as query content.

For the production attacks: no relevant artifacts are included, as the patched Samsung Notes may be misused if released publicly (see further notes).

No user study artifacts are included in this repository.

## Basic Requirements (Required for Functional and Reproduced badges)

<!-- For both sections below, if you are giving reviewers remote access to special
hardware (e.g., Intel SGX v2.0) or proprietary software (e.g., Matlab R2025a)
for the purpose of the artifact evaluation, do not provide these instructions
here but rather in the corresponding submission field on HotCRP. -->

### Hardware Requirements (Required for Functional and Reproduced badges)

Replace this with the following:

1. A rooted Android 16+ device (The exact mobile device, on which the experiments were conducted, was the Pixel 10 Pro)
2. A computer with USB access to connect to the Android device for `adb` access.

<!-- 1. A list of the _minimal hardware requirements_ to execute your artifact. If no
   specific hardware is needed, then state "Can run on a laptop (No special
   hardware requirements)". You may state how a researcher could gain access to
   that hardware, e.g., by buying, renting, or even emulating it.
2. When applying for the "Reproduced" badge, list _the specifications of the
   hardware_ on which the experiments reported in the paper were performed. This
   is especially relevant in cases were results might be influenced by the
   hardware used (e.g., latency, bandwidth, throughput experiments, etc.).
3. If your experiments require significant hardware resources (e.g., more than 8
   CPU cores, more than 16GB RAM) consider providing experiments with reduced
   scale, especially for the "Functional" badge. -->

### Software Requirements (Required for Functional and Reproduced badges)

Replace this with the software required to run your artifact and its versions,
as follows.

1. **Operating System**
   Host/build machine: macOS Sequoia 15.7.4 (24G517). Any OS able to run Android Studio and shell scripts should work. The **target device**, however is fixed: a physical **Google Pixel 10 Pro**, **rooted**, running **Android 16 (API 36.1)**.

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

### Estimated Time and Storage Consumption (Required for Functional and Reproduced badges)

- Human and compute time required to run the artifact: **5 hours**
- Overall disk space consumed by artifact: **~10GB**

## Environment (Required for all badges)

### Accessibility (Required for all badges)

The artifact is accessible on Github:
https://github.com/appfunc/app-functions-security-analysis

### Set Up the Environment (Required for Functional and Reproduced badges)

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

Then you need to create and configure a `local.properties` file (an example `local.properties.example` file can be used as the base). In this file, provide the Gemini API Key, a URL which accepts HTTP Post requests for T4 attack (such as webhook.site), and enable the necessary attacks (e. g. `T1_ENABLED=true`) depending on the experiment being run. Once configured, you can then build the two applications:

```bash
./gradlew :app:assembleDebug # Builds the AI assistant application
./gradlew :tool:assembleDebug # Builds the AppFunction provider application
```

Once succesfully built, copy these files to your working directory, outside the container:

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

However, the AI Assistant (`app`) application requires the privileged `EXECUTE_APP_FUNCTIONS` permission, and thus must be installed as a privileged system application using our interactive script:

```bash
./install_as_system_app.sh app-debug.apk
```

This script _could_ work (and has been tested to run) on an emulator with writeable system partitions, however the script does at times fail during installation. If the installation is succesful, you should see `EXECUTE_APP_FUNCTIONS granted on <device>`

### Testing the Environment (Required for Functional and Reproduced badges)

Both applications (Tool and AI Assistant) should be accessible on the phone. The Tool application should be launchable with a place-holder screen without crashing. The AI Assistant application should output the following text on screen:

"Hi, I'm Gemini, ready to execute AppFunctions
Available AppFunctions:
<functions, such as getRecommendedApps>"

## Artifact Evaluation (Required for Functional and Reproduced badges)

This section should include all the steps required to evaluate your artifact's
functionality and validate your paper's key results and claims. Therefore,
highlight your paper's main results and claims in the first subsection. And
describe the experiments that support your claims in the subsection after that.

### Main Results and Claims

List all your paper's results and claims that are supported by your submitted
artifacts.

#### Main Result 1: Name

Describe the results in 1 to 3 sentences. Mention what the independent and
dependent variables are; independent variables are the ones on the x-axes of
your figures, whereas the dependent ones are on the y-axes. By varying the
independent variable (e.g., file size) in a given manner (e.g., linearly), we
expect to see trends in the dependent variable (e.g., runtime, communication
overhead) vary in another manner (e.g., exponentially). Refer to the related
sections, figures, and/or tables in your paper and reference the experiments
that support this result/claim. See example below.

#### Main Result 2: Example Name

Our paper claims that when varying the file size linearly, the runtime also
increases linearly. This claim is reproducible by executing our
[Experiment 2](#experiment-2-example-name). In this experiment, we change the
file size linearly, from 2KB to 24KB, at intervals of 2KB each, and we show that
the runtime also increases linearly, reaching at most 1ms. We report these
results in "Figure 1a" and "Table 3" (Column 3 or Row 2) of our paper.

### Experiments

List each experiment to execute to reproduce your results. Describe:

- How to execute it in detailed steps.
- What the expected result is.
- How long it takes to execute in human and compute times (approximately).
- How much space it consumes on disk (approximately) (omit if <10GB).
- Which claim and results does it support, and how.

#### Experiment 1: Name

- Time: replace with estimate in human-minutes/hours + compute-minutes/hours.
- Storage: replace with estimate for disk space used (omit if <10GB).

Provide a short explanation of the experiment and expected results. Describe
thoroughly the steps to perform the experiment and to collect and organize the
results as expected from your paper (see example below). Use code segments to
simplify the workflow, as follows.

```bash
python3 experiment_1.py
```

#### Experiment 2: Example Name

- Time: 10 human-minutes + 3 compute-hours
- Storage: 20GB

This example experiment reproduces
[Main Result 2: Example Name](#main-result-2-example-name), the following script
will run the simulation automatically with the different parameters specified in
the paper. (You may run the following command from the example Docker image.)

```bash
python3 main.py
```

Results from this example experiment will be aggregated over several iterations
by the script and output directly in raw format along with variances and
standard deviations in the `output-folder/` directory. You will also find there
the plots for "Figure 1a" in `.pdf` format and the table for "Table 3" in `.tex`
format. These can be directly compared to the results reported in the paper, and
should not quantitatively vary by more than 5% from expected results.

## Limitations (Required for Functional and Reproduced badges)

Describe which steps, experiments, results, graphs, tables, etc. are _not
reproducible_ with the provided artifact. Explain why this is not
included/possible and argue why the artifact should _still_ be evaluated for the
respective badges.

## Notes on Reusability (Encouraged for all badges)

First, this section might not apply to your artifacts. Describe how your
artifact can be used beyond your research paper, e.g., as a general framework.
The overall goal of artifact evaluation is not only to reproduce and verify your
research but also to help other researchers to re-use and extend your artifacts.
Discuss how your artifacts can be adapted to other settings, e.g., more input
dimensions, other datasets, and other behavior, through replacing individual
modules and functionality or running more iterations of a specific module.
