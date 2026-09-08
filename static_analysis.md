# AppFunctions analysis notes (250k Google Play sample)

### ShareLive (com.samsung.android.app.sharelive)

**Source**: Samsung S26 preinstall  
**Description**: has two appFunctions (`shareFilesToNearbyDevice` and `findNearbyDevices`)

| Guideline | Violates? | Findings |
| :---- | :---- | :---- |
| AppFunctions that are exposed benefit from natural language instructions, as compared to manual UI interaction | Yes | `shareFilestoNearbyDevice` would require both instructing the LLM what files need to be shared, as well as enumerating nearby devices, and then selecting which (if more than one) should receive this file. Seems there is more room for error than simply using the UI. |
| AppFunctions only give the executor access to only the data and actions that are required to execute a task |  | Inconclusive \- `shareFilesToNearbyDevice` follows this, but `findNearbyDevices` has too many obfuscated variables it seems. The lack of a NL schema makes this even more difficult |
| AppFunctions do not disclose highly personal or confidential data. If it is exposed, explicit user consent is granted |  | Inconclusive \- `shareFilesToNearbyDevice` follows this, but `findNearbyDevices` has too many obfuscated variables it seems. The lack of a NL schema makes this even more difficult |
| Destructive actions require user confirmation | No | No destructive actions, therefore no violation |
| AppFunctions are disabled by default, and are enabled at runtime by explicit logic checks | Yes | Both functions enabled by default |
| AppFunctions that contain long-running operations are async functions | No | Partially decompiled functions exhibit coroutine behaviours (also includes coroutine errors) |

**Other**: overall very large AppFunctions, hard to analyze due to lack of information in decompiled code. Could infer some parameters, but not all. Seems to be for internal use right now, will test in dynamic analysis.

### SamsungDialer (com.samsung.android.dialer)

**Source**: Samsung S26 preinstall  
**Description**: the “phone” app from Samsung. Exposes regular phone actions, such as making a call, blocking a call, showing call records, finding persons

| Guideline | Violates? | Findings |
| :---- | :---- | :---- |
| AppFunctions that are exposed benefit from natural language instructions, as compared to manual UI interaction | No | Almost no schemas, but methods are pretty self descriptive and would be easy to execute with a single NL instruction |
| AppFunctions only give the executor access to only the data and actions that are required to execute a task |  | Inconclusive \- obfuscation |
| AppFunctions do not disclose highly personal or confidential data. If it is exposed, explicit user consent is granted |  | Inconclusive \- obfuscation |
| Destructive actions require user confirmation | Yes | `deleteCallRecords` does not seem to have a confirmation/anything. It just takes a list as input and deletes them. |
| AppFunctions are disabled by default, and are enabled at runtime by explicit logic checks | Yes | All are enabled by default |
| AppFunctions that contain long-running operations are async functions | Yes | The `findPersons` method appears to be blocking. Depending on the sample size of persons in contacts, this could be a long-running operation |

**Other**: Decompiled code is hard to read, methods are pretty large. I did observe permission checks on the tool side, so it’s not like the method is getting contacts without permission.

### Samsung Internet Browser (com.sec.android.app.sbrowser)

**Source**: Samsung S26 preinstall  
**Description**: Browser application. All AppFunction methods are located within com.sec.android.app.sbrowser.cross\_app\_action.BrowserFunctions. More approachable analysis, as the source code is not completely obfuscated during decompilation

| Guideline | Violates? | Findings |
| :---- | :---- | :---- |
| AppFunctions that are exposed benefit from natural language instructions, as compared to manual UI interaction | No | All exposed methods seem to benefit from NL instructions |
| AppFunctions only give the executor access to only the data and actions that are required to execute a task | \- | Inconclusive. Some variables are obfuscated and following the path of the Model leads to a GenericDocument. |
| AppFunctions do not disclose highly personal or confidential data. If it is exposed, explicit user consent is granted | Yes | If logcat usage for input params is considered sensitive \- then they disclose it. Otherwise inconclusive. Also if we consider browsing history highly confidential \- then violation is present. |
| Destructive actions require user confirmation | Yes | `deleteBookmarks` does not exhibit code that checks for confirmation |
| AppFunctions are disabled by default, and are enabled at runtime by explicit logic checks | Yes | All enabled by default |
| AppFunctions that contain long-running operations are async functions | No | Makes use of coroutines for `getHistories` method which may be long-running |

