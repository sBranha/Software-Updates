using Microsoft.Win32;
using System.Net;
using System.Net.Sockets;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Effects;
using System.Windows.Media.Imaging;
using System.Windows.Shapes;
using Forms = System.Windows.Forms;
using DrawingColor = System.Drawing.Color;

namespace CameraAutoUpload.Windows;

public partial class MainWindow : Window
{
    readonly FtpReceiverService ftp = new();
    readonly WatchFolderService watcher = new();
    readonly UploadQueueService uploads = new();
    readonly Stack<string> undo = new();
    readonly Stack<string> redo = new();
    readonly Dictionary<Guid, FrameworkElement> layerVisuals = new();
    readonly SemaphoreSlim pipelineGate = new(1, 1);

    AppSettings settings = new();
    EditorDocument document = new();
    string? photoPath;
    FrameworkElement? photoVisual;
    LayerModel? selectedLayer;
    bool photoSelected;
    bool loadingSettings;
    bool rebuildingLayers;
    bool dragging;
    Point lastDrag;
    bool cropMode;
    bool cropDragging;
    Point cropStart;
    Rectangle? cropVisual;
    bool touchSnapshotTaken;

    public MainWindow()
    {
        InitializeComponent();
        AppPaths.Ensure();
        settings = SettingsStore.Load();
        LoadSavedLayout();
        ApplySettingsToUi();

        ftp.Log += Log;
        ftp.FileReceived += HandleIncomingFileAsync;
        watcher.Log += Log;
        watcher.FileArrived += HandleIncomingFileAsync;
        uploads.Log += Log;
        uploads.CountChanged += n => Dispatcher.Invoke(() => { QueueCountText.Text = $"{n} pending"; HomeQueueStatus.Text = $"{n} pending"; });
        uploads.Load();
        uploads.Start(() => settings);

        Artboard.AddHandler(UIElement.PreviewMouseLeftButtonDownEvent, new MouseButtonEventHandler(CropPreviewDown), true);
        Artboard.AddHandler(UIElement.PreviewMouseMoveEvent, new MouseEventHandler(CropPreviewMove), true);
        Artboard.AddHandler(UIElement.PreviewMouseLeftButtonUpEvent, new MouseButtonEventHandler(CropPreviewUp), true);
        Artboard.ManipulationStarted += (_, _) => { if ((photoSelected || selectedLayer != null) && !touchSnapshotTaken) { PushUndo(); touchSnapshotTaken = true; } };
        Artboard.ManipulationCompleted += (_, _) => touchSnapshotTaken = false;

        RefreshVisuals();
        RefreshHome();
        Closed += (_, _) => { ftp.Dispose(); watcher.Dispose(); };
        Log("Camera Auto Upload Windows 1.0 started");
    }

    void LoadSavedLayout()
    {
        try
        {
            if (File.Exists(AppPaths.TemplateFile))
                document = JsonSerializer.Deserialize<EditorDocument>(File.ReadAllText(AppPaths.TemplateFile), JsonOptions.Default) ?? new();
        }
        catch (Exception ex) { Log("Layout load: " + ex.Message); }
    }

    void ApplySettingsToUi()
    {
        loadingSettings = true;
        FtpPortBox.Text = settings.FtpPort.ToString();
        FtpUserBox.Text = settings.FtpUser;
        FtpPasswordBox.Text = settings.FtpPassword;
        WatchFolderBox.Text = settings.WatchFolder;
        AutoProcessCheck.IsChecked = settings.AutoProcess;
        FlickrEnabledCheck.IsChecked = settings.FlickrEnabled;
        FlickrApiKeyBox.Text = settings.FlickrApiKey;
        FlickrApiSecretBox.Text = settings.FlickrApiSecret;
        FlickrTokenBox.Text = settings.FlickrToken;
        FlickrTokenSecretBox.Text = settings.FlickrTokenSecret;
        OutputRootBox.Text = settings.OutputRoot;
        foreach (ComboBoxItem item in UploadChoiceCombo.Items)
            if (string.Equals(item.Tag?.ToString(), settings.UploadChoice.ToString(), StringComparison.OrdinalIgnoreCase)) item.IsSelected = true;
        LockLayoutButton.Content = settings.LayoutLocked ? "UNLOCK LAYOUT" : "LOCK LAYOUT";
        LockLayoutButton.Background = BrushFrom(settings.LayoutLocked ? "#FFB8860B" : "#FFB43A38");
        loadingSettings = false;
    }

    void SaveSettingsFromUi()
    {
        if (loadingSettings) return;
        if (int.TryParse(FtpPortBox.Text, out var port) && port > 0 && port <= 65535) settings.FtpPort = port;
        settings.FtpUser = FtpUserBox.Text.Trim();
        settings.FtpPassword = FtpPasswordBox.Text;
        settings.WatchFolder = WatchFolderBox.Text.Trim();
        settings.AutoProcess = AutoProcessCheck.IsChecked == true;
        settings.FlickrEnabled = FlickrEnabledCheck.IsChecked == true;
        settings.FlickrApiKey = FlickrApiKeyBox.Text.Trim();
        settings.FlickrApiSecret = FlickrApiSecretBox.Text.Trim();
        settings.FlickrToken = FlickrTokenBox.Text.Trim();
        settings.FlickrTokenSecret = FlickrTokenSecretBox.Text.Trim();
        if (!string.IsNullOrWhiteSpace(OutputRootBox.Text)) settings.OutputRoot = OutputRootBox.Text.Trim();
        if (UploadChoiceCombo.SelectedItem is ComboBoxItem ci && Enum.TryParse<UploadChoice>(ci.Tag?.ToString(), out var choice)) settings.UploadChoice = choice;
        SettingsStore.Save(settings);
        RefreshHome();
    }

