package pt.andrades.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

/** Recebe a resposta do instalador do Android: mostra o pedido de confirmação ou avisa se falhou. */
public final class UpdateReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else if (status != PackageInstaller.STATUS_SUCCESS) {
            String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            Toast.makeText(c, "A atualização falhou" + (msg != null ? ": " + msg : ""), Toast.LENGTH_LONG).show();
        }
    }
}
