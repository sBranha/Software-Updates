using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Net;
using System.Text;
using System.Web.Script.Serialization;

namespace DavidCompanion;

public static class CloudClient
{
	private static readonly JavaScriptSerializer Json = new JavaScriptSerializer
	{
		MaxJsonLength = int.MaxValue
	};

	private static readonly object SyncLock = new object();

	private static string BaseUrl(string value)
	{
		string text = (value ?? "").Trim();
		while (text.EndsWith("/"))
		{
			text = text.Substring(0, text.Length - 1);
		}
		return text;
	}

	public static bool IsConnected()
	{
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		if (!ServiceControl.IsSuspended() && string.Equals(securitySettings.CloudConnectedPreference, "on", StringComparison.OrdinalIgnoreCase) && string.Equals(securitySettings.CloudApiMode, "person", StringComparison.OrdinalIgnoreCase) && !string.IsNullOrWhiteSpace(securitySettings.CloudUrl))
		{
			return !string.IsNullOrWhiteSpace(securitySettings.CloudDeviceToken);
		}
		return false;
	}

	private static CloudResponse Request(string method, string url, object body, string bearer)
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
			HttpWebRequest httpWebRequest = (HttpWebRequest)WebRequest.Create(url);
			httpWebRequest.Method = method;
			httpWebRequest.ContentType = "application/json; charset=utf-8";
			httpWebRequest.Accept = "application/json";
			httpWebRequest.UserAgent = "David-Windows/6.1.6";
			httpWebRequest.Timeout = 8000;
			httpWebRequest.ReadWriteTimeout = 8000;
			if (!string.IsNullOrWhiteSpace(bearer))
			{
				httpWebRequest.Headers[HttpRequestHeader.Authorization] = "Bearer " + bearer;
			}
			if (body != null)
			{
				byte[] bytes = Encoding.UTF8.GetBytes(Json.Serialize(body));
				httpWebRequest.ContentLength = bytes.Length;
				using Stream stream = httpWebRequest.GetRequestStream();
				stream.Write(bytes, 0, bytes.Length);
			}
			using HttpWebResponse httpWebResponse = (HttpWebResponse)httpWebRequest.GetResponse();
			using StreamReader streamReader = new StreamReader(httpWebResponse.GetResponseStream());
			string input = streamReader.ReadToEnd();
			return Json.Deserialize<CloudResponse>(input) ?? new CloudResponse
			{
				ok = false,
				error = "The Connected Care server returned an empty response."
			};
		}
		catch (WebException ex)
		{
			try
			{
				if (ex.Response is HttpWebResponse httpWebResponse2)
				{
					using StreamReader streamReader2 = new StreamReader(httpWebResponse2.GetResponseStream());
					CloudResponse cloudResponse = Json.Deserialize<CloudResponse>(streamReader2.ReadToEnd());
					if (cloudResponse != null)
					{
						return cloudResponse;
					}
				}
			}
			catch
			{
			}
			return new CloudResponse
			{
				ok = false,
				error = ((ex.Status == WebExceptionStatus.Timeout) ? "Connected Care timed out." : "Could not reach the Connected Care server.")
			};
		}
		catch (Exception ex2)
		{
			return new CloudResponse
			{
				ok = false,
				error = ex2.Message
			};
		}
	}

	private static CloudHelp NormalizeHelp(CloudResponse r)
	{
		if (r == null)
		{
			return null;
		}
		if (r.help != null)
		{
			return r.help;
		}
		if (string.IsNullOrWhiteSpace(r.helpId) && string.IsNullOrWhiteSpace(r.status))
		{
			return null;
		}
		return new CloudHelp
		{
			id = r.helpId,
			helpId = r.helpId,
			status = r.status,
			startedAt = r.startedAt,
			createdAt = r.startedAt,
			claimedBy = r.claimedBy,
			claimedName = r.claimedName,
			claimedAt = r.claimedAt,
			resolvedAt = r.resolvedAt
		};
	}

	private static void ApplyIdentity(CloudResponse r, string token)
	{
		if (r == null || !r.ok)
		{
			return;
		}
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		securitySettings.CloudUrl = BaseUrl(string.IsNullOrWhiteSpace(securitySettings.CloudUrl) ? "https://david.forgegather.net" : securitySettings.CloudUrl);
		string text;
		if (string.IsNullOrWhiteSpace(r.deviceId))
		{
			text = ((r.device == null) ? "" : r.device.id);
		}
		else
		{
			text = r.deviceId;
		}
		if (!string.IsNullOrWhiteSpace(text))
		{
			securitySettings.CloudDeviceId = text;
		}
		if (!string.IsNullOrWhiteSpace(token))
		{
			securitySettings.CloudDeviceToken = token;
		}
		CloudPersonProfile cloudPersonProfile = r.person ?? r.profile;
		if (cloudPersonProfile != null)
		{
			if (!string.IsNullOrWhiteSpace(cloudPersonProfile.id))
			{
				securitySettings.CloudPersonId = cloudPersonProfile.id;
			}
			if (!string.IsNullOrWhiteSpace(cloudPersonProfile.name))
			{
				securitySettings.PersonFullName = cloudPersonProfile.name;
			}
			if (!string.IsNullOrWhiteSpace(cloudPersonProfile.preferredName))
			{
				securitySettings.PersonPreferredName = cloudPersonProfile.preferredName;
			}
			else if (!string.IsNullOrWhiteSpace(cloudPersonProfile.name))
			{
				securitySettings.PersonPreferredName = cloudPersonProfile.name;
			}
			securitySettings.PersonLocation = cloudPersonProfile.location ?? "";
			securitySettings.PersonRoom = cloudPersonProfile.room ?? "";
		}
		if (r.license != null)
		{
			if (!string.IsNullOrWhiteSpace(r.license.id))
			{
				securitySettings.LicenseId = r.license.id;
			}
			if (!string.IsNullOrWhiteSpace(r.license.publicId))
			{
				securitySettings.LicensePublicId = r.license.publicId;
			}
			if (!string.IsNullOrWhiteSpace(r.license.type))
			{
				securitySettings.LicenseType = r.license.type;
			}
			if (!string.IsNullOrWhiteSpace(r.license.status))
			{
				securitySettings.LicenseStatus = r.license.status;
			}
		}
		securitySettings.CloudConnectedPreference = "on";
		securitySettings.CloudApiMode = "person";
		securitySettings.SetupCompletedPreference = "done";
		AppData.SaveSettings(securitySettings);
	}

	public static bool EnsureSharedConnection()
	{
		try
		{
			ServiceControl.Refresh(force: false);
			if (ServiceControl.IsSuspended())
			{
				return false;
			}
			SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
			string text = (securitySettings.CloudUrl = BaseUrl(string.IsNullOrWhiteSpace(securitySettings.CloudUrl) ? "https://david.forgegather.net" : securitySettings.CloudUrl));
			string text3 = text;
			AppData.SaveSettings(securitySettings);
			List<string> list = new List<string>();
			if (!string.IsNullOrWhiteSpace(securitySettings.CloudDeviceToken))
			{
				list.Add(securitySettings.CloudDeviceToken.Trim());
			}
			string text4 = LicenseService.CurrentDeviceToken();
			if (!string.IsNullOrWhiteSpace(text4) && !list.Contains(text4))
			{
				list.Add(text4);
			}
			foreach (string item in list)
			{
				CloudResponse cloudResponse = Request("POST", text3 + "/license-api.php?action=person-status", new { }, item);
				if (cloudResponse != null && cloudResponse.ok)
				{
					ApplyIdentity(cloudResponse, item);
					return true;
				}
			}
		}
		catch
		{
		}
		return false;
	}

	public static CloudResponse ClaimSetupCode(string code)
	{
		ServiceControl.Refresh(force: false);
		if (ServiceControl.IsSuspended())
		{
			return new CloudResponse
			{
				ok = false,
				error = ServiceControl.SuspendedMessage()
			};
		}
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		string text = BaseUrl(string.IsNullOrWhiteSpace(securitySettings.CloudUrl) ? "https://david.forgegather.net" : securitySettings.CloudUrl);
		CloudResponse cloudResponse = Request("POST", text + "/license-api.php?action=person-claim", new
		{
			code = (code ?? "").Trim(),
			fingerprint = LicenseService.Fingerprint(),
			deviceKind = "windows",
			deviceName = Environment.MachineName,
			appVersion = "6.1.6"
		}, "");
		if (cloudResponse != null && cloudResponse.ok)
		{
			string token = ((!string.IsNullOrWhiteSpace(cloudResponse.deviceToken)) ? cloudResponse.deviceToken : cloudResponse.licenseToken);
			ApplyIdentity(cloudResponse, token);
			MergeRemote(cloudResponse.reminders);
			MergeOccurrences(cloudResponse.occurrenceStates);
			SecuritySettings securitySettings2 = AppData.LoadSettings() ?? securitySettings;
			securitySettings2.CloudLastSyncUtc = DateTime.UtcNow.ToString("o");
			AppData.SaveSettings(securitySettings2);
		}
		return cloudResponse;
	}

	public static CloudResponse Register(string serverUrl, string setupKey)
	{
		return new CloudResponse
		{
			ok = false,
			error = "Use the caregiver website → person → Devices → Add Device, then enter the 6-digit code."
		};
	}

	public static CloudResponse RefreshPairCode()
	{
		return new CloudResponse
		{
			ok = false,
			error = "Phone pairing codes were replaced by the caregiver Add Device flow."
		};
	}

	public static CloudResponse TestHelpNotification()
	{
		return StartHelp();
	}

	private static void MergeRemote(List<Reminder> remote)
	{
		if (remote == null)
		{
			return;
		}
		lock (AppData.LocalLock)
		{
			AppData.SaveReminders(new ReminderStore
			{
				Items = AppData.CompactReminders(remote)
			});
		}
	}

	private static List<CloudOccurrenceState> LocalOccurrences()
	{
		List<CloudOccurrenceState> list = new List<CloudOccurrenceState>();
		foreach (StateEntry item in AppData.LoadState().Items ?? new List<StateEntry>())
		{
			if (item == null || !string.Equals(item.Status, "done", StringComparison.OrdinalIgnoreCase) || string.IsNullOrWhiteSpace(item.Key))
			{
				continue;
			}
			int num = item.Key.IndexOf('|');
			if (num > 0)
			{
				string scheduledFor = "";
				if (DateTime.TryParseExact(item.Key.Substring(num + 1), "yyyy-MM-ddTHH:mm", CultureInfo.InvariantCulture, DateTimeStyles.None, out var result))
				{
					scheduledFor = DateTime.SpecifyKind(result, DateTimeKind.Local).ToUniversalTime().ToString("o");
				}
				string text = item.Updated ?? "";
				if (string.IsNullOrWhiteSpace(text))
				{
					text = DateTime.UtcNow.ToString("o");
				}
				list.Add(new CloudOccurrenceState
				{
					reminderId = item.Key.Substring(0, num),
					occurrenceKey = item.Key,
					state = "done",
					scheduledFor = scheduledFor,
					completedAt = text,
					updatedAt = text
				});
			}
		}
		return list;
	}

	private static void MergeOccurrences(List<CloudOccurrenceState> remote)
	{
		if (remote == null)
		{
			return;
		}
		foreach (CloudOccurrenceState item in remote)
		{
			if (item != null && !string.IsNullOrWhiteSpace(item.occurrenceKey) && string.Equals(item.state, "done", StringComparison.OrdinalIgnoreCase))
			{
				StateEntry state = AppData.GetState(item.occurrenceKey);
				DateTime result = DateTime.MinValue;
				DateTime result2 = DateTime.MaxValue;
				if (state != null)
				{
					DateTime.TryParse(state.Updated, null, DateTimeStyles.RoundtripKind, out result);
				}
				if (!string.IsNullOrWhiteSpace(item.updatedAt))
				{
					DateTime.TryParse(item.updatedAt, null, DateTimeStyles.RoundtripKind, out result2);
				}
				if (state == null || !string.Equals(state.Status, "done", StringComparison.OrdinalIgnoreCase) || result2 >= result)
				{
					AppData.SetState(item.occurrenceKey, "done", null);
				}
			}
		}
	}

	private static CloudResponse PersonSync(object reminders)
	{
		if (!EnsureSharedConnection())
		{
			return new CloudResponse
			{
				ok = false,
				error = "This Windows computer is not linked to a David person. Open Connected Care and enter an Add Device code."
			};
		}
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		List<HistoryEntry> history = (AppData.LoadHistory().Items ?? new List<HistoryEntry>()).OrderBy((HistoryEntry x) => x.Timestamp).Reverse().Take(250)
			.Reverse()
			.ToList();
		CloudResponse cloudResponse = Request("POST", BaseUrl(securitySettings.CloudUrl) + "/license-api.php?action=person-sync", new
		{
			reminders = reminders,
			history = history,
			occurrenceStates = LocalOccurrences(),
			appVersion = "6.1.6"
		}, securitySettings.CloudDeviceToken);
		if (cloudResponse != null && cloudResponse.ok)
		{
			MergeRemote(cloudResponse.reminders);
			MergeOccurrences(cloudResponse.occurrenceStates);
			cloudResponse.help = NormalizeHelp(cloudResponse);
			ApplyIdentity(cloudResponse, securitySettings.CloudDeviceToken);
			SecuritySettings securitySettings2 = AppData.LoadSettings() ?? securitySettings;
			securitySettings2.CloudLastSyncUtc = DateTime.UtcNow.ToString("o");
			AppData.SaveSettings(securitySettings2);
		}
		return cloudResponse;
	}

	public static CloudResponse MarkOccurrenceDoneRemote(string occurrenceKey, string reminderId, DateTime scheduled)
	{
		if (!EnsureSharedConnection())
		{
			return new CloudResponse
			{
				ok = false,
				error = "This Windows computer is not linked to David Connected Care."
			};
		}
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		return Request("POST", BaseUrl(securitySettings.CloudUrl) + "/license-api.php?action=person-occurrence-done", new
		{
			occurrenceKey = (occurrenceKey ?? ""),
			reminderId = (reminderId ?? ""),
			scheduledFor = scheduled.ToUniversalTime().ToString("o"),
			appVersion = "6.1.6"
		}, securitySettings.CloudDeviceToken);
	}

	public static bool OccurrenceDoneRemote(string occurrenceKey)
	{
		if (string.IsNullOrWhiteSpace(occurrenceKey) || !EnsureSharedConnection())
		{
			return false;
		}
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		CloudResponse cloudResponse = Request("POST", BaseUrl(securitySettings.CloudUrl) + "/license-api.php?action=person-occurrence-status", new { occurrenceKey }, securitySettings.CloudDeviceToken);
		int num;
		if (cloudResponse != null && cloudResponse.ok)
		{
			if (!cloudResponse.done)
			{
				num = (string.Equals(cloudResponse.state, "done", StringComparison.OrdinalIgnoreCase) ? 1 : 0);
				if (num == 0)
				{
					goto IL_0093;
				}
			}
			else
			{
				num = 1;
			}
			AppData.SetState(occurrenceKey, "done", null);
		}
		else
		{
			num = 0;
		}
		goto IL_0093;
		IL_0093:
		return (byte)num != 0;
	}

	public static CloudResponse SyncNow()
	{
		lock (SyncLock)
		{
			return PersonSync(AppData.LoadReminders().Items ?? new List<Reminder>());
		}
	}

	public static CloudResponse SaveReminder(Reminder reminder)
	{
		if (reminder == null)
		{
			return new CloudResponse
			{
				ok = false,
				error = "Reminder is missing."
			};
		}
		return SyncNow();
	}

	public static CloudResponse DeleteReminder(string reminderId)
	{
		Reminder item = new Reminder
		{
			Id = (reminderId ?? ""),
			Deleted = true,
			Enabled = false,
			ModifiedUtc = DateTime.UtcNow.ToString("o")
		};
		return PersonSync(new List<Reminder> { item });
	}

	public static CloudResponse ClearAllReminders()
	{
		CloudResponse cloudResponse = SyncNow();
		if (cloudResponse == null || !cloudResponse.ok)
		{
			return cloudResponse;
		}
		List<Reminder> list = new List<Reminder>();
		string modifiedUtc = DateTime.UtcNow.ToString("o");
		foreach (Reminder item in cloudResponse.reminders ?? new List<Reminder>())
		{
			if (item != null && !item.Deleted && !string.IsNullOrWhiteSpace(item.Id))
			{
				list.Add(new Reminder
				{
					Id = item.Id,
					Deleted = true,
					Enabled = false,
					ModifiedUtc = modifiedUtc
				});
			}
		}
		return PersonSync(list);
	}

	public static CloudResponse StartHelp()
	{
		if (!EnsureSharedConnection())
		{
			return new CloudResponse
			{
				ok = false,
				error = "David is not connected to caregivers."
			};
		}
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		CloudResponse cloudResponse = Request("POST", BaseUrl(securitySettings.CloudUrl) + "/license-api.php?action=person-help-start", new { }, securitySettings.CloudDeviceToken);
		if (cloudResponse != null)
		{
			cloudResponse.help = NormalizeHelp(cloudResponse);
		}
		return cloudResponse;
	}

	public static CloudResponse ReplyChat(string message, string inReplyTo, string quick)
	{
		if (!EnsureSharedConnection())
		{
			return new CloudResponse
			{
				ok = false,
				error = "David is not connected to caregivers."
			};
		}
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		return Request("POST", BaseUrl(securitySettings.CloudUrl) + "/license-api.php?action=person-chat", new
		{
			message = (message ?? ""),
			inReplyTo = (inReplyTo ?? ""),
			quickReply = (quick ?? "")
		}, securitySettings.CloudDeviceToken);
	}

	public static CloudResponse HelpStatus()
	{
		if (!EnsureSharedConnection())
		{
			return new CloudResponse
			{
				ok = false,
				error = "David is not connected to caregivers."
			};
		}
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		CloudResponse cloudResponse = Request("POST", BaseUrl(securitySettings.CloudUrl) + "/license-api.php?action=person-help-status", new { }, securitySettings.CloudDeviceToken);
		if (cloudResponse != null)
		{
			cloudResponse.help = NormalizeHelp(cloudResponse);
		}
		return cloudResponse;
	}
}