    void RefreshHome()
    {
        HomeLockStatus.Text = settings.LayoutLocked ? "Locked / automatic" : "Off";
        HomeCameraStatus.Text = ftp.Running ? $"FTP running :{settings.FtpPort}" : (!string.IsNullOrWhiteSpace(settings.WatchFolder) ? "Tether folder configured" : "Not running");
        HomeQueueStatus.Text = $"{uploads.Count} pending";
    }

    void Log(string text)
    {
        Dispatcher.Invoke(() =>
        {
            var line = $"[{DateTime.Now:HH:mm:ss}] {text}\r\n";
            ActivityLog.AppendText(line);
            ActivityLog.ScrollToEnd();
        });
    }

    void ShowPage(Grid page)
    {
        HomePage.Visibility = Visibility.Collapsed;
        EditorPage.Visibility = Visibility.Collapsed;
        CameraPage.Visibility = Visibility.Collapsed;
        UploadsPage.Visibility = Visibility.Collapsed;
        SettingsPage.Visibility = Visibility.Collapsed;
        page.Visibility = Visibility.Visible;
    }

    void Home_Click(object sender, RoutedEventArgs e) { RefreshHome(); ShowPage(HomePage); }
    void Editor_Click(object sender, RoutedEventArgs e) { RefreshVisuals(); ShowPage(EditorPage); }
    void Camera_Click(object sender, RoutedEventArgs e) => ShowPage(CameraPage);
    void Uploads_Click(object sender, RoutedEventArgs e) => ShowPage(UploadsPage);
    void Settings_Click(object sender, RoutedEventArgs e) => ShowPage(SettingsPage);

    void SettingsChanged(object sender, RoutedEventArgs e) => SaveSettingsFromUi();

    void OpenPhoto_Click(object sender, RoutedEventArgs e)
    {
        var dlg = new OpenFileDialog { Filter = "Photos|*.jpg;*.jpeg;*.png;*.tif;*.tiff|All files|*.*" };
        if (dlg.ShowDialog() != true) return;
        PushUndo();
        LoadPhoto(dlg.FileName, true);
    }

    void LoadPhoto(string path, bool fit)
    {
        photoPath = path;
        if (fit)
        {
            var src = ImageTools.LoadBitmap(path);
            var scale = Math.Max(document.CanvasWidth / src.PixelWidth, document.CanvasHeight / src.PixelHeight);
            document.Photo.Width = src.PixelWidth * scale;
            document.Photo.Height = src.PixelHeight * scale;
            document.Photo.X = (document.CanvasWidth - document.Photo.Width) / 2;
            document.Photo.Y = (document.CanvasHeight - document.Photo.Height) / 2;
            document.Photo.Rotation = 0;
            document.Photo.CropLeft = 0; document.Photo.CropTop = 0; document.Photo.CropRight = 1; document.Photo.CropBottom = 1;
        }
        photoSelected = true; selectedLayer = null;
        RefreshVisuals();
        EditorStatus.Text = "Photo loaded. Drag it, use the wheel/pinch to resize, rotate with touch, crop it, or double-click it for Hue/Opacity.";
    }

    void AddImage_Click(object sender, RoutedEventArgs e)
    {
        var dlg = new OpenFileDialog { Filter = "PNG / Images|*.png;*.jpg;*.jpeg;*.webp;*.bmp|All files|*.*" };
        if (dlg.ShowDialog() != true) return;
        try
        {
            PushUndo();
            var ext = Path.GetExtension(dlg.FileName);
            var target = FtpReceiverService.UniquePath(AppPaths.AssetFolder, Path.GetFileNameWithoutExtension(dlg.FileName) + ext);
            File.Copy(dlg.FileName, target, false);
            var src = ImageTools.LoadBitmap(target);
            var width = 760d;
            var height = Math.Max(30, width * src.PixelHeight / Math.Max(1d, src.PixelWidth));
            var layer = new LayerModel
            {
                Kind = LayerKind.Image,
                Name = "Image / PNG",
                AssetPath = target,
                Width = width,
                Height = height,
                X = (document.CanvasWidth - width) / 2,
                Y = (document.CanvasHeight - height) / 2
            };
            document.Layers.Add(layer);
            SelectLayer(layer);
            RefreshVisuals();
        }
        catch (Exception ex) { System.Windows.MessageBox.Show(ex.Message, "Add image"); }
    }

    void AddText_Click(object sender, RoutedEventArgs e)
    {
        PushUndo();
        var layer = new LayerModel
        {
            Kind = LayerKind.Text,
            Name = "Text",
            Text = "PLAYER NAME",
            Width = 760,
            Height = 150,
            X = 160,
            Y = 1040,
            FontSize = 76,
            Align = "Center"
        };
        document.Layers.Add(layer);
        SelectLayer(layer);
        RefreshVisuals();
        OpenTextProperties(layer);
    }

    void RefreshVisuals()
    {
        if (Artboard == null) return;
        Artboard.Children.Clear();
        layerVisuals.Clear();
        photoVisual = null;
        if (!string.IsNullOrWhiteSpace(photoPath) && File.Exists(photoPath))
        {
            try
            {
                var src = ImageTools.HueShift(ImageTools.LoadBitmap(photoPath), document.Photo.Hue);
                photoVisual = CreateImageVisual(src, document.Photo.X, document.Photo.Y, document.Photo.Width, document.Photo.Height,
                    document.Photo.Rotation, document.Photo.Opacity, document.Photo.CropLeft, document.Photo.CropTop, document.Photo.CropRight, document.Photo.CropBottom, photoSelected, "PHOTO");
                Panel.SetZIndex(photoVisual, 0);
                Artboard.Children.Add(photoVisual);
            }
            catch (Exception ex) { Log("Photo preview: " + ex.Message); }
        }

        for (var i = 0; i < document.Layers.Count; i++)
        {
            var layer = document.Layers[i];
            if (!layer.Visible) continue;
            FrameworkElement visual;
            if (layer.Kind == LayerKind.Image)
            {
                if (!File.Exists(layer.AssetPath)) continue;
                var src = ImageTools.HueShift(ImageTools.LoadBitmap(layer.AssetPath), layer.Hue);
                visual = CreateImageVisual(src, layer.X, layer.Y, layer.Width, layer.Height, layer.Rotation, layer.Opacity,
                    layer.CropLeft, layer.CropTop, layer.CropRight, layer.CropBottom, ReferenceEquals(layer, selectedLayer), layer);
            }
            else visual = CreateTextVisual(layer, ReferenceEquals(layer, selectedLayer));
            Panel.SetZIndex(visual, 10 + i);
            Artboard.Children.Add(visual);
            layerVisuals[layer.Id] = visual;
        }
        RefreshLayerList();
    }

