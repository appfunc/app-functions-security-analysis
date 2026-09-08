# Android AppFunctions Security Analysis Artifacts

This repository contains artifacts for the paper "The Agent in the Middle: A Security and Privacy Analysis of Android AppFunctions".

The repository contains the following:
- Android Executor (NotyAgent) application, built on top of the publicly available (https://github.com/PatilShreyas/appfunctions-notyagent-app) repository as a starting point
- Android AppFunctions provider application, built on top of the publicly available (https://github.com/FilipFan/AppFunctionsPilot) repository as a starting point
- The `install_as_system_app.sh` script, for installing the privileged executor on a rooted Pixel 10 Pro device
- The large scale analysis processing script `scan_appfunctions.py` used in searching for AppFunction code signatures in the APK database (along with results250K.json output)
- The PDF `static_analysis.md`, containing static analysis notes of the flagged applications
- The `hook_gemini.js` Frida script, used in the Gemini reverse engineering process (better launched with `attach.sh` helper script)
- The `tested_apps.md` file, containing the exact versions + SHA256 hashes of tested apps
- The `appFuncsP10.json` and `appFuncsS26.json` files containing the full list of available AppFunctions from the Pixel and Samsung devices, which were available during the study

*Note: In the current Android Developer Previews, Executor apps must be installed as **Privileged System Apps** to execute AppFunctions.*

*Note 2: The executor application requires a valid Google AI Studio API key for Gemini models. Used in `app/ui/screen/AgentChatViewModel.kt`*

---

## Installation of privileged executor (Android PoC)
1. Open your terminal in the root of this project.
2. Make the script executable:
   ```bash
   chmod +x install_as_system_app.sh
   ```
   
3. Run the script
   ```bash
   ./install_as_system_app.sh
   ```
4. Select which rooted device or AVD to use.

*Note: the tool application can be run as-is in Android Studio. It requires no elevated permissions or specific API keys.*

--- 

## Samsung Notes patching

We do not disclose exact patching scripts, or patched APKs which are invocable by a privileged 
executor. Samsung Notes runs natively (with Samsung proprietary libraries) on the S26. For the
Pixel 10 Pro we required two changes to have it install:

- **AndroidManifest.xml**: changed `required="false"` for the com.samsung.device library
- **res/values/bools.xml**: changed `support_settings_search` to `false`, as it uses the proprietary Samsung code which we did not ship.

The application is unstable, and crashes on nearly all interactions. However, for the AppFunctions analysis this is irrelevant. The only
code path that we needed to work was the AppFunction -> patched code -> result dispatch pipeline.