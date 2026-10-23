package br.com.dih.retifica;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(DitadoVozPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
