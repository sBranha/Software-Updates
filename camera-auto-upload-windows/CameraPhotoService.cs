using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Text.RegularExpressions;
using System.Threading.Tasks;

namespace CameraAutoUpload.Windows;

public static class CameraPhotoService
{
    static readonly HttpClient Http = CreateClient();

    // Only official manufacturer product/lineup pages are allowed here.
    // Windows first checks the curated/bundled asset made at build time, then
    // may refresh from the official page when internet is available.
    static readonly Dictionary<string, string> OfficialPages = new(StringComparer.OrdinalIgnoreCase)
    {
        // Nikon
        ["Nikon|Z9"] = "https://imaging.nikon.com/imaging/lineup/mirrorless/z9/",
        ["Nikon|Z8"] = "https://www.nikonusa.com/p/z-8/1698/overview",
        ["Nikon|Z6III"] = "https://imaging.nikon.com/imaging/lineup/mirrorless/z6_3/",
        ["Nikon|Z5II"] = "https://imaging.nikon.com/imaging/lineup/mirrorless/z5_2/",
        ["Nikon|Zf"] = "https://imaging.nikon.com/imaging/lineup/mirrorless/zf/",
        ["Nikon|Z50II"] = "https://imaging.nikon.com/imaging/lineup/mirrorless/z50_2/",
        ["Nikon|ZR"] = "https://imaging.nikon.com/imaging/lineup/mirrorless/zr/",
        ["Nikon|Z7II"] = "https://imaging.nikon.com/imaging/lineup/mirrorless/z7_2/",
        ["Nikon|Z6II"] = "https://imaging.nikon.com/imaging/lineup/mirrorless/z6_2/",
        ["Nikon|Z7"] = "https://imaging.nikon.com/imaging/lineup/mirrorless/z7/",
        ["Nikon|Z6"] = "https://imaging.nikon.com/imaging/lineup/mirrorless/z6/",
        ["Nikon|D850"] = "https://imaging.nikon.com/imaging/lineup/dslr/d850/",
        ["Nikon|D6"] = "https://imaging.nikon.com/imaging/lineup/dslr/d6/",

        // Canon
        ["Canon|EOS R1"] = "https://www.usa.canon.com/shop/p/eos-r1",
        ["Canon|EOS R5 Mark II"] = "https://www.usa.canon.com/shop/p/eos-r5-mark-ii",
        ["Canon|EOS R3"] = "https://www.usa.canon.com/shop/p/eos-r3",
        ["Canon|EOS R5"] = "https://www.usa.canon.com/shop/p/eos-r5",
        ["Canon|EOS R6 Mark III"] = "https://www.usa.canon.com/shop/p/eos-r6-mark-iii",
        ["Canon|EOS R6 Mark II"] = "https://www.usa.canon.com/shop/p/eos-r6-mark-ii",
        ["Canon|EOS R6 V"] = "https://www.usa.canon.com/shop/p/eos-r6-v",
        ["Canon|EOS R6"] = "https://www.usa.canon.com/shop/p/eos-r6",
        ["Canon|EOS-1D X Mark III"] = "https://www.usa.canon.com/shop/p/eos-1d-x-mark-iii",
        ["Canon|EOS-1D X Mark II"] = "https://www.usa.canon.com/shop/p/eos-1d-x-mark-ii",

        // Sony
        ["Sony|α7 V"] = "https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce7m5-b",
        ["Sony|α1 II"] = "https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce1m2-b",
        ["Sony|α1"] = "https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce1-b",
        ["Sony|α9 III"] = "https://electronics.sony.com/imaging/interchangeable-lens-cameras/all-interchangeable-lens-cameras/p/ilce9m3b",
        ["Sony|α9 II"] = "https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce9m2-b",
        ["Sony|α9"] = "https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce9-b",
        ["Sony|α7 IV"] = "https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce7m4-b",
        ["Sony|α7 III"] = "https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce7m3-b",
        ["Sony|α7S III"] = "https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce7sm3-b",
        ["Sony|α7R V"] = "https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce7rm5-b",
        ["Sony|α7R IV"] = "https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce7rm4-b",
        ["Sony|α7R IVA"] = "https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce7rm4a-b",
        ["Sony|α7R III"] = "https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce7rm3-b",
        ["Sony|α7R IIIA"] = "https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce7rm3a-b",
        ["Sony|α7C II"] = "https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce7cm2-b",
        ["Sony|α7CR"] = "https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce7cr-b",
        ["Sony|α7C"] = "https://electronics.sony.com/imaging/interchangeable-lens-cameras/full-frame/p/ilce7c-b",
        ["Sony|FX3"] = "https://electronics.sony.com/imaging/cinema-line-cameras/all-cinema-line-cameras/p/ilmefx3-b",
        ["Sony|FX30"] = "https://electronics.sony.com/imaging/cinema-line-cameras/all-cinema-line-cameras/p/ilmefx30-b",

        // Fujifilm
        ["Fujifilm|GFX100 II"] = "https://fujifilm-x.com/en-us/products/cameras/gfx100-ii/",
        ["Fujifilm|GFX100S II"] = "https://fujifilm-x.com/en-us/products/cameras/gfx100s-ii/",
        ["Fujifilm|X-H2"] = "https://fujifilm-x.com/en-us/products/cameras/x-h2/",
        ["Fujifilm|X-H2S"] = "https://fujifilm-x.com/en-us/products/cameras/x-h2s/"
    };

    static HttpClient CreateClient()
    {
        var c = new HttpClient { Timeout = TimeSpan.FromSeconds(18) };
        c.DefaultRequestHeaders.UserAgent.ParseAdd("Mozilla/5.0 CameraAutoUpload-Windows/1.0.7");
        return c;
    }

