package com.rlv.ivilab;

import java.util.*;

/** Narrow transport policy; does not infer application authorization from connectivity. */
public final class NativePolicy {
    private static final Map<Integer, String> NAMES = new LinkedHashMap<>();
    private static final Set<Integer> RFCOMM = new HashSet<>(Arrays.asList(0x1101, 0x1105, 0x1106, 0x1108, 0x1112, 0x111e, 0x111f, 0x112f, 0x1132, 0x1133));
    static {
        NAMES.put(0x1101, "Serial Port"); NAMES.put(0x1105, "OBEX Object Push"); NAMES.put(0x1106, "OBEX File Transfer");
        NAMES.put(0x1108, "Headset"); NAMES.put(0x1112, "Headset Audio Gateway");
        NAMES.put(0x110a, "A2DP Audio Source"); NAMES.put(0x110b, "A2DP Audio Sink");
        NAMES.put(0x110c, "AVRCP Target"); NAMES.put(0x110e, "AV Remote Control"); NAMES.put(0x110f, "AVRCP Controller");
        NAMES.put(0x111e, "Hands-Free"); NAMES.put(0x111f, "Hands-Free Audio Gateway");
        NAMES.put(0x112e, "Phonebook Access Client"); NAMES.put(0x112f, "Phonebook Access Server");
        NAMES.put(0x1132, "Message Access Server"); NAMES.put(0x1133, "Message Notification Server");
    }
    private NativePolicy() { }
    private static int assigned(UUID uuid) {
        if (uuid == null) return -1;
        String value = uuid.toString();
        if (!value.startsWith("0000") || !value.substring(8).equals("-0000-1000-8000-00805f9b34fb")) return -1;
        return Integer.parseInt(value.substring(4, 8), 16);
    }
    public static String label(UUID uuid) { String name = NAMES.get(assigned(uuid)); return (name == null ? "Other advertised service" : name) + " • " + uuid; }
    public static boolean rfcomm(UUID uuid) { return RFCOMM.contains(assigned(uuid)); }
    public static String verdict(boolean connected, boolean bondRequired, int bondAfter) {
        if (!connected) return "INCONCLUSIVE";
        if (!bondRequired) return "OBSERVED";
        return bondAfter == 12 ? "PASS" : "FAIL";
    }
}
