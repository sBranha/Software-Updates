using System;
using System.IO;
using System.Linq;
using System.Net;
using System.Security.Cryptography;
using System.Text;
using System.Web.Script.Serialization;
using System.Windows.Forms;
using Microsoft.Win32;

namespace DavidCompanion;

public static class LicenseService
{
	private static readonly JavaScriptSerializer Json = new JavaScriptSerializer();

	private static readonly byte[] Entropy = Encoding.UTF8.GetBytes("David-Lifetime-License-v1");

	public static string ServerRoot()
	{
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		string text = (string.IsNullOrWhiteSpace(securitySettings.CloudUrl) ? "https://david.forgegather.net" : securitySettings.CloudUrl.Trim());
		while (text.EndsWith("/"))
		{
			text = text.Substring(0, text.Length - 1);
		}
		return text;
	}

	public static string Fingerprint()
	{
		string text = "";
		try
		{
			using RegistryKey registryKey = Registry.LocalMachine.OpenSubKey("SOFTWARE\\Microsoft\\Cryptography", writable: false);
			if (registryKey != null)
			{
				text = Convert.ToString(registryKey.GetValue("MachineGuid")) ?? "";
			}
		}
		catch
		{
		}
		if (string.IsNullOrWhiteSpace(text))
		{
			text = Environment.MachineName + "|" + Environment.GetFolderPath(Environment.SpecialFolder.Windows);
		}
		using SHA256 sHA = SHA256.Create();
		return BitConverter.ToString(sHA.ComputeHash(Encoding.UTF8.GetBytes("David|Windows|" + text.Trim()))).Replace("-", "").ToLowerInvariant();
	}

	private static string Protect(string token)
	{
		if (string.IsNullOrWhiteSpace(token))
		{
			return "";
		}
		try
		{
			return Convert.ToBase64String(ProtectedData.Protect(Encoding.UTF8.GetBytes(token), Entropy, DataProtectionScope.CurrentUser));
		}
		catch
		{
			return "";
		}
	}

	private static string Unprotect(string value)
	{
		if (string.IsNullOrWhiteSpace(value))
		{
			return "";
		}
		try
		{
			return Encoding.UTF8.GetString(ProtectedData.Unprotect(Convert.FromBase64String(value), Entropy, DataProtectionScope.CurrentUser));
		}
		catch
		{
			return "";
		}
	}

	public static string CurrentDeviceToken()
	{
		try
		{
			return Unprotect((AppData.LoadSettings() ?? new SecuritySettings()).LicenseEncryptedToken);
		}
		catch
		{
			return "";
		}
	}

	private static DavidLicenseResponse Post(string action, object body)
	{
		try
		{
			try
			{
				ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12;
			}
			catch
			{
			}
			if (!Uri.TryCreate(ServerRoot() + "/license-person-api.php?action=" + Uri.EscapeDataString(action), UriKind.Absolute, out var result) || !string.Equals(result.Scheme, "https", StringComparison.OrdinalIgnoreCase))
			{
				return new DavidLicenseResponse
				{
					ok = false,
					error = "David activation requires the secure HTTPS licensing server."
				};
			}
			HttpWebRequest httpWebRequest = (HttpWebRequest)WebRequest.Create(result);
			httpWebRequest.Method = "POST";
			httpWebRequest.ContentType = "application/json; charset=utf-8";
			httpWebRequest.Accept = "application/json";
			httpWebRequest.UserAgent = "David-Windows/6.1.6";
			httpWebRequest.Timeout = 8000;
			httpWebRequest.ReadWriteTimeout = 8000;
			byte[] bytes = Encoding.UTF8.GetBytes(Json.Serialize(body));
			httpWebRequest.ContentLength = bytes.Length;
			using (Stream stream = httpWebRequest.GetRequestStream())
			{
				stream.Write(bytes, 0, bytes.Length);
			}
			using HttpWebResponse httpWebResponse = (HttpWebResponse)httpWebRequest.GetResponse();
			using StreamReader streamReader = new StreamReader(httpWebResponse.GetResponseStream());
			DavidLicenseResponse davidLicenseResponse = Json.Deserialize<DavidLicenseResponse>(streamReader.ReadToEnd());
			if (davidLicenseResponse == null)
			{
				davidLicenseResponse = new DavidLicenseResponse
				{
					ok = false,
					error = "The David licensing server returned an empty response."
				};
			}
			return davidLicenseResponse;
		}
		catch (WebException ex)
		{
			try
			{
				if (ex.Response is HttpWebResponse httpWebResponse2)
				{
					using StreamReader streamReader2 = new StreamReader(httpWebResponse2.GetResponseStream());
					DavidLicenseResponse davidLicenseResponse2 = Json.Deserialize<DavidLicenseResponse>(streamReader2.ReadToEnd());
					if (davidLicenseResponse2 != null)
					{
						return davidLicenseResponse2;
					}
				}
			}
			catch
			{
			}
			return new DavidLicenseResponse
			{
				ok = false,
				temporary = true,
				transportFailure = true,
				error = ((ex.Status == WebExceptionStatus.Timeout) ? "The David licensing server timed out." : "David could not reach the licensing server.")
			};
		}
		catch (Exception ex2)
		{
			return new DavidLicenseResponse
			{
				ok = false,
				error = ex2.Message
			};
		}
	}

