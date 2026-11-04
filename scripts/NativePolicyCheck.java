import com.rlv.ivilab.NativePolicy;
import java.util.UUID;

public class NativePolicyCheck {
    private static int checks;
    private static void expect(boolean result) { checks++; if (!result) throw new AssertionError("Native policy check " + checks); }
    private static UUID assigned(int value) { return UUID.fromString(String.format("0000%04x-0000-1000-8000-00805f9b34fb", value)); }
    public static void main(String[] args) {
        for (int state : new int[]{10, 11, 12, -1}) {
            expect(NativePolicy.verdict(false, false, state).equals("INCONCLUSIVE"));
            expect(NativePolicy.verdict(false, true, state).equals("INCONCLUSIVE"));
            expect(NativePolicy.verdict(true, false, state).equals("OBSERVED"));
            expect(NativePolicy.verdict(true, true, state).equals(state == 12 ? "PASS" : "FAIL"));
        }
        expect(NativePolicy.rfcomm(assigned(0x111e)));
        expect(NativePolicy.rfcomm(assigned(0x112f)));
        expect(NativePolicy.rfcomm(assigned(0x1105)));
        expect(!NativePolicy.rfcomm(assigned(0x110b)));
        expect(!NativePolicy.rfcomm(assigned(0x110f)));
        expect(!NativePolicy.rfcomm(assigned(0x112e)));
        expect(!NativePolicy.rfcomm(UUID.fromString("0000111e-0000-1000-8000-00805f9b34fa")));
        expect(!NativePolicy.rfcomm(null));
        expect(NativePolicy.label(assigned(0x111e)).startsWith("Hands-Free •"));
        expect(NativePolicy.label(assigned(0x110b)).startsWith("A2DP Audio Sink •"));
        expect(NativePolicy.label(assigned(0x9999)).startsWith("Other advertised service •"));
        System.out.println(checks + " native service/policy checks passed");
    }
}
