package net.heretical_camelid.fhau.android_app;

import android.annotation.SuppressLint;

import android.os.Bundle;
import android.os.Message;
import net.heretical_camelid.fhau.lib.*;
import net.heretical_camelid.fhau.lib.interfaces.IAmpProvider;
import net.heretical_camelid.fhau.lib.registries.FenderJsonPresetRegistry;
import net.heretical_camelid.fhau.lib.registries.PresetRegistryBase;
import net.heretical_camelid.fhau.lib.registries.PresetSuiteRegistry;

import java.util.ArrayList;
import java.util.HashMap;

public class AndroidUsbAmpProvider implements IAmpProvider {

    MainActivity m_mainActivity;
    DeviceTransportUsbHid m_deviceTransportUsbHid;
    LTSeriesProtocol m_protocol;

    FenderJsonPresetRegistry m_presetRegistry;
    PresetSuiteRegistry m_presetSuiteRegistry;
    String m_ampModel;
    String m_firmwareVersion;

    AndroidUsbAmpProvider(
        MainActivity mainActivity
    ) {
        m_mainActivity = mainActivity;
        m_deviceTransportUsbHid = new DeviceTransportUsbHid(m_mainActivity, this);
        m_presetRegistry = new FenderJsonPresetRegistry(null);
        m_protocol = new LTSeriesProtocol(m_presetRegistry,true);

        m_presetSuiteRegistry = null;
    }

    public boolean getFirmwareVersionAndPresets() {
        String[] firmwareVersionHolder = new String[] { null };
        int startupStatus = m_protocol.doStartup(firmwareVersionHolder);
        m_firmwareVersion = firmwareVersionHolder[0];
        m_mainActivity.appendToLog("Firmware Version: " + m_firmwareVersion);
        int presetNamesStatus = m_protocol.getPresetNamesList();
        m_mainActivity.appendToLog(String.format(
            "Amp contains %d unique presets", m_presetRegistry.uniquePresetCount()
        ));

        return startupStatus==AbstractMessageProtocolBase.STATUS_OK;
    }

    public PresetRegistryBase getPresetRegistry() {
        return m_presetRegistry;
    }

    @Override
    public void switchPreset(int slotIndex) {
        FenderJsonPresetRegistry.Record presetRecord = m_presetRegistry.get(slotIndex);
        if(presetRecord == null) {
            m_mainActivity.appendToLog("No preset found for slot id " + slotIndex);
        } else {
            m_protocol.switchPreset(slotIndex);
            Bundle msgData = new Bundle();
            msgData.putInt(MainActivity.MESSAGE_SLOT_INDEX, slotIndex);
            msgData.putString(
                MainActivity.MESSAGE_PRESET_NAME,
                presetRecord.displayName().replaceAll("( )+", " ").strip()
            );
            msgData.putString(MainActivity.MESSAGE_PRESET_EFFECTS, presetRecord.effects());
            Message presetChangeMsg = new Message();
            presetChangeMsg.what = MessageType_e.MESSAGE_PRESET_SELECTED.ordinal();
            presetChangeMsg.setData(msgData);
            m_mainActivity.m_providerHandler.sendMessage(presetChangeMsg);
        }
    }

    @Override
    public ArrayList<PresetSuiteRegistry.PresetSuiteEntry> buildAmpBasedPresetSuites(int maxPresetsPerSuite, int targetPresetsPerSuite, int maxAmpsPerSuite) {
        m_presetSuiteRegistry = new PresetSuiteRegistry(m_presetRegistry);
        return m_presetSuiteRegistry.buildPresetSuites(
            maxPresetsPerSuite, targetPresetsPerSuite, maxAmpsPerSuite
        );
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    public ProviderState_e attemptConnection() {
        ProviderState_e state = m_deviceTransportUsbHid.attemptUsbHidConnection();
        if(state==ProviderState_e.PROVIDER_DEVICE_CONNECTION_SUCCEEDED) {
            String[] startupItemWrapper = new String[2];
            m_protocol.setDeviceTransport(m_deviceTransportUsbHid);
            m_protocol.doStartup(startupItemWrapper);
            m_protocol.getPresetNamesList();
            m_protocol.startHeartbeatThread();
            buildAmpBasedPresetSuites(9, 7, 3);
        }
        return state;
    }

    public void switchSuite(int position) {
        String suiteName = m_presetSuiteRegistry.nameAt(position);
        HashMap<Integer,FenderJsonPresetRegistry.Record> suitePresetRecords = m_presetSuiteRegistry.recordsAt(position);
        m_mainActivity.suiteSelected(suiteName, suitePresetRecords);
    }
}