	private static void Apply(DavidLicenseResponse r, string newToken)
	{
		if (r != null && r.license != null)
		{
			SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
			securitySettings.LicenseId = r.license.id ?? "";
			securitySettings.LicensePublicId = r.license.publicId ?? "";
			securitySettings.LicenseType = r.license.type ?? "";
			securitySettings.LicenseStatus = (r.active ? "active" : (r.license.status ?? "inactive"));
			securitySettings.LicenseCustomerName = r.license.customerName ?? "";
			securitySettings.LicenseFingerprint = Fingerprint();
			if (!string.IsNullOrWhiteSpace(newToken))
			{
				securitySettings.LicenseEncryptedToken = Protect(newToken);
			}
			if (string.IsNullOrWhiteSpace(securitySettings.LicenseActivatedAt))
			{
				securitySettings.LicenseActivatedAt = DateTime.UtcNow.ToString("o");
			}
			securitySettings.LicenseLastValidatedUtc = DateTime.UtcNow.ToString("o");
			securitySettings.LicenseExpiresAt = r.license.expiresAt ?? "";
			securitySettings.LicenseCloudPlan = r.license.cloudPlan ?? "";
			securitySettings.LicenseNeverExpires = (r.license.neverExpires ? "yes" : "no");
			AppData.SaveSettings(securitySettings);
		}
	}

	public static bool IsLocallyActive()
	{
		try
		{
			SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
			if (!string.Equals(securitySettings.LicenseStatus, "active", StringComparison.OrdinalIgnoreCase))
			{
				return false;
			}
			if (string.IsNullOrWhiteSpace(securitySettings.LicensePublicId) || string.IsNullOrWhiteSpace(Unprotect(securitySettings.LicenseEncryptedToken)))
			{
				return false;
			}
			if (!string.Equals(securitySettings.LicenseFingerprint, Fingerprint(), StringComparison.OrdinalIgnoreCase))
			{
				return false;
			}
			if (!string.IsNullOrWhiteSpace(securitySettings.LicenseExpiresAt) && DateTime.TryParse(securitySettings.LicenseExpiresAt, out var result) && result.ToUniversalTime() < DateTime.UtcNow)
			{
				return false;
			}
			return true;
		}
		catch
		{
			return false;
		}
	}

	public static DavidLicenseResponse Activate(string code)
	{
		ServiceControl.Refresh(force: false);
		if (ServiceControl.IsSuspended())
		{
			return new DavidLicenseResponse
			{
				ok = false,
				temporary = true,
				serviceSuspended = true,
				serviceMode = "suspended",
				error = ServiceControl.SuspendedMessage()
			};
		}
		string text = new string((code ?? "").Where(char.IsLetterOrDigit).ToArray()).ToUpperInvariant();
		if (text.Length != 12)
		{
			return new DavidLicenseResponse
			{
				ok = false,
				error = "Enter the 12-character David activation code."
			};
		}
		DavidLicenseResponse davidLicenseResponse = Post("activate", new
		{
			code = text,
			fingerprint = Fingerprint(),
			deviceKind = "windows",
			deviceName = Environment.MachineName,
			appVersion = "6.1.6"
		});
		if (davidLicenseResponse != null && davidLicenseResponse.ok && davidLicenseResponse.active && !string.IsNullOrWhiteSpace(davidLicenseResponse.licenseToken))
		{
			Apply(davidLicenseResponse, davidLicenseResponse.licenseToken);
		}
		return davidLicenseResponse;
	}

