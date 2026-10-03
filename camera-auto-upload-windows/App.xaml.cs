using System;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Threading;

namespace CameraAutoUpload.Windows;

public partial class App : System.Windows.Application
{
    Window? startupWindow;

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
            ShowStartupWindow();
            Dispatcher.BeginInvoke(new Action(OpenMainWindow), DispatcherPriority.ApplicationIdle);
        }
        catch (Exception ex)
        {
            ShowStartupFailure(ex);
        }
    }

    void ShowStartupWindow()
    {
        startupWindow = new Window
        {
            Title = "Camera Auto Upload",
            Width = 430,
            Height = 190,
            ResizeMode = ResizeMode.NoResize,
            WindowStartupLocation = WindowStartupLocation.CenterScreen,
            WindowState = WindowState.Normal,
            ShowInTaskbar = true,
            Topmost = true,
            Background = new SolidColorBrush(Color.FromRgb(10, 14, 19)),
            Foreground = Brushes.White
        };

        var panel = new StackPanel { Margin = new Thickness(24) };
        panel.Children.Add(new TextBlock
        {
            Text = "CAMERA AUTO UPLOAD",
            FontSize = 24,
            FontWeight = FontWeights.Bold,
            Foreground = Brushes.White
        });
        panel.Children.Add(new TextBlock
        {
            Text = "Starting Windows app...",
            FontSize = 16,
            Foreground = new SolidColorBrush(Color.FromRgb(143, 162, 181)),
            Margin = new Thickness(0, 10, 0, 0)
        });
        startupWindow.Content = panel;
        startupWindow.Show();
        startupWindow.Activate();
    }

    void OpenMainWindow()
    {
        try
        {
            WriteStartupLog("Constructing main window");
            var window = new MainWindow
            {
                WindowStartupLocation = WindowStartupLocation.CenterScreen,
                WindowState = WindowState.Normal,
                ShowInTaskbar = true
            };

            MainWindow = window;
            window.Loaded += (_, _) =>
            {
                try
                {
                    window.WindowState = WindowState.Normal;
                    window.ShowInTaskbar = true;
                    window.Topmost = true;
                    window.Activate();
                    window.Focus();
                    Dispatcher.BeginInvoke(new Action(() =>
                    {
                        window.Topmost = false;
                        window.Activate();
                    }), DispatcherPriority.ApplicationIdle);
                    WriteStartupLog("Main window loaded and activated");
                }
                catch (Exception ex) { WriteCrashLog("WINDOW_ACTIVATE", ex); }
            };

            window.Show();
            startupWindow?.Close();
            startupWindow = null;
            WriteStartupLog("Main window Show() completed");
        }
        catch (Exception ex)
        {
            startupWindow?.Close();
            startupWindow = null;
            ShowStartupFailure(ex);
        }
    }

    void ShowStartupFailure(Exception ex)
    {
        var path = WriteCrashLog("STARTUP", ex);
        System.Windows.MessageBox.Show(
            "Camera Auto Upload could not start.\n\nA crash log was saved here:\n" + path + "\n\n" + ex.Message,
            "Camera Auto Upload - Startup Error",
            MessageBoxButton.OK,
            MessageBoxImage.Error);
        Shutdown(1);
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
