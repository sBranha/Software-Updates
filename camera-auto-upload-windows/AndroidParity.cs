using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace CameraAutoUpload.Windows;

public partial class MainWindow
{
    string photosFilter = "All";
    bool editorGrid;

    protected override void OnContentRendered(EventArgs e)
    {
        base.OnContentRendered(e);
        // The legacy page switcher predates the Android-style Photos page. These
        // hooks keep Photos mutually exclusive without disturbing the proven core.
        HomePage.IsVisibleChanged += (_, __) => { if (HomePage.Visibility == Visibility.Visible) { PhotosPage.Visibility = Visibility.Collapsed; PageTitle.Text = "Camera Auto Upload"; RefreshParityUi(); } };
        UploadsPage.IsVisibleChanged += (_, __) => { if (UploadsPage.Visibility == Visibility.Visible) { PhotosPage.Visibility = Visibility.Collapsed; PageTitle.Text = "Uploads"; } };
        SettingsPage.IsVisibleChanged += (_, __) => { if (SettingsPage.Visibility == Visibility.Visible) { PhotosPage.Visibility = Visibility.Collapsed; PageTitle.Text = "Settings"; RefreshParityUi(); } };
        EditorPage.IsVisibleChanged += (_, __) => { if (EditorPage.Visibility == Visibility.Visible) { PhotosPage.Visibility = Visibility.Collapsed; PageTitle.Text = "Photo Editor"; } };
        CameraPage.IsVisibleChanged += (_, __) => { if (CameraPage.Visibility == Visibility.Visible) { PhotosPage.Visibility = Visibility.Collapsed; PageTitle.Text = "Camera Connection"; } };
        RefreshParityUi();
        RefreshPhotos();
    }

    void RefreshParityUi()
    {
        var cp = CameraProfiles.Find(settings.CameraBrand, settings.CameraModel);
        HomeCameraName.Text = cp.DisplayName;
        HomeCameraMode.Text = cp.Mode + (string.IsNullOrWhiteSpace(cp.Accessory) ? "" : " • " + cp.Accessory);
        SettingsCameraName.Text = cp.DisplayName;
        SettingsCameraMode.Text = cp.Mode + (string.IsNullOrWhiteSpace(cp.Accessory) ? "" : " • " + cp.Accessory + " required");
        var ip = LocalIPv4();
        ConnectionSummary.Text = ftp.Running
            ? $"● Connected / listening • {cp.DisplayName} • {cp.Mode}\nPC FTP address: {ip}:{settings.FtpPort} • Passive/PASV • user {settings.FtpUser}"
            : $"○ Waiting for {cp.DisplayName}\n{cp.Mode} • PC address {ip}:{settings.FtpPort}";
        SettingsConnectionText.Text = $"Selected camera: {cp.DisplayName}\nConnection: {cp.Mode}\nPC IPv4: {ip}\nFTP: port {settings.FtpPort} • user {settings.FtpUser} • Passive/PASV";
        EditorAutomationText.Text = settings.LayoutLocked
            ? "● LOCKED AUTO LAYOUT — incoming JPEGs keep the original, create the edited copy automatically, then follow your Original / Edited / Both upload choice."
            : "○ Manual editor — open Editor from the bottom bar, build the layout, then tap LOCK when you want incoming photos processed automatically.";
    }

    void Photos_Click(object sender, RoutedEventArgs e)
    {
        HomePage.Visibility = Visibility.Collapsed;
        EditorPage.Visibility = Visibility.Collapsed;
        CameraPage.Visibility = Visibility.Collapsed;
        UploadsPage.Visibility = Visibility.Collapsed;
        SettingsPage.Visibility = Visibility.Collapsed;
        PhotosPage.Visibility = Visibility.Visible;
        PageTitle.Text = "Photos";
        RefreshPhotos();
    }

