using System.Text.Json;
using System.Text.Json.Serialization;

namespace CameraAutoUpload.Windows;

public enum LayerKind { Text, Image }
public enum UploadChoice { OriginalOnly, EditedOnly, Both }

public sealed class LayerModel
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public LayerKind Kind { get; set; }
    public string Name { get; set; } = "Layer";
    public double X { get; set; } = 100;
    public double Y { get; set; } = 100;
    public double Width { get; set; } = 300;
    public double Height { get; set; } = 120;
    public double Rotation { get; set; }
    public double Opacity { get; set; } = 1;
    public bool Visible { get; set; } = true;
    public string Text { get; set; } = "TEXT";
    public string FontFamily { get; set; } = "Arial";
    public double FontSize { get; set; } = 72;
    public bool Bold { get; set; } = true;
    public bool Italic { get; set; }
    public string TextColor { get; set; } = "#FFFFFFFF";
    public string StrokeColor { get; set; } = "#FF000000";
    public double StrokeWidth { get; set; }
    public string ShadowColor { get; set; } = "#AA000000";
    public double ShadowRadius { get; set; }
    public double ShadowX { get; set; } = 4;
    public double ShadowY { get; set; } = 4;
    public string Align { get; set; } = "Center";
    public string AssetPath { get; set; } = "";
    public double Hue { get; set; }
    public double CropLeft { get; set; }
    public double CropTop { get; set; }
    public double CropRight { get; set; } = 1;
    public double CropBottom { get; set; } = 1;
}

public sealed class PhotoState
{
    public double X { get; set; }
    public double Y { get; set; }
    public double Width { get; set; } = 1080;
    public double Height { get; set; } = 1350;
    public double Rotation { get; set; }
    public double Opacity { get; set; } = 1;
    public double Hue { get; set; }
    public double CropLeft { get; set; }
    public double CropTop { get; set; }
    public double CropRight { get; set; } = 1;
    public double CropBottom { get; set; } = 1;
}

public sealed class EditorDocument
{
    public string Name { get; set; } = "My Layout";
    public double CanvasWidth { get; set; } = 1080;
    public double CanvasHeight { get; set; } = 1350;
    public PhotoState Photo { get; set; } = new();
    public List<LayerModel> Layers { get; set; } = new();

    public EditorDocument Clone() => JsonSerializer.Deserialize<EditorDocument>(JsonSerializer.Serialize(this, JsonOptions.Default), JsonOptions.Default) ?? new();
}

public sealed class AppSettings
{
    public int FtpPort { get; set; } = 2121;
    public string FtpUser { get; set; } = "nikon";
    public string FtpPassword { get; set; } = "nikon";
    public string WatchFolder { get; set; } = "";
    public bool AutoProcess { get; set; } = true;
    public bool LayoutLocked { get; set; }
    public bool FlickrEnabled { get; set; }
    public UploadChoice UploadChoice { get; set; } = UploadChoice.EditedOnly;
    public string FlickrApiKey { get; set; } = "";
    public string FlickrApiSecret { get; set; } = "";
    public string FlickrToken { get; set; } = "";
    public string FlickrTokenSecret { get; set; } = "";
    public string OutputRoot { get; set; } = AppPaths.DefaultOutputRoot;
}

public sealed class UploadItem
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public string FilePath { get; set; } = "";
    public DateTime AddedUtc { get; set; } = DateTime.UtcNow;
    public int Attempts { get; set; }
    public string LastError { get; set; } = "";
}

public static class AppPaths
{
    public static readonly string Root = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "CameraAutoUpload");
    public static readonly string SettingsFile = Path.Combine(Root, "settings.json");
    public static readonly string TemplateFile = Path.Combine(Root, "locked-layout.json");
    public static readonly string QueueFile = Path.Combine(Root, "upload-queue.json");
    public static readonly string AssetFolder = Path.Combine(Root, "Assets");
    public static readonly string DefaultOutputRoot = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.MyPictures), "Camera Auto Upload");

    public static void Ensure()
    {
        Directory.CreateDirectory(Root);
        Directory.CreateDirectory(AssetFolder);
        Directory.CreateDirectory(DefaultOutputRoot);
    }
}

public static class JsonOptions
{
    public static readonly JsonSerializerOptions Default = new()
    {
        WriteIndented = true,
        PropertyNameCaseInsensitive = true,
        Converters = { new JsonStringEnumConverter() }
    };
}
