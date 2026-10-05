using System;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace CameraAutoUpload.Windows;

public partial class MainWindow
{
    async void RefreshCameraArtwork105(CameraProfile cp)
    {
        if (HomeCameraArt == null) return;

        HomeCameraArt.Children.Clear();
        HomeCameraArt.Width = 560;
        HomeCameraArt.Height = 330;

        var loading = new TextBlock
        {
            Text = $"Loading real {cp.DisplayName} photo…",
            Foreground = new SolidColorBrush(Color.FromRgb(160, 171, 181)),
            FontSize = 16,
            HorizontalAlignment = HorizontalAlignment.Center,
            TextAlignment = TextAlignment.Center,
            Width = 520
        };
        Canvas.SetLeft(loading, 20);
        Canvas.SetTop(loading, 145);
        HomeCameraArt.Children.Add(loading);

        string? path = null;
        try { path = await CameraPhotoService.GetPhotoAsync(cp); }
        catch { }

        // The user may have selected a different camera while the photo was downloading.
        var current = CameraProfiles.Find(settings.CameraBrand, settings.CameraModel);
        if (!current.Brand.Equals(cp.Brand, StringComparison.OrdinalIgnoreCase) || !current.Model.Equals(cp.Model, StringComparison.OrdinalIgnoreCase)) return;

        HomeCameraArt.Children.Clear();

        if (string.IsNullOrWhiteSpace(path) || !File.Exists(path))
        {
            var noPhoto = new StackPanel { Width = 500 };
            noPhoto.Children.Add(new TextBlock
            {
                Text = cp.DisplayName,
                Foreground = Brushes.White,
                FontSize = 30,
                FontWeight = FontWeights.SemiBold,
                HorizontalAlignment = HorizontalAlignment.Center
            });
            noPhoto.Children.Add(new TextBlock
            {
                Text = "Real camera photo unavailable right now.\nNo generic or clip-art camera will be substituted.",
                Foreground = new SolidColorBrush(Color.FromRgb(160, 171, 181)),
                FontSize = 14,
                TextAlignment = TextAlignment.Center,
                TextWrapping = TextWrapping.Wrap,
                Margin = new Thickness(0, 10, 0, 0)
            });
            Canvas.SetLeft(noPhoto, 30);
            Canvas.SetTop(noPhoto, 115);
            HomeCameraArt.Children.Add(noPhoto);
            return;
        }

        try
        {
            var bitmap = new BitmapImage();
            bitmap.BeginInit();
            bitmap.CacheOption = BitmapCacheOption.OnLoad;
            bitmap.UriSource = new Uri(path, UriKind.Absolute);
            bitmap.EndInit();
            bitmap.Freeze();

            var photo = new Image
            {
                Source = bitmap,
                Width = 520,
                Height = 260,
                Stretch = Stretch.Uniform,
                SnapsToDevicePixels = true
            };
            Canvas.SetLeft(photo, 20);
            Canvas.SetTop(photo, 8);
            HomeCameraArt.Children.Add(photo);

            var strip = new Border
            {
                Width = 520,
                Height = 48,
                Background = new SolidColorBrush(Color.FromArgb(235, 10, 13, 17)),
                BorderBrush = new SolidColorBrush(Color.FromRgb(44, 52, 61)),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(6),
                Padding = new Thickness(12, 6, 12, 6)
            };
            var row = new Grid();
            row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            var label = new StackPanel { Orientation = Orientation.Vertical };
            label.Children.Add(new TextBlock { Text = cp.DisplayName, Foreground = Brushes.White, FontSize = 16, FontWeight = FontWeights.SemiBold });
            label.Children.Add(new TextBlock { Text = cp.Mode, Foreground = new SolidColorBrush(Color.FromRgb(154, 166, 178)), FontSize = 10 });
            row.Children.Add(label);
            var real = new TextBlock { Text = "REAL MODEL PHOTO", Foreground = new SolidColorBrush(Color.FromRgb(106, 216, 135)), FontSize = 10, FontWeight = FontWeights.Bold, VerticalAlignment = VerticalAlignment.Center };
            Grid.SetColumn(real, 1);
            row.Children.Add(real);
            strip.Child = row;
            Canvas.SetLeft(strip, 20);
            Canvas.SetTop(strip, 274);
            HomeCameraArt.Children.Add(strip);
        }
        catch
        {
            var bad = new TextBlock
            {
                Text = $"Could not display the cached {cp.DisplayName} photo.",
                Foreground = new SolidColorBrush(Color.FromRgb(239, 83, 80)),
                FontSize = 15,
                Width = 520,
                TextAlignment = TextAlignment.Center
            };
            Canvas.SetLeft(bad, 20);
            Canvas.SetTop(bad, 145);
            HomeCameraArt.Children.Add(bad);
        }
    }
}
