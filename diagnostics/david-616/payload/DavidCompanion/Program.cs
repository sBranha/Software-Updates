using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using System.Windows.Forms;
using Microsoft.Win32;

namespace DavidCompanion;

public static class Program
{
	public const string Version = "6.1.6";

	private static string ExePath => Assembly.GetExecutingAssembly().Location;

	private static string InstallDir => Path.GetDirectoryName(ExePath);

	private static string StartupShortcutPath => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Startup), "David.lnk");

	[DllImport("shell32.dll", CharSet = CharSet.Unicode)]
	private static extern int SetCurrentProcessExplicitAppUserModelID(string AppID);

	[STAThread]
	public static void Main(string[] args)
	{
		try
		{
			SetCurrentProcessExplicitAppUserModelID("David.DailyCompanion");
		}
		catch
		{
		}
		Application.EnableVisualStyles();
		Application.SetCompatibleTextRenderingDefault(defaultValue: false);
		try
		{
			AppData.Ensure();
			if (args.Any((string x) => string.Equals(x, "/uninstall", StringComparison.OrdinalIgnoreCase)))
			{
				RunProtectedUninstall();
				return;
			}
			ServiceControl.Refresh(force: false);
			if (args.Any((string x) => string.Equals(x, "/background", StringComparison.OrdinalIgnoreCase)))
			{
				if (!string.Equals((AppData.LoadSettings() ?? new SecuritySettings()).StartupPreference, "off", StringComparison.OrdinalIgnoreCase) && LicenseService.IsLocallyActive() && LicenseService.ValidateIfDue(force: false, out var _) && LicenseService.IsLocallyActive())
				{
					try
					{
						CloudClient.EnsureSharedConnection();
					}
					catch
					{
					}
					BackgroundContext backgroundContext = new BackgroundContext();
					if (!backgroundContext.AlreadyRunning)
					{
						Application.Run(backgroundContext);
					}
				}
			}
			else
			{
				if (!LicenseService.EnsureActivated(null))
				{
					return;
				}
				try
				{
					CloudClient.EnsureSharedConnection();
				}
				catch
				{
				}
				if (args.Any((string x) => string.Equals(x, "/caregiver", StringComparison.OrdinalIgnoreCase)))
				{
					EnsureShellShortcuts();
					EnsureAutoStart();
					RegisterUninstallEntry();
					StartBackgroundProcess();
					if (PinSecurity.EnsurePinCreated() && PinSecurity.Prompt("Enter the caregiver PIN to open the Caregiver Manager."))
					{
						Application.Run(new ManagerForm());
					}
					return;
				}
				EnsureShellShortcuts();
				EnsureAutoStart();
				RegisterUninstallEntry();
				StartBackgroundProcess();
				if (PinSecurity.EnsurePinCreated())
				{
					EnsureFirstRunSetup();
					Application.Run(new HomeForm());
				}
			}
		}
		catch (Exception ex)
		{
			try
			{
				string text = Path.Combine(AppData.Root, "David-crash.log");
				Directory.CreateDirectory(AppData.Root);
				File.AppendAllText(text, string.Concat(DateTime.Now.ToString("s"), Environment.NewLine, ex, Environment.NewLine, Environment.NewLine));
				MessageBox.Show("David could not start.\r\n\r\n" + ex.Message + "\r\n\r\nCrash log: " + text, "David", MessageBoxButtons.OK, MessageBoxIcon.Hand);
			}
			catch
			{
			}
		}
	}

	public static Icon TryLoadIcon()
	{
		try
		{
			string text = Path.Combine(InstallDir, "David.ico");
			if (File.Exists(text))
			{
				return new Icon(text);
			}
		}
		catch
		{
		}
		return SystemIcons.Information;
	}

	public static void EnsureFirstRunSetup()
	{
		try
		{
			SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
			if (CloudClient.IsConnected())
			{
				if (!string.Equals(securitySettings.SetupCompletedPreference, "done", StringComparison.OrdinalIgnoreCase))
				{
					securitySettings.SetupCompletedPreference = "done";
					AppData.SaveSettings(securitySettings);
				}
				return;
			}
			using FirstRunSetupForm firstRunSetupForm = new FirstRunSetupForm();
			firstRunSetupForm.ShowDialog();
		}
		catch
		{
		}
	}

	private static void CreateShellShortcut(string path, string arguments, string description)
	{
		object obj = null;
		object obj2 = null;
		try
		{
			string directoryName = Path.GetDirectoryName(path);
			if (!string.IsNullOrWhiteSpace(directoryName))
			{
				Directory.CreateDirectory(directoryName);
			}
			Type typeFromProgID = Type.GetTypeFromProgID("WScript.Shell");
			if (!(typeFromProgID == null))
			{
				obj = Activator.CreateInstance(typeFromProgID);
				obj2 = typeFromProgID.InvokeMember("CreateShortcut", BindingFlags.InvokeMethod, null, obj, new object[1] { path });
				Type type = obj2.GetType();
				type.InvokeMember("TargetPath", BindingFlags.SetProperty, null, obj2, new object[1] { ExePath });
				type.InvokeMember("Arguments", BindingFlags.SetProperty, null, obj2, new object[1] { arguments ?? "" });
				type.InvokeMember("WorkingDirectory", BindingFlags.SetProperty, null, obj2, new object[1] { InstallDir });
				string text = Path.Combine(InstallDir, "David.ico");
				type.InvokeMember("IconLocation", BindingFlags.SetProperty, null, obj2, new object[1] { (File.Exists(text) ? text : ExePath) + ",0" });
				type.InvokeMember("Description", BindingFlags.SetProperty, null, obj2, new object[1] { description ?? "David Daily Companion" });
				type.InvokeMember("Save", BindingFlags.InvokeMethod, null, obj2, null);
			}
		}
		catch
		{
		}
		finally
		{
			try
			{
				if (obj2 != null && Marshal.IsComObject(obj2))
				{
					Marshal.FinalReleaseComObject(obj2);
				}
			}
			catch
			{
			}
			try
			{
				if (obj != null && Marshal.IsComObject(obj))
				{
					Marshal.FinalReleaseComObject(obj);
				}
			}
			catch
			{
			}
		}
	}

	public static void EnsureShellShortcuts()
	{
	}

	public static bool IsAutoStartEnabled()
	{
		try
		{
			if (string.Equals((AppData.LoadSettings() ?? new SecuritySettings()).StartupPreference, "off", StringComparison.OrdinalIgnoreCase))
			{
				return false;
			}
			return File.Exists(StartupShortcutPath);
		}
		catch
		{
			return false;
		}
	}

	public static void EnsureAutoStart()
	{
		try
		{
			SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
			if (string.IsNullOrWhiteSpace(securitySettings.StartupPreference))
			{
				securitySettings.StartupPreference = "on";
				AppData.SaveSettings(securitySettings);
			}
		}
		catch
		{
		}
	}

	public static void SetAutoStart(bool enabled)
	{
		try
		{
			SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
			securitySettings.StartupPreference = (enabled ? "on" : "off");
			AppData.SaveSettings(securitySettings);
		}
		catch
		{
		}
	}

	private static void WriteAutoStartRegistry(bool enabled)
	{
	}

	public static void RegisterUninstallEntry()
	{
	}

	public static void MarkHealthyStart()
	{
		try
		{
			File.WriteAllText(Path.Combine(InstallDir, "update-health.txt"), "6.1.6|" + DateTime.UtcNow.ToString("o"), Encoding.UTF8);
		}
		catch
		{
		}
	}

	public static void StartBackgroundProcess()
	{
		try
		{
			Process.Start(new ProcessStartInfo
			{
				FileName = ExePath,
				Arguments = "/background",
				UseShellExecute = true
			});
		}
		catch
		{
		}
	}

	public static void StartHomeProcess()
	{
		try
		{
			Process.Start(new ProcessStartInfo
			{
				FileName = ExePath,
				UseShellExecute = true
			});
		}
		catch
		{
		}
	}

	public static void OpenLatestDavidDownload()
	{
		try
		{
			SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
			if (!string.IsNullOrWhiteSpace(securitySettings.CloudUrl))
			{
				securitySettings.CloudUrl.Trim().TrimEnd('/');
			}
			string fileName = "https://github.com/sBranha/Software-Updates/tree/main/david/windows";
			Process.Start(new ProcessStartInfo
			{
				FileName = fileName,
				UseShellExecute = true
			});
		}
		catch
		{
			MessageBox.Show("David could not open the public GitHub download page. Open the sBranha/Software-Updates repository and choose david/windows.", "David", MessageBoxButtons.OK, MessageBoxIcon.Asterisk);
		}
	}

	public static bool HasLastKnownGood()
	{
		try
		{
			return File.Exists(Path.Combine(InstallDir, "David-last-good.exe"));
		}
		catch
		{
			return false;
		}
	}

	public static void RestoreLastKnownGood(IWin32Window owner)
	{
		try
		{
			string text = Path.Combine(InstallDir, "David-last-good.exe");
			if (!File.Exists(text))
			{
				MessageBox.Show("No previous David program is available yet. A last-known-good copy is created when a newer installer successfully replaces David.", "David");
			}
			else if (MessageBox.Show("Restore the previous working David program?\r\n\r\nThis rolls back only David.exe. Your reminders, PIN, history, and settings stay in place.", "David - Program Rollback", MessageBoxButtons.YesNo, MessageBoxIcon.Exclamation) == DialogResult.Yes)
			{
				StopBackgroundProcesses();
				string arguments = "/c timeout /t 3 /nobreak >nul & copy /Y \"" + text + "\" \"" + ExePath + "\" >nul & start \"\" \"" + ExePath + "\"";
				Process.Start(new ProcessStartInfo("cmd.exe", arguments)
				{
					CreateNoWindow = true,
					UseShellExecute = false,
					WindowStyle = ProcessWindowStyle.Hidden
				});
				Application.Exit();
			}
		}
		catch (Exception ex)
		{
			MessageBox.Show("David could not start the rollback.\r\n\r\n" + ex.Message, "David", MessageBoxButtons.OK, MessageBoxIcon.Hand);
		}
	}

	public static void StartCaregiverProcess()
	{
		try
		{
			Process.Start(new ProcessStartInfo
			{
				FileName = ExePath,
				Arguments = "/caregiver",
				UseShellExecute = true
			});
		}
		catch
		{
		}
	}

	public static void OpenCaregiverProtected(IWin32Window owner)
	{
		if (!PinSecurity.Prompt("Enter the caregiver PIN to manage David's reminders."))
		{
			return;
		}
		using ManagerForm managerForm = new ManagerForm();
		managerForm.ShowDialog(owner);
	}

	public static void StartUninstallerProcess()
	{
		try
		{
			Process.Start(new ProcessStartInfo
			{
				FileName = ExePath,
				Arguments = "/uninstall",
				UseShellExecute = true
			});
		}
		catch
		{
		}
	}

	public static void StopBackgroundProcesses()
	{
		try
		{
			int id = Process.GetCurrentProcess().Id;
			string[] array = new string[2] { "David", "ForgeReminder" };
			for (int i = 0; i < array.Length; i++)
			{
				Process[] processesByName = Process.GetProcessesByName(array[i]);
				foreach (Process process in processesByName)
				{
					try
					{
						if (process.Id != id)
						{
							process.Kill();
							process.WaitForExit(2000);
						}
					}
					catch
					{
					}
				}
			}
		}
		catch
		{
		}
	}

	private static void RunProtectedUninstall()
	{
		if (!PinSecurity.EnsurePinCreated() || !PinSecurity.Prompt("Enter the caregiver PIN to uninstall David.") || MessageBox.Show("Uninstall David from this Windows account?", "David", MessageBoxButtons.YesNo, MessageBoxIcon.Exclamation) != DialogResult.Yes)
		{
			return;
		}
		bool flag = MessageBox.Show("Also delete all reminders, history, and the caregiver PIN stored on this computer?\r\n\r\nChoose No to keep the data for a future reinstall.", "David", MessageBoxButtons.YesNo, MessageBoxIcon.Question) == DialogResult.Yes;
		SetAutoStart(enabled: false);
		try
		{
			Registry.CurrentUser.DeleteSubKeyTree("Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\David", throwOnMissingSubKey: false);
		}
		catch
		{
		}
		StopBackgroundProcesses();
		string path = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory), "David.lnk");
		string path2 = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs), "David.lnk");
		string path3 = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs), "David Caregiver Manager.lnk");
		string startupShortcutPath = StartupShortcutPath;
		try
		{
			if (File.Exists(path))
			{
				File.Delete(path);
			}
		}
		catch
		{
		}
		try
		{
			if (File.Exists(path2))
			{
				File.Delete(path2);
			}
		}
		catch
		{
		}
		try
		{
			if (File.Exists(path3))
			{
				File.Delete(path3);
			}
		}
		catch
		{
		}
		try
		{
			if (File.Exists(startupShortcutPath))
			{
				File.Delete(startupShortcutPath);
			}
		}
		catch
		{
		}
		string text = "/c timeout /t 2 /nobreak >nul & rmdir /s /q \"" + InstallDir + "\"";
		if (flag)
		{
			text = text + " & rmdir /s /q \"" + AppData.Root + "\"";
		}
		try
		{
			Process.Start(new ProcessStartInfo("cmd.exe", text)
			{
				CreateNoWindow = true,
				UseShellExecute = false,
				WindowStyle = ProcessWindowStyle.Hidden
			});
		}
		catch
		{
		}
		MessageBox.Show("David is being uninstalled.", "David", MessageBoxButtons.OK, MessageBoxIcon.Asterisk);
	}
}
