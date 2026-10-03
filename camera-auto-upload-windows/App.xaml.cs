using System;
using System.IO;
using System.Windows;
using System.Windows.Threading;

namespace CameraAutoUpload.Windows;

public partial class App : System.Windows.Application
{
    public App()
    {
        DispatcherUnhandledException += OnDispatcherUnhandledException;
        AppDomain.CurrentDomain.UnhandledException += OnUnhandledException;
    }

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);
        try
        {
            WriteStartupLog("Starting Camera Auto Upload Windows 1.0.2");
            var window = new MainWindow();
            MainWindow = window;
            window.Show();
            WriteStartupLog("Main window opened successfully");
        }
        catch (Exception ex)
        {
            var path = WriteCrashLog("STARTUP", ex);
            System.Windows.MessageBox.Show(
                "Camera Auto Upload could not start.\n\nA crash log was saved here:\n" + path + "\n\n" + ex.Message,
                "Camera Auto Upload - Startup Error",
                MessageBoxButton.OK,
                MessageBoxImage.Error);
            Shutdown(1);
        }
    }

    void OnDispatcherUnhandledException(object sender, DispatcherUnhandledExceptionEventArgs e)
    {
        var path = WriteCrashLog("UI", e.Exception);
        System.Windows.MessageBox.Show(
            "Camera Auto Upload hit an unexpected error.\n\nCrash log:\n" + path + "\n\n" + e.Exception.Message,
            "Camera Auto Upload - Error",
            MessageBoxButton.OK,
            MessageBoxImage.Error);
        e.Handled = true;
    }

    void OnUnhandledException(object? sender, UnhandledExceptionEventArgs e)
    {
        if (e.ExceptionObject is Exception ex) WriteCrashLog("BACKGROUND", ex);
    }

    static string LogFolder()
    {
        try
        {
            var root = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "CameraAutoUpload", "Logs");
            Directory.CreateDirectory(root);
            return root;
        }
        catch
        {
            var root = Path.Combine(Path.GetTempPath(), "CameraAutoUpload-Logs");
            Directory.CreateDirectory(root);
            return root;
        }
    }

    static void WriteStartupLog(string text)
    {
        try
        {
            File.AppendAllText(Path.Combine(LogFolder(), "startup.log"), $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss}] {text}{Environment.NewLine}");
        }
        catch { }
    }

    static string WriteCrashLog(string phase, Exception ex)
    {
        var path = Path.Combine(LogFolder(), "startup-crash.log");
        try
        {
            File.AppendAllText(path,
                $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss}] {phase}{Environment.NewLine}{ex}{Environment.NewLine}{new string('-', 80)}{Environment.NewLine}");
        }
        catch { }
        return path;
    }
}
