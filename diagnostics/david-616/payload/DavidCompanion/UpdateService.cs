using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net;
using System.Security.Cryptography;
using System.Web.Script.Serialization;

namespace DavidCompanion;

public static class UpdateService
{
	private static readonly JavaScriptSerializer Json = new JavaScriptSerializer();

	public static string ManifestUrl()
	{
		LicenseService.ServerRoot().TrimEnd('/');
		return "https://raw.githubusercontent.com/sBranha/Software-Updates/main/david/windows/latest.json";
	}

	public static bool IsNewer(string available, string installed)
	{
		try
		{
			if (!Version.TryParse((available ?? "").Trim(), out var result))
			{
				return false;
			}
			if (!Version.TryParse((installed ?? "").Trim(), out var result2))
			{
				return false;
			}
			return result > result2;
		}
		catch
		{
			return false;
		}
	}

	public static bool Check(out UpdateManifest manifest, out string error)
	{
		manifest = null;
		error = "";
		try
		{
			try
			{
				ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12;
			}
			catch
			{
			}
			if (!Uri.TryCreate(ManifestUrl(), UriKind.Absolute, out var result) || !string.Equals(result.Scheme, "https", StringComparison.OrdinalIgnoreCase))
			{
				error = "The David GitHub update channel must use HTTPS.";
				return false;
			}
			HttpWebRequest httpWebRequest = (HttpWebRequest)WebRequest.Create(result);
			httpWebRequest.Method = "GET";
			httpWebRequest.Accept = "application/json";
			httpWebRequest.UserAgent = "David-Windows/6.1.6";
			httpWebRequest.Timeout = 10000;
			httpWebRequest.ReadWriteTimeout = 10000;
			httpWebRequest.Headers["Pragma"] = "no-cache";
			using (HttpWebResponse httpWebResponse = (HttpWebResponse)httpWebRequest.GetResponse())
			{
				using StreamReader streamReader = new StreamReader(httpWebResponse.GetResponseStream());
				string input = streamReader.ReadToEnd();
				manifest = Json.Deserialize<UpdateManifest>(input);
			}
			if (manifest == null || string.IsNullOrWhiteSpace(manifest.version) || string.IsNullOrWhiteSpace(manifest.installerUrl) || string.IsNullOrWhiteSpace(manifest.sha256))
			{
				error = "The GitHub update manifest is incomplete.";
				manifest = null;
				return false;
			}
			if (!Uri.TryCreate(manifest.installerUrl, UriKind.Absolute, out var result2) || !string.Equals(result2.Scheme, "https", StringComparison.OrdinalIgnoreCase) || !string.Equals(result2.Host, result.Host, StringComparison.OrdinalIgnoreCase) || !result2.AbsolutePath.StartsWith("/sBranha/Software-Updates/", StringComparison.Ordinal))
			{
				error = "The update installer is not on the official David GitHub update host.";
				manifest = null;
				return false;
			}
			manifest.installerUrl = result2.AbsoluteUri;
			string text = (manifest.sha256 ?? "").Trim().Replace(" ", "");
			if (text.Length != 64 || text.Any((char c) => !Uri.IsHexDigit(c)))
			{
				error = "The GitHub update manifest has an invalid SHA-256 value.";
				manifest = null;
				return false;
			}
			manifest.sha256 = text.ToLowerInvariant();
			return true;
		}
		catch (WebException ex)
		{
			try
			{
				if (ex.Response is HttpWebResponse httpWebResponse2)
				{
					using StreamReader streamReader2 = new StreamReader(httpWebResponse2.GetResponseStream());
					string input2 = streamReader2.ReadToEnd();
					Dictionary<string, object> dictionary = Json.Deserialize<Dictionary<string, object>>(input2);
					if (dictionary != null && dictionary.TryGetValue("error", out var value) && value != null)
					{
						error = Convert.ToString(value);
						return false;
					}
				}
			}
			catch
			{
			}
			error = ((ex.Status == WebExceptionStatus.Timeout) ? "The GitHub update check timed out." : "David could not reach the public GitHub update repository.");
			return false;
		}
		catch (Exception ex2)
		{
			error = ex2.Message;
			return false;
		}
	}

	public static void NotifyLinkedCaregiver(UpdateManifest manifest)
	{
	}

	private static string Sha256File(string path)
	{
		using SHA256 sHA = SHA256.Create();
		using FileStream inputStream = File.OpenRead(path);
		return BitConverter.ToString(sHA.ComputeHash(inputStream)).Replace("-", "").ToLowerInvariant();
	}

	private static string SafeFileNameFromUrl(string url, string version)
	{
		try
		{
			string fileName = Path.GetFileName(new Uri(url).LocalPath);
			if (!string.IsNullOrWhiteSpace(fileName) && fileName.EndsWith(".exe", StringComparison.OrdinalIgnoreCase))
			{
				return fileName;
			}
		}
		catch
		{
		}
		return "David-" + (version ?? "UPDATE").Replace("/", "-").Replace("\\", "-") + "-Setup.exe";
	}

	public static bool PrepareAndStart(UpdateManifest manifest, out string error, out string backupFolder)
	{
		error = "";
		backupFolder = "";
		try
		{
			if (manifest == null)
			{
				error = "No update has been selected.";
				return false;
			}
			if (string.IsNullOrWhiteSpace(manifest.installerUrl))
			{
				error = "The GitHub update manifest did not provide a Windows installer.";
				return false;
			}
			if (!Uri.TryCreate(manifest.installerUrl, UriKind.Absolute, out var result) || !result.AbsolutePath.EndsWith(".exe", StringComparison.OrdinalIgnoreCase))
			{
				error = "The GitHub update repository is not offering the normal Windows Setup.exe package.";
				return false;
			}
			string text = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "David", "Updates");
			Directory.CreateDirectory(text);
			string text2 = Path.Combine(text, SafeFileNameFromUrl(manifest.installerUrl, manifest.version));
			try
			{
				ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12;
			}
			catch
			{
			}
			HttpWebRequest httpWebRequest = (HttpWebRequest)WebRequest.Create(manifest.installerUrl);
			httpWebRequest.Method = "GET";
			httpWebRequest.UserAgent = "David-Windows/6.1.6";
			httpWebRequest.Timeout = 45000;
			httpWebRequest.ReadWriteTimeout = 45000;
			using (HttpWebResponse httpWebResponse = (HttpWebResponse)httpWebRequest.GetResponse())
			{
				using Stream stream = httpWebResponse.GetResponseStream();
				using FileStream destination = new FileStream(text2, FileMode.Create, FileAccess.Write, FileShare.None);
				stream.CopyTo(destination);
			}
			if (!string.Equals(Sha256File(text2), manifest.sha256, StringComparison.OrdinalIgnoreCase))
			{
				try
				{
					File.Delete(text2);
				}
				catch
				{
				}
				error = "The downloaded update did not match the published GitHub SHA-256. Nothing was installed.";
				return false;
			}
			backupFolder = AppData.CreateBackup(automatic: false);
			Process.Start(new ProcessStartInfo
			{
				FileName = text2,
				Arguments = "/VERYSILENT /SUPPRESSMSGBOXES /NORESTART /CLOSEAPPLICATIONS",
				UseShellExecute = true,
				Verb = "runas"
			});
			return true;
		}
		catch (Win32Exception ex)
		{
			error = ((ex.NativeErrorCode == 1223) ? "The Windows administrator prompt was canceled. Nothing was installed." : ex.Message);
			return false;
		}
		catch (Exception ex2)
		{
			error = ex2.Message;
			return false;
		}
	}
}