	public static bool ValidateIfDue(bool force, out string message)
	{
		message = "";
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		if (!IsLocallyActive())
		{
			message = "David is not activated on this computer.";
			return false;
		}
		ServiceControl.Refresh(force);
		if (ServiceControl.IsSuspended())
		{
			message = ServiceControl.SuspendedMessage();
			return true;
		}
		if (!force && DateTime.TryParse(securitySettings.LicenseLastCheckAttemptUtc, out var result) && (DateTime.UtcNow - result.ToUniversalTime()).TotalHours < 6.0)
		{
			return true;
		}
		securitySettings = AppData.LoadSettings() ?? securitySettings;
		securitySettings.LicenseLastCheckAttemptUtc = DateTime.UtcNow.ToString("o");
		AppData.SaveSettings(securitySettings);
		string text = Unprotect(securitySettings.LicenseEncryptedToken);
		if (string.IsNullOrWhiteSpace(text))
		{
			message = "The local David activation token could not be opened on this Windows account.";
			return false;
		}
		DavidLicenseResponse davidLicenseResponse = Post("status", new
		{
			token = text,
			fingerprint = Fingerprint(),
			deviceKind = "windows",
			deviceName = Environment.MachineName,
			appVersion = "6.1.6"
		});
		if (davidLicenseResponse != null && davidLicenseResponse.serviceSuspended)
		{
			SecuritySettings securitySettings2 = AppData.LoadSettings() ?? securitySettings;
			securitySettings2.ServiceMode = "suspended";
			if (!string.IsNullOrWhiteSpace(davidLicenseResponse.error))
			{
				securitySettings2.ServiceSuspendedMessage = davidLicenseResponse.error;
			}
			if (!string.IsNullOrWhiteSpace(davidLicenseResponse.supportUrl))
			{
				securitySettings2.ServiceSupportUrl = davidLicenseResponse.supportUrl;
			}
			securitySettings2.ServiceLastCheckUtc = DateTime.UtcNow.ToString("o");
			AppData.SaveSettings(securitySettings2);
			message = ServiceControl.SuspendedMessage();
			return true;
		}
		if (davidLicenseResponse == null || !davidLicenseResponse.ok)
		{
			message = ((davidLicenseResponse == null) ? "License check unavailable." : davidLicenseResponse.error);
			if (davidLicenseResponse == null || davidLicenseResponse.temporary || davidLicenseResponse.transportFailure)
			{
				return true;
			}
			SecuritySettings securitySettings3 = AppData.LoadSettings() ?? new SecuritySettings();
			securitySettings3.LicenseStatus = "inactive";
			AppData.SaveSettings(securitySettings3);
			return false;
		}
		Apply(davidLicenseResponse, "");
		if (!davidLicenseResponse.active)
		{
			message = "This David license is no longer active. Contact the David Administrator.";
			return false;
		}
		message = "David license verified.";
		return true;
	}

	public static bool EnsureActivated(IWin32Window owner)
	{
		ServiceControl.Refresh(force: false);
		if (IsLocallyActive() && ValidateIfDue(force: false, out var _))
		{
			return true;
		}
		if (ServiceControl.IsSuspended())
		{
			MessageBox.Show(ServiceControl.SuspendedMessage(), "David Connected Care", MessageBoxButtons.OK, MessageBoxIcon.Asterisk);
			return false;
		}
		using LicenseActivationForm licenseActivationForm = new LicenseActivationForm();
		return licenseActivationForm.ShowDialog(owner) == DialogResult.OK && IsLocallyActive();
	}

	public static string TypeLabel(SecuritySettings s)
	{
		string a = ((s == null) ? "" : s.LicenseType) ?? "";
		if (string.Equals(a, "complimentary_lifetime", StringComparison.OrdinalIgnoreCase))
		{
			return "Complimentary Lifetime";
		}
		if (string.Equals(a, "organization_lifetime", StringComparison.OrdinalIgnoreCase))
		{
			return "Organization Lifetime";
		}
		if (string.Equals(a, "demo", StringComparison.OrdinalIgnoreCase))
		{
			return "Demo";
		}
		return "Lifetime";
	}
}
