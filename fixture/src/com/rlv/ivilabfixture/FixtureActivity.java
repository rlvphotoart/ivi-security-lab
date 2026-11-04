package com.rlv.ivilabfixture;

import android.app.Activity;
import android.os.Bundle;
import android.database.Cursor;
import android.net.Uri;
import android.widget.TextView;
import java.io.FileInputStream;

public class FixtureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setTextColor(0xffe6edf3); view.setTextSize(20); view.setPadding(28, 48, 28, 28);
        String result;
        try (Cursor c = getContentResolver().query(Uri.parse("content://com.rlv.ivilabfixture.private/canary"), null, null, null, null);
             FileInputStream in = openFileInput("canary.txt")) {
            if (c == null || !c.moveToFirst() || in.read() < 0) throw new Exception("Fixture self-check failed");
            result = "READY — self-check passed\n\nPrivate canary file and private provider are available to the fixture itself.\n\nUID: " + android.os.Process.myUid() + "\n\nReturn to IVI Security Lab and run the containment checks. This fixture holds no vehicle data.";
        } catch (Exception e) { result = "NOT READY\n" + e; }
        view.setText("IVI Lab Fixture\n\n" + result); setContentView(view);
    }
}
