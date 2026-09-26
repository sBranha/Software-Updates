using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Threading;
using System.Windows.Forms;

internal static class DavidUpdateBootstrap
{
	private sealed class ProcInfo
	{
		public Process Process;

		public string Path;
	}

	private const string Version = "6.1.6";

	private static string LogPath
	{
		get
		{
			string text = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "David");
			Directory.CreateDirectory(text);
			return Path.Combine(text, "update-install.log");
		}
	}

	[STAThread]
	private static int Main(string[] args)
	{
		try
		{
			Log("Starting David 6.1.6 updater bootstrap.");
			string text = ParseTarget(args);
			List<ProcInfo> list = FindDavidProcesses();
			List<string> list2 = new List<string>();
			if (!string.IsNullOrWhiteSpace(text))
			{
				list2.Add(Path.GetFullPath(text));
			}
			foreach (ProcInfo item in list)
			{
				if (!string.IsNullOrWhiteSpace(item.Path))
				{
					list2.Add(item.Path);
				}
			}
			string text2 = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "David", "David.exe");
			if (File.Exists(text2))
			{
				list2.Add(text2);
			}
			list2 = list2.Where((string x) => !string.IsNullOrWhiteSpace(x)).Select(Path.GetFullPath).Distinct(StringComparer.OrdinalIgnoreCase)
				.ToList();
			if (list2.Count == 0)
			{
				list2.Add(text2);
			}
			RequestClose(list);
			WaitThenStop(list, 20000);
			byte[] array = ReadResource("DavidPayload.exe");
			byte[] array2 = ReadResource("DavidIcon.ico");
			if (array == null || array.Length < 100000)
			{
				throw new Exception("Embedded David payload is missing.");
			}
			string text3 = null;
			foreach (string item2 in list2)
			{
				try
				{
					string directoryName = Path.GetDirectoryName(item2);
					if (!string.IsNullOrWhiteSpace(directoryName))
					{
						Directory.CreateDirectory(directoryName);
						string destFileName = Path.Combine(directoryName, "David-last-good.exe");
						if (File.Exists(item2))
						{
							File.Copy(item2, destFileName, overwrite: true);
						}
						string text4 = item2 + ".new";
						File.WriteAllBytes(text4, array);
						if (File.Exists(item2))
						{
							File.Delete(item2);
						}
						File.Move(text4, item2);
						if (array2 != null && array2.Length > 0)
						{
							File.WriteAllBytes(Path.Combine(directoryName, "David.ico"), array2);
						}
						Log("Installed 6.1.6 to " + item2);
						if (text3 == null)
						{
							text3 = item2;
						}
					}
				}
				catch (Exception ex)
				{
					Log("Target failed: " + item2 + " :: " + ex.Message);
				}
			}
			if (text3 == null || !File.Exists(text3))
			{
				throw new Exception("David could not replace the currently installed program.");
			}
			Thread.Sleep(750);
			ProcessStartInfo processStartInfo = new ProcessStartInfo();
			processStartInfo.FileName = text3;
			processStartInfo.WorkingDirectory = Path.GetDirectoryName(text3);
			processStartInfo.UseShellExecute = true;
			Process.Start(processStartInfo);
			Log("Relaunched " + text3);
			return 0;
		}
		catch (Exception ex2)
		{
			try
			{
				Log("FAILED: " + ex2);
			}
			catch
			{
			}
			try
			{
				MessageBox.Show("David could not finish the update.\r\n\r\n" + ex2.Message + "\r\n\r\nLog: " + LogPath, "David Update", MessageBoxButtons.OK, MessageBoxIcon.Hand);
			}
			catch
			{
			}
			return 1;
		}
	}

	private static List<ProcInfo> FindDavidProcesses()
	{
		List<ProcInfo> list = new List<ProcInfo>();
		Process[] processesByName = Process.GetProcessesByName("David");
		foreach (Process process in processesByName)
		{
			try
			{
				string text = null;
				try
				{
					text = process.MainModule.FileName;
				}
				catch
				{
				}
				list.Add(new ProcInfo
				{
					Process = process,
					Path = text
				});
				Log("Found running David PID " + process.Id + " at " + (text ?? "unknown"));
			}
			catch
			{
			}
		}
		return list;
	}

	private static void RequestClose(List<ProcInfo> list)
	{
		foreach (ProcInfo item in list)
		{
			try
			{
				if (!item.Process.HasExited && item.Process.MainWindowHandle != IntPtr.Zero)
				{
					item.Process.CloseMainWindow();
				}
			}
			catch
			{
			}
		}
	}

	private static void WaitThenStop(List<ProcInfo> list, int totalMs)
	{
		for (int i = 0; i < totalMs; i += 500)
		{
			bool flag = false;
			foreach (ProcInfo item in list)
			{
				try
				{
					if (!item.Process.HasExited)
					{
						flag = true;
					}
				}
				catch
				{
				}
			}
			if (!flag)
			{
				return;
			}
			Thread.Sleep(500);
		}
		foreach (ProcInfo item2 in list)
		{
			try
			{
				if (!item2.Process.HasExited)
				{
					Log("Stopping remaining David PID " + item2.Process.Id);
					item2.Process.Kill();
					item2.Process.WaitForExit(5000);
				}
			}
			catch
			{
			}
		}
	}

	private static string ParseTarget(string[] args)
	{
		if (args == null)
		{
			return "";
		}
		foreach (string text in args)
		{
			string text2 = text ?? "";
			if (text2.StartsWith("/TARGET=", StringComparison.OrdinalIgnoreCase) || text2.StartsWith("--target=", StringComparison.OrdinalIgnoreCase))
			{
				int num = text2.IndexOf('=');
				return text2.Substring(num + 1).Trim().Trim('"');
			}
		}
		return "";
	}

	private static byte[] ReadResource(string name)
	{
		using Stream stream = Assembly.GetExecutingAssembly().GetManifestResourceStream(name);
		if (stream == null)
		{
			return null;
		}
		using MemoryStream memoryStream = new MemoryStream();
		stream.CopyTo(memoryStream);
		return memoryStream.ToArray();
	}

	private static void Log(string line)
	{
		File.AppendAllText(LogPath, DateTime.Now.ToString("s") + "  " + line + Environment.NewLine);
	}
}
