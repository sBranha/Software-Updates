using System;
using System.IO;
using System.Net;
using System.Web.Script.Serialization;

namespace DavidCompanion;

public static class ServiceControl
{
	private static readonly JavaScriptSerializer Json = new JavaScriptSerializer();

	private static string Root()
	{
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		string text = (string.IsNullOrWhiteSpace(securitySettings.CloudUrl) ? "https://david.forgegather.net" : securitySettings.CloudUrl.Trim());
		while (text.EndsWith("/"))
		{
			text = text.Substring(0, text.Length - 1);
		}
		return text;
	}

	public static bool Refresh(bool force)
	{
		try
		{
			SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
			if (!force && DateTime.TryParse(securitySettings.ServiceLastCheckAttemptUtc, out var result) && (DateTime.UtcNow - result.ToUniversalTime()).TotalMinutes < 5.0)
			{
				return true;
			}
			securitySettings.ServiceLastCheckAttemptUtc = DateTime.UtcNow.ToString("o");
			AppData.SaveSettings(securitySettings);
			if (!Uri.TryCreate(Root() + "/service-api.php?action=status", UriKind.Absolute, out var result2) || !string.Equals(result2.Scheme, "https", StringComparison.OrdinalIgnoreCase))
			{
				return false;
			}
			try
			{
				ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12;
			}
			catch
			{
			}
			HttpWebRequest httpWebRequest = (HttpWebRequest)WebRequest.Create(result2);
			httpWebRequest.Method = "GET";
			httpWebRequest.Accept = "application/json";
			httpWebRequest.UserAgent = "David-Windows/6.1.6";
			httpWebRequest.Timeout = 8000;
			httpWebRequest.ReadWriteTimeout = 8000;
			using HttpWebResponse httpWebResponse = (HttpWebResponse)httpWebRequest.GetResponse();
			using StreamReader streamReader = new StreamReader(httpWebResponse.GetResponseStream());
			DavidServiceStatusResponse davidServiceStatusResponse = Json.Deserialize<DavidServiceStatusResponse>(streamReader.ReadToEnd());
			if (davidServiceStatusResponse == null || !davidServiceStatusResponse.ok)
			{
				return false;
			}
			string text = (davidServiceStatusResponse.mode ?? "on").Trim().ToLowerInvariant();
			if (text != "on" && text != "support_needed" && text != "suspended")
			{
				text = "on";
			}
			SecuritySettings securitySettings2 = AppData.LoadSettings() ?? securitySettings;
			securitySettings2.ServiceMode = text;
			securitySettings2.ServiceSupportMessage = davidServiceStatusResponse.supportMessage ?? "";
			securitySettings2.ServiceSupportUrl = davidServiceStatusResponse.supportUrl ?? "";
			securitySettings2.ServiceSuspendedMessage = davidServiceStatusResponse.suspendedMessage ?? "";
			securitySettings2.ServiceLastCheckUtc = DateTime.UtcNow.ToString("o");
			AppData.SaveSettings(securitySettings2);
			return true;
		}
		catch
		{
			return false;
		}
	}

	public static bool IsSuspended()
	{
		try
		{
			return string.Equals((AppData.LoadSettings() ?? new SecuritySettings()).ServiceMode, "suspended", StringComparison.OrdinalIgnoreCase);
		}
		catch
		{
			return false;
		}
	}

	public static bool SupportNeeded()
	{
		try
		{
			return string.Equals((AppData.LoadSettings() ?? new SecuritySettings()).ServiceMode, "support_needed", StringComparison.OrdinalIgnoreCase);
		}
		catch
		{
			return false;
		}
	}

	public static string SuspendedMessage()
	{
		try
		{
			SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
			if (!string.IsNullOrWhiteSpace(securitySettings.ServiceSuspendedMessage))
			{
				return securitySettings.ServiceSuspendedMessage;
			}
		}
		catch
		{
		}
		return "David Connected Care is temporarily paused by the Administrator. Local reminders and saved daily-care information on this computer remain available.";
	}
}