    FrameworkElement CreateImageVisual(BitmapSource source, double x, double y, double width, double height, double rotation, double opacity,
        double cropL, double cropT, double cropR, double cropB, bool selected, object tag)
    {
        var image = new Image { Source = source, Stretch = Stretch.Fill, Width = width, Height = height };
        image.Clip = new RectangleGeometry(new Rect(cropL * width, cropT * height, Math.Max(1, (cropR - cropL) * width), Math.Max(1, (cropB - cropT) * height)));
        var border = new Border
        {
            Width = width, Height = height, Child = image, Opacity = opacity, Tag = tag,
            BorderBrush = selected ? Brushes.Gold : Brushes.Transparent, BorderThickness = new Thickness(selected ? 4 : 0),
            Background = Brushes.Transparent, RenderTransformOrigin = new Point(.5, .5), RenderTransform = new RotateTransform(rotation)
        };
        Canvas.SetLeft(border, x); Canvas.SetTop(border, y);
        HookElement(border);
        return border;
    }

    FrameworkElement CreateTextVisual(LayerModel l, bool selected)
    {
        var text = new TextBlock
        {
            Text = DisplayText(l.Text),
            FontFamily = new FontFamily(string.IsNullOrWhiteSpace(l.FontFamily) ? "Arial" : l.FontFamily),
            FontSize = l.FontSize,
            FontWeight = l.Bold ? FontWeights.Bold : FontWeights.Normal,
            FontStyle = l.Italic ? FontStyles.Italic : FontStyles.Normal,
            Foreground = EditorRenderer.BrushFrom(l.TextColor, Brushes.White),
            TextAlignment = l.Align.Equals("Right", StringComparison.OrdinalIgnoreCase) ? TextAlignment.Right : l.Align.Equals("Left", StringComparison.OrdinalIgnoreCase) ? TextAlignment.Left : TextAlignment.Center,
            TextWrapping = TextWrapping.Wrap,
            VerticalAlignment = VerticalAlignment.Center,
            HorizontalAlignment = HorizontalAlignment.Stretch
        };
        if (l.ShadowRadius > 0 || Math.Abs(l.ShadowX) > .1 || Math.Abs(l.ShadowY) > .1)
        {
            var sc = ColorFrom(l.ShadowColor, Colors.Black);
            text.Effect = new DropShadowEffect { Color = sc, BlurRadius = l.ShadowRadius, ShadowDepth = Math.Sqrt(l.ShadowX * l.ShadowX + l.ShadowY * l.ShadowY), Direction = Math.Atan2(-l.ShadowY, l.ShadowX) * 180 / Math.PI, Opacity = 1 };
        }
        var border = new Border
        {
            Width = l.Width, Height = l.Height, Child = text, Opacity = l.Opacity, Tag = l,
            BorderBrush = selected ? Brushes.Gold : Brushes.Transparent, BorderThickness = new Thickness(selected ? 4 : 0),
            Background = new SolidColorBrush(Color.FromArgb(2, 255, 255, 255)), RenderTransformOrigin = new Point(.5, .5), RenderTransform = new RotateTransform(l.Rotation)
        };
        Canvas.SetLeft(border, l.X); Canvas.SetTop(border, l.Y);
        HookElement(border);
        return border;
    }

    string DisplayText(string text) => (text ?? "")
        .Replace("{PLAYER_NAME}", "PLAYER NAME", StringComparison.OrdinalIgnoreCase)
        .Replace("{NUMBER}", "#00", StringComparison.OrdinalIgnoreCase)
        .Replace("{POSITION}", "POSITION", StringComparison.OrdinalIgnoreCase)
        .Replace("{TEAM}", "TEAM", StringComparison.OrdinalIgnoreCase)
        .Replace("{EVENT}", "EVENT", StringComparison.OrdinalIgnoreCase);

    void HookElement(FrameworkElement element)
    {
        element.PreviewMouseLeftButtonDown += Element_MouseDown;
        element.PreviewMouseMove += Element_MouseMove;
        element.PreviewMouseLeftButtonUp += Element_MouseUp;
        element.PreviewTouchDown += (_, e) => { SelectFromTag(element.Tag); e.Handled = false; };
    }

    void Element_MouseDown(object sender, MouseButtonEventArgs e)
    {
        if (cropMode) return;
        if (sender is not FrameworkElement el) return;
        SelectFromTag(el.Tag);
        if (e.ClickCount >= 2)
        {
            if (photoSelected) OpenPhotoProperties();
            else if (selectedLayer?.Kind == LayerKind.Text) OpenTextProperties(selectedLayer);
            else if (selectedLayer?.Kind == LayerKind.Image) OpenImageProperties(selectedLayer);
            e.Handled = true; return;
        }
        PushUndo();
        dragging = true;
        lastDrag = e.GetPosition(Artboard);
        el.CaptureMouse();
        e.Handled = true;
    }

    void Element_MouseMove(object sender, System.Windows.Input.MouseEventArgs e)
    {
        if (!dragging || e.LeftButton != MouseButtonState.Pressed) return;
        var now = e.GetPosition(Artboard); var dx = now.X - lastDrag.X; var dy = now.Y - lastDrag.Y; lastDrag = now;
        if (photoSelected) { document.Photo.X += dx; document.Photo.Y += dy; }
        else if (selectedLayer != null) { selectedLayer.X += dx; selectedLayer.Y += dy; }
        RefreshVisuals();
        e.Handled = true;
    }

