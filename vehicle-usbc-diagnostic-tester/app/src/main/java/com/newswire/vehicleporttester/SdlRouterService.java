package com.newswire.vehicleporttester;

/** SDL's trusted router multiplexes its own Bluetooth / AOA transports; it is
 *  not an arbitrary raw USB parser and does not touch the existing Android Auto
 *  accessory directly. */
public final class SdlRouterService extends com.smartdevicelink.transport.SdlRouterService { }
