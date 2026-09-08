// Best attempt at trying to figure out what the (REDACTED) Gemini does and how to make it trigger our AppFunction (findNotes from our spoofed tool app)

// Initial static analysis suggests internal Google AppFunctionMetadata schemas (we have to fit a certain triple)
// Not all available AppFunctions (visible via adb list-app-functions) are picked up by Gemini (schema check)
// Furthermore the "ROBIN" internal codename, resulting in a huge case-switch with ROBIN_OP_CREATE_SAMSUNG_NOTE and such, suggesting
// that Gemini does not have free reign over which AppFunctions it can choose - there's a static pool.

// As it is now, we can trigger the AppFunction "findNotes", but it instantly crashes the executor app, as most likely we break the return path in the process
// we cant get the result back to the UI
// This script works as a one-shot script, if anything gets modified (frida watch) the script may no longer work

// EXACT tested version
//    android:versionCode="301744903"
//    android:versionName="17.24.28.sa.arm64"
// Device: ROOTED Pixel 10 Pro

var NOTES_PKG = "com.samsung.android.app.notes";
var FN_ID = "dev.filipfan.appfunctionspilot.tool.functions.Notes#findNotes"; // but havent found evidence gemini checks against that. There is something else that the OG Samsung Notes app has that works
var SCHEMA = "notes/findNotes/1"; // spoofed schema
var PARAMS_TYPE =
  "dev.filipfan.appfunctionspilot.tool.functions.NotesSchema$FindNotesParams";

// IMPORTANT:
// we bypass indexing by conforming to the schema findNotes, notes, ver=1
// Google Assistant only looks for schemas it has hardcoded in its source (internal types)

// Might contain symbol inaccuracies, as during reverse engineering Google Assistant app jumped 400 version codes
// (de-sync of hooked vs statically analyzed app)