    static string CacheRoot
    {
        get
        {
            // New folder intentionally ignores any random/retailer photos cached by 1.0.6.
            var p = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "CameraAutoUpload", "OfficialCameraPhotosV107");
            Directory.CreateDirectory(p);
            return p;
        }
    }

    static string BundledRoot => Path.Combine(AppContext.BaseDirectory, "CameraAssets");

    public static async Task<string?> GetPhotoAsync(CameraProfile cp)
    {
        var stem = SafeName(cp.Brand + "_" + cp.Model);

        // Prefer curated assets shipped with the installer. Nikon Z8 is the exact
        // same professional photo already used in the Android app.
        var bundled = FindImage(BundledRoot, stem);
        if (bundled != null) return bundled;

        var cached = FindImage(CacheRoot, stem);
        if (cached != null) return cached;

        var key = cp.Brand + "|" + cp.Model;
        if (!OfficialPages.TryGetValue(key, out var productPage)) return null;
        if (!IsOfficialManufacturerUrl(cp.Brand, productPage)) return null;

        try
        {
            var htmlText = await Http.GetStringAsync(productPage).ConfigureAwait(false);
            var imageUrl = ExtractHeroImage(productPage, htmlText);
            if (string.IsNullOrWhiteSpace(imageUrl) || !IsOfficialManufacturerUrl(cp.Brand, imageUrl!))
            {
                // Some manufacturers serve product images from their own CDN host.
                // Allow the page's HTTPS image only when its host belongs to the same
                // manufacturer's known web family.
                if (string.IsNullOrWhiteSpace(imageUrl) || !IsKnownManufacturerCdn(cp.Brand, imageUrl!)) return null;
            }

            return await DownloadOfficialImageAsync(imageUrl!, stem).ConfigureAwait(false);
        }
        catch { return null; }
    }

    static string? FindImage(string root, string stem)
    {
        try
        {
            if (!Directory.Exists(root)) return null;
            foreach (var ext in new[] { ".png", ".jpg", ".jpeg" })
            {
                var p = Path.Combine(root, stem + ext);
                if (File.Exists(p)) return p;
            }
        }
        catch { }
        return null;
    }

    static string? ExtractHeroImage(string pageUrl, string htmlText)
    {
        var patterns = new[]
        {
            "<meta[^>]+property=[\\\"']og:image[\\\"'][^>]+content=[\\\"']([^\\\"']+)",
            "<meta[^>]+content=[\\\"']([^\\\"']+)[\\\"'][^>]+property=[\\\"']og:image[\\\"']",
            "<meta[^>]+name=[\\\"']twitter:image(?::src)?[\\\"'][^>]+content=[\\\"']([^\\\"']+)"
        };
        foreach (var pattern in patterns)
        {
            var m = Regex.Match(htmlText, pattern, RegexOptions.IgnoreCase | RegexOptions.Singleline);
            if (!m.Success) continue;
            var raw = WebUtility.HtmlDecode(m.Groups[1].Value.Trim());
            if (raw.StartsWith("//")) raw = "https:" + raw;
            if (Uri.TryCreate(raw, UriKind.Absolute, out var absolute)) return absolute.ToString();
            if (Uri.TryCreate(new Uri(pageUrl), raw, out var relative)) return relative.ToString();
        }
        return null;
    }

    static async Task<string?> DownloadOfficialImageAsync(string url, string stem)
    {
        try
        {
            using var resp = await Http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead).ConfigureAwait(false);
            if (!resp.IsSuccessStatusCode) return null;
            var media = resp.Content.Headers.ContentType?.MediaType ?? "";
            string? ext = media.Contains("png", StringComparison.OrdinalIgnoreCase) ? ".png"
                : media.Contains("jpeg", StringComparison.OrdinalIgnoreCase) || media.Contains("jpg", StringComparison.OrdinalIgnoreCase) ? ".jpg"
                : null;
            if (ext == null) return null; // WPF does not reliably decode WebP without an extra codec.
            var bytes = await resp.Content.ReadAsByteArrayAsync().ConfigureAwait(false);
            if (bytes.Length < 8000) return null;
            var path = Path.Combine(CacheRoot, stem + ext);
            await File.WriteAllBytesAsync(path, bytes).ConfigureAwait(false);
            return path;
        }
        catch { return null; }
    }

    static bool IsOfficialManufacturerUrl(string brand, string url)
    {
        if (!Uri.TryCreate(url, UriKind.Absolute, out var u) || u.Scheme != Uri.UriSchemeHttps) return false;
        var h = u.Host.ToLowerInvariant();
        return brand switch
        {
            "Nikon" => h.EndsWith("nikon.com") || h.EndsWith("nikonusa.com"),
            "Sony" => h.EndsWith("sony.com"),
            "Canon" => h.EndsWith("canon.com"),
            "Fujifilm" => h.EndsWith("fujifilm-x.com") || h.EndsWith("fujifilm.com"),
            _ => false
        };
    }

    static bool IsKnownManufacturerCdn(string brand, string url)
    {
        if (!Uri.TryCreate(url, UriKind.Absolute, out var u) || u.Scheme != Uri.UriSchemeHttps) return false;
        var h = u.Host.ToLowerInvariant();
        return brand switch
        {
            "Nikon" => h.Contains("nikon") || h.EndsWith("scene7.com"),
            "Sony" => h.Contains("sony") || h.Contains("scene7") || h.Contains("akamai"),
            "Canon" => h.Contains("canon") || h.Contains("scene7"),
            "Fujifilm" => h.Contains("fujifilm") || h.Contains("fujifilm-x"),
            _ => false
        };
    }

    static string SafeName(string value) => string.Concat(value.Select(ch => char.IsLetterOrDigit(ch) ? ch : '_'));
}