**Other**: cant create bookmark in “Secret mode” which is samsung browsers incognito. They seem to be enabled, but gemini wont execute them (must be gated). Unlike Samsung Notes \- it does not check if screen is locked for sensitive actions, such as tab retrieval. There’s a lot of references to schemas (which i cant find in natural language), that Samsung is using that are under the google package \- no information online (e. g. com.google.android.appfunctions.schema.common.v1.browser.FindTabsParamsx)

### PhotoEditor AI full (com.sec.android.mimage.uid.photoretouching)

**Source**: Samsung S26 preinstall  
**Description**: AI photo editor

| Guideline | Violates? | Findings |
| :---- | :---- | :---- |
| AppFunctions that are exposed benefit from natural language instructions, as compared to manual UI interaction | Yes | No schema available, as well as the function names not being very descriptive. Looking at the code it does retouching \- i find it hard to see how the user can describe the retouching process (unless “make it look good” is enough of an instruction) |
| AppFunctions only give the executor access to only the data and actions that are required to execute a task | No | It appears that currently passed/returned parameters are tied to the task |
| AppFunctions do not disclose highly personal or confidential data. If it is exposed, explicit user consent is granted | \- | Logs URI, filter name to logcat, but other than that we do definitively can not say |
| Destructive actions require user confirmation | No | No explicitly destructive actions |
| AppFunctions are disabled by default, and are enabled at runtime by explicit logic checks | Yes | App functions enabled by default |
| AppFunctions that contain long-running operations are async functions | No | They all spawn an intent, so execution flow is passed somewhere else |

**Other**: photo editing app, Gemini does not invoke.

### My Files (com.sec.android.app.myfiles)

**Source**: Samsung S26 preinstall  
**Description**: File manager

| Guideline | Violates? | Findings |
| :---- | :---- | :---- |
| AppFunctions that are exposed benefit from natural language instructions, as compared to manual UI interaction | No | Function names seem helpful to a user, operating on natural language instructions alone. File managers (especially third party ones) are notorious for being hard to navigate |
| AppFunctions only give the executor access to only the data and actions that are required to execute a task |  | Inconclusive |
| AppFunctions do not disclose highly personal or confidential data. If it is exposed, explicit user consent is granted |  | Inconclusive |
| Destructive actions require user confirmation | No | No destructive actions, so no violation by default |
| AppFunctions are disabled by default, and are enabled at runtime by explicit logic checks | Yes | All functions enabled by default |
| AppFunctions that contain long-running operations are async functions |  | Inconclusive |

**Other**: hard to analyze \- almost all method names and params are lost. But the implementation is weird \- the methods CHECK for the functionId, before executing, this seems either too deep (decompiled) code, or that's how Samsung implemented their AppFunction invocations \- a general catch all method where they parse by functionId. Once again \- out of guidelines. The find files method seems to also pull files from the cloud in their results, not just local.

### Android GMS (com.google.android.gms)

**Source**: Samsung S26 preinstall  
**Description**: Seems to be a settings application (device state, etc.)

| Guideline | Violates? | Findings |
| :---- | :---- | :---- |
| AppFunctions that are exposed benefit from natural language instructions, as compared to manual UI interaction | No | It has NL descriptions in the schema for the assistant. The first application (from S26) so far to exhibit this behaviour. As well it has useful functions that could be triggered by a small NL instruction. |
| AppFunctions only give the executor access to only the data and actions that are required to execute a task |  | Inconclusive |
| AppFunctions do not disclose highly personal or confidential data. If it is exposed, explicit user consent is granted |  | Inconclusive |
| Destructive actions require user confirmation |  | Inconclusive |
| AppFunctions are disabled by default, and are enabled at runtime by explicit logic checks | No | First application to contain enabledByDefault false. |
| AppFunctions that contain long-running operations are async functions |  | Inconclusive |

**Other**: I’ve used the appFunctions to manually change settings on Pixel Pro 10 (such as battery show percentage, etc.). Decompiled APK is mangled, hard to infer guidelines. Contains google wallet AppFunctions (add card), google fit (get data). This application has a lot of power, because it can control some settings via device state. Overall \- very mangled decompilation (jadx reaches its limits on some methods)

### Clock package (com.sec.android.app.clockpackage)