    void Element_MouseUp(object sender, MouseButtonEventArgs e)
    {
        if (sender is FrameworkElement el) el.ReleaseMouseCapture();
        dragging = false; e.Handled = true;
    }

    void SelectFromTag(object? tag)
    {
        if (tag is string s && s == "PHOTO") { photoSelected = true; selectedLayer = null; }
        else if (tag is LayerModel l) { photoSelected = false; selectedLayer = l; }
        RefreshVisuals();
    }

    void SelectLayer(LayerModel l) { selectedLayer = l; photoSelected = false; }

    void RefreshLayerList()
    {
        rebuildingLayers = true;
        LayerList.Items.Clear();
        LayerList.Items.Add("PHOTO");
        foreach (var l in document.Layers) LayerList.Items.Add((l.Visible ? "● " : "○ ") + l.Name + (l.Kind == LayerKind.Text ? " — " + DisplayText(l.Text) : ""));
        if (photoSelected) LayerList.SelectedIndex = 0;
        else if (selectedLayer != null)
        {
            var idx = document.Layers.IndexOf(selectedLayer);
            if (idx >= 0) LayerList.SelectedIndex = idx + 1;
        }
        rebuildingLayers = false;
    }

    void LayerList_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (rebuildingLayers || LayerList.SelectedIndex < 0) return;
        if (LayerList.SelectedIndex == 0) { photoSelected = true; selectedLayer = null; }
        else
        {
            var idx = LayerList.SelectedIndex - 1;
            if (idx >= 0 && idx < document.Layers.Count) { selectedLayer = document.Layers[idx]; photoSelected = false; }
        }
        RefreshVisuals();
    }

    void Artboard_MouseDown(object sender, MouseButtonEventArgs e)
    {
        if (cropMode || e.OriginalSource is not Canvas) return;
        photoSelected = false; selectedLayer = null; RefreshVisuals();
    }
    void Artboard_MouseMove(object sender, System.Windows.Input.MouseEventArgs e) { }
    void Artboard_MouseUp(object sender, MouseButtonEventArgs e) { dragging = false; }

    void Artboard_MouseWheel(object sender, MouseWheelEventArgs e)
    {
        if (!photoSelected && selectedLayer == null) return;
        PushUndo();
        var f = e.Delta > 0 ? 1.06 : 0.94;
        if (photoSelected) ScaleBox(document.Photo, f);
        else if (selectedLayer != null)
        {
            var cx = selectedLayer.X + selectedLayer.Width / 2; var cy = selectedLayer.Y + selectedLayer.Height / 2;
            selectedLayer.Width = Math.Clamp(selectedLayer.Width * f, 20, document.CanvasWidth * 4);
            selectedLayer.Height = Math.Clamp(selectedLayer.Height * f, 20, document.CanvasHeight * 4);
            selectedLayer.X = cx - selectedLayer.Width / 2; selectedLayer.Y = cy - selectedLayer.Height / 2;
            if (selectedLayer.Kind == LayerKind.Text) selectedLayer.FontSize = Math.Clamp(selectedLayer.FontSize * f, 5, 500);
        }
        RefreshVisuals(); e.Handled = true;
    }

    void ScaleBox(PhotoState p, double f)
    {
        var cx = p.X + p.Width / 2; var cy = p.Y + p.Height / 2;
        p.Width = Math.Clamp(p.Width * f, 40, document.CanvasWidth * 6); p.Height = Math.Clamp(p.Height * f, 40, document.CanvasHeight * 6);
        p.X = cx - p.Width / 2; p.Y = cy - p.Height / 2;
    }

    void Artboard_ManipulationDelta(object sender, ManipulationDeltaEventArgs e)
    {
        if (!photoSelected && selectedLayer == null) return;
        var d = e.DeltaManipulation;
        if (photoSelected)
        {
            document.Photo.X += d.Translation.X; document.Photo.Y += d.Translation.Y;
            var f = Math.Max(.2, (d.Scale.X + d.Scale.Y) / 2); ScaleBox(document.Photo, f);
            document.Photo.Rotation += d.Rotation;
        }
        else if (selectedLayer != null)
        {
            selectedLayer.X += d.Translation.X; selectedLayer.Y += d.Translation.Y;
            var f = Math.Max(.2, (d.Scale.X + d.Scale.Y) / 2);
            var cx = selectedLayer.X + selectedLayer.Width / 2; var cy = selectedLayer.Y + selectedLayer.Height / 2;
            selectedLayer.Width = Math.Clamp(selectedLayer.Width * f, 20, document.CanvasWidth * 4);
            selectedLayer.Height = Math.Clamp(selectedLayer.Height * f, 20, document.CanvasHeight * 4);
            selectedLayer.X = cx - selectedLayer.Width / 2; selectedLayer.Y = cy - selectedLayer.Height / 2;
            if (selectedLayer.Kind == LayerKind.Text) selectedLayer.FontSize = Math.Clamp(selectedLayer.FontSize * f, 5, 500);
            selectedLayer.Rotation += d.Rotation;
        }
        RefreshVisuals(); e.Handled = true;
    }

    void Crop_Click(object sender, RoutedEventArgs e)
    {
        if (!photoSelected && selectedLayer?.Kind != LayerKind.Image) { System.Windows.MessageBox.Show("Select the photo or an image/PNG layer first."); return; }
        cropMode = true; cropDragging = false;
        EditorStatus.Text = "CROP MODE: draw a box around the part you want to KEEP.";
    }

    void CropPreviewDown(object sender, MouseButtonEventArgs e)
    {
        if (!cropMode) return;
        if (!photoSelected && selectedLayer?.Kind != LayerKind.Image) { cropMode = false; return; }
        PushUndo();
        cropStart = e.GetPosition(Artboard); cropDragging = true;
        cropVisual = new Rectangle { Stroke = Brushes.White, StrokeThickness = 4, Fill = new SolidColorBrush(Color.FromArgb(30, 255, 255, 255)), IsHitTestVisible = false };
        Panel.SetZIndex(cropVisual, 9999); Artboard.Children.Add(cropVisual);
        Canvas.SetLeft(cropVisual, cropStart.X); Canvas.SetTop(cropVisual, cropStart.Y);
        e.Handled = true;
    }

    void CropPreviewMove(object sender, System.Windows.Input.MouseEventArgs e)
    {
        if (!cropMode || !cropDragging || cropVisual == null) return;
        var p = e.GetPosition(Artboard); var x = Math.Min(p.X, cropStart.X); var y = Math.Min(p.Y, cropStart.Y);
        cropVisual.Width = Math.Abs(p.X - cropStart.X); cropVisual.Height = Math.Abs(p.Y - cropStart.Y);
        Canvas.SetLeft(cropVisual, x); Canvas.SetTop(cropVisual, y); e.Handled = true;
    }

    void CropPreviewUp(object sender, MouseButtonEventArgs e)
    {
        if (!cropMode || !cropDragging) return;
        var end = e.GetPosition(Artboard); cropDragging = false; cropMode = false;
        var keep = new Rect(Math.Min(end.X, cropStart.X), Math.Min(end.Y, cropStart.Y), Math.Abs(end.X - cropStart.X), Math.Abs(end.Y - cropStart.Y));
        if (cropVisual != null) Artboard.Children.Remove(cropVisual); cropVisual = null;
        var bounds = SelectedBounds(); var intersection = Rect.Intersect(bounds, keep);
        if (!intersection.IsEmpty && intersection.Width > 5 && intersection.Height > 5)
        {
            var l = Math.Clamp((intersection.Left - bounds.Left) / bounds.Width, 0, 1);
            var t = Math.Clamp((intersection.Top - bounds.Top) / bounds.Height, 0, 1);
            var r = Math.Clamp((intersection.Right - bounds.Left) / bounds.Width, 0, 1);
            var b = Math.Clamp((intersection.Bottom - bounds.Top) / bounds.Height, 0, 1);
            if (photoSelected) { document.Photo.CropLeft = l; document.Photo.CropTop = t; document.Photo.CropRight = r; document.Photo.CropBottom = b; }
            else if (selectedLayer != null) { selectedLayer.CropLeft = l; selectedLayer.CropTop = t; selectedLayer.CropRight = r; selectedLayer.CropBottom = b; }
        }
        RefreshVisuals(); EditorStatus.Text = "Crop applied non-destructively. RESET CROP restores the full layer."; e.Handled = true;
    }

    Rect SelectedBounds()
    {
        if (photoSelected) return new Rect(document.Photo.X, document.Photo.Y, document.Photo.Width, document.Photo.Height);
        if (selectedLayer != null) return new Rect(selectedLayer.X, selectedLayer.Y, selectedLayer.Width, selectedLayer.Height);
        return Rect.Empty;
    }

    void ResetCrop_Click(object sender, RoutedEventArgs e)
    {
        PushUndo();
        if (photoSelected) { document.Photo.CropLeft = 0; document.Photo.CropTop = 0; document.Photo.CropRight = 1; document.Photo.CropBottom = 1; }
        else if (selectedLayer?.Kind == LayerKind.Image) { selectedLayer.CropLeft = 0; selectedLayer.CropTop = 0; selectedLayer.CropRight = 1; selectedLayer.CropBottom = 1; }
        else return;
        RefreshVisuals();
    }

    void PushUndo()
    {
        try { undo.Push(JsonSerializer.Serialize(document, JsonOptions.Default)); while (undo.Count > 50) TrimBottom(undo); redo.Clear(); }
        catch { }
    }
    static void TrimBottom(Stack<string> s) { var a = s.Reverse().Skip(1).Reverse().ToArray(); s.Clear(); foreach (var x in a) s.Push(x); }
    void Undo_Click(object sender, RoutedEventArgs e) { if (undo.Count == 0) return; redo.Push(JsonSerializer.Serialize(document, JsonOptions.Default)); Restore(undo.Pop()); }
    void Redo_Click(object sender, RoutedEventArgs e) { if (redo.Count == 0) return; undo.Push(JsonSerializer.Serialize(document, JsonOptions.Default)); Restore(redo.Pop()); }
    void Restore(string json)
    {
        document = JsonSerializer.Deserialize<EditorDocument>(json, JsonOptions.Default) ?? new(); selectedLayer = null; photoSelected = false; RefreshVisuals();
    }

    void Delete_Click(object sender, RoutedEventArgs e)
    {
        if (selectedLayer == null) { if (photoSelected) System.Windows.MessageBox.Show("The base photo is not deleted by the layer Delete button. Open another photo instead."); return; }
        PushUndo(); document.Layers.Remove(selectedLayer); selectedLayer = null; RefreshVisuals();
    }
    void Duplicate_Click(object sender, RoutedEventArgs e)
    {
        if (selectedLayer == null) return; PushUndo();
        var copy = JsonSerializer.Deserialize<LayerModel>(JsonSerializer.Serialize(selectedLayer, JsonOptions.Default), JsonOptions.Default)!;
        copy.Id = Guid.NewGuid(); copy.X += 24; copy.Y += 24; copy.Name += " Copy";
        var idx = document.Layers.IndexOf(selectedLayer); document.Layers.Insert(Math.Min(document.Layers.Count, idx + 1), copy); SelectLayer(copy); RefreshVisuals();
    }
    void SendBack_Click(object sender, RoutedEventArgs e) { MoveLayer(-1); }
    void BringFront_Click(object sender, RoutedEventArgs e) { MoveLayer(1); }
    void MoveLayer(int delta)
    {
        if (selectedLayer == null) return; var from = document.Layers.IndexOf(selectedLayer); var to = Math.Clamp(from + delta, 0, document.Layers.Count - 1); if (from == to) return;
        PushUndo(); document.Layers.RemoveAt(from); document.Layers.Insert(to, selectedLayer); RefreshVisuals();
    }

    void SaveLayout_Click(object sender, RoutedEventArgs e) { SaveLayout(); EditorStatus.Text = "Layout saved."; }
    void SaveLayout()
    {
        AppPaths.Ensure(); File.WriteAllText(AppPaths.TemplateFile, JsonSerializer.Serialize(document, JsonOptions.Default));
    }
    void LockLayout_Click(object sender, RoutedEventArgs e)
    {
        settings.LayoutLocked = !settings.LayoutLocked;
        if (settings.LayoutLocked) SaveLayout();
        SettingsStore.Save(settings); ApplySettingsToUi(); RefreshHome();
        EditorStatus.Text = settings.LayoutLocked ? "LOCKED: new camera JPEGs can automatically use this exact layout." : "Layout unlocked.";
    }

    void Export_Click(object sender, RoutedEventArgs e)
    {
        if (string.IsNullOrWhiteSpace(photoPath) || !File.Exists(photoPath)) { System.Windows.MessageBox.Show("Open a photo first."); return; }
        var dlg = new SaveFileDialog { Filter = "JPEG|*.jpg", FileName = Path.GetFileNameWithoutExtension(photoPath) + "_edited.jpg" };
        if (dlg.ShowDialog() != true) return;
        try { EditorRenderer.Render(document.Clone(), photoPath, dlg.FileName); Log("Exported: " + dlg.FileName); }
        catch (Exception ex) { System.Windows.MessageBox.Show(ex.Message, "Export"); }
    }

    void OpenTextProperties(LayerModel l)
    {
        var index = document.Layers.IndexOf(l); if (index < 0) return;
        var backup = JsonSerializer.Deserialize<LayerModel>(JsonSerializer.Serialize(l, JsonOptions.Default), JsonOptions.Default)!;
        var win = PropertyWindow("Text Properties", 480, 700); var panel = (StackPanel)((ScrollViewer)win.Content).Content;
        var text = AddField(panel, "Text", l.Text, multiline: true);
        panel.Children.Add(Label("Font"));
        var fonts = new ComboBox { IsEditable = false, MaxDropDownHeight = 260 };
        foreach (var f in Fonts.SystemFontFamilies.OrderBy(x => x.Source)) fonts.Items.Add(f.Source);
        fonts.SelectedItem = l.FontFamily; panel.Children.Add(fonts);
        panel.Children.Add(Label("Size")); var size = Slider(8, 260, l.FontSize); panel.Children.Add(size);
        var bold = new CheckBox { Content = "Bold", IsChecked = l.Bold }; var italic = new CheckBox { Content = "Italic", IsChecked = l.Italic };
        var bi = new StackPanel { Orientation = Orientation.Horizontal }; bi.Children.Add(bold); bi.Children.Add(italic); panel.Children.Add(bi);
        panel.Children.Add(Label("Alignment")); var align = new ComboBox(); align.Items.Add("Left"); align.Items.Add("Center"); align.Items.Add("Right"); align.SelectedItem = l.Align; panel.Children.Add(align);
        var colorButton = new Button { Content = "TEXT COLOR…" }; panel.Children.Add(colorButton);
        var strokeColor = new Button { Content = "OUTLINE COLOR…" }; panel.Children.Add(strokeColor);
        panel.Children.Add(Label("Outline width")); var stroke = Slider(0, 18, l.StrokeWidth); panel.Children.Add(stroke);
        var shadowColor = new Button { Content = "SHADOW COLOR…" }; panel.Children.Add(shadowColor);
        panel.Children.Add(Label("Shadow blur")); var shadow = Slider(0, 40, l.ShadowRadius); panel.Children.Add(shadow);
        panel.Children.Add(Label("Opacity")); var opacity = Slider(5, 100, l.Opacity * 100); panel.Children.Add(opacity);
        var buttons = DialogButtons(win, panel); var apply = buttons.Item1; var cancel = buttons.Item2; var applied = false;

        void Live() { RefreshVisuals(); }
        text.TextChanged += (_, _) => { l.Text = text.Text; l.Name = string.IsNullOrWhiteSpace(text.Text) ? "Text" : "Text"; Live(); };
        fonts.SelectionChanged += (_, _) => { if (fonts.SelectedItem is string f) l.FontFamily = f; Live(); };
        size.ValueChanged += (_, _) => { l.FontSize = size.Value; Live(); };
        bold.Checked += (_, _) => { l.Bold = true; Live(); }; bold.Unchecked += (_, _) => { l.Bold = false; Live(); };
        italic.Checked += (_, _) => { l.Italic = true; Live(); }; italic.Unchecked += (_, _) => { l.Italic = false; Live(); };
        align.SelectionChanged += (_, _) => { if (align.SelectedItem is string a) l.Align = a; Live(); };
        stroke.ValueChanged += (_, _) => { l.StrokeWidth = stroke.Value; Live(); };
        shadow.ValueChanged += (_, _) => { l.ShadowRadius = shadow.Value; Live(); };
        opacity.ValueChanged += (_, _) => { l.Opacity = opacity.Value / 100; Live(); };
        colorButton.Click += (_, _) => { var c = ChooseColor(l.TextColor); if (c != null) { l.TextColor = c; Live(); } };
        strokeColor.Click += (_, _) => { var c = ChooseColor(l.StrokeColor); if (c != null) { l.StrokeColor = c; Live(); } };
        shadowColor.Click += (_, _) => { var c = ChooseColor(l.ShadowColor); if (c != null) { l.ShadowColor = c; Live(); } };
        apply.Click += (_, _) => { applied = true; win.Close(); };
        cancel.Click += (_, _) => win.Close();
        PushUndo(); win.ShowDialog();
        if (!applied) { document.Layers[index] = backup; selectedLayer = backup; }
        RefreshVisuals();
    }

    void OpenImageProperties(LayerModel l)
    {
        var index = document.Layers.IndexOf(l); if (index < 0) return;
        var backup = JsonSerializer.Deserialize<LayerModel>(JsonSerializer.Serialize(l, JsonOptions.Default), JsonOptions.Default)!;
        var win = PropertyWindow("Image Properties", 440, 380); var panel = (StackPanel)((ScrollViewer)win.Content).Content;
        panel.Children.Add(Label("Hue shift (-180° to +180°)")); var hue = Slider(-180, 180, l.Hue); panel.Children.Add(hue);
        var hueText = Label($"{l.Hue:0}°"); panel.Children.Add(hueText);
        panel.Children.Add(Label("Opacity")); var opacity = Slider(5, 100, l.Opacity * 100); panel.Children.Add(opacity);
        var reset = new Button { Content = "RESET HUE" }; panel.Children.Add(reset);
        var buttons = DialogButtons(win, panel); var applied = false;
        hue.ValueChanged += (_, _) => { l.Hue = hue.Value; hueText.Content = $"{l.Hue:0}°"; RefreshVisuals(); };
        opacity.ValueChanged += (_, _) => { l.Opacity = opacity.Value / 100; RefreshVisuals(); };
        reset.Click += (_, _) => { hue.Value = 0; };
        buttons.Item1.Click += (_, _) => { applied = true; win.Close(); }; buttons.Item2.Click += (_, _) => win.Close();
        PushUndo(); win.ShowDialog(); if (!applied) { document.Layers[index] = backup; selectedLayer = backup; } RefreshVisuals();
    }

    void OpenPhotoProperties()
    {
        if (string.IsNullOrWhiteSpace(photoPath)) return;
        var backup = JsonSerializer.Deserialize<PhotoState>(JsonSerializer.Serialize(document.Photo, JsonOptions.Default), JsonOptions.Default)!;
        var win = PropertyWindow("Photo Properties", 440, 420); var panel = (StackPanel)((ScrollViewer)win.Content).Content;
        panel.Children.Add(Label("Hue shift (-180° to +180°)")); var hue = Slider(-180, 180, document.Photo.Hue); panel.Children.Add(hue);
        var hueText = Label($"{document.Photo.Hue:0}°"); panel.Children.Add(hueText);
        panel.Children.Add(Label("Opacity")); var opacity = Slider(5, 100, document.Photo.Opacity * 100); panel.Children.Add(opacity);
        var fit = new Button { Content = "FIT / FILL CANVAS" }; panel.Children.Add(fit);
        var reset = new Button { Content = "RESET HUE" }; panel.Children.Add(reset);
        var buttons = DialogButtons(win, panel); var applied = false;
        hue.ValueChanged += (_, _) => { document.Photo.Hue = hue.Value; hueText.Content = $"{document.Photo.Hue:0}°"; RefreshVisuals(); };
        opacity.ValueChanged += (_, _) => { document.Photo.Opacity = opacity.Value / 100; RefreshVisuals(); };
        fit.Click += (_, _) => { if (photoPath != null) LoadPhoto(photoPath, true); };
        reset.Click += (_, _) => hue.Value = 0;
        buttons.Item1.Click += (_, _) => { applied = true; win.Close(); }; buttons.Item2.Click += (_, _) => win.Close();
        PushUndo(); win.ShowDialog(); if (!applied) document.Photo = backup; RefreshVisuals();
    }

    Window PropertyWindow(string title, double width, double height)
    {
        var panel = new StackPanel { Margin = new Thickness(16) };
        var scroll = new ScrollViewer { VerticalScrollBarVisibility = ScrollBarVisibility.Auto, Content = panel };
        return new Window { Title = title, Width = width, Height = height, MinHeight = 300, Background = BrushFrom("#FF111820"), Foreground = Brushes.White, Content = scroll, Owner = this, WindowStartupLocation = WindowStartupLocation.CenterOwner };
    }
    TextBox AddField(Panel panel, string label, string value, bool multiline = false)
    {
        panel.Children.Add(Label(label)); var b = new TextBox { Text = value, Margin = new Thickness(0, 4, 0, 8), Padding = new Thickness(8), Background = BrushFrom("#FF17202A"), Foreground = Brushes.White, AcceptsReturn = multiline, Height = multiline ? 90 : double.NaN, TextWrapping = multiline ? TextWrapping.Wrap : TextWrapping.NoWrap }; panel.Children.Add(b); return b;
    }
    System.Windows.Controls.Label Label(string text) => new() { Content = text, Foreground = Brushes.White, Margin = new Thickness(0, 7, 0, 2) };
    Slider Slider(double min, double max, double value) => new() { Minimum = min, Maximum = max, Value = Math.Clamp(value, min, max), Margin = new Thickness(0, 2, 0, 8), IsSnapToTickEnabled = false };
    Tuple<Button, Button> DialogButtons(Window win, Panel panel)
    {
        var row = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right, Margin = new Thickness(0, 12, 0, 0) };
        var cancel = new Button { Content = "CANCEL", MinWidth = 90 }; var apply = new Button { Content = "APPLY", MinWidth = 90, Background = BrushFrom("#FF2E7D32") }; row.Children.Add(cancel); row.Children.Add(apply); panel.Children.Add(row); return Tuple.Create(apply, cancel);
    }

    string? ChooseColor(string current)
    {
        using var dlg = new Forms.ColorDialog { FullOpen = true };
        try { var c = ColorFrom(current, Colors.White); dlg.Color = DrawingColor.FromArgb(c.A, c.R, c.G, c.B); } catch { }
        if (dlg.ShowDialog() != Forms.DialogResult.OK) return null;
        var x = dlg.Color; return $"#{x.A:X2}{x.R:X2}{x.G:X2}{x.B:X2}";
    }

    static Brush BrushFrom(string hex) => EditorRenderer.BrushFrom(hex, Brushes.White);
    static Color ColorFrom(string hex, Color fallback)
    {
        try { var x = ColorConverter.ConvertFromString(hex); if (x is Color c) return c; } catch { } return fallback;
    }

    void FtpStart_Click(object sender, RoutedEventArgs e)
    {
        SaveSettingsFromUi();
        try
        {
            var inbound = Path.Combine(settings.OutputRoot, "Inbound FTP"); Directory.CreateDirectory(inbound);
            ftp.Start(settings.FtpPort, settings.FtpUser, settings.FtpPassword, inbound);
            var ip = LocalIPv4(); FtpStatusText.Text = $"RUNNING — camera target {ip}:{settings.FtpPort} — passive mode"; FtpStatusText.Foreground = Brushes.LightGreen;
            HomeCameraStatus.Text = $"FTP {ip}:{settings.FtpPort}"; Log($"Camera FTP target: {ip}:{settings.FtpPort}");
        }
        catch (Exception ex) { System.Windows.MessageBox.Show(ex.Message, "FTP receiver"); }
    }
    void FtpStop_Click(object sender, RoutedEventArgs e) { ftp.Stop(); FtpStatusText.Text = "FTP stopped"; FtpStatusText.Foreground = Brushes.Gold; RefreshHome(); }
    void Hotspot_Click(object sender, RoutedEventArgs e) => WindowsCameraHelpers.OpenHotspotSettings();

    void ChooseWatchFolder_Click(object sender, RoutedEventArgs e)
    {
        using var dlg = new Forms.FolderBrowserDialog { Description = "Choose the camera / Nikon NX Tether output folder", UseDescriptionForTitle = true };
        if (dlg.ShowDialog() != Forms.DialogResult.OK) return; WatchFolderBox.Text = dlg.SelectedPath; SaveSettingsFromUi();
    }
    void StartWatch_Click(object sender, RoutedEventArgs e)
    {
        SaveSettingsFromUi(); if (!Directory.Exists(settings.WatchFolder)) { System.Windows.MessageBox.Show("Choose a valid tether folder first."); return; }
        watcher.Start(settings.WatchFolder); WatchStatusText.Text = "WATCHING — " + settings.WatchFolder; WatchStatusText.Foreground = Brushes.LightGreen; RefreshHome();
    }
    void ScanCamera_Click(object sender, RoutedEventArgs e)
    {
        var folders = WindowsCameraHelpers.FindCameraFolders().Distinct(StringComparer.OrdinalIgnoreCase).ToList();
        if (folders.Count == 0) { System.Windows.MessageBox.Show("No mounted camera/DCIM drive was found. If the camera is tethered through Nikon NX Tether, choose its output folder instead."); return; }
        WatchFolderBox.Text = folders[0]; SaveSettingsFromUi(); watcher.Start(folders[0]); WatchStatusText.Text = "WATCHING CAMERA — " + folders[0]; WatchStatusText.Foreground = Brushes.LightGreen; Log("Camera/DCIM folder found: " + folders[0]);
    }

    void ChooseOutput_Click(object sender, RoutedEventArgs e)
    {
        using var dlg = new Forms.FolderBrowserDialog { Description = "Choose Camera Auto Upload output folder", UseDescriptionForTitle = true };
        if (dlg.ShowDialog() != Forms.DialogResult.OK) return; OutputRootBox.Text = dlg.SelectedPath; SaveSettingsFromUi();
    }

    async Task HandleIncomingFileAsync(string source)
    {
        await pipelineGate.WaitAsync();
        try
        {
            var ext = Path.GetExtension(source).ToLowerInvariant(); var isJpeg = ext is ".jpg" or ".jpeg";
            var day = DateTime.Now.ToString("yyyy-MM-dd");
            var originals = Path.Combine(settings.OutputRoot, "Originals", day); Directory.CreateDirectory(originals);
            var originalCopy = FtpReceiverService.UniquePath(originals, Path.GetFileName(source));
            if (!string.Equals(Path.GetFullPath(source), Path.GetFullPath(originalCopy), StringComparison.OrdinalIgnoreCase)) File.Copy(source, originalCopy, false);
            else originalCopy = source;
            Log("Original preserved: " + originalCopy);

            string? edited = null;
            if (isJpeg && settings.AutoProcess && settings.LayoutLocked && File.Exists(AppPaths.TemplateFile))
            {
                try
                {
                    var locked = JsonSerializer.Deserialize<EditorDocument>(File.ReadAllText(AppPaths.TemplateFile), JsonOptions.Default) ?? document.Clone();
                    var processed = Path.Combine(settings.OutputRoot, "Processed", day); Directory.CreateDirectory(processed);
                    edited = FtpReceiverService.UniquePath(processed, Path.GetFileNameWithoutExtension(originalCopy) + "_edited.jpg");
                    await EditorRenderer.RenderJpegAsync(locked, originalCopy, edited);
                    Log("Locked layout applied: " + edited);
                }
                catch (Exception ex) { Log("Auto editor: " + ex.Message); }
            }

            if (settings.FlickrEnabled && isJpeg)
            {
                if (settings.UploadChoice is UploadChoice.OriginalOnly or UploadChoice.Both) await uploads.EnqueueAsync(originalCopy);
                if (edited != null && settings.UploadChoice is UploadChoice.EditedOnly or UploadChoice.Both) await uploads.EnqueueAsync(edited);
            }
            Dispatcher.Invoke(RefreshHome);
        }
        catch (Exception ex) { Log("Incoming file: " + ex.Message); }
        finally { pipelineGate.Release(); }
    }

    static string LocalIPv4()
    {
        try { return Dns.GetHostAddresses(Dns.GetHostName()).FirstOrDefault(x => x.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(x))?.ToString() ?? "127.0.0.1"; }
        catch { return "127.0.0.1"; }
    }
}