Java.perform(function () {
  console.log("hook_gemini.js loaded");

  var Class_aib = Java.use("aib");
  var Class_Map = Java.use("java.util.Map");
  var Class_CDL = Java.use("java.util.concurrent.CountDownLatch");
  var Class_OR = Java.use("android.os.OutcomeReceiver");
  var Class_EAFR = Java.use(
    "android.app.appfunctions.ExecuteAppFunctionResponse",
  );
  var Class_GD = Java.use("android.app.appsearch.GenericDocument");
  var Class_AFM = Java.use("android.app.appfunctions.AppFunctionManager");
  var Class_GDB = Java.use("android.app.appsearch.GenericDocument$Builder");
  var Class_EAFRB = Java.use(
    "android.app.appfunctions.ExecuteAppFunctionRequest$Builder",
  );
  var Class_Exec = Java.use("java.util.concurrent.Executors");
  var Class_CS = Java.use("android.os.CancellationSignal");
  var Class_TimeUnit = Java.use("java.util.concurrent.TimeUnit");

  var setStringOverload = Class_GDB.setPropertyString.overload(
    "java.lang.String",
    "[Ljava.lang.String;",
  );
  var setDocumentOverload = Class_GDB.setPropertyDocument.overload(
    "java.lang.String",
    "[Landroid.app.appsearch.GenericDocument;",
  );

  // aib.b(pkg, meta, schema, Map)
  // JADX: p000.aib.m6080b(String str, AppFunctionMetadataDocument appFunctionMetadataDocument, ajq ajqVar, Map map)
  // adapted for logging of ALL of our spoofed app schemas
  try {
    Class_aib.b.overloads.forEach(function (overload) {
      if (overload.argumentTypes.length !== 4) return;

      var aib_b_implementation = function (pkg, metadata, schema, resultMap) {
        var packageName = pkg != null ? pkg + "" : "null";

        var schemaName = "";
        var schemaCategory = "";
        var schemaVersion = "";

        if (schema !== null) {
          try {
            var schemaFieldA = schema.getClass().getDeclaredField("a");
            schemaFieldA.setAccessible(true);
            schemaCategory = schemaFieldA.get(schema) + "";

            var schemaFieldB = schema.getClass().getDeclaredField("b");
            schemaFieldB.setAccessible(true);
            schemaName = schemaFieldB.get(schema) + "";

            var schemaFieldC = schema.getClass().getDeclaredField("c");
            schemaFieldC.setAccessible(true);
            schemaVersion = schemaFieldC.get(schema) + "";
          } catch (e) {
            console.error("[ERROR] aib.b metadata parsing: " + e);
            schemaName = "null";
            schemaCategory = "null";
            schemaVersion = "null";
          }
        }

        var isMyApp = packageName === NOTES_PKG;

        var mapSize = "null";
        if (resultMap !== null) {
          try {
            mapSize = Java.cast(resultMap, Class_Map).size() + "";
          } catch (e) {
            console.error("[ERROR] aib.b resultMap parsing: " + e);
            mapSize = "null";
          }
        }

        if (isMyApp) {
          console.log(
            `[aib.b] pkg=${packageName} schema(cat=${schemaCategory}) fn=${schemaName} ver=${schemaVersion}) resultMap=${mapSize}`,
          );
        }

        var result,
          threw = null;
        overload.implementation = null;

        try {
          result = overload.call(this, pkg, metadata, schema, resultMap);
        } catch (e) {
          threw = e;
        } finally {
          overload.implementation = aib_b_implementation;
        }

        if (threw !== null) throw threw;

        return result;

        console.log(`[aib.b] returned ${result}`);
      };

      overload.implementation = aib_b_implementation;
    });

    console.log("aib.b hooked");
  } catch (e) {
    console.error("[ERROR] aib.b: " + e.message);
  }

  // aib.f(): builds map of package names, schemas (inject our own)
  // JADX: p000.aib.m6084f(anr var1, Set set, ghhg var2)
  try {
    Class_aib.f.overloads.forEach(function (overload) {
      if (overload.argumentTypes.length !== 3) return;

      overload.implementation = function (anr, opSet, ghhg) {
        console.log("[aib.f] bypassing AppSearch check");

        try {
          this.a(NOTES_PKG, SCHEMA, ghhg);
          return null;
        } catch (e) {
          console.error("[ERROR] aib.f bypass fail: " + e.message);
          return null;
        }
      };
    });

    console.log("aib.f hooked");
  } catch (e) {
    console.error("[ERROR] aib.f: " + e.message);
  }

  // aib.a(): AFM dispatch
  // JADX: p000.aib.mo5620a(String str, String str2, ghhg var)
  // the actual execution meat and potatoes
  // previous versions we could trigger path which logs "usingJetpack"
  var callInProgress = false;
  Class_aib.a.overloads.forEach(function (overload) {
    if (overload.argumentTypes.length !== 3) return;

    var impl = function (pkg, schemaId, cont) {
      // defensive coroutine check against null packages
      if (pkg === null || pkg === undefined) {
        overload.implementation = null;
        try {
          return overload.call(this, pkg, schemaId, cont);
        } catch (e) {
          return null;
        } finally {
          overload.implementation = impl;
        }
      }

      if (callInProgress) return null;
      callInProgress = true;
      console.log(`[aib.a] pkg=${pkg} schema=${schemaId}`);

      try {
        var latch = Class_CDL.$new(1);

        var recv = Java.registerClass({
          name: "com.fridatest.gemini",
          implements: [Class_OR],
          methods: {
            onResult: function (response) {
              try {
                var doc = Java.cast(
                  Java.cast(response, Class_EAFR).getResultDocument(),
                  Class_GD,
                );
                var ret = doc.getPropertyDocument(
                  "androidAppfunctionsReturnValue",
                );
                if (ret !== null) {
                  var nested = Java.cast(ret, Class_GD);
                  console.log(`[AFM] schemaType=${nested.getSchemaType()}`);
                  nested.getPropertyNames().forEach(function (pn) {
                    console.log(`[AFM] ${pn}=${nested.getPropertyString(pn)}`);
                  });
                }
              } catch (e) {
                console.log(`[AFM] onResult err: ${e.message}`);
              } finally {
                latch.countDown();
              }
            },
            onError: function (err) {
              console.log(`[AFM] onError: ${err}`);
              latch.countDown();
            },
          },
        }).$new();

        var innerDoc = Class_GDB.$new("", "params", PARAMS_TYPE);
        setStringOverload.call(
          innerDoc,
          "query",
          Java.array("java.lang.String", [""]),
        );
        // location is not in the internal schema, but let's see if gemini can somehow populate and leak PII
        // but might crash
        setStringOverload.call(
          innerDoc,
          "location",
          Java.array("java.lang.String", [""]),
        );

        var outerDoc = Class_GDB.$new("", "request", "AppFunctionParams");
        setDocumentOverload.call(
          outerDoc,
          "params",
          Java.array("android.app.appsearch.GenericDocument", [
            Class_GDB.build.call(innerDoc),
          ]),
        );

        var request = Class_EAFRB.$new(NOTES_PKG, FN_ID)
          .setParameters(Class_GDB.build.call(outerDoc))
          .build();

        Java.choose("android.app.appfunctions.AppFunctionManager", {
          onMatch: function (afm) {
            afm.executeAppFunction(
              request,
              Class_Exec.newSingleThreadExecutor(),
              Class_CS.$new(),
              recv,
            );
            return "stop";
          },
          onComplete: function () {},
        });

        latch.await(5, Class_TimeUnit.SECONDS.value);
        console.log("[aib.a] done");
        return null;
      } catch (e) {
        console.log(`[ERROR] aib.a: ${e.message}`);
        return null;
      } finally {
        callInProgress = false;
      }
    };

    overload.implementation = impl;
  });

  console.log("[DONE] hook_gemini.js ready, prompt Gemini to 'find Notes'");
});
