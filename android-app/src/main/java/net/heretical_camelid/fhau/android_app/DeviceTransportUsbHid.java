package net.heretical_camelid.fhau.android_app;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import com.benlypan.usbhid.OnUsbHidDeviceListener;
import com.benlypan.usbhid.UsbHidDevice;

import net.heretical_camelid.fhau.lib.interfaces.IAmpProvider;
import net.heretical_camelid.fhau.lib.interfaces.IDeviceTransport;

import java.util.HashMap;

import static android.content.Context.RECEIVER_NOT_EXPORTED;

class DeviceTransportUsbHid implements IDeviceTransport, OnUsbHidDeviceListener {
    final static String ACTION_USB_PERMISSION = "net.heretical_camelid.fhau.android_app.USB_PERMISSION";

    final MainActivity m_activity;
    final AndroidUsbAmpProvider m_provider;

    UsbManager m_usbManager;
    UsbBroadcastReceiver m_usbReceiver;
    PendingIntent m_permissionIntent;
    UsbHidDevice m_usbHidDevice;
    UsbDevice m_usbDevice;
    IAmpProvider.ProviderState_e m_state;

    final static int TIMEOUT_MS = 500;

    DeviceTransportUsbHid(
        MainActivity activity,
        AndroidUsbAmpProvider provider) {
        m_activity = activity;
        m_provider = provider;

        m_usbManager = (UsbManager) m_activity.getSystemService(Context.USB_SERVICE);
        m_usbReceiver = null;
        m_usbDevice = null;
        m_usbHidDevice = null;
        m_state = IAmpProvider.ProviderState_e.PROVIDER_INITIAL;
    }

    void setUsbHidDevice(UsbHidDevice uhd) {
        m_usbHidDevice = uhd;
    }

    @Override
    public int read(byte[] packetBuffer) {
        final int NUM_ATTEMPTS=3;
        final int MSEC_BETWEEN_ATTEMPTS=100;
        for(int i=0; i<NUM_ATTEMPTS; ++i) {
            try {
                byte[] bytesRead = m_usbHidDevice.read(packetBuffer.length,TIMEOUT_MS);
                if(bytesRead==null) {
                    return 0;
                } else {
                    System.arraycopy(bytesRead, 0, packetBuffer, 0, bytesRead.length);
                    return bytesRead.length;
                }
            }
            catch(IllegalArgumentException e) {
                try {
                    Thread.sleep(MSEC_BETWEEN_ATTEMPTS);
                } catch (InterruptedException ex) {
                    // Do nothing
                }
            }
        }
        return 0;
    }

    @Override
    public int write(byte[] commandBytes) {
        m_usbHidDevice.write(commandBytes);
        return commandBytes.length;
    }
    @Override
    public String getLastErrorMessage() {
        return "not available";
    }