**Source**: Samsung S26 preinstall  
**Description**: Clock app

| Guideline | Violates? | Findings |
| :---- | :---- | :---- |
| AppFunctions that are exposed benefit from natural language instructions, as compared to manual UI interaction | No | No NL schemas, but function names are self explanatory. |
| AppFunctions only give the executor access to only the data and actions that are required to execute a task | No | ALL but one function seems to demand more than is needed. Seems to return |
| AppFunctions do not disclose highly personal or confidential data. If it is exposed, explicit user consent is granted | No | Its a clock/timer app, no confidential data is contained. Does not violate by default |
| Destructive actions require user confirmation | Yes | `deleteTimer` does not seem to exhibit confirmation code (input context and list of timers to delete) |
| AppFunctions are disabled by default, and are enabled at runtime by explicit logic checks | Yes | It’s a clock app. No logic is required to enable “setAlarm” at runtime? What would be the case for us to disallow the user to set an alarm? A timer? |
| AppFunctions that contain long-running operations are async functions |  | Inconclusive |

**Other**: All functions seem to be included in p042cj.C2285e (JADX); Lots of symbols lost during decompilation, hard to infer most details.

### Samsung reminders (com.samsung.android.app.reminder)

**Source**: Samsung S26 preinstall  
**Description**: Samsung reminders app

| Guideline | Violates? | Findings |
| :---- | :---- | :---- |
| AppFunctions that are exposed benefit from natural language instructions, as compared to manual UI interaction | No | No NL schemas. Function names are descriptive of task at hand |
| AppFunctions only give the executor access to only the data and actions that are required to execute a task | No | Returned data seems to be tied to the domain |
| AppFunctions do not disclose highly personal or confidential data. If it is exposed, explicit user consent is granted | \- | Inconclusive |
| Destructive actions require user confirmation | \- | Inconclusive |
| AppFunctions are disabled by default, and are enabled at runtime by explicit logic checks | Yes | Samsung seems to enjoy enabling AppFunctions by default |
| AppFunctions that contain long-running operations are async functions | \- | Inconclusive |

**Other**: AppFunctions contained in com.samsung.android.app.reminder.data.crossappactions.C13367w (JADX).  Lots of symbols lost during decompilation, hard to infer most details.

### Samsung contacts (com.samsung.android.app.contacts)

**Source**: Samsung S26 preinstall  
**Description**: Samsung contacts app

| Guideline | Violates? | Findings |
| :---- | :---- | :---- |
| AppFunctions that are exposed benefit from natural language instructions, as compared to manual UI interaction | No | No NL schemas. Function names are descriptive of task at hand |
| AppFunctions only give the executor access to only the data and actions that are required to execute a task | \- | Inconclusive |
| AppFunctions do not disclose highly personal or confidential data. If it is exposed, explicit user consent is granted | \- | Inconclusive |
| Destructive actions require user confirmation | \- | Inconclusive |
| AppFunctions are disabled by default, and are enabled at runtime by explicit logic checks | Yes | All enabled by default |
| AppFunctions that contain long-running operations are async functions | \- | Inconclusive |

**Other**: another weird implementation of AppFunction invocation \- a `case` structure that matches (string) the invoked function id (appfunctions\_aggregated\_deps.C4913x14e70088). Lots of symbols lost during decompilation, hard to infer most details. The case structure is more difficult to analyze 

### Samsung Gallery 2018 (com.sec.android.gallery3d)

**Source**: Samsung S26 preinstall  
**Description**: Samsung gallery application

| Guideline | Violates? | Findings |
| :---- | :---- | :---- |
| AppFunctions that are exposed benefit from natural language instructions, as compared to manual UI interaction | Yes | Has NL instructions for appFunction schemas. HOWEVER, includes deprecated functions (NL instructions point the agent to NOT use a function, and use another one instead) |
| AppFunctions only give the executor access to only the data and actions that are required to execute a task | Yes | `showMediaItem` returns an entire activity (intent to show image/media). Which might make sense (open external app), but why not just give an UriGrant and then let the assistant figure out the best way to show media. Activity has more data than required for `showMediaItem`, but it’s a design choice it seemsFor example there’s loadMedia \- sounds more appropriate for an Intent returning method |
| AppFunctions do not disclose highly personal or confidential data. If it is exposed, explicit user consent is granted | No | Content uris get logged via logcat, |
| Destructive actions require user confirmation | No | Seems to contain no delete/remove destructive actions |
| AppFunctions are disabled by default, and are enabled at runtime by explicit logic checks | Yes | All AppFunctions enabled by default |
| AppFunctions that contain long-running operations are async functions | No | To my best knowledge, all long running operations are async |

