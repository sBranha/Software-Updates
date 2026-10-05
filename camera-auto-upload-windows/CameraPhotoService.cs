using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Text.Json;
using System.Threading.Tasks;

namespace CameraAutoUpload.Windows;

public static class CameraPhotoService
{
    static readonly HttpClient Http = CreateClient();

    // Clean product photos for models we explicitly know. Other models use the
    // Wikimedia Commons model search below and are cached after the first load.
    static readonly Dictionary<string, string> Exact = new(StringComparer.OrdinalIgnoreCase)
    {
        ["Nikon|Z8"] = "https://www.castlecameras.co.uk/images/products/large/17312.jpg",
        ["Sony|α7 III"] = "https://www.cameraworld.co.uk/media/catalog/product/cache/3ee916212870d32cfbe3be35bdad97d6/a/7/a7_iii_1cejejhdeq_.jpg",
        ["Sony|α9 III"] = "https://cdn-tp2.mozu.com/28945-m4/cms/files/L2517602.jpg",
        ["Nikon|Zf"] = "https://www.cameraland.nl/media/catalog/product/cache/1/image/1800x/040ec09b1e35df139433887a97daa66f/1/6/1695167434_1788062_2.jpg",
        ["Nikon|Z50II"] = "https://cdn.verk.net/images/25/2_968534-1500x1500.jpeg",
        ["Nikon|Z6III"] = "https://cdn.uniquephoto.com/resources/uniquephoto/images/products/processed/NKD2040.superZoom.a.jpg",
        ["Nikon|Z6II"] = "https://cdn.grupoelcorteingles.es/SGFM/dctm/MEDIA03/202012/14/00110116000299____3__1200x1200.jpg",
        ["Nikon|Z6"] = "https://images.tcdn.com.br/img/img_prod/103912/nikon_z_6_corpo_24_5_mp_2287_1_a52b789218849b22a114da025f97c7a8.jpg",
        ["Canon|EOS R5"] = "https://tridist.si/img/products/CANON-EOS-R5-body_im1.png",
        ["Canon|EOS R5 Mark II"] = "https://cdn11.bigcommerce.com/s-745x53acpn/images/stencil/1280x1280/products/10362/31585/EOSR5MII_2_copy__37892.1764955318.jpg?c=2",
        ["Canon|EOS R6 Mark II"] = "https://i.ebayimg.com/images/g/Qi8AAeSw2jdpdFOg/s-l1200.jpg",
        ["Sony|α1"] = "https://media.foto-erhardt.de/images/product_images/original_images/333/sony-alpha-1-ilce-1-gehause-161168388633320304.jpg"
    };

    static HttpClient CreateClient()
    {
        var c = new HttpClient { Timeout = TimeSpan.FromSeconds(15) };
        c.DefaultRequestHeaders.UserAgent.ParseAdd("CameraAutoUpload-Windows/1.0.6");
        return c;
    }