    IAmpProvider.ProviderState_e attemptUsbHidConnection() {
        if(m_usbDevice==null)
        {
            UsbDevice usbDevice = null;
            HashMap<String, UsbDevice> usbDeviceMap = m_usbManager.getDeviceList();
            if (usbDeviceMap == null) {
                m_activity.appendToLog("device map not received");
                m_state = IAmpProvider.ProviderState_e.PROVIDER_NO_APPLICABLE_DEVICE;
                return m_state;
            } else if (usbDeviceMap.size() == 0) {
                m_activity.appendToLog("device map empty");
                m_state = IAmpProvider.ProviderState_e.PROVIDER_NO_APPLICABLE_DEVICE;
                return m_state;
            } else {
                for (String deviceName : usbDeviceMap.keySet()) {
                    usbDevice = usbDeviceMap.get(deviceName);
                    if (usbDevice.getVendorId() != 0x1ed8) {
                        m_activity.appendToLog(String.format(
                            "non-FMIC device found with vid=%04x pid=%04x",
                            usbDevice.getVendorId(), usbDevice.getProductId()
                        ));
                        usbDevice = null;
                        continue;
                    } else {
                        m_activity.appendToLog(String.format(
                            "FMIC device found with vid=%04x pid=%04x",
                            usbDevice.getVendorId(), usbDevice.getProductId()
                        ));
                        m_activity.appendToLog(
                            "FMIC product name: " + usbDevice.getProductName()
                        );
                        m_usbDevice = usbDevice;
                        break;
                    }
                }
                if(m_usbDevice==null) {
                    m_activity.appendToLog(
                        "Device map did not contain any applicable devices"
                    );
                    m_state = IAmpProvider.ProviderState_e.PROVIDER_NO_APPLICABLE_DEVICE;
                    return m_state;
                }
            }
            assert m_usbDevice!=null;
            if(m_usbHidDevice==null) {
                if (!m_usbManager.hasPermission(m_usbDevice)) {
                    m_activity.appendToLog(
                        "Requesting permission to connect to device"
                    );
                    registerForPermissionIntent(m_provider);
                    m_usbManager.requestPermission(m_usbDevice, m_permissionIntent);
                    m_state = IAmpProvider.ProviderState_e.PROVIDER_CONNECTING_TO_DEVICE;
                    return m_state;
                } else {
                    m_activity.appendToLog("Permission to connect to device already held");
                    m_state = IAmpProvider.ProviderState_e.PROVIDER_CONNECTING_TO_DEVICE;
                }

                if(m_state == IAmpProvider.ProviderState_e.PROVIDER_CONNECTING_TO_DEVICE) {
                    try {
                        // If we are still in the function at this point,
                        // we must have permission, so we try to connect
                        UsbDeviceConnection cxn = m_usbManager.openDevice(m_usbDevice);
                        if (cxn != null) {
                            m_usbHidDevice = UsbHidDevice.factory(
                                m_activity,
                                m_usbDevice.getVendorId(), m_usbDevice.getProductId()
                            );
                            if (m_usbHidDevice == null) {
                                m_usbHidDevice.open(m_provider.m_mainActivity, this);
                                m_activity.appendToLog("No USB HID device found");
                                m_state = IAmpProvider.ProviderState_e.PROVIDER_DEVICE_CONNECTION_FAILED;
                                return m_state;
                            }
                            m_usbHidDevice.open(m_provider.m_mainActivity, null);
                            m_provider.m_protocol.setDeviceTransport(this);
                            m_state = IAmpProvider.ProviderState_e.PROVIDER_DEVICE_CONNECTION_SUCCEEDED;
                        } else {
                            m_state = IAmpProvider.ProviderState_e.PROVIDER_DEVICE_CONNECTION_FAILED;
                        }
                    } catch (Exception e) {
                        m_activity.appendToLog("Exception caught - see logcat for details");
                        System.out.println(e.toString());
                        e.printStackTrace(System.out);
                        m_state = IAmpProvider.ProviderState_e.PROVIDER_DEVICE_CONNECTION_FAILED;
                    }
                } else {
                    m_activity.appendToLog("attemptConnection failed, state=" + m_state);
                }
            } else {
                m_activity.appendToLog("attemptConnection: m_usbHidDevice non-null, state=" + m_state);
            }
        }
        return m_state;
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    void registerForPermissionIntent(AndroidUsbAmpProvider m_provider) {
        if (m_usbReceiver == null) {
            m_usbReceiver = new UsbBroadcastReceiver();
            m_usbReceiver.setTransport(this);
            m_permissionIntent = PendingIntent.getBroadcast(
                m_activity, 0,
                new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE
            );
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
            filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
            filter.addAction(UsbManager.EXTRA_PERMISSION_GRANTED);
            m_activity.appendToLog("Registering for permission intent");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                m_provider.m_mainActivity.registerReceiver(m_usbReceiver, filter, RECEIVER_NOT_EXPORTED);
            } else {
                m_provider.m_mainActivity.registerReceiver(m_usbReceiver, filter);
            }
            m_activity.appendToLog("Registered for permission intent");
        } else {
            m_activity.appendToLog("Already registered for permission intent");
        }
    }

    @Override
    public void onUsbHidDeviceConnected(UsbHidDevice device) {
        m_activity.appendToLog("Device connected");
    }

    @Override
    public void onUsbHidDeviceConnectFailed(UsbHidDevice device) {
        m_activity.appendToLog("Device connect failed");
        m_usbHidDevice.close();
        m_usbHidDevice = null;
        m_usbDevice = null;
        m_state = IAmpProvider.ProviderState_e.PROVIDER_INITIAL;
    }

    public void usbAccessPermissionGranted() {
        assert m_state==IAmpProvider.ProviderState_e.PROVIDER_CONNECTING_TO_DEVICE;
        m_provider.attemptConnection();
    }

    public void usbAccessPermissionDenied() {
        m_state = IAmpProvider.ProviderState_e.PROVIDER_DEVICE_CONNECTION_FAILED;
    }
}
