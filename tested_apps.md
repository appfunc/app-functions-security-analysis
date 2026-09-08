# Tested Android AppFunction Applications

Below is the list of pre-installed applications on both the Samsung S26, and the Pixel 10 Pro, which were used for in-the-wild analysis.

- **S26** - Samsung Galaxy S26
- **P10** - Pixel 10 Pro

All of the applications are contained within this repository, under the `/apks` directory. All files were uploaded using `git LFS`.

| App Name                     | Source | Package                                    | Version Name                | SHA256                                                           |
| ---------------------------- | ------ | ------------------------------------------ | --------------------------- | ---------------------------------------------------------------- |
| ShareLive                    | S26    | com.samsung.android.app.sharelive          | 13.8.51.28                  | 53384c898739a2970589a2e575e3d7400ce3aa8307a9becd0d29e7ac39e16a35 |
| SamsungDialer                | S26    | com.samsung.android.dialer                 | 16.1.26.9                   | 3d933372980ef0314cc5931a5e858d1edcbdacf7415845b2b6b097ecb8400cf3 |
| Samsung Internet Browser     | S26    | com.sec.android.app.sbrowser               | 29.0.4.46                   | 961da0c3fb688efe659143b9b12be0d37c772353e1486cd14475c316f33f3350 |
| PhotoEditor AI full          | S26    | com.sec.android.mimage.uid.photoretouching | 3.8.12.29                   | bf70ccae47f547e6e89be7eb84e64f80ecb442efe2de50660df0445c91b2d3ca |
| My Files                     | S26    | com.sec.android.app.myfiles                | 15.4.06.10                  | 1eee8caffc0c4de857d7792d17918d5336944a71ea61e5f94ce355f1b17fdca7 |
| Android GMS                  | S26    | com.google.android.gms                     | 26.18.33 (260400-913931251) | 5be44c190c7b4f0977e1272b738b563d44a3ce8cf7d7fe8b5e45483f3bece4bb |
| Clock                        | S26    | com.sec.android.app.clockpackage           | 12.5.51.2                   | 10dd3264c0c8791fdf8ae0cc3f396f72b43426d11866edc326d96d21c291e226 |
| Samsung Reminders            | S26    | com.samsung.android.app.reminder           | 12.7.06.15                  | ce7a9370ad8272b5afa69085bbbfee95f12783f3b3be2d579c816c9214485ab8 |
| Samsung Contacts             | S26    | com.samsung.android.app.contacts           | 16.1.26.9                   | db539d0bd60b05b0fb45ebe28b58ce90649ca7d23f67c28e1b75088f77826baf |
| Samsung Gallery 2018         | S26    | com.sec.android.gallery3d                  | 15.8.00.61                  | 16cc8652bc7ad941522c59962470252024708957e12af055e2fc5737a84f7718 |
| Google Permission Controller | S26    | com.android.permissioncontroller           | aml_361154040               | 0dd6b7a829f52642feed580612ad3a10fa3e4235a8f4a2363c3659f2b6ae21d3 |
| Samsung InCallUI             | S26    | com.samsung.android.incallui               | 17.5.00.284                 | 12342bc153f413d78d1bd734ba876ce4ff3547d58dc1e6b70d524b429943799a |
| Samsung Calendar             | S26    | com.samsung.android.calendar               | 12.7.06.13                  | 0dcef47b25f44c3aa9adc0a1488f273f8667a067616223fe0982698da1af5458 |
| Samsung Notes                | S26    | com.samsung.android.notes                  | 4.4.38.5                    | 7b3a66ae01f1bcd8ff16480791fffb12d0e7ba6154f2978ffb7157d2905283aa |
| Pixel Support                | P10    | com.google.android.apps.pixel.support      | 1.0.897474424.release       | 667c4ecdd344a311118817a01470ef2c5ad809b9df4ba3909189db9224f83b88 |
| Google Wellbeing             | P10    | com.google.android.apps.wellbeing          | 1.42.900698277 (831727)     | 2d45bbc8adbe2dc32acad8c0a57279a39d83a050779c99859f2fc4c38d943b4d |
| Settings Google              | P10    | com.android.settings                       | 16                          | f74a9814d30edc67b0d80f8478873762fe425f20dac6998f570acda390985cb7 |

The entire list of available AppFunctions was extracted using the following command via adb

```agsl
adb shell cmd app_function list-app-functions > result.json
```

The entire list of AppFunctions, available to us during our study can be found in this repository:

```agsl
./appFuncsP10.json  // Available AppFunctions from the Pixel 10 Pro
./appFuncsS26.json  // Available AppFunctions from the Samsung Galaxy S26
```