    void ChooseCamera_Click(object sender, RoutedEventArgs e)
    {
        var win = new Window
        {
            Title = "Choose FTP camera", Width = 560, Height = 700, MinHeight = 450,
            Background = new SolidColorBrush(Color.FromRgb(7, 11, 15)), Foreground = Brushes.White,
            Owner = this, WindowStartupLocation = WindowStartupLocation.CenterOwner
        };
        var root = new DockPanel { Margin = new Thickness(14) };
        var title = new TextBlock { Text = "Choose FTP camera", FontSize = 22, FontWeight = FontWeights.Bold, Margin = new Thickness(0, 0, 0, 8) };
        DockPanel.SetDock(title, Dock.Top); root.Children.Add(title);
        var list = new ListBox { Background = new SolidColorBrush(Color.FromRgb(15, 21, 28)), Foreground = Brushes.White, BorderBrush = new SolidColorBrush(Color.FromRgb(51, 65, 78)) };
        foreach (var p in CameraProfiles.All) list.Items.Add(p.DisplayWithAccessory);
        var current = CameraProfiles.Find(settings.CameraBrand, settings.CameraModel);
        list.SelectedIndex = Array.FindIndex(CameraProfiles.All, x => x.Brand == current.Brand && x.Model == current.Model);
        root.Children.Add(list);
        var row = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right, Margin = new Thickness(0, 10, 0, 0) };
        DockPanel.SetDock(row, Dock.Bottom);
        var cancel = new Button { Content = "CANCEL", MinWidth = 90 };
        var use = new Button { Content = "USE CAMERA", MinWidth = 110, Background = new SolidColorBrush(Color.FromRgb(25, 118, 210)) };
        cancel.Click += (_, __) => win.Close();
        use.Click += (_, __) =>
        {
            if (list.SelectedIndex < 0) return;
            var p = CameraProfiles.All[list.SelectedIndex];
            settings.CameraBrand = p.Brand; settings.CameraModel = p.Model; SettingsStore.Save(settings);
            RefreshParityUi(); RefreshHome(); win.Close();
        };
        row.Children.Add(cancel); row.Children.Add(use); root.Children.Add(row);
        win.Content = root; win.ShowDialog();
    }

    void CameraSetup_Click(object sender, RoutedEventArgs e)
    {
        var cp = CameraProfiles.Find(settings.CameraBrand, settings.CameraModel);
        var win = new Window
        {
            Title = cp.DisplayName + " — " + cp.SetupTitle, Width = 720, Height = 650, MinWidth = 520, MinHeight = 380,
            Background = new SolidColorBrush(Color.FromRgb(7, 11, 15)), Foreground = Brushes.White,
            Owner = this, WindowStartupLocation = WindowStartupLocation.CenterOwner
        };
        var box = new StackPanel { Margin = new Thickness(20) };
        box.Children.Add(new TextBlock { Text = cp.DisplayName, FontSize = 24, FontWeight = FontWeights.Bold });
        box.Children.Add(new TextBlock { Text = cp.SetupTitle, FontSize = 17, Foreground = Brushes.DeepSkyBlue, Margin = new Thickness(0, 4, 0, 14) });
        var note = string.IsNullOrWhiteSpace(cp.Accessory) ? "" : $"\n\nHardware note: {cp.Accessory} is required.";
        box.Children.Add(new TextBlock { Text = cp.SetupSteps + note + $"\n\nCurrent Windows FTP address: {LocalIPv4()}:{settings.FtpPort}\nUser: {settings.FtpUser}", TextWrapping = TextWrapping.Wrap, FontSize = 14, LineHeight = 22 });
        var buttons = new WrapPanel { Margin = new Thickness(0, 18, 0, 0) };
        var start = new Button { Content = "START FTP RECEIVER", Background = new SolidColorBrush(Color.FromRgb(46, 125, 50)) };
        var hot = new Button { Content = "WINDOWS HOTSPOT", Background = new SolidColorBrush(Color.FromRgb(25, 118, 210)) };
        var close = new Button { Content = "CLOSE" };
        start.Click += (s, a) => { FtpStart_Click(s, a); RefreshParityUi(); };
        hot.Click += Hotspot_Click; close.Click += (_, __) => win.Close();
        buttons.Children.Add(start); buttons.Children.Add(hot); buttons.Children.Add(close); box.Children.Add(buttons);
        win.Content = new ScrollViewer { Content = box, VerticalScrollBarVisibility = ScrollBarVisibility.Auto };
        win.ShowDialog();
    }

    void UsbTether_Click(object sender, RoutedEventArgs e)
    {
        ShowPage(CameraPage); PageTitle.Text = "USB / Tether";
        var cp = CameraProfiles.Find(settings.CameraBrand, settings.CameraModel);
        WatchStatusText.Text = cp.Brand switch
        {
            "Nikon" => "Choose the Nikon NX Tether output folder, or scan a mounted camera/DCIM drive.",
            "Canon" => "Choose the Canon EOS Utility capture folder, or scan a mounted camera/DCIM drive.",
            "Sony" => "Choose the Sony tether/capture output folder, or scan a mounted camera/DCIM drive.",
            "Fujifilm" => "Choose the Fujifilm tether/capture output folder, or scan a mounted camera/DCIM drive.",
            _ => "Choose the camera tether output folder."
        };
    }

    void AdvancedCamera_Click(object sender, RoutedEventArgs e) { ShowPage(CameraPage); PageTitle.Text = "Camera Connection"; }

    void RefreshPhotos_Click(object sender, RoutedEventArgs e) => RefreshPhotos();
    void PhotosAll_Click(object sender, RoutedEventArgs e) { photosFilter = "All"; RefreshPhotos(); }
    void PhotosOriginals_Click(object sender, RoutedEventArgs e) { photosFilter = "Originals"; RefreshPhotos(); }
    void PhotosEdited_Click(object sender, RoutedEventArgs e) { photosFilter = "Processed"; RefreshPhotos(); }
    void PhotosInbound_Click(object sender, RoutedEventArgs e) { photosFilter = "Inbound FTP"; RefreshPhotos(); }

    void RefreshPhotos()
    {
        if (PhotosList == null) return;
        PhotosList.Items.Clear();
        var root = settings.OutputRoot;
        var scan = photosFilter == "All" ? new[] { "Originals", "Processed", "Inbound FTP" } : new[] { photosFilter };
        var files = new List<string>();
        foreach (var sub in scan)
        {
            var dir = Path.Combine(root, sub); if (!Directory.Exists(dir)) continue;
            try { files.AddRange(Directory.EnumerateFiles(dir, "*.*", SearchOption.AllDirectories).Where(IsPhotoFile)); } catch { }
        }
        foreach (var f in files.OrderByDescending(File.GetLastWriteTime).Take(1000)) PhotosList.Items.Add(f);
        PhotosFolderText.Text = $"{photosFilter} • {files.Count} photo(s) • {root}";
    }

    static bool IsPhotoFile(string path)
    {
        var x = Path.GetExtension(path).ToLowerInvariant();
        return x is ".jpg" or ".jpeg" or ".png" or ".tif" or ".tiff" or ".nef" or ".arw" or ".cr2" or ".cr3" or ".raf" or ".dng";
    }

    void PhotosList_DoubleClick(object sender, MouseButtonEventArgs e)
    {
        if (PhotosList.SelectedItem is not string p || !File.Exists(p)) return;
        try { System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo(p) { UseShellExecute = true }); } catch { }
    }

    void OpenPhotosFolder_Click(object sender, RoutedEventArgs e)
    {
        Directory.CreateDirectory(settings.OutputRoot);
        try { System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo("explorer.exe", $"\"{settings.OutputRoot}\"") { UseShellExecute = true }); } catch { }
    }

    void AddName_Click(object sender, RoutedEventArgs e) => AddPresetText("PLAYER NAME", "Player Name", 760, 150, 160, 1050, 76);
    void AddNumber_Click(object sender, RoutedEventArgs e) => AddPresetText("#00", "Number", 300, 180, 720, 1040, 100);
    void AddPosition_Click(object sender, RoutedEventArgs e) => AddPresetText("POSITION", "Position", 520, 100, 280, 1190, 54);

    void AddPresetText(string text, string name, double w, double h, double x, double y, double size)
    {
        PushUndo();
        var layer = new LayerModel { Kind = LayerKind.Text, Name = name, Text = text, Width = w, Height = h, X = x, Y = y, FontSize = size, Align = "Center" };
        document.Layers.Add(layer); SelectLayer(layer); RefreshVisuals();
    }

    void AddShape_Click(object sender, RoutedEventArgs e)
    {
        // Uses a solid square glyph so it behaves like the Android shape layer:
        // independently movable, scalable, rotatable, colorable and reorderable.
        AddPresetText("■", "Shape", 420, 320, 330, 500, 300);
    }

    void Color_Click(object sender, RoutedEventArgs e)
    {
        if (photoSelected) { OpenPhotoProperties(); return; }
        if (selectedLayer == null) { System.Windows.MessageBox.Show("Select the photo, text, PNG, logo, or shape first."); return; }
        if (selectedLayer.Kind == LayerKind.Image) { OpenImageProperties(selectedLayer); return; }
        var c = ChooseColor(selectedLayer.TextColor); if (c == null) return;
        PushUndo(); selectedLayer.TextColor = c; RefreshVisuals();
    }

    void Properties_Click(object sender, RoutedEventArgs e)
    {
        if (photoSelected) OpenPhotoProperties();
        else if (selectedLayer?.Kind == LayerKind.Text) OpenTextProperties(selectedLayer);
        else if (selectedLayer?.Kind == LayerKind.Image) OpenImageProperties(selectedLayer);
        else System.Windows.MessageBox.Show("Tap/click a photo or layer first.");
    }

    void Layers_Click(object sender, RoutedEventArgs e)
    {
        LayersPanel.Visibility = LayersPanel.Visibility == Visibility.Visible ? Visibility.Collapsed : Visibility.Visible;
    }

    void FitPhoto_Click(object sender, RoutedEventArgs e)
    {
        if (string.IsNullOrWhiteSpace(photoPath) || !File.Exists(photoPath)) { System.Windows.MessageBox.Show("Choose a photo first."); return; }
        PushUndo(); LoadPhoto(photoPath, true);
    }

    void FitView_Click(object sender, RoutedEventArgs e)
    {
        EditorViewbox.Stretch = Stretch.Uniform; EditorViewbox.LayoutTransform = Transform.Identity;
    }

    void Grid_Click(object sender, RoutedEventArgs e)
    {
        editorGrid = !editorGrid;
        if (!editorGrid) { Artboard.Background = new SolidColorBrush(Color.FromRgb(29, 38, 48)); return; }
        var group = new DrawingGroup();
        using (var dc = group.Open())
        {
            dc.DrawRectangle(new SolidColorBrush(Color.FromRgb(29, 38, 48)), null, new Rect(0, 0, 40, 40));
            var pen = new Pen(new SolidColorBrush(Color.FromArgb(55, 255, 255, 255)), 1);
            dc.DrawLine(pen, new Point(0, 0), new Point(40, 0)); dc.DrawLine(pen, new Point(0, 0), new Point(0, 40));
        }
        Artboard.Background = new DrawingBrush(group) { TileMode = TileMode.Tile, Viewport = new Rect(0, 0, 40, 40), ViewportUnits = BrushMappingMode.Absolute };
    }
}
