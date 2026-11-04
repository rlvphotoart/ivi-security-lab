# IVI Security Lab 1.1 — Android 14+

**Phone-only mode now works with a native IVI, without an IVI-side APK.** See [the Romanian phone-only guide](PHONE-ONLY.ro.md). Use the **IVI** tab for native services, **Local** for containment on the device running the app, **Evidence** for reports, and **Lab** for the original two-app fixture.

The phone-only mode inventories SDP service UUIDs (potentially cached), observes A2DP/HFP state, and performs one bounded RFCOMM connection attempt to a selected advertised supported service. It sends and reads zero application bytes on native connections. Transport failures remain inconclusive. Optional PASS/FAIL evaluates only an explicit bond-state expectation. Native contacts/messages authorization is recorded as an operator observation, not inferred from transport access.

A phone-only Bluetooth connection cannot establish IVI-02 app containment on the remote head unit.

Two local, signed APKs for controlled IVI-01 and IVI-02 checks. For native Bluetooth tests, install only the probe on the phone. The original Lab peer mode requires the probe on both devices. Install the fixture only where you want local containment checks. No root, platform signing, Internet access, vehicle commands, firmware changes or background service is required.

## What this delivers

- A pre-permission negative test of the Android bonded-device API.
- Bluetooth discovery, selected-peer pairing requests and bond-state observations.
- A custom, one-connection RFCOMM peer service with secure and deliberately unauthenticated transport options.
- Valid and invalid **application-level** lab messages with bounded inputs and timeouts. This is not Bluetooth stack fuzzing.
- Active attempts to open a separate fixture app's private canary file and query its non-exported provider.
- Read-open attempts against two protected system paths, privileged-permission inspection and SELinux-state observation.
- Persistent local JSON evidence and document-picker export. No overall vehicle-security score is calculated.

This is a test harness, **not a working exploit for PerfektBlue or another Bluetooth CVE**. It cannot inject arbitrary HCI/L2CAP packets through Android's normal app APIs. It does not exercise native HFP/A2DP/PBAP/MAP authorization or prove CAN/SGW containment. IVI-02 assumes an ordinary app foothold, not a demonstrated browser exploit or privileged process compromise.

## Install

The APKs use minimum SDK 34 and target SDK 34 (Android 14). These are sideloaded lab builds, not store releases. Keys are generated locally in `keys/` and excluded from the source archive. Keep those keys locally for future in-place updates. The keystore password is the conventional development value `android`; the keys are for lab use only.

Use a particular device serial whenever multiple devices are connected:

```sh
adb devices -l
adb -s YOUR_DEVICE_SERIAL install -r dist/ivi-lab-probe.apk
adb -s YOUR_DEVICE_SERIAL install -r dist/ivi-lab-fixture.apk
```

OEM software can restrict third-party app installation or access to Bluetooth. Record those restrictions; do not treat inability to install as a passed security test. An Android Auto projection screen is not an Android Automotive app installation target. The probe must actually run on the IVI for IVI containment evidence.

## Original IVI-01 controlled Lab peer procedure

Use a parked vehicle or isolated bench. Install the probe on both devices. For these checks, the counterpart must run this app's listener; the custom service does not exist on a stock IVI by itself.

1. On each device, open **Lab** and press **Check permission boundary (before grant)**. Expected: `PASS` from a `SecurityException`. If permissions were previously granted, revoke Nearby devices in system App settings, reopen and repeat. No Bluetooth adapter means inconclusive.
2. Grant Nearby devices permissions and enable Bluetooth through the system UI.
3. On the receiving device, request 60-second discoverability. Complete the system prompt, then press **Listen once**. Leave this app in the foreground.
4. On the sending device, discover for 12 seconds or list bonded peers, then choose the intended test peer. For unpaired tests, use the system Bluetooth settings to forget the peer first. Record initial bond state and visible confirmation prompts.
5. Leave the unauthenticated switch **off** on both devices. Send a valid lab message. Complete or reject system pairing prompts as required by your test case. Successful matching reply validates only our lab message handler. A timeout is **inconclusive**, not proof that authentication blocked access.
6. Start another one-shot listener, then send an invalid lab message. Expected reply: `IVILAB/1 REJECT`. The `PASS` verdict applies to the lab service's input handler, not the Bluetooth stack.
7. Optionally repeat with the unauthenticated switch **on on both devices**. This is an intentionally weaker lab fixture. Successful connection is not a discovered IVI vulnerability. “Unauthenticated” does not guarantee absence of link encryption.
8. Stop the test. Discoverability can remain until the system's 60-second timeout. Leaving the app foreground closes test sockets. Existing bonds remain until removed in system settings.

The listener waits up to 60 seconds for a single connection, then bounds message handling to 10 seconds. The sender closes its socket after 20 seconds. Messages are at most 256 bytes, the only valid request is a nonce-bearing `IVILAB/1 PING`, and the only negative-test payload is the fixed harmless string `IVILAB/1 INVALID harmless-input`. No file transfers, commands, arbitrary payloads or automatic repeated requests are implemented.

