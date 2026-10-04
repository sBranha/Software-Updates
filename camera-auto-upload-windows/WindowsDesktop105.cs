using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Shapes;

namespace CameraAutoUpload.Windows;

public partial class MainWindow
{
    void RefreshCameraArtwork105(CameraProfile cp)
    {
        if (HomeCameraArt == null) return;
        DrawCameraArtwork105(HomeCameraArt, cp);
    }

    void DrawCameraArtwork105(Canvas canvas, CameraProfile cp)
    {
        canvas.Children.Clear();
        canvas.Width = 560;
        canvas.Height = 330;

        var accent = cp.Brand switch
        {
            "Nikon" => Color.FromRgb(246, 210, 26),
            "Canon" => Color.FromRgb(218, 38, 46),
            "Sony" => Color.FromRgb(58, 132, 255),
            "Fujifilm" => Color.FromRgb(53, 190, 154),
            _ => Color.FromRgb(110, 170, 230)
        };
        var accentBrush = new SolidColorBrush(accent);
        var bodyBrush = new LinearGradientBrush(Color.FromRgb(47, 52, 58), Color.FromRgb(17, 20, 24), 90);
        var dark = new SolidColorBrush(Color.FromRgb(11, 13, 16));
        var metal = new SolidColorBrush(Color.FromRgb(82, 88, 95));

        var halo = new Ellipse { Width = 460, Height = 150, Fill = new SolidColorBrush(Color.FromArgb(35, accent.R, accent.G, accent.B)) };
        Canvas.SetLeft(halo, 50); Canvas.SetTop(halo, 145); canvas.Children.Add(halo);

        bool dslr = cp.Model.StartsWith("D", StringComparison.OrdinalIgnoreCase) || cp.Model.Contains("1D X", StringComparison.OrdinalIgnoreCase);
        bool cinema = cp.Brand == "Sony" && cp.Model.StartsWith("FX", StringComparison.OrdinalIgnoreCase);
        bool gfx = cp.Brand == "Fujifilm" && cp.Model.StartsWith("GFX", StringComparison.OrdinalIgnoreCase);
        bool integratedGrip = cp.Model is "Z9" or "D6" || cp.Model.Contains("1D X", StringComparison.OrdinalIgnoreCase);

        if (cinema)
        {
            var body = new Border { Width = 330, Height = 190, Background = bodyBrush, BorderBrush = metal, BorderThickness = new Thickness(2), CornerRadius = new CornerRadius(12) };
            Canvas.SetLeft(body, 115); Canvas.SetTop(body, 82); canvas.Children.Add(body);
            for (int i = 0; i < 4; i++)
            {
                var slot = new Rectangle { Width = 12, Height = 42, RadiusX = 3, RadiusY = 3, Fill = dark };
                Canvas.SetLeft(slot, 132 + i * 24); Canvas.SetTop(slot, 102); canvas.Children.Add(slot);
            }
            var lens = new Ellipse { Width = 155, Height = 155, Fill = dark, Stroke = metal, StrokeThickness = 6 };
            Canvas.SetLeft(lens, 205); Canvas.SetTop(lens, 100); canvas.Children.Add(lens);
            var glass = new Ellipse { Width = 112, Height = 112, Fill = new RadialGradientBrush(Color.FromRgb(57, 102, 133), Color.FromRgb(5, 10, 18)), Stroke = accentBrush, StrokeThickness = 3 };
            Canvas.SetLeft(glass, 226.5); Canvas.SetTop(glass, 121.5); canvas.Children.Add(glass);
        }
        else
        {
            double bodyW = integratedGrip ? 395 : (gfx ? 365 : 345);
            double bodyH = integratedGrip ? 215 : (gfx ? 190 : 175);
            double bodyX = (560 - bodyW) / 2;
            double bodyY = integratedGrip ? 70 : 92;

            var body = new Border { Width = bodyW, Height = bodyH, Background = bodyBrush, BorderBrush = metal, BorderThickness = new Thickness(2), CornerRadius = new CornerRadius(dslr ? 18 : 13) };
            Canvas.SetLeft(body, bodyX); Canvas.SetTop(body, bodyY); canvas.Children.Add(body);

            var grip = new Border { Width = integratedGrip ? 90 : 72, Height = integratedGrip ? 205 : 150, Background = new SolidColorBrush(Color.FromRgb(23, 26, 30)), BorderBrush = metal, BorderThickness = new Thickness(1.5), CornerRadius = new CornerRadius(18, 28, 18, 18) };
            Canvas.SetLeft(grip, bodyX + bodyW - (integratedGrip ? 72 : 56)); Canvas.SetTop(grip, bodyY + (integratedGrip ? 5 : 15)); canvas.Children.Add(grip);

            double humpW = dslr ? 125 : 108;
            double humpH = dslr ? 70 : 52;
            var hump = new Border { Width = humpW, Height = humpH, Background = new SolidColorBrush(Color.FromRgb(28, 31, 35)), BorderBrush = metal, BorderThickness = new Thickness(1.5), CornerRadius = new CornerRadius(14, 14, 5, 5) };
            Canvas.SetLeft(hump, 280 - humpW / 2); Canvas.SetTop(hump, bodyY - humpH + 12); canvas.Children.Add(hump);

            var hotshoe = new Rectangle { Width = 55, Height = 8, RadiusX = 2, RadiusY = 2, Fill = metal };
            Canvas.SetLeft(hotshoe, 252.5); Canvas.SetTop(hotshoe, bodyY - humpH + 6); canvas.Children.Add(hotshoe);

            double lensSize = gfx ? 180 : (dslr ? 172 : 162);
            var lens = new Ellipse { Width = lensSize, Height = lensSize, Fill = dark, Stroke = metal, StrokeThickness = 7 };
            Canvas.SetLeft(lens, 280 - lensSize / 2); Canvas.SetTop(lens, bodyY + bodyH / 2 - lensSize / 2); canvas.Children.Add(lens);
            var ring = new Ellipse { Width = lensSize - 28, Height = lensSize - 28, Fill = new SolidColorBrush(Color.FromRgb(18, 23, 28)), Stroke = accentBrush, StrokeThickness = 3 };
            Canvas.SetLeft(ring, 280 - (lensSize - 28) / 2); Canvas.SetTop(ring, bodyY + bodyH / 2 - (lensSize - 28) / 2); canvas.Children.Add(ring);
            var glass = new Ellipse { Width = lensSize - 62, Height = lensSize - 62, Fill = new RadialGradientBrush(Color.FromRgb(65, 121, 153), Color.FromRgb(4, 8, 14)) };
            Canvas.SetLeft(glass, 280 - (lensSize - 62) / 2); Canvas.SetTop(glass, bodyY + bodyH / 2 - (lensSize - 62) / 2); canvas.Children.Add(glass);
            var shine = new Ellipse { Width = 32, Height = 18, Fill = new SolidColorBrush(Color.FromArgb(120, 190, 225, 255)) };
            Canvas.SetLeft(shine, 245); Canvas.SetTop(shine, bodyY + 62); canvas.Children.Add(shine);

            var shutter = new Ellipse { Width = 23, Height = 23, Fill = accentBrush, Stroke = Brushes.White, StrokeThickness = 1 };
            Canvas.SetLeft(shutter, bodyX + bodyW - 78); Canvas.SetTop(shutter, bodyY + 18); canvas.Children.Add(shutter);
        }

        var brand = new TextBlock { Text = cp.Brand.ToUpperInvariant(), Foreground = Brushes.White, FontSize = 18, FontWeight = FontWeights.Bold, LetterSpacing = 1.2 };
        Canvas.SetLeft(brand, 28); Canvas.SetTop(brand, 18); canvas.Children.Add(brand);
        var accentLine = new Rectangle { Width = 145, Height = 5, Fill = accentBrush, RadiusX = 2, RadiusY = 2 };
        Canvas.SetLeft(accentLine, 28); Canvas.SetTop(accentLine, 47); canvas.Children.Add(accentLine);

        var model = new TextBlock { Text = cp.Model, Foreground = Brushes.White, FontSize = 34, FontWeight = FontWeights.SemiBold };
        Canvas.SetLeft(model, 28); Canvas.SetTop(model, 263); canvas.Children.Add(model);
        var mode = new TextBlock { Text = cp.Mode, Foreground = new SolidColorBrush(Color.FromRgb(169, 179, 190)), FontSize = 13 };
        Canvas.SetLeft(mode, 30); Canvas.SetTop(mode, 306); canvas.Children.Add(mode);

        var selected = new Border { Background = new SolidColorBrush(Color.FromArgb(215, 17, 22, 29)), BorderBrush = accentBrush, BorderThickness = new Thickness(1), CornerRadius = new CornerRadius(7), Padding = new Thickness(9, 4, 9, 4), Child = new TextBlock { Text = "SELECTED CAMERA", Foreground = Brushes.White, FontSize = 11, FontWeight = FontWeights.Bold } };
        Canvas.SetLeft(selected, 398); Canvas.SetTop(selected, 18); canvas.Children.Add(selected);
    }
}
