package com.rlv.ivilab;

import java.io.*;
import java.util.UUID;

/** Tiny bounded protocol used only by the controlled RFCOMM fixture. */
public final class LabProtocol {
    public static final String PING = "IVILAB/1 PING ";
    public static final String ACK = "IVILAB/1 ACK ";
    private LabProtocol() { }
    public static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < 256; i++) {
            int b = in.read();
            if (b < 0) throw new EOFException("Peer closed before newline");
            if (b == '\n') return out.toString("US-ASCII");
            out.write(b);
        }
        throw new IOException("Lab message exceeded 256-byte bound");
    }
    public static String response(String input) {
        if (input.startsWith(PING)) {
            String nonce = input.substring(PING.length());
            try { if (UUID.fromString(nonce).toString().equals(nonce)) return ACK + nonce; }
            catch (IllegalArgumentException ignored) { }
        }
        return "IVILAB/1 REJECT";
    }
}
