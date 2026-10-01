# Camera Auto Upload — FTP Camera Setup Help

This document matches the in-app help included in **0.5.1 beta**. It covers **57 camera profiles**. The Android app runs a local FTP server on port **2121**, saves incoming JPEGs to the phone, and can then upload them to Flickr over cellular.

## Common FTP settings

- Protocol: **FTP** (plain FTP; not FTPS/SFTP)
- Server/Host: the current phone IP shown inside the app
- Port: **2121** (enter **02121** on cameras that require five digits)
- User: **nikon**
- Password: the value shown in **Settings → Advanced FTP**
- Passive/PASV: **On** when available
- Destination: **Root/Home folder**
- Recommended transfer file: **JPEG**
- Automatic transfer/upload: **On**
- Delete after transfer: **Off**

The phone IP can change whenever the camera/phone network changes, so always use the IP currently displayed by the app.

## Nikon — built-in wireless FTP

Supported profiles: **Z9, Z8, Z6III, Z5II, Zf, Z50II, ZR**.

1. Open **Network menu → Connect to FTP server → Network settings → Create profile → Connection wizard**.
2. Choose **Direct connection / Wi-Fi access point mode**.
3. Join the camera-created Wi-Fi network from the Android phone.
4. Choose FTP on the camera and enter port 2121, user `nikon`, the app FTP password, and the server home/root folder.
5. Enable **Options → Auto upload**.
6. For RAW+JPEG, choose JPEG-only transfer when available.

Official manuals:
- Z9: https://onlinemanual.nikonimglib.com/z9/en/ftp_servers_connecting_via_ethernet_or_wlan_67.html
- Z8: https://onlinemanual.nikonimglib.com/z8/en/ftp_servers_connecting_via_wireless_lan_92.html
- Z6III: https://onlinemanual.nikonimglib.com/z6III/en/ftp_servers_connecting_via_wireless_lan_95.html
- Z5II: https://onlinemanual.nikonimglib.com/z5II/en/ftp_servers_connecting_via_wireless_lan_101.html
- Zf: https://onlinemanual.nikonimglib.com/zf/en/ftp_servers_connecting_via_wireless_lan_89.html
- Z50II: https://onlinemanual.nikonimglib.com/z50II/en/25-04.html
- ZR: https://onlinemanual.nikonimglib.com/zr/en/09-09-07.html

## Nikon — wireless transmitter required

Supported profiles:
- Z7II + WT-7
- Z6II + WT-7
- Z7 + WT-7
- Z6 + WT-7
- D850 + WT-7
- D780 + WT-7
- D500 + WT-7
- D810A + WT-7
- D810 + WT-7
- D750 + WT-7
- D7200 + WT-7
- D6 + WT-6
- D5 + WT-6 / WT-5
- D4S + WT-5
- D4 + WT-5

Attach and enable the listed transmitter, create an FTP Upload profile, use access-point mode when available, join that network from Android, then use the common FTP settings above. Enable upload-as-taken/automatic FTP upload.

Official references:
- Z7II/Z6II: https://onlinemanual.nikonimglib.com/z7II_z6II/en/10_establishing_wireless_connections_04.html
- Z7/Z6: https://onlinemanual.nikonimglib.com/z7_z6/en/10_connections_02.html
- D850: https://onlinemanual.nikonimglib.com/d850/en/19_technical_notes_02.html
- D780: https://onlinemanual.nikonimglib.com/d780/en/11_network_connections_04.html
- D6: https://onlinemanual.nikonimglib.com/d6/en/13_ethernet_wt-6_05.html
- WT-7 compatible-camera reference: https://downloadcenter.nikonimglib.com/en/download/fw/378.html
- Nikon network-device compatibility: https://downloadcenter.nikonimglib.com/en/download/sw/272.html

## Canon — phone hotspot + FTP

Built-in profiles: **EOS R1, EOS R5 Mark II, EOS R3, EOS R5, EOS R6 Mark III, EOS R6 Mark II, EOS R6 V, EOS R6**.

Accessory profiles: **EOS-1D X Mark III + WFT-E9, EOS-1D X Mark II + WFT-E8**.

1. Turn Android **Mobile Hotspot** on and connect the Canon camera to it.
2. Open **Communication functions / Wireless features → Transfer images to FTP server**.
3. Create/add a connection and choose **FTP**.
4. Enter the phone hotspot IP shown by the app, port 2121, Passive mode Enable, user `nikon`, and the app FTP password.
5. Choose the root folder.
6. Set **Automatic transfer = Enable** and choose JPEG when shooting RAW+JPEG.

