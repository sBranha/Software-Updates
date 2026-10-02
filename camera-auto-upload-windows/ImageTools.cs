using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace CameraAutoUpload.Windows;

public static class ImageTools
{
    public static BitmapSource LoadBitmap(string path)
    {
        var bi = new BitmapImage();
        bi.BeginInit();
        bi.CacheOption = BitmapCacheOption.OnLoad;
        bi.UriSource = new Uri(path, UriKind.Absolute);
        bi.EndInit();
        bi.Freeze();
        return bi;
    }

    public static BitmapSource HueShift(BitmapSource source, double degrees)
    {
        if (Math.Abs(degrees) < 0.01) return source;
        var conv = new FormatConvertedBitmap(source, PixelFormats.Bgra32, null, 0);
        var stride = conv.PixelWidth * 4;
        var pixels = new byte[stride * conv.PixelHeight];
        conv.CopyPixels(pixels, stride, 0);
        var shift = degrees / 360.0;
        for (var i = 0; i < pixels.Length; i += 4)
        {
            var b = pixels[i] / 255.0;
            var g = pixels[i + 1] / 255.0;
            var r = pixels[i + 2] / 255.0;
            RgbToHsv(r, g, b, out var h, out var s, out var v);
            h = (h + shift) % 1.0; if (h < 0) h += 1.0;
            HsvToRgb(h, s, v, out r, out g, out b);
            pixels[i] = (byte)Math.Clamp(Math.Round(b * 255), 0, 255);
            pixels[i + 1] = (byte)Math.Clamp(Math.Round(g * 255), 0, 255);
            pixels[i + 2] = (byte)Math.Clamp(Math.Round(r * 255), 0, 255);
        }
        var wb = new WriteableBitmap(conv.PixelWidth, conv.PixelHeight, source.DpiX, source.DpiY, PixelFormats.Bgra32, null);
        wb.WritePixels(new System.Windows.Int32Rect(0, 0, wb.PixelWidth, wb.PixelHeight), pixels, stride, 0);
        wb.Freeze();
        return wb;
    }

    static void RgbToHsv(double r, double g, double b, out double h, out double s, out double v)
    {
        var max = Math.Max(r, Math.Max(g, b)); var min = Math.Min(r, Math.Min(g, b)); var d = max - min;
        v = max; s = max <= 0 ? 0 : d / max;
        if (d <= 0) { h = 0; return; }
        if (max == r) h = ((g - b) / d) % 6;
        else if (max == g) h = (b - r) / d + 2;
        else h = (r - g) / d + 4;
        h /= 6; if (h < 0) h += 1;
    }

    static void HsvToRgb(double h, double s, double v, out double r, out double g, out double b)
    {
        var i = (int)Math.Floor(h * 6); var f = h * 6 - i;
        var p = v * (1 - s); var q = v * (1 - f * s); var t = v * (1 - (1 - f) * s);
        switch (i % 6)
        {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
    }
}