**Other**: Some weird implementation inconsistencies \- some functions check whether `appFunctionContext` is null, others don't. In addition: there is an AppFunctionUriGrant method, that returns an UriGrant with read perms to the agent. While completely normal, another malicious app, if injection succeeds, could arbitrary read any image.

### Google Permission Controller (com.android.permissioncontroller)

**Source**: Samsung S26 preinstall  
**Description**: idk

| Guideline | Violates? | Findings |
| :---- | :---- | :---- |
| AppFunctions that are exposed benefit from natural language instructions, as compared to manual UI interaction | No | Just a single method to get permissions device state. Might not be a standalone end goal for an NL instruction, but an intermediary one. |
| AppFunctions only give the executor access to only the data and actions that are required to execute a task | Yes | If the goal is to get permission state \- then yes, only that is disclosed |
| AppFunctions do not disclose highly personal or confidential data. If it is exposed, explicit user consent is granted | \- | Inconclusive |
| Destructive actions require user confirmation | No | No destructive actions |
| AppFunctions are disabled by default, and are enabled at runtime by explicit logic checks | Yes | Enabled by default |
| AppFunctions that contain long-running operations are async functions | No | Uses androidx.lifecycle |

**Other**: this app demonstrates yet another signal \- `appfunctions.xml` file that was uncaught. DISCUSSION: demonstrates that appFunction standards are not defined, almost each app finds a way to implement this API differently somehow. 

App has only one method \- and it checks if the screen is locked, which is great\!

### Samsung InCallUI (com.samsung.android.incallui)

**Source**: Samsung S26 preinstall  
**Description**: another phone application (may be a separate package from phone app, name suggests in-call funcs)

| Guideline | Violates? | Findings |
| :---- | :---- | :---- |
| AppFunctions that are exposed benefit from natural language instructions, as compared to manual UI interaction | No | Exposed app functions benefit from when the user can not interact with the phone (Hey assistant, reject call) |
| AppFunctions only give the executor access to only the data and actions that are required to execute a task | Yes | `getPersons` appFunction returns a list of Persons. The DTO has `emailAddresses` on the object \-\> what is the use case for an in-call ui app to disclose this information to the agent? |
| AppFunctions do not disclose highly personal or confidential data. If it is exposed, explicit user consent is granted | \- | Inconclusive if consent is granted (permissions are there for the provider, but we never know about the agent) |
| Destructive actions require user confirmation | No | No destructive actions present |
| AppFunctions are disabled by default, and are enabled at runtime by explicit logic checks | Yes | Enabled by default |
| AppFunctions that contain long-running operations are async functions | \- | Inconclusive |

**Other**: `getPersons` has no auth it seems or lock screen check \-\> but you need to know these person ids. Most symbols lost in decompilation (managed to infer by context the DTOs). Unusual invocation structure as well \- a large if structure that matches by functionId

### Samsung Calendar (com.samsung.android.calendar)

**Source**: Samsung S26 preinstall  
**Description**: Samsung calendar application

| Guideline | Violates? | Findings |
| :---- | :---- | :---- |
| AppFunctions that are exposed benefit from natural language instructions, as compared to manual UI interaction | No | All of the exposed AppFunctions benefit from NL instructions (create an event, show my events, change event time) |
| AppFunctions only give the executor access to only the data and actions that are required to execute a task | \- | Inconclusive. While returned functions are very full, could not find explicit violations |
| AppFunctions do not disclose highly personal or confidential data. If it is exposed, explicit user consent is granted | \- | Inconclusive. App does have permission to handle this data, but we are unsure if the user is prompted/consents somewhere else. |
| Destructive actions require user confirmation | \- | Inconclusive. I am not able to prove that `deleteEvents` has user confirmation (params do not indicate, but maybe they spawn a confirmation dialog) |
| AppFunctions are disabled by default, and are enabled at runtime by explicit logic checks | Yes | All enabled by default |
| AppFunctions that contain long-running operations are async functions | No | Methods seem to include async |

