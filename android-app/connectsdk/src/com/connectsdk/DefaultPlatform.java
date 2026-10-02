package com.connectsdk;

import java.util.HashMap;

/**
 * Missing from upstream master (the published AAR shipped it). DeviceService -> DiscoveryProvider
 * registry used by DiscoveryManager.registerDefaultDeviceTypes().
 */
public final class DefaultPlatform {

    private DefaultPlatform() {
        // not instantiable
    }

    public static String getPlatformName() {
        return "Android";
    }

    public static HashMap<String, String> getDeviceServiceMap() {
        final HashMap<String, String> devicesList = new HashMap<>();
        final String ssdp = "com.connectsdk.discovery.provider.SSDPDiscoveryProvider";
        final String zeroconf = "com.connectsdk.discovery.provider.ZeroconfDiscoveryProvider";
        // ponytail: no Google Cast module in this fork, so Cast entries are left out
        devicesList.put("com.connectsdk.service.DIALService", ssdp);
        devicesList.put("com.connectsdk.service.DLNAService", ssdp);
        devicesList.put("com.connectsdk.service.NetcastTVService", ssdp);
        devicesList.put("com.connectsdk.service.RokuService", ssdp);
        devicesList.put("com.connectsdk.service.WebOSTVService", ssdp);
        devicesList.put("com.connectsdk.service.webos.WebOSTVDeviceService", ssdp);
        devicesList.put("com.connectsdk.service.AirPlayService", zeroconf);
        return devicesList;
    }
}