    static string CacheRoot
    {
        get
        {
            var p = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "CameraAutoUpload", "CameraPhotos");
            Directory.CreateDirectory(p);
            return p;
        }
    }

    public static async Task<string?> GetPhotoAsync(CameraProfile cp)
    {
        var stem = SafeName(cp.Brand + "_" + cp.Model);
        var cached = Directory.EnumerateFiles(CacheRoot, stem + ".*").FirstOrDefault(x => IsSupportedExtension(Path.GetExtension(x)));
        if (!string.IsNullOrWhiteSpace(cached) && File.Exists(cached)) return cached;

        var key = cp.Brand + "|" + cp.Model;
        if (Exact.TryGetValue(key, out var exact))
        {
            var path = await DownloadSupportedAsync(exact, stem).ConfigureAwait(false);
            if (path != null) return path;
        }

        var commons = await FindCommonsImageAsync(cp).ConfigureAwait(false);
        if (!string.IsNullOrWhiteSpace(commons.url))
        {
            var path = await DownloadSupportedAsync(commons.url!, stem, commons.ext).ConfigureAwait(false);
            if (path != null) return path;
        }

        return null;
    }

    static async Task<(string? url, string? ext)> FindCommonsImageAsync(CameraProfile cp)
    {
        try
        {
            var modelQuery = cp.Model.Replace("α", "Alpha ");
            var q = Uri.EscapeDataString($"{cp.Brand} {modelQuery} camera");
            var api = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrnamespace=6&gsrlimit=12&gsrsearch=" + q + "&prop=imageinfo&iiprop=url%7Cmime&iiurlwidth=1400&format=json&formatversion=2";
            var json = await Http.GetStringAsync(api).ConfigureAwait(false);
            using var doc = JsonDocument.Parse(json);
            if (!doc.RootElement.TryGetProperty("query", out var query) || !query.TryGetProperty("pages", out var pages) || pages.ValueKind != JsonValueKind.Array) return (null, null);

            var wanted = Normalize(cp.Brand + cp.Model);
            var wantedModel = Normalize(cp.Model.Replace("α", "alpha"));
            var candidates = new List<(int index, string url, string ext, string title)>();
            foreach (var page in pages.EnumerateArray())
            {
                var title = page.TryGetProperty("title", out var t) ? t.GetString() ?? "" : "";
                var index = page.TryGetProperty("index", out var ix) && ix.TryGetInt32(out var n) ? n : 9999;
                if (!page.TryGetProperty("imageinfo", out var infos) || infos.ValueKind != JsonValueKind.Array || infos.GetArrayLength() == 0) continue;
                var info = infos[0];
                var mime = info.TryGetProperty("mime", out var m) ? m.GetString() ?? "" : "";
                var ext = mime.Equals("image/png", StringComparison.OrdinalIgnoreCase) ? ".png" : mime.Equals("image/jpeg", StringComparison.OrdinalIgnoreCase) ? ".jpg" : "";
                if (ext.Length == 0) continue;
                string? url = null;
                if (info.TryGetProperty("thumburl", out var tu)) url = tu.GetString();
                if (string.IsNullOrWhiteSpace(url) && info.TryGetProperty("url", out var ou)) url = ou.GetString();
                if (string.IsNullOrWhiteSpace(url)) continue;
                var nt = Normalize(title.Replace("α", "alpha"));
                if (!nt.Contains(Normalize(cp.Brand))) continue;
                // Prefer exact model names, but still allow the top search hit when Commons
                // spells Alpha/A7 or Mark names a little differently.
                var score = nt.Contains(wanted) || nt.Contains(wantedModel) ? index - 1000 : index;
                candidates.Add((score, url!, ext, title));
            }
            var best = candidates.OrderBy(x => x.index).FirstOrDefault();
            return string.IsNullOrWhiteSpace(best.url) ? (null, null) : (best.url, best.ext);
        }
        catch { return (null, null); }
    }

    static async Task<string?> DownloadSupportedAsync(string url, string stem, string? forcedExt = null)
    {
        try
        {
            using var resp = await Http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead).ConfigureAwait(false);
            if (!resp.IsSuccessStatusCode) return null;
            var media = resp.Content.Headers.ContentType?.MediaType ?? "";
            var ext = forcedExt;
            if (string.IsNullOrWhiteSpace(ext))
            {
                if (media.Contains("png", StringComparison.OrdinalIgnoreCase)) ext = ".png";
                else if (media.Contains("jpeg", StringComparison.OrdinalIgnoreCase) || media.Contains("jpg", StringComparison.OrdinalIgnoreCase)) ext = ".jpg";
                else
                {
                    var u = url.Split('?')[0].ToLowerInvariant();
                    if (u.EndsWith(".png")) ext = ".png";
                    else if (u.EndsWith(".jpg") || u.EndsWith(".jpeg")) ext = ".jpg";
                }
            }
            if (ext is not ".jpg" and not ".png") return null;
            var bytes = await resp.Content.ReadAsByteArrayAsync().ConfigureAwait(false);
            if (bytes.Length < 5000) return null;
            var path = Path.Combine(CacheRoot, stem + ext);
            await File.WriteAllBytesAsync(path, bytes).ConfigureAwait(false);
            return path;
        }
        catch { return null; }
    }

    static bool IsSupportedExtension(string ext) => ext.Equals(".jpg", StringComparison.OrdinalIgnoreCase) || ext.Equals(".png", StringComparison.OrdinalIgnoreCase);
    static string SafeName(string value) => string.Concat(value.Select(ch => char.IsLetterOrDigit(ch) ? ch : '_'));
    static string Normalize(string value) => new(value.ToLowerInvariant().Where(char.IsLetterOrDigit).ToArray());
}