**Other**: appFuncs located in p841s7.AbstractC10153a . Methods have explicit permission checks, such as “WRITE\_CALENDAR” check in `deleteEvents`.

### ⭐ Samsung Notes (com.samsung.android.notes)

**Source**: Samsung S26 preinstall  
**Description**: Samsung notes application. It is (so far) the only application that was included in the Google press release as an example of AppFunctions in production.

| Guideline | Violates? | Findings |
| :---- | :---- | :---- |
| AppFunctions that are exposed benefit from natural language instructions, as compared to manual UI interaction | No | All exposed AppFunctions benefit from NL instructions. Most likely why they chose this app as the pilot |
| AppFunctions only give the executor access to only the data and actions that are required to execute a task | Yes | All return functions (notes, folders) seem to contain only the note/folder DTO. *Could* argue that some methods could just return ids, and then a second call would be needed.Also update note \- lets say note is full of passwords, and the assistant invokes `updateNote` to add another line \- it would in return get the entire note. |
| AppFunctions do not disclose highly personal or confidential data. If it is exposed, explicit user consent is granted | No | It depends on the notes. Lock screen detection prevents unauthorized users from reading the notes |
| Destructive actions require user confirmation | Yes | `deleteFolders` method does not check for confirmation (nor has any confirmation param). Simply usecase is executed, deleted id’s are returned. `deleteNotes` blatantly violates this. |
| AppFunctions are disabled by default, and are enabled at runtime by explicit logic checks | Yes | All enabled by default |
| AppFunctions that contain long-running operations are async functions | No | Long running functions appear to be using coroutines |

**Other**: Exhibits checks for lock screens (can only create content when the screen is locked).

### Pixel Support (com.google.android.apps.pixel.support)

**Source**: Pixel 10 Pro preinstall  
**Description**: Pixel support/help application

| Guideline | Violates? | Findings |
| :---- | :---- | :---- |
| AppFunctions that are exposed benefit from natural language instructions, as compared to manual UI interaction | \- | Inconclusive. Information disclosed by this application might be needed for more technical AppFunctions, rather than being useful standalone. |
| AppFunctions only give the executor access to only the data and actions that are required to execute a task | \- | Inconclusive, the parameter list is complete, hard to infer what the required task really is |
| AppFunctions do not disclose highly personal or confidential data. If it is exposed, explicit user consent is granted | \- | Data could be used to fingerprint, but inconclusive |
| Destructive actions require user confirmation | No | Both functions are getters \- no destructive actions present |
| AppFunctions are disabled by default, and are enabled at runtime by explicit logic checks | Yes | All functions enabled by default |
| AppFunctions that contain long-running operations are async functions | \- | Inconclusive |

**Other**: absolute majority of symbols lost during decompilation. Only contains two app functions \- get battery device state and get uncategorized device state.

### Google Wellbeing (com.google.android.apps.wellbeing)

**Source**: Pixel 10 Pro preinstall  
**Description**: Digital wellness monitoring application

| Guideline | Violates? | Findings |
| :---- | :---- | :---- |
| AppFunctions that are exposed benefit from natural language instructions, as compared to manual UI interaction | No | Very technical functions, but LLM could translate from NL instructions. Essentially set settings/etc. |
| AppFunctions only give the executor access to only the data and actions that are required to execute a task | \- | Inconclusive. |
| AppFunctions do not disclose highly personal or confidential data. If it is exposed, explicit user consent is granted | \- | Inconclusive |
| Destructive actions require user confirmation | No | Unless we consider setting device state item as destructive, then no, nothing is deleted. |
| AppFunctions are disabled by default, and are enabled at runtime by explicit logic checks | Yes | All enabled by default |
| AppFunctions that contain long-running operations are async functions | \- | Inconclusive |

**Other**: absolute majority of symbols lost during decompilation.

### Settings Google (com.android.settings)

**Source**: Pixel 10 Pro preinstall  
**Description**: Google settings app

| Guideline | Violates? | Findings |
| :---- | :---- | :---- |
| AppFunctions that are exposed benefit from natural language instructions, as compared to manual UI interaction | No | Exposed functions benefit from NL instructions |
| AppFunctions only give the executor access to only the data and actions that are required to execute a task | \- | Inconclusive |
| AppFunctions do not disclose highly personal or confidential data. If it is exposed, explicit user consent is granted | \- | Inconclusive |
| Destructive actions require user confirmation | No | No destructive actions present |
| AppFunctions are disabled by default, and are enabled at runtime by explicit logic checks | Yes | All appFuncs are enabled by default |
| AppFunctions that contain long-running operations are async functions | \- | Inconclusive |