Official manuals:
- EOS R1: https://cam.start.canon/en/C018/manual/html/UG-06_Network_0030.html
- EOS R5 Mark II: https://cam.start.canon/en/C017/manual/html/UG-06_Network_0060.html
- EOS R3: https://cam.start.canon/en/C010/manual/html/UG-06_Network_0070.html
- EOS R5: https://cam.start.canon/en/C003/manual/html/UG-06_Network_0070.html
- EOS R6 Mark III: https://cam.start.canon/en/C022/manual/html/UG-07_Network_0070.html
- EOS R6 Mark II: https://cam.start.canon/en/C012/manual/html/UG-07_Network_0070.html
- EOS R6 V: https://cam.start.canon/en/C023/manual/html/index.html
- EOS R6: https://cam.start.canon/en/C004/manual/html/UG-06_Network_0070.html
- Canon compatibility reference: https://cam.start.canon/en/S013/manual/html/UG-00_Before_0020.html

## Sony — phone hotspot + FTP

Supported profiles: **α7 V, α1 II, α1, α9 III, α9 II, α9, α7 IV, α7 III, α7S III, α7R V, α7R IV, α7R IVA, α7R III, α7R IIIA, α7C II, α7CR, α7C, FX3, FX30, FX2, ILX-LR1**.

1. Turn Android **Mobile Hotspot** on and connect the Sony camera using **Network → Wi-Fi / Access Point Set**.
2. Open **Network → FTP Transfer → FTP Transfer Func. → Server Setting**.
3. Host Name = phone IP shown by the app; Port = 2121; Secure Protocol = Off; User = `nikon`; Password = app FTP password.
4. Turn **FTP Function** on.
5. Enable **Auto FTP Transfer / Auto Trans When Shot**.
6. For RAW+JPEG, choose JPEG as the transfer target.

Official manuals:
- α7 V: https://helpguide.sony.net/di/ftp_2540/v1/en/index.html
- α1 II: https://helpguide.sony.net/di/ftp_2440/v1/en/contents/auto_transfer.html
- α1: https://helpguide.sony.net/di/ftp_2420/v1/en/contents/auto_transfer.html
- α9 III: https://helpguide.sony.net/di/ftp_2380/v1/en/contents/auto_transfer.html
- α7 IV: https://helpguide.sony.net/di/ftp_2110/v1/en/contents/TP1000657885.html
- α7 III / α9 / α9 II / α7R III / IIIA / α7R IV / IVA / α7C: https://helpguide.sony.net/di/ftp/v1/en/contents/TP0002043783.html
- α7S III: https://helpguide.sony.net/di/ftp_2410/v1/en/contents/FTP_server_connection.html
- α7R V: https://helpguide.sony.net/di/ftp_2230/v1/en/index.html
- α7C II: https://helpguide.sony.net/ilc/2360/v1/en/contents/0704L_ftp_transfer.html
- α7CR: https://helpguide.sony.net/ilc/2370/v1/en/contents/0704L_ftp_transfer.html
- FX3: https://helpguide.sony.net/di/ftp_2210/v1/en/contents/FTP_server_connection.html
- FX30: https://helpguide.sony.net/di/ftp_2220/v1/en/contents/FTP_server_connection.html
- FX2: https://helpguide.sony.net/di/ftp_2530/v1/en/contents/FTP_server_connection.html
- ILX-LR1: https://helpguide.sony.net/di/ftp_2390/v1/en/contents/FTP_server_connection.html

## Fujifilm — phone hotspot + FTP

Built-in profiles: **GFX100 II, GFX100S II**.

FT-XH required: **X-H2, X-H2S**.

1. Turn Android Mobile Hotspot on and connect the camera/file transmitter.
2. Open **Network/USB Setting → Create/Edit Connection Setting → Create Using Wizard → FTP Transfer → Wireless LAN**.
3. Configure the hotspot and create FTP server settings.
4. FTP server type = FTP; address = phone IP; port = 2121; proxy Disable; PASV Enable; user/password = `nikon` plus the app FTP password; destination = Root Folder.
5. Enable **FTP Optional Setting → Auto Image Transfer Order**.
6. Use JPEG for the app/Flickr workflow.

Official manuals:
- GFX100 II: https://fujifilm-dsc.com/en-int/manual/gfx100ii/connections/ftp_upload/index.html
- GFX100S II: https://fujifilm-dsc.com/en/manual/gfx100s-ii/connections/ftp_upload/index.html
- X-H2: https://fujifilm-dsc.com/en/manual/x-h2_connection/overview_usage/ftp_upload/index.html
- X-H2S: https://app.fujifilm-dsc.com/en/manual/x-h2s_connection/overview_usage/ftp_upload/index.html

## Troubleshooting

**Camera cannot connect:** verify same network, phone IP, port 2121, user `nikon`, password, plain FTP, and Passive/PASV. Temporarily disable VPNs.

**Camera connects but no photo arrives:** enable automatic transfer and choose JPEG; confirm the camera is connected to its FTP profile while shooting.

**Phone receives photos but Flickr does not:** camera-to-phone FTP and Flickr are separate. Keep mobile data on and Flickr Upload enabled. Pending uploads remain queued and retry later.

## Security

The FTP server is intended only for the local camera/phone network. **Do not port-forward or expose port 2121 to the public internet.** Use a strong FTP password.
