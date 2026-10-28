# Trail-Status

Home-screen widgets showing whether DFW mountain bike trails are open, using the trail status
that [DORBA](https://dorba.org) publishes. Defaults to **North Shore** (Northshore Trails @ Grapevine Lake)
and **Erwin Park**.

## Where the data comes from

The "Trail Report Status" table on dorba.org is an embedded Trailforks widget:

```
https://www.trailforks.com/widgets/region_list/?rids=<DORBA's region ids>&rows=50&cols=status,title,last_report_ts
```

Each row gives the trail name, a status icon (`sgreen` All Clear, `syellow` Minor Issue,
`samber` Significant Issue, `sred` Closed, with a numeric `data-sort` code 1–4), and the time of the
last report. Both widgets fetch that page **on the phone** and parse it. There is no server to run.

Both sites sit behind Cloudflare, which returned 403 to plain scripted requests from a desktop.
The iOS widget falls back to a headless WebKit view if a direct request is blocked. Both widgets
keep the last good result and mark it "cached" when a refresh fails.

`fixtures/region_list.html` is a trimmed snapshot of that page, used by the parser tests on both platforms.

## iPhone: Scriptable widget

Native iOS widgets need a Mac and Xcode, so the iPhone version runs in the free
[Scriptable](https://scriptable.app) app.

1. Install Scriptable from the App Store.
2. Copy `ios-scriptable/TrailStatus.js` into Scriptable. You can paste it into a new script, or save the
   file to iCloud Drive › Scriptable.
3. Run it once in the app. It previews the widget and logs every trail name.
4. Add a Scriptable widget to the home screen (small, medium or large). Long-press it, choose
   **Edit Widget**, and set **Script** to *TrailStatus*.
5. Optional: set **Parameter** to a comma-separated list of trails, e.g. `North Shore, Erwin Park, Big Cedar`.
   Names match loosely, and when several trails match, the shortest name wins.
6. Optional: set **When Interacting** to *Open URL*. A single-trail widget then opens its Trailforks page.

iOS decides when widgets refresh, usually every 15–60 minutes. The script asks for a refresh every 30 minutes.

Tests (Node 18+):

```bash
node --test ios-scriptable/test/parse.test.js
```

## Android: native widget (Kotlin + Jetpack Glance)

1. Open the `android/` folder in Android Studio and let it sync. It will download Gradle and the SDK.
2. Run the app on your phone (USB debugging) or an emulator.
3. Long-press the home screen, open **Widgets**, then **DFW Trail Status**, and place it. The trail picker
   opens. Tap the trail for that widget. Place one widget per trail.
4. The whole widget is tinted by status: green open, yellow/orange caution, red closed. Tap it to refresh.
   It also refreshes in the background every 30 minutes over WorkManager.

Opening the app lists every DORBA trail with its status. Tapping one opens it on Trailforks.

Parser unit tests: `./gradlew :app:testDebugUnitTest` from `android/`.

## Layout

```
fixtures/region_list.html          sample of the Trailforks table (test input)
ios-scriptable/TrailStatus.js      Scriptable widget (fetch, parse, cache, UI)
ios-scriptable/test/               Node tests for the parser
android/app/src/main/java/...      Parser, repository/cache, Glance widget, refresh worker, picker
android/app/src/test/...           JUnit tests for the parser
```