**Other**: absolute majority of symbols lost during decompilation. 

---

### Kummute (com.manja.kumpool)

**Source**: Google Play 250k sample  
**Description**: Some transportation app in asia  
**Other**: NO APP FUNCTIONS (FP)

### My Beeline (kg.beeline.odp)

**Source**: Google Play 250k sample  
**Description**: Asian country (russian speaking) whatsapp/viber alternative  
**Other**: NO APP FUNCTIONS (FP)

### Flic (io.flic.app)

**Source**: Google Play 250k sample  
**Description**: Home IoT controller  
**Other**: NO APP FUNCTIONS EXPOSED \- it makes use of `androidx.app.appfunctions.AppFunctionService` and has references to app\_functions.xml schemas, but none are exposed  
![][image1]

### Discobonuss (center.streaming.discobonuss)

**Source**: Google Play 250k sample  
**Description**: Some media app (russian), has [ru.vk](http://ru.vk) (russian facebook connections)  
**Other**: NO APP FUNCTIONS (FP)

### Auto Text \- Schedule Messages (com.hnib.smslater)

**Source**: Google Play 250k sample  
**Description**: Automation app  
**Other**: NO APP FUNCTIONS (FP)

### TVU Anywhere (com.tvunetworks.android.anywhere)

**Source**: Google Play 250k sample  
**Description**: TV channel streaming app  
**Other**: NO APP FUNCTIONS (FP)

### Simple PDF Editor & Viewer (com.simplepdf.editorviewer)

**Source**: Google Play 250k sample  
**Description**: PDF editing app  
**Other**: NO APP FUNCTIONS (FP)

### FotoAI Keyboard (com.foto.ai.keyboard)

**Source**: Google Play 250k sample  
**Description**: Keyboard app  
**Other**: NO APP FUNCTIONS (FP)

### Navo Antivirus (com.navobytes.navoantivirus)

**Source**: Google Play 250k sample  
**Description**: Antivirus Application  
**Other**: NO APP FUNCTIONS (FP)

### Spinfire Pro App (com.spinfiresport.spinfirepro\_app)

**Source**: Google Play 250k sample  
**Description**: Fitness application  
**Other**: NO APP FUNCTIONS (FP)

### Tenways (com.ycy.bluetoothbike)

**Source**: Google Play 250k sample  
**Description**: Electric bike app  
**Other**: NO APP FUNCTIONS (FP)

### Opay Egypt (team.opay.pay.egypt)

**Source**: Google Play 250k sample  
**Description**: Payment/bills application  
**Other**: NO APP FUNCTIONS (FP)

### HiChat (com.qy.hailiao)

**Source**: Google Play 250k sample  
**Description**: Chat application  
**Other**: NO APP FUNCTIONS (FP)

### Google Photos (com.google.android.apps.photos)

**Source**: Google Play 250k sample  
**Description**: Photos Application  
**Other**: REFERENCES APP FUNCTION SCHEMAS referenced in the manifest, but I could not find any exposed AppFunctions. Uses `androidx.app.appfunctions.service`

### Google’s Find Hub (com.google.android.apps.adm)

**Source**: Google Play 250k sample  
**Description**: Like Apple’s Find My, but for google/android devices  
**Other**: REFERENCES APP FUNCTION SCHEMAS referenced in the manifest  android:permission\="android.permission.BIND\_APP\_FUNCTION\_SERVICE"  
)t, but I could not find any exposed AppFunctions. Uses `androidx.app.appfunctions.service`

### Perplexity App (ai.perplexity.app.android) 

**Source**: Google Play 250k sample  
**Description**: Perplexity AI assistant  
**Other**: REFERENCES APP FUNCTION SCHEMAS referenced in the manifest  android:permission\="android.permission.BIND\_APP\_FUNCTION\_SERVICE"  
)t, but I could not find any exposed AppFunctions. Uses the android jetpack libraries for AppFunctions, and has requested (but not been granted) `EXECUTE_APP_FUNCTIONS` permission. Makes references to AppFunctions (such as createEvent, etc.)

[image1]: <data:image/png;base64,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>