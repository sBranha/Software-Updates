using System.Globalization;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace CameraAutoUpload.Windows;

public static class EditorRenderer
{
    public static Task RenderJpegAsync(EditorDocument document, string photoPath, string outputPath)
    {
        return System.Windows.Application.Current.Dispatcher.InvokeAsync(() => Render(document, photoPath, outputPath)).Task;
    }

    public static void Render(EditorDocument document, string photoPath, string outputPath)
    {
        var visual = new DrawingVisual();
        using (var dc = visual.RenderOpen())
        {
            dc.DrawRectangle(Brushes.Black, null, new Rect(0, 0, document.CanvasWidth, document.CanvasHeight));
            DrawPhoto(dc, document, photoPath);
            foreach (var layer in document.Layers.Where(x => x.Visible))
            {
                if (layer.Kind == LayerKind.Image) DrawImageLayer(dc, layer);
                else DrawTextLayer(dc, layer);
            }
        }
        var bmp = new RenderTargetBitmap((int)Math.Round(document.CanvasWidth), (int)Math.Round(document.CanvasHeight), 96, 96, PixelFormats.Pbgra32);
        bmp.Render(visual);
        Directory.CreateDirectory(Path.GetDirectoryName(outputPath)!);
        var encoder = new JpegBitmapEncoder { QualityLevel = 96 };
        encoder.Frames.Add(BitmapFrame.Create(bmp));
        using var fs = new FileStream(outputPath, FileMode.Create, FileAccess.Write, FileShare.Read);
        encoder.Save(fs);
    }

    static void DrawPhoto(DrawingContext dc, EditorDocument d, string path)
    {
        if (!File.Exists(path)) return;
        var p = d.Photo;
        var src = ImageTools.HueShift(ImageTools.LoadBitmap(path), p.Hue);
        var dst = new Rect(p.X, p.Y, p.Width, p.Height);
        var clip = new Rect(dst.X + p.CropLeft * dst.Width, dst.Y + p.CropTop * dst.Height,
            Math.Max(1, (p.CropRight - p.CropLeft) * dst.Width), Math.Max(1, (p.CropBottom - p.CropTop) * dst.Height));
        dc.PushOpacity(Math.Clamp(p.Opacity, 0, 1));
        dc.PushTransform(new RotateTransform(p.Rotation, dst.X + dst.Width / 2, dst.Y + dst.Height / 2));
        dc.PushClip(new RectangleGeometry(clip));
        dc.DrawImage(src, dst);
        dc.Pop(); dc.Pop(); dc.Pop();
    }

    static void DrawImageLayer(DrawingContext dc, LayerModel layer)
    {
        if (!File.Exists(layer.AssetPath)) return;
        var src = ImageTools.HueShift(ImageTools.LoadBitmap(layer.AssetPath), layer.Hue);
        var dst = new Rect(layer.X, layer.Y, layer.Width, layer.Height);
        var clip = new Rect(dst.X + layer.CropLeft * dst.Width, dst.Y + layer.CropTop * dst.Height,
            Math.Max(1, (layer.CropRight - layer.CropLeft) * dst.Width), Math.Max(1, (layer.CropBottom - layer.CropTop) * dst.Height));
        dc.PushOpacity(Math.Clamp(layer.Opacity, 0, 1));
        dc.PushTransform(new RotateTransform(layer.Rotation, dst.X + dst.Width / 2, dst.Y + dst.Height / 2));
        dc.PushClip(new RectangleGeometry(clip));
        dc.DrawImage(src, dst);
        dc.Pop(); dc.Pop(); dc.Pop();
    }

    static void DrawTextLayer(DrawingContext dc, LayerModel l)
    {
        var family = new FontFamily(string.IsNullOrWhiteSpace(l.FontFamily) ? "Arial" : l.FontFamily);
        var typeface = new Typeface(family, l.Italic ? FontStyles.Italic : FontStyles.Normal, l.Bold ? FontWeights.Bold : FontWeights.Normal, FontStretches.Normal);
        var fill = BrushFrom(l.TextColor, Brushes.White);
        var ft = new FormattedText(l.Text ?? "", CultureInfo.CurrentCulture, FlowDirection.LeftToRight, typeface,
            Math.Max(4, l.FontSize), fill, 1.0)
        {
            MaxTextWidth = Math.Max(1, l.Width),
            MaxTextHeight = Math.Max(1, l.Height),
            Trimming = TextTrimming.None,
            TextAlignment = l.Align.Equals("Right", StringComparison.OrdinalIgnoreCase) ? TextAlignment.Right :
                            l.Align.Equals("Left", StringComparison.OrdinalIgnoreCase) ? TextAlignment.Left : TextAlignment.Center
        };
        var originX = ft.TextAlignment == TextAlignment.Center ? l.X + l.Width / 2 : ft.TextAlignment == TextAlignment.Right ? l.X + l.Width : l.X;
        var geometry = ft.BuildGeometry(new Point(originX, l.Y));
        var cx = l.X + l.Width / 2; var cy = l.Y + l.Height / 2;
        dc.PushOpacity(Math.Clamp(l.Opacity, 0, 1));
        dc.PushTransform(new RotateTransform(l.Rotation, cx, cy));
        if (l.ShadowRadius > 0.01 || Math.Abs(l.ShadowX) > 0.01 || Math.Abs(l.ShadowY) > 0.01)
        {
            dc.PushTransform(new TranslateTransform(l.ShadowX, l.ShadowY));
            dc.DrawGeometry(BrushFrom(l.ShadowColor, new SolidColorBrush(Color.FromArgb(160, 0, 0, 0))), null, geometry);
            dc.Pop();
        }
        Pen? pen = l.StrokeWidth > 0.01 ? new Pen(BrushFrom(l.StrokeColor, Brushes.Black), l.StrokeWidth) : null;
        dc.DrawGeometry(fill, pen, geometry);
        dc.Pop(); dc.Pop();
    }

    public static Brush BrushFrom(string hex, Brush fallback)
    {
        try
        {
            var obj = ColorConverter.ConvertFromString(hex);
            if (obj is Color c) return new SolidColorBrush(c);
        }
        catch { }
        return fallback;
    }
}
