package com.rlv.ivilab;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.util.AtomicFile;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final String FIXTURE = "com.rlv.ivilabfixture";
    private static final UUID SERVICE = UUID.fromString("92e6bd8b-4bb2-4aa8-971a-8c99c927be71");
    private static final String PING = LabProtocol.PING, ACK = LabProtocol.ACK;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService workers = Executors.newFixedThreadPool(3);
    private final Map<String, BluetoothDevice> peers = new LinkedHashMap<>();
    private final JSONArray events = new JSONArray();
    private final Object reportLock = new Object();
    private BluetoothAdapter adapter;
    private volatile BluetoothServerSocket server;
    private volatile BluetoothSocket client, accepted;
    private volatile boolean peerBusy;
    private volatile int operation;
    private boolean registered;
    private LinearLayout root;
    private TextView logView, peerView;
    private EditText notes;
    private Spinner role;
    private Switch insecure;
    private Switch bondRequired;
    private TextView serviceView;
    private UUID nativeService;
    private final Map<String, List<UUID>> advertised = new HashMap<>();
    private String pendingSdp;
    private int sdpOperation;
    private BluetoothDevice selected;
    private String session = UUID.randomUUID().toString();
    private String roleValue = "Phone", notesValue = "";
    private int page;
    private byte[] pendingExport;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        adapter = manager == null ? null : manager.getAdapter();
        restore();
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_UUID);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED); registered = true;
        draw();
        event("SESSION", "OBSERVED", "App opened", "Android " + Build.VERSION.RELEASE + "; SDK " + Build.VERSION.SDK_INT + "; UID " + android.os.Process.myUid());
    }

    private void restore() {
        try (FileInputStream in = new AtomicFile(new File(getFilesDir(), "report.json")).openRead()) {
            JSONObject old = new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            session = old.getString("session_id"); roleValue = old.optString("role", "Phone"); notesValue = old.optString("operator_notes");
            JSONArray saved = old.getJSONArray("events");
            for (int i = 0; i < saved.length(); i++) events.put(saved.getJSONObject(i));
        } catch (Exception ignored) { }
    }

    private void draw() {
        LinearLayout shell = new LinearLayout(this); shell.setOrientation(1); shell.setPadding(dp(20), dp(12), dp(20), 0); shell.setBackgroundColor(0xff101820);
        setContentView(shell);
        TextView brand = text("IVI / SECURITY LAB", 13, 0xff66e2c4); brand.setTypeface(null, Typeface.BOLD); shell.addView(brand);
        TextView title = text(page == 0 ? "Phone → vehicle IVI" : page == 1 ? "Local containment" : page == 2 ? "Test evidence" : "Controlled lab peer", 27, 0xfff0f5f8); shell.addView(title);
        LinearLayout tabs = new LinearLayout(this);
        String[] names = {"IVI", "Local", "Evidence", "Lab"};
        for (int i = 0; i < names.length; i++) { final int index = i; Button b = button(names[i], () -> { saveInputs(); page = index; draw(); }); tabs.addView(b, new LinearLayout.LayoutParams(0, dp(54), 1)); }
        shell.addView(tabs);
        ScrollView scroll = new ScrollView(this); root = new LinearLayout(this); root.setOrientation(1); root.setPadding(0, dp(8), 0, dp(24)); scroll.addView(root); shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        logView = null; peerView = null; serviceView = null; notes = null; role = null; insecure = null; bondRequired = null;
        if (page == 0) nativePage(); else if (page == 1) containmentPage(); else if (page == 2) evidencePage(); else bluetoothPage();
    }

    private TextView text(String value, int size, int color) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setPadding(dp(4), dp(6), dp(4), dp(6)); return t; }
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density); }
    private Button button(String name, Runnable action) { Button b = new Button(this); b.setText(name); b.setAllCaps(false); b.setTextColor(0xff101820); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff66e2c4)); b.setOnClickListener(v -> { try { action.run(); } catch (Exception e) { event("APP", "INCONCLUSIVE", name, describe(e)); } }); return b; }
    private void addButton(String name, Runnable action) { root.addView(button(name, action), new LinearLayout.LayoutParams(-1, -2)); }
    private void info(String title, String body) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(1); card.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(0xff1c2a35); bg.setCornerRadius(dp(12)); card.setBackground(bg);
        card.addView(text(title, 18, 0xff66e2c4)); card.addView(text(body, 15, 0xffd4dee6));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, dp(8), 0, dp(12)); root.addView(card, p);
    }

    private void bluetoothPage() {
        info("IVI-01 • Controlled peer test", "Install this APK on both devices. Select the intended peer. The custom lab service exchanges at most 256 bytes per connection. These checks do not fuzz the Bluetooth stack or reproduce a CVE.");
        info("Device", Build.MANUFACTURER + " " + Build.MODEL + "\nAndroid " + Build.VERSION.RELEASE + " • SDK " + Build.VERSION.SDK_INT + "\nAutomotive feature: " + getPackageManager().hasSystemFeature("android.hardware.type.automotive"));
        addButton("1. Check permission boundary (before grant)", this::permissionBoundary);
        addButton("2. Grant Nearby devices permissions", () -> requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE}, 10));
        addButton("3. Request Bluetooth enable", () -> { if (ready(false)) startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)); });
        addButton("Discoverable for 60 seconds (system prompt)", () -> { if (ready(true)) { Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE); i.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 60); startActivity(i); } });
        addButton("List bonded peers", this::bonded);
        addButton("Discover nearby peers • 12 seconds", this::discover);
        peerView = text("Selected peer: " + (selected == null ? "none" : deviceLabel(selected)), 15, 0xffe6edf3); root.addView(peerView);
        addButton("Choose peer", this::choosePeer);
        addButton("Request pairing with selected peer", () -> { if (ready(true) && requirePeer()) event("IVI-01.PAIR", "OBSERVED", "Pairing requested", selected.getAddress() + "; accepted by API=" + selected.createBond() + "; use system UI to confirm or reject; no verdict implied"); });
        insecure = new Switch(this); insecure.setText("Use unauthenticated lab RFCOMM transport"); insecure.setTextColor(0xfff4c878); insecure.setPadding(dp(4), dp(12), 0, dp(12)); root.addView(insecure);
        root.addView(text("Off = secure RFCOMM. On = intentionally weaker lab service. Successful access to this test service is an observation, not a vehicle vulnerability. Unauthenticated does not necessarily mean unencrypted.", 14, 0xffbbc8d1));
        addButton("Listen once on this device • 60 seconds", this::listen);
        addButton("Send valid lab message to peer", () -> connect(false));
        addButton("Send invalid lab message to peer", () -> connect(true));
        addButton("Stop Bluetooth test", () -> stop("Operator stopped test"));
        info("Native profiles need a separate test", "HFP, A2DP, PBAP and MAP authorization must be exercised through the vehicle's native functions. This APK's RFCOMM service does not test those profile implementations. Pairing and connection observations appear in Evidence.");
    }

    private void nativePage() {
        info("Phone-only • No IVI app needed", "Select your vehicle IVI. Discover its advertised services, observe native audio/call connections, or attempt one RFCOMM connection with no application data sent. Use the system pairing prompt normally. SDP results may be cached and do not prove authorization.");
        addButton("1. Check permission boundary (before grant)", this::permissionBoundary);
        addButton("2. Grant Nearby devices permissions", () -> requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, 10));
        addButton("3. Request Bluetooth enable", () -> { if (ready(false)) startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)); });
        addButton("List bonded peers", this::bonded);
        addButton("Discover nearby peers • 12 seconds", this::discover);
        peerView = text("Selected peer: " + (selected == null ? "none" : deviceLabel(selected)), 15, 0xffe6edf3); root.addView(peerView);
        addButton("Choose peer", this::choosePeer);
        addButton("Request pairing with selected peer", () -> { if (ready(true) && requirePeer()) event("IVI-01.PAIR", "OBSERVED", "Pairing requested", selected.getAddress() + "; API accepted=" + selected.createBond() + "; record visible approval/rejection on both devices"); });
        addButton("Open system Bluetooth settings", () -> startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)));
        addButton("Read cached service UUIDs", () -> { if (ready(true) && requirePeer()) services(selected, selected.getUuids(), "cache read; not fresh SDP proof"); });
        addButton("Request IVI service discovery (SDP)", this::requestSdp);
        serviceView = text("RFCOMM service: " + (nativeService == null ? "not selected" : NativePolicy.label(nativeService)), 14, 0xffe6edf3); root.addView(serviceView);
        addButton("Choose advertised RFCOMM service", this::chooseService);
        bondRequired = new Switch(this); bondRequired.setText("Test policy requires a bond after connection"); bondRequired.setTextColor(0xfff4c878); root.addView(bondRequired);
        root.addView(text("Off = observe only. On = PASS/FAIL checks only the recorded bond state after a successful connection. It does not establish profile authorization, encryption strength, MITM resistance or access to protected data.", 14, 0xffbbc8d1));
        addButton("Attempt secure connection • no payload", () -> nativeConnect(false));
        addButton("Attempt unauthenticated connection • no payload", () -> nativeConnect(true));
        addButton("Observe selected IVI audio / call profiles", this::profiles);
        addButton("Record manual profile authorization result", this::manualResult);
        addButton("Stop Bluetooth test", () -> stop("Operator stopped phone-only test"));
        info("Scope", "No commands, contacts, messages, files or custom lab payloads are sent to the IVI. A reachable service is not proof of data access. A timeout is inconclusive. Local containment checks describe the device running this app, so phone-only mode cannot establish IVI-02 containment.");
    }

    private void requestSdp() {
        if (!ready(true) || !requirePeer()) return;
        if (peerBusy || pendingSdp != null) { event("IVI-01.SDP", "INCONCLUSIVE", "Bluetooth test busy", "Wait or stop the current operation"); return; }
        adapter.cancelDiscovery(); final String address = selected.getAddress(); final int token = ++sdpOperation;
        pendingSdp = address;
        boolean started = selected.fetchUuidsWithSdp();
        if (!started) pendingSdp = null;
        event("IVI-01.SDP", started ? "OBSERVED" : "INCONCLUSIVE", "SDP request", address + "; API accepted=" + started + "; UUID broadcasts can contain cached data even after a request");
        ui.postDelayed(() -> { if (token == sdpOperation && address.equals(pendingSdp)) { pendingSdp = null; event("IVI-01.SDP", "INCONCLUSIVE", "SDP response timeout", "No UUID event in 20 seconds; not an authorization result"); } }, 20000);
    }
    private void services(BluetoothDevice device, ParcelUuid[] values, String source) {
        List<UUID> list = new ArrayList<>(); StringBuilder detail = new StringBuilder(device.getAddress() + "; " + source);
        if (values != null) for (ParcelUuid value : values) { if (value != null && !list.contains(value.getUuid())) { list.add(value.getUuid()); detail.append("\n").append(NativePolicy.label(value.getUuid())); } }
        advertised.put(device.getAddress(), list);
        if (nativeService != null && !list.contains(nativeService)) nativeService = null;
        if (serviceView != null) serviceView.setText("RFCOMM service: " + (nativeService == null ? "not selected" : NativePolicy.label(nativeService)));
        event("IVI-01.SERVICES", list.isEmpty() ? "INCONCLUSIVE" : "OBSERVED", "Selected IVI service inventory", detail + (list.isEmpty() ? "; no UUIDs available; service absence is not established" : "; metadata only; authorization not tested"));
    }
    private void chooseService() {
        if (!ready(true) || !requirePeer()) return;
        List<UUID> candidates = new ArrayList<>();
        List<UUID> all = advertised.get(selected.getAddress());
        if (all != null) for (UUID uuid : all) if (NativePolicy.rfcomm(uuid)) candidates.add(uuid);
        if (candidates.isEmpty()) { event("IVI-01.NATIVE", "INCONCLUSIVE", "No supported RFCOMM service selected", "Read cached UUIDs or request SDP. A2DP/AVRCP are not raw RFCOMM targets; an IVI may expose no supported target."); return; }
        String[] labels = new String[candidates.size()]; for (int i = 0; i < labels.length; i++) labels[i] = NativePolicy.label(candidates.get(i));
        new AlertDialog.Builder(this).setTitle("Advertised RFCOMM services").setItems(labels, (d, which) -> { nativeService = candidates.get(which); if (serviceView != null) serviceView.setText("RFCOMM service: " + NativePolicy.label(nativeService)); event("IVI-01.NATIVE", "OBSERVED", "Native service selected", nativeService.toString()); }).setNegativeButton("Cancel", null).show();
    }
    private void nativeConnect(boolean weak) {
        if (!ready(true) || !requirePeer()) return;
        List<UUID> known = advertised.get(selected.getAddress());
        if (nativeService == null || known == null || !known.contains(nativeService) || !NativePolicy.rfcomm(nativeService)) { event("IVI-01.NATIVE", "INCONCLUSIVE", "No advertised RFCOMM target", "Choose a supported service from this IVI's UUID inventory first"); return; }
        if (peerBusy || pendingSdp != null) { event("IVI-01.NATIVE", "INCONCLUSIVE", "Bluetooth test busy", "Stop or wait for the current operation"); return; }
        final BluetoothDevice target = selected; final UUID uuid = nativeService;
        final boolean expectedBond = bondRequired != null && bondRequired.isChecked(); final int token = ++operation; peerBusy = true;
        workers.execute(() -> {
            BluetoothSocket socket = null;
            try {
                adapter.cancelDiscovery(); int before = target.getBondState();
                socket = weak ? target.createInsecureRfcommSocketToServiceRecord(uuid) : target.createRfcommSocketToServiceRecord(uuid); client = socket;
                if (token != operation) throw new IOException("Test cancelled");
                final BluetoothSocket deadlineSocket = socket; ui.postDelayed(() -> close(deadlineSocket), 20000);
                event("IVI-01.NATIVE", "OBSERVED", "Native connection attempt", target.getAddress() + "; service=" + uuid + "; mode=" + (weak ? "unauthenticated" : "secure") + "; bondBefore=" + before + "; expectedBond=" + expectedBond + "; no application payload");
                socket.connect();
                if (token != operation) throw new IOException("Test cancelled");
                int after = target.getBondState();
                event("IVI-01.NATIVE", NativePolicy.verdict(true, expectedBond, after), "Native RFCOMM transport established", target.getAddress() + "; service=" + uuid + "; mode=" + (weak ? "unauthenticated" : "secure") + "; bondBefore=" + before + "; bondAfter=" + after + "; expectedBond=" + expectedBond + "; applicationBytesSent=0; applicationBytesRead=0; verdict, if any, covers only the chosen bond-state policy; protected-data authorization not tested");
            } catch (Exception e) { event("IVI-01.NATIVE", "INCONCLUSIVE", "Native connection not established", describe(e) + "; timeout/refusal/cancellation cannot establish IVI authorization enforcement"); }
            finally { close(socket); client = null; peerBusy = false; }
        });
    }
    private void profiles() {
        if (!ready(true) || !requirePeer()) return;
        final BluetoothDevice target = selected;
        for (int profile : new int[]{BluetoothProfile.A2DP, BluetoothProfile.HEADSET}) {
            final java.util.concurrent.atomic.AtomicBoolean delivered = new java.util.concurrent.atomic.AtomicBoolean();
            final String label = profile == BluetoothProfile.A2DP ? "A2DP" : "HFP/Headset";
            boolean started = adapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
                @Override public void onServiceConnected(int type, BluetoothProfile proxy) {
                    try { if (delivered.compareAndSet(false, true) && !isDestroyed()) event("IVI-01.PROFILE", "OBSERVED", "Selected IVI profile state", label + "; peer=" + target.getAddress() + "; state=" + proxy.getConnectionState(target) + "; 0=disconnected,1=connecting,2=connected,3=disconnecting; local system profile observation, not remote data authorization"); }
                    catch (Exception e) { event("IVI-01.PROFILE", "INCONCLUSIVE", "Profile query failed", label + "; " + describe(e)); }
                    finally { adapter.closeProfileProxy(type, proxy); }
                }
                @Override public void onServiceDisconnected(int type) { }
            }, profile);
            if (!started) { delivered.set(true); event("IVI-01.PROFILE", "INCONCLUSIVE", "Profile proxy unavailable", label); }
            else ui.postDelayed(() -> { if (delivered.compareAndSet(false, true)) event("IVI-01.PROFILE", "INCONCLUSIVE", "Profile query timeout", label); }, 10000);
        }
    }
    private void manualResult() {
        if (!requirePeer()) return;
        final String address = selected.getAddress();
        String[] cases = {"Pairing requires visible confirmation", "Contacts stay unavailable after permission denial", "Messages stay unavailable after permission denial", "IVI remains responsive after disconnect"};
        new AlertDialog.Builder(this).setTitle("Choose manual native-profile test").setItems(cases, (d, which) -> {
            EditText evidence = new EditText(this); evidence.setHint("Required: observed behavior and evidence/trace ID"); evidence.setMinLines(3);
            AlertDialog dialog = new AlertDialog.Builder(this).setTitle(cases[which]).setView(evidence).setPositiveButton("Record result", null).setNegativeButton("Cancel", null).create();
            dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
                String note = evidence.getText().toString().trim(); if (note.isEmpty()) { evidence.setError("Record the observation first"); return; }
                new AlertDialog.Builder(this).setTitle("Operator-assessed verdict").setItems(new String[]{"PASS", "FAIL", "INCONCLUSIVE"}, (x, outcome) -> { event("IVI-01.MANUAL", new String[]{"PASS", "FAIL", "INCONCLUSIVE"}[outcome], cases[which], "source=operator; peer=" + address + "; " + note + "; not independently verified by APK"); dialog.dismiss(); }).show();
            })); dialog.show();
        }).setNegativeButton("Cancel", null).show();
    }

    private boolean has(String permission) { return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED; }
    private boolean ready(boolean enabled) {
        if (adapter == null) { event("IVI-01", "INCONCLUSIVE", "Bluetooth unavailable", "No Bluetooth adapter exposed to this app"); return false; }
        if (!has(Manifest.permission.BLUETOOTH_CONNECT) || !has(Manifest.permission.BLUETOOTH_SCAN)) { event("IVI-01", "INCONCLUSIVE", "Permissions missing", "Grant Nearby devices or run the pre-grant boundary check"); return false; }
        if (enabled && !adapter.isEnabled()) { event("IVI-01", "INCONCLUSIVE", "Bluetooth disabled", "Enable Bluetooth using the system prompt"); return false; } return true;
    }
    private boolean requirePeer() { if (selected != null) return true; event("IVI-01", "INCONCLUSIVE", "No peer selected", "Discover/list and choose the intended test peer"); return false; }
    private void permissionBoundary() {
        if (adapter == null) { event("IVI-01.PERM", "INCONCLUSIVE", "Permission boundary", "No Bluetooth adapter"); return; }
        if (has(Manifest.permission.BLUETOOTH_CONNECT)) { event("IVI-01.PERM", "INCONCLUSIVE", "Permission boundary", "Permission already granted. Revoke Nearby devices in system App settings and reopen before repeating."); return; }
        try { adapter.getBondedDevices(); event("IVI-01.PERM", "FAIL", "Bonded-device permission boundary", "Protected API returned without BLUETOOTH_CONNECT; check whether this app has privileged exemptions"); }
        catch (SecurityException e) { event("IVI-01.PERM", "PASS", "Bonded-device permission boundary", "SecurityException without BLUETOOTH_CONNECT"); }
        catch (Exception e) { event("IVI-01.PERM", "INCONCLUSIVE", "Permission boundary", describe(e)); }
    }
    private String deviceLabel(BluetoothDevice d) { try { return (d.getName() == null ? "Unnamed" : d.getName()) + " • " + d.getAddress() + " • bond=" + d.getBondState(); } catch (SecurityException e) { return "Permission required"; } }
    private void bonded() { if (!ready(true)) return; for (BluetoothDevice d : adapter.getBondedDevices()) peers.put(d.getAddress(), d); event("IVI-01.INVENTORY", "OBSERVED", "Bonded peers listed", "Candidates=" + peers.size()); choosePeer(); }
    private void discover() {
        if (!ready(true)) return;
        if (peerBusy) { event("IVI-01", "INCONCLUSIVE", "Test busy", "Stop the peer test before discovery"); return; }
        adapter.cancelDiscovery(); boolean started = adapter.startDiscovery();
        event("IVI-01.DISCOVERY", started ? "OBSERVED" : "INCONCLUSIVE", "Discovery requested", "Started=" + started + "; automatic cancellation after 12 seconds");
        ui.postDelayed(() -> { try { if (adapter != null && has(Manifest.permission.BLUETOOTH_SCAN)) adapter.cancelDiscovery(); } catch (Exception ignored) { } }, 12000);
    }
    private void choosePeer() {
        if (!ready(true)) return;
        ArrayList<BluetoothDevice> list = new ArrayList<>(peers.values()); String[] labels = new String[list.size()];
        for (int i = 0; i < list.size(); i++) labels[i] = deviceLabel(list.get(i));
        if (labels.length == 0) { event("IVI-01", "INCONCLUSIVE", "No candidates", "List bonded peers or run discovery"); return; }
        new AlertDialog.Builder(this).setTitle("Select your test peer").setItems(labels, (d, which) -> { selected = list.get(which); nativeService = null; pendingSdp = null; sdpOperation++; if (serviceView != null) serviceView.setText("RFCOMM service: not selected"); if (peerView != null) peerView.setText("Selected peer: " + deviceLabel(selected)); event("IVI-01.PEER", "OBSERVED", "Peer selected", deviceLabel(selected)); }).setNegativeButton("Cancel", null).show();
    }
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            try {
                String action = intent.getAction();
                BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                if (BluetoothDevice.ACTION_FOUND.equals(action) && d != null && has(Manifest.permission.BLUETOOTH_CONNECT)) peers.put(d.getAddress(), d);
                if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action) && d != null && selected != null && d.getAddress().equals(selected.getAddress())) event("IVI-01.BOND", "OBSERVED", "Selected peer bond changed", deviceLabel(d));
                if (d != null && selected != null && d.getAddress().equals(selected.getAddress())) {
                    if (BluetoothDevice.ACTION_UUID.equals(action)) { pendingSdp = null; Parcelable[] raw = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID); List<ParcelUuid> values = new ArrayList<>(); if (raw != null) for (Parcelable value : raw) if (value instanceof ParcelUuid) values.add((ParcelUuid)value); services(d, raw == null ? null : values.toArray(new ParcelUuid[0]), "ACTION_UUID broadcast; response may be cached, freshness not guaranteed"); }
                    if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action) || BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) event("IVI-01.LINK", "OBSERVED", "Selected IVI link changed", deviceLabel(d) + "; " + action + "; ACL state is not profile authorization");
                }
                if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) event("IVI-01.DISCOVERY", "OBSERVED", "Discovery finished", "Candidates=" + peers.size() + "; choose intended test peer");
            } catch (Exception e) { event("IVI-01.EVENT", "INCONCLUSIVE", "Bluetooth event", describe(e)); }
        }
    };

    private void listen() {
        if (!ready(true)) return;
        if (peerBusy) { event("IVI-01", "INCONCLUSIVE", "Test busy", "Stop or wait for the active test"); return; }
        final boolean weak = insecure.isChecked(); final int token = ++operation; peerBusy = true;
        workers.execute(() -> {
            BluetoothServerSocket local = null; BluetoothSocket socket = null;
            try {
                local = weak ? adapter.listenUsingInsecureRfcommWithServiceRecord("IVILab", SERVICE) : adapter.listenUsingRfcommWithServiceRecord("IVILab", SERVICE);
                server = local; if (token != operation) throw new IOException("Test cancelled"); event("IVI-01.LISTEN", "OBSERVED", "Listening once", "Mode=" + (weak ? "unauthenticated" : "secure") + "; UUID=" + SERVICE + "; timeout=60s");
                socket = local.accept(60000); accepted = socket;
                final BluetoothSocket deadlineSocket = socket; ui.postDelayed(() -> close(deadlineSocket), 10000);
                String input = LabProtocol.readLine(socket.getInputStream());
                String answer = LabProtocol.response(input); boolean valid = answer.startsWith(ACK);
                socket.getOutputStream().write((answer + "\n").getBytes(StandardCharsets.US_ASCII)); socket.getOutputStream().flush();
                event("IVI-01.LAB_PROTOCOL", "OBSERVED", valid ? "Valid lab message handled" : "Invalid lab message rejected", "Remote=" + socket.getRemoteDevice().getAddress() + "; bond=" + socket.getRemoteDevice().getBondState() + "; mode=" + (weak ? "unauthenticated" : "secure") + "; lab fixture only");
            } catch (Exception e) { event("IVI-01.LISTEN", "INCONCLUSIVE", "Listener ended", describe(e) + "; a timeout/transport failure is not proof of authorization enforcement"); }
            finally { close(socket); close(local); accepted = null; server = null; peerBusy = false; }
        });
    }
    private void connect(boolean invalid) {
        if (!ready(true) || !requirePeer()) return;
        if (peerBusy) { event("IVI-01", "INCONCLUSIVE", "Test busy", "Stop or wait for the active test"); return; }
        final boolean weak = insecure.isChecked(); final BluetoothDevice target = selected; final int token = ++operation; peerBusy = true;
        workers.execute(() -> {
            BluetoothSocket socket = null;
            try {
                adapter.cancelDiscovery(); int before = target.getBondState();
                socket = weak ? target.createInsecureRfcommSocketToServiceRecord(SERVICE) : target.createRfcommSocketToServiceRecord(SERVICE); client = socket; if (token != operation) throw new IOException("Test cancelled");
                final BluetoothSocket deadlineSocket = socket; ui.postDelayed(() -> close(deadlineSocket), 20000);
                socket.connect(); String nonce = UUID.randomUUID().toString();
                String payload = invalid ? "IVILAB/1 INVALID harmless-input" : PING + nonce;
                socket.getOutputStream().write((payload + "\n").getBytes(StandardCharsets.US_ASCII)); socket.getOutputStream().flush();
                String response = LabProtocol.readLine(socket.getInputStream()); String expected = invalid ? "IVILAB/1 REJECT" : ACK + nonce;
                event("IVI-01.LAB_PROTOCOL", response.equals(expected) ? "PASS" : "FAIL", invalid ? "Invalid lab input response" : "Valid lab input response", "Expected protocol response=" + response.equals(expected) + "; peer=" + target.getAddress() + "; mode=" + (weak ? "unauthenticated" : "secure") + "; bondBefore=" + before + "; bondAfter=" + target.getBondState() + "; verdict applies only to our lab message handler, not native IVI profiles or stack");
            } catch (Exception e) { event("IVI-01.CONNECT", "INCONCLUSIVE", "Peer connection ended", describe(e) + "; check peer listener, UUID, pairing UI and transport; not an authorization pass"); }
            finally { close(socket); client = null; peerBusy = false; }
        });
    }
    private static void close(Closeable c) { if (c != null) try { c.close(); } catch (IOException ignored) { } }
    private void stop(String reason) {
        operation++;
        pendingSdp = null; sdpOperation++;
        close(server); close(client); close(accepted);
        try { if (adapter != null && has(Manifest.permission.BLUETOOTH_SCAN)) adapter.cancelDiscovery(); } catch (Exception ignored) { }
        event("IVI-01.STOP", "OBSERVED", "Bluetooth test stopped", reason + "; sockets closed; discoverability may remain until its 60-second system timeout");
    }

    private void containmentPage() {
        info("IVI-02 • Local device only", "These checks run on this device. If the APK is on a phone, they test only that phone, not the remote IVI. IVI containment needs an app running on the IVI or separate bench instrumentation. Probes do not send vehicle commands or modify firmware.");
        info("Fixture setup", "Install IVI Lab Fixture on the same device and Android user. Open it and confirm READY before testing. It creates a harmless private file and a non-exported provider under a separate app UID.");
        addButton("Open fixture self-check", () -> { Intent i = getPackageManager().getLaunchIntentForPackage(FIXTURE); if (i == null) event("IVI-02.FIXTURE", "INCONCLUSIVE", "Fixture missing", "Install IVI Lab Fixture first"); else startActivity(i); });
        addButton("Run containment checks", () -> workers.execute(this::containment));
        info("Scope of the verdicts", "PASS means the specific resource attempt was denied under the recorded permissions. File-not-found, missing fixtures and unexpected errors are inconclusive. Permission inspection is not a vehicle API invocation. SGW/CAN isolation requires a separate bench test and traffic capture.");
    }
    private void containment() {
        event("IVI-02.CONTEXT", "OBSERVED", "Execution context", "UID=" + android.os.Process.myUid() + "; automotive=" + getPackageManager().hasSystemFeature("android.hardware.type.automotive") + "; fingerprint=" + Build.FINGERPRINT);
        try {
            ApplicationInfo a = getPackageManager().getApplicationInfo(FIXTURE, 0);
            if (a.uid == android.os.Process.myUid()) { event("IVI-02.FIXTURE", "INCONCLUSIVE", "Fixture UID separation absent", "Both apps have UID=" + a.uid); }
            else {
                probeFile("IVI-02.FILE", new File(a.dataDir, "files/canary.txt").getPath());
                try (Cursor c = getContentResolver().query(Uri.parse("content://" + FIXTURE + ".private/canary"), null, null, null, null)) {
                    event("IVI-02.PROVIDER", c == null ? "INCONCLUSIVE" : "FAIL", "Private provider access", c == null ? "Null cursor; no authorization verdict" : "Non-exported fixture provider returned a cursor across separate UIDs");
                } catch (SecurityException e) { event("IVI-02.PROVIDER", "PASS", "Private provider access denied", "SecurityException across separate app UIDs"); }
                  catch (Exception e) { event("IVI-02.PROVIDER", "INCONCLUSIVE", "Private provider probe", describe(e)); }
            }
        } catch (PackageManager.NameNotFoundException e) { event("IVI-02.FIXTURE", "INCONCLUSIVE", "Fixture unavailable", "Install and open fixture on this device and Android user"); }
          catch (Exception e) { event("IVI-02.FIXTURE", "INCONCLUSIVE", "Fixture probe error", describe(e)); }
        probeFile("IVI-02.SYSTEM_FILE", "/data/system/packages.xml");
        probeFile("IVI-02.SYSTEM_FILE", "/data/misc/bluedroid/bt_config.conf");
        String[] privileges = {"android.permission.WRITE_SECURE_SETTINGS", "android.permission.INSTALL_PACKAGES", "android.permission.BLUETOOTH_PRIVILEGED", "android.car.permission.CAR_DIAGNOSTIC_READ_ALL", "android.car.permission.CAR_DIAGNOSTIC_CLEAR"};
        for (String permission : privileges) {
            try {
                getPackageManager().getPermissionInfo(permission, 0);
                boolean granted = has(permission);
                event("IVI-02.PERMISSION", granted ? "FAIL" : "PASS", "Ordinary-app privilege baseline", permission + "; granted=" + granted + "; inspection only, API not invoked; these permissions are not requested by this APK");
            } catch (PackageManager.NameNotFoundException e) { event("IVI-02.PERMISSION", "INCONCLUSIVE", "Permission not defined", permission); }
        }
        try {
            String enforce = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("/sys/fs/selinux/enforce")), StandardCharsets.UTF_8).trim();
            event("IVI-02.SELINUX", "OBSERVED", "SELinux state", "enforce=" + enforce + "; observation only, not complete containment proof");
        } catch (Exception e) { event("IVI-02.SELINUX", "INCONCLUSIVE", "SELinux state unavailable", describe(e)); }
        event("IVI-02.COMPLETE", "OBSERVED", "Containment checks finished", "Review individual results. No overall vehicle-security verdict is calculated.");
    }
    private void probeFile(String id, String path) {
        try (FileInputStream ignored = new FileInputStream(path)) {
            event(id, "FAIL", "Protected file opened", path + "; no contents read or exported");
        } catch (SecurityException e) { event(id, "PASS", "Protected file access denied", path + "; SecurityException"); }
          catch (IOException e) {
            Throwable cause = e; boolean denied = false;
            while (cause != null) { if (cause instanceof ErrnoException) { int errno = ((ErrnoException)cause).errno; denied = errno == OsConstants.EACCES || errno == OsConstants.EPERM; break; } cause = cause.getCause(); }
            event(id, denied ? "PASS" : "INCONCLUSIVE", denied ? "Protected path access denied" : "Protected file probe unavailable", path + "; " + describe(e) + "; denied-path evidence does not establish that a file exists");
        }
    }

    private void evidencePage() {
        info("Local evidence", "Reports remain in this app's private storage until you export them. They include build details, timestamps, chosen peer addresses and your notes. There is no Internet permission, cloud upload or background service. Up to 500 events are retained per session.");
        role = new Spinner(this); role.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"IVI", "Phone", "Emulator / bench"}));
        role.setSelection(roleValue.equals("Phone") ? 1 : roleValue.equals("Emulator / bench") ? 2 : 0); root.addView(role);
        notes = new EditText(this); notes.setTextColor(0xffe6edf3); notes.setHintTextColor(0xffa0b0be); notes.setHint("Target model, firmware, setup, observed pairing prompt and policy requirement"); notes.setMinLines(3); notes.setText(notesValue); root.addView(notes);
        addButton("Save notes", () -> { saveInputs(); event("SESSION", "OBSERVED", "Operator notes updated", "Role=" + roleValue); });
        addButton("Export JSON report", this::export);
        addButton("Start new session", () -> new AlertDialog.Builder(this).setTitle("Start a new session?").setMessage("Export the current report first if needed. This clears the local event history.").setPositiveButton("New session", (d, w) -> { stop("Starting new session"); synchronized (reportLock) { while (events.length() > 0) events.remove(0); session = UUID.randomUUID().toString(); } event("SESSION", "OBSERVED", "New session", "Fresh local event history"); }).setNegativeButton("Cancel", null).show());
        logView = text("", 13, 0xffe6edf3); logView.setTypeface(Typeface.MONOSPACE); logView.setTextIsSelectable(true); root.addView(logView); refreshLog();
    }
    private void saveInputs() { if (notes != null) notesValue = notes.getText().toString(); if (role != null) roleValue = role.getSelectedItem().toString(); synchronized (reportLock) { persist(); } }
    private JSONObject report() throws JSONException {
        JSONObject r = new JSONObject(); r.put("schema_version", 2); r.put("app_version", "1.1"); r.put("session_id", session); r.put("role", roleValue); r.put("operator_notes", notesValue);
        r.put("scope", "Phone-only native IVI service/transport checks are separate from local device containment and controlled peer lab tests");
        r.put("device", new JSONObject().put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL).put("android_release", Build.VERSION.RELEASE).put("sdk", Build.VERSION.SDK_INT).put("fingerprint", Build.FINGERPRINT).put("security_patch", Build.VERSION.SECURITY_PATCH).put("app_uid", android.os.Process.myUid()).put("automotive_feature", getPackageManager().hasSystemFeature("android.hardware.type.automotive")));
        JSONObject grants = new JSONObject(); for (String p : new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE}) grants.put(p, has(p)); r.put("permissions_at_export", grants);
        r.put("limitations", "Native service discovery may be cached. Native RFCOMM checks send/read zero application bytes. Transport verdicts cover only the operator-selected bond policy. Native profile authorization results are manual operator reports. Local containment applies only to the device running the app. No Bluetooth-stack CVE reproduction, CAN/SGW test or physical vehicle proof. Lab-protocol PASS applies only to lab service. Transport failures are inconclusive.");
        r.put("events", new JSONArray(events.toString())); return r;
    }
    private void persist() {
        AtomicFile file = new AtomicFile(new File(getFilesDir(), "report.json")); FileOutputStream out = null;
        try { byte[] data = report().toString(2).getBytes(StandardCharsets.UTF_8); out = file.startWrite(); out.write(data); file.finishWrite(out); }
        catch (Exception e) { if (out != null) file.failWrite(out); android.util.Log.e("IVILab", "Report persistence failed", e); }
    }
    private void event(String id, String verdict, String title, String detail) {
        synchronized (reportLock) {
            try { SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US); format.setTimeZone(TimeZone.getTimeZone("UTC"));
                JSONObject grants = new JSONObject().put("bluetooth_connect", has(Manifest.permission.BLUETOOTH_CONNECT)).put("bluetooth_scan", has(Manifest.permission.BLUETOOTH_SCAN));
                events.put(new JSONObject().put("timestamp_utc", format.format(new Date())).put("elapsed_realtime_ms", SystemClock.elapsedRealtime()).put("id", id).put("verdict", verdict).put("title", title).put("detail", detail).put("permissions", grants));
                while (events.length() > 500) events.remove(0); persist();
            } catch (Exception e) { android.util.Log.e("IVILab", "Event recording failed", e); }
        }
        android.util.Log.i("IVILab", id + " " + verdict + " " + title + " | " + detail);
        ui.post(() -> { if (isDestroyed()) return; refreshLog(); Toast.makeText(this, verdict + ": " + title, Toast.LENGTH_SHORT).show(); });
    }
    private void refreshLog() {
        if (logView == null) return; StringBuilder b = new StringBuilder();
        synchronized (reportLock) { for (int i = events.length() - 1; i >= Math.max(0, events.length() - 60); i--) { JSONObject e = events.optJSONObject(i); if (e != null) b.append(e.optString("verdict")).append(" • ").append(e.optString("id")).append("\n").append(e.optString("title")).append("\n").append(e.optString("detail")).append("\n\n"); } }
        logView.setText(b.toString());
    }
    private static String describe(Throwable e) { return e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()); }
    private void export() {
        saveInputs(); synchronized (reportLock) { try { pendingExport = report().toString(2).getBytes(StandardCharsets.UTF_8); } catch (JSONException e) { throw new IllegalStateException(e); } }
        try (FileOutputStream out = openFileOutput("pending-export.json", 0)) { out.write(pendingExport); }
        catch (IOException e) { event("REPORT", "INCONCLUSIVE", "Export snapshot failed", describe(e)); return; }
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT); i.setType("application/json"); i.addCategory(Intent.CATEGORY_OPENABLE); i.putExtra(Intent.EXTRA_TITLE, "ivi-lab-" + session + ".json"); startActivityForResult(i, 20);
    }
    @Override public void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 20 && pendingExport == null) { try (FileInputStream in = openFileInput("pending-export.json")) { pendingExport = in.readAllBytes(); } catch (IOException ignored) { } }
        if (request == 20 && result == RESULT_OK && data != null && data.getData() != null && pendingExport != null) {
            try (OutputStream out = getContentResolver().openOutputStream(data.getData())) { if (out == null) throw new IOException("No output stream"); out.write(pendingExport); event("REPORT", "OBSERVED", "Report exported", "Saved through Android document picker"); }
            catch (Exception e) { event("REPORT", "INCONCLUSIVE", "Export failed", describe(e)); }
        }
        pendingExport = null;
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) { super.onRequestPermissionsResult(request, permissions, grants); event("IVI-01.PERM", "OBSERVED", "Permission prompt completed", "CONNECT=" + has(Manifest.permission.BLUETOOTH_CONNECT) + "; SCAN=" + has(Manifest.permission.BLUETOOTH_SCAN) + "; ADVERTISE=" + has(Manifest.permission.BLUETOOTH_ADVERTISE)); }
    @Override protected void onStop() { saveInputs(); stop("App left foreground"); super.onStop(); }
    @Override protected void onDestroy() { if (registered) unregisterReceiver(receiver); workers.shutdownNow(); super.onDestroy(); }
}
