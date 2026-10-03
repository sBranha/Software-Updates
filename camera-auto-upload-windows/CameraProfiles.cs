namespace CameraAutoUpload.Windows;

public sealed class CameraProfile
{
    public string Brand { get; init; } = "Nikon";
    public string Model { get; init; } = "Z8";
    public string Mode { get; init; } = "Camera Wi-Fi FTP";
    public string Accessory { get; init; } = "";
    public string SetupTitle { get; init; } = "FTP setup";
    public string SetupSteps { get; init; } = "";
    public string DisplayName => $"{Brand} {Model}";
    public string DisplayWithAccessory => string.IsNullOrWhiteSpace(Accessory) ? DisplayName : $"{DisplayName}  •  {Accessory} required";
}

public static class CameraProfiles
{
    static string Server() => "Use plain FTP (not FTPS/SFTP). Server = the Windows PC IPv4 address shown by Camera Auto Upload, port 2121, user nikon, password = the password shown in the app, Passive/PASV mode when offered, destination = root. For the automatic Flickr/card workflow, transfer JPEG files.";

    static CameraProfile Nikon(string model, string accessory = "") => new()
    {
        Brand = "Nikon", Model = model, Mode = "Direct Wi-Fi FTP", Accessory = accessory,
        SetupTitle = string.IsNullOrWhiteSpace(accessory) ? "Direct Wi-Fi FTP" : "Wireless transmitter + FTP",
        SetupSteps = string.IsNullOrWhiteSpace(accessory)
            ? $"Camera: Network menu → Connect to FTP server → Network settings → Create profile → Connection wizard. Use the camera's direct Wi-Fi/access-point profile when available, then connect this Windows PC to that camera Wi-Fi network. Enter this PC's FTP address and enable automatic upload / upload as taken.\n\n{Server()}"
            : $"Attach and enable the {accessory}. Create an FTP upload profile on the camera/transmitter. Use access-point mode if available, connect this Windows PC to that network, then enter the PC FTP address shown in Camera Auto Upload. Enable automatic upload / upload as taken.\n\n{Server()}"
    };

    static CameraProfile Canon(string model, string accessory = "") => new()
    {
        Brand = "Canon", Model = model, Mode = "Windows hotspot + FTP", Accessory = accessory,
        SetupTitle = string.IsNullOrWhiteSpace(accessory) ? "Windows hotspot + FTP" : "Windows hotspot + wireless transmitter FTP",
        SetupSteps = $"Turn on Windows Mobile Hotspot and connect the camera{(string.IsNullOrWhiteSpace(accessory) ? "" : "/" + accessory)} to that hotspot. Camera: Communication functions / Wireless features → Transfer images to FTP server. Choose FTP, enter the PC hotspot IPv4 address shown by Camera Auto Upload, port 2121, Passive mode Enable, user nikon, and the app FTP password. Choose Root folder and enable Automatic transfer.\n\n{Server()}"
    };

    static CameraProfile Sony(string model) => new()
    {
        Brand = "Sony", Model = model, Mode = "Windows hotspot + FTP", SetupTitle = "Windows hotspot + FTP",
        SetupSteps = $"Turn on Windows Mobile Hotspot and connect the camera to it using Network → Wi-Fi / Access Point Set. Camera: Network → FTP Transfer → FTP Transfer Func. → Server Setting. Set Host Name to the PC hotspot IPv4 address shown by Camera Auto Upload, Port 2121, Secure Protocol Off, user nikon, and the app FTP password. Use Passive mode if offered. Enable Auto FTP Transfer / Auto Trans When Shot.\n\n{Server()}"
    };

    static CameraProfile Fuji(string model, string accessory = "") => new()
    {
        Brand = "Fujifilm", Model = model, Mode = "Windows hotspot + FTP", Accessory = accessory,
        SetupTitle = string.IsNullOrWhiteSpace(accessory) ? "Windows hotspot + FTP" : "FT-XH + Windows hotspot + FTP",
        SetupSteps = $"Turn on Windows Mobile Hotspot and connect the camera{(string.IsNullOrWhiteSpace(accessory) ? "" : "/" + accessory)} to it. Camera: Network/USB Setting → Create/Edit Connection Setting → Create Using Wizard → FTP Transfer → Wireless LAN. Create the FTP server profile using the PC hotspot IPv4 address, port 2121, proxy Disable, PASV Enable, user nikon, and the app FTP password. Choose Root Folder and enable Auto Image Transfer Order.\n\n{Server()}"
    };

    public static readonly CameraProfile[] All =
    {
        Nikon("Z9"), Nikon("Z8"), Nikon("Z6III"), Nikon("Z5II"), Nikon("Zf"), Nikon("Z50II"), Nikon("ZR"),
        Nikon("Z7II","WT-7"), Nikon("Z6II","WT-7"), Nikon("Z7","WT-7"), Nikon("Z6","WT-7"), Nikon("D850","WT-7"), Nikon("D6","WT-6"),
        Canon("EOS R1"), Canon("EOS R5 Mark II"), Canon("EOS R3"), Canon("EOS R5"), Canon("EOS R6 Mark III"), Canon("EOS R6 Mark II"), Canon("EOS R6 V"), Canon("EOS R6"),
        Canon("EOS-1D X Mark III","WFT-E9"), Canon("EOS-1D X Mark II","WFT-E8"),
        Sony("α7 V"), Sony("α1 II"), Sony("α1"), Sony("α9 III"), Sony("α9 II"), Sony("α9"), Sony("α7 IV"), Sony("α7 III"), Sony("α7S III"), Sony("α7R V"), Sony("α7R IV"), Sony("α7R IVA"), Sony("α7R III"), Sony("α7R IIIA"), Sony("α7C II"), Sony("α7CR"), Sony("α7C"), Sony("FX3"), Sony("FX30"),
        Fuji("GFX100 II"), Fuji("GFX100S II"), Fuji("X-H2","FT-XH"), Fuji("X-H2S","FT-XH")
    };

    public static CameraProfile Find(string? brand, string? model) => All.FirstOrDefault(x => x.Brand == brand && x.Model == model) ?? All.First(x => x.Brand == "Nikon" && x.Model == "Z8");
}
