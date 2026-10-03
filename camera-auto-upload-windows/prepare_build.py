from pathlib import Path

# Build-time source cleanup for the Windows target.
# Keep WPF Rectangle without importing System.Windows.Shapes.Path,
# which conflicts with System.IO.Path.
p = Path('camera-auto-upload-windows/MainWindow.xaml.cs')
s = p.read_text(encoding='utf-8')
s = s.replace('using System.Windows.Shapes;\n', 'using Rectangle = System.Windows.Shapes.Rectangle;\n')

# XAML controls with Checked/SelectionChanged/TextChanged handlers can fire while
# InitializeComponent is still constructing the window. Keep settings writes
# disabled until ApplySettingsToUi has populated every control.
s = s.replace('    bool loadingSettings;\n', '    bool loadingSettings = true;\n')
p.write_text(s, encoding='utf-8')

# C# does not allow yield-return inside a try block that has catch.
p = Path('camera-auto-upload-windows/Services.cs')
s = p.read_text(encoding='utf-8')
start = s.index('    public static IEnumerable<string> FindCameraFolders()')
end = s.index('    public static void OpenHotspotSettings()', start)
replacement = '''    public static IEnumerable<string> FindCameraFolders()\n    {\n        var found = new List<string>();\n        foreach (var d in DriveInfo.GetDrives())\n        {\n            try\n            {\n                if (!d.IsReady) continue;\n                if (d.DriveType is not (DriveType.Removable or DriveType.Fixed)) continue;\n                var dcim = Path.Combine(d.RootDirectory.FullName, "DCIM");\n                if (Directory.Exists(dcim)) found.Add(dcim);\n            }\n            catch { }\n        }\n        return found;\n    }\n\n'''
s = s[:start] + replacement + s[end:]
p.write_text(s, encoding='utf-8')
