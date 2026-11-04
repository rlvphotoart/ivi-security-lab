import com.rlv.ivilab.LabProtocol;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class ProtocolCheck {
    static int checks;
    static void expect(boolean condition) { checks++; if (!condition) throw new AssertionError("Check " + checks); }
    static ByteArrayInputStream bytes(String s) { return new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII)); }
    public static void main(String[] args) throws Exception {
        String nonce = "92e6bd8b-4bb2-4aa8-971a-8c99c927be71";
        expect(LabProtocol.response(LabProtocol.PING + nonce).equals(LabProtocol.ACK + nonce));
        for (String input : new String[]{"", "IVILAB/1 INVALID harmless-input", LabProtocol.PING, LabProtocol.PING + "1-1-1-1-1", LabProtocol.PING + nonce + " extra", LabProtocol.PING + nonce + "\r", "IVILAB/2 PING " + nonce}) expect(LabProtocol.response(input).equals("IVILAB/1 REJECT"));
        ByteArrayInputStream two = bytes("first\nsecond\n");
        expect(LabProtocol.readLine(two).equals("first")); expect(LabProtocol.readLine(two).equals("second"));
        expect(LabProtocol.readLine(bytes("\n")).equals(""));
        byte[] max = new byte[256]; Arrays.fill(max, (byte)'x'); max[255] = '\n';
        expect(LabProtocol.readLine(new ByteArrayInputStream(max)).length() == 255);
        byte[] over = new byte[257]; Arrays.fill(over, (byte)'x'); over[256] = '\n';
        try { LabProtocol.readLine(new ByteArrayInputStream(over)); throw new AssertionError("Oversized frame accepted"); } catch (IOException expected) { checks++; }
        try { LabProtocol.readLine(bytes("truncated")); throw new AssertionError("Truncated frame accepted"); } catch (EOFException expected) { checks++; }
        System.out.println(checks + " protocol checks passed");
    }
}