## IVI-02 procedure

1. On the IVI itself, open **IVI Lab Fixture**. Confirm **READY — self-check passed**. This proves that its own private file and provider exist and work from its own UID.
2. Open **IVI Security Lab → Local**, then run checks.
3. Expected: the fixture private file's open attempt is denied, and the fixture provider rejects the other app's UID with `SecurityException`.
4. System-path denials are specific access-control observations. A denied parent path does not establish that the named file exists. Missing paths or unexpected errors remain inconclusive. File contents are not read or exported.
5. The ordinary-app policy baseline expects the inspected privileged permissions to be absent. Undefined permissions remain inconclusive. These checks inspect grants; they do not call car diagnostic APIs or demonstrate authorization enforcement at those APIs.
6. Inspect any failures with the device's OEM security requirements. Granting system privileges to the probe changes the test context and invalidates the ordinary-app assumption.

## Evidence and interpretation

Set the role (**IVI**, **Phone**, or **Emulator / bench**) in Evidence. Enter head-unit model, firmware, test setup, expected native-profile policy, pairing-prompt observations and any independent trace IDs. Save notes and export JSON.

- `PASS`: the individual check met its narrow expected outcome.
- `FAIL`: the specific test expectation was violated; investigate against the recorded context.
- `OBSERVED`: information without an automated security verdict.
- `INCONCLUSIVE`: prerequisites or evidence do not establish the outcome.

The report includes model, build fingerprint, Android security patch, UID, permission snapshots, UTC and elapsed timestamps, selected-peer Bluetooth addresses and operator notes. Nearby discovered peers are kept only in memory; discovery does not export every surrounding device. Reports are local, capped at 500 events per session, and survive app restart. Export before starting a new session or uninstalling. Exports are snapshots: later events are not retroactively included.

An error reaching a custom Bluetooth service cannot distinguish authentication enforcement from a missing service, range issues, rejected prompts or radio failure. Manual observations and independent traces are needed. Do not infer that native Bluetooth profiles, firmware integrity or gateway isolation passed from these checks.

## Rebuild

No Gradle or external application libraries are needed. The build uses the installed Android SDK tools and Java 17. Run:

```sh
bash scripts/build.sh
```

Default tool locations are for this Mac. Override `ANDROID_SDK_ROOT` and `LAB_JAVA_HOME` as needed. Required SDK components: `build-tools;35.0.0` and `platforms;android-34`. Outputs are signed and verified with `apksigner`; SHA-256 hashes are written beside them.

## Verification

```sh
bash scripts/test-protocol.sh
python3 scripts/smoke.py --serial emulator-5554
```

The checks exercise valid/invalid Lab messages, nonce validation, framing bounds, truncated inputs, native service filtering and bond-policy verdicts. Native transport failure is never classified as an authorization pass. The UI smoke script is restricted to disposable emulator serials: it installs both APKs, clears their data, runs the fixture self-check, tests permission and containment behavior, exports JSON and checks process-restart persistence. It requires ADB on PATH and an English emulator UI. It does not test physical Bluetooth communication.

The delivered build is compiled against Android 14/API 34. The private repository workflow validates the exact signed APKs committed under `dist/` on an Android 14/API 34 phone emulator; workflow results are the source for that CI run, not evidence of vehicle behavior. Runtime evidence in `outputs/emulator-5554/` comes from an Android 16/API 36 **phone emulator**, not an Android Automotive image or physical vehicle. The fixture provider was denied; the private-file probe returned ENOENT and correctly remained inconclusive. Automotive-specific permissions were undefined on that image. Android 14 vehicle execution, native Bluetooth profiles and actual phone-to-IVI transport still need target testing.

## Additional information needed for a CVE reproduction

Provide the IVI make/model, exact firmware/build fingerprint, Bluetooth implementation and version where available, and the CVE of interest. That determines applicability and whether a separate Linux host/adapter test is required. Android 14 by itself does not identify the Bluetooth stack or establish vulnerability.

## Technical references

- [Android Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- [Bluetooth device socket APIs](https://developer.android.com/reference/android/bluetooth/BluetoothDevice)
- [Android Bluetooth adapter APIs](https://developer.android.com/reference/android/bluetooth/BluetoothAdapter)
- [Android Automotive vehicle system isolation](https://source.android.com/docs/automotive/security/vehicle_system_isolation)
- [PerfektBlue research advisory](https://pcacybersecurity.com/resources/advisory/perfekt-blue)


Native service identifiers follow [Bluetooth SIG Assigned Numbers](https://www.bluetooth.com/specifications/assigned-numbers/). UUID cache and SDP behavior follow the [Android BluetoothDevice API](https://developer.android.com/reference/android/bluetooth/BluetoothDevice).
