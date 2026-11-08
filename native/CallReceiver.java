package de.taxiruf25500.funk;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;

/** Merkt sich die Nummer beim Klingeln; sobald der Anruf angenommen wird (OFFHOOK), wird die Nummer an die App übergeben. */
public class CallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        if (!TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) return;
        String st = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        String num = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
        SharedPreferences sp = ctx.getSharedPreferences("funk_call", Context.MODE_PRIVATE);
        if (TelephonyManager.EXTRA_STATE_RINGING.equals(st)) {
            if (num != null && !num.isEmpty()) { sp.edit().putString("ring", num).putLong("ringT", System.currentTimeMillis()).apply(); CallWatcherPlugin.ring(num); }
        } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(st)) {
            String n = sp.getString("ring", "");
            if (n.isEmpty() || System.currentTimeMillis() - sp.getLong("ringT", 0) > 90000) return;
            sp.edit().remove("ring").apply();
            if (CallWatcherPlugin.deliver(n, "in")) return;
            CallWatcherPlugin.pending = n;
            try {
                NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
                if (android.os.Build.VERSION.SDK_INT >= 26) { NotificationChannel ch = new NotificationChannel("funk_call", "Anrufe", NotificationManager.IMPORTANCE_HIGH); nm.createNotificationChannel(ch); }
                Intent open = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("funk_number", n);
                PendingIntent pi = PendingIntent.getActivity(ctx, 7, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                android.app.Notification.Builder b = android.os.Build.VERSION.SDK_INT >= 26 ? new android.app.Notification.Builder(ctx, "funk_call") : new android.app.Notification.Builder(ctx);
                b.setSmallIcon(android.R.drawable.sym_call_incoming).setContentTitle("Auftrag aufnehmen").setContentText(n + " – tippen zum Öffnen").setContentIntent(pi).setAutoCancel(true).setCategory(android.app.Notification.CATEGORY_CALL).setPriority(android.app.Notification.PRIORITY_HIGH);
                nm.notify(7001, b.build());
            } catch (Throwable t) { }
        } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(st)) {
            sp.edit().remove("ring").apply(); CallWatcherPlugin.callEnd();
        }
    }
}
