using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;

namespace DavidCompanion;

public static class QuietAwayService
{
	public static bool IsProtected(Reminder r)
	{
		if (r == null)
		{
			return false;
		}
		if (!DailySummary.IsHighPriority(r))
		{
			return string.Equals(r.Category, "Appointment", StringComparison.OrdinalIgnoreCase);
		}
		return true;
	}

	private static TimeSpan ParseTime(string value, int fallbackHour)
	{
		if (!TimeSpan.TryParse(value ?? "", out var result))
		{
			return TimeSpan.FromHours(fallbackHour);
		}
		return result;
	}

	public static bool QuietHoursActive(SecuritySettings s, DateTime now)
	{
		if (s == null || !string.Equals(s.QuietHoursEnabled, "on", StringComparison.OrdinalIgnoreCase))
		{
			return false;
		}
		TimeSpan timeSpan = ParseTime(s.QuietHoursStartTime, 22);
		TimeSpan timeSpan2 = ParseTime(s.QuietHoursEndTime, 7);
		TimeSpan timeOfDay = now.TimeOfDay;
		if (timeSpan == timeSpan2)
		{
			return false;
		}
		if (timeSpan < timeSpan2)
		{
			if (timeOfDay >= timeSpan)
			{
				return timeOfDay < timeSpan2;
			}
			return false;
		}
		if (!(timeOfDay >= timeSpan))
		{
			return timeOfDay < timeSpan2;
		}
		return true;
	}

	public static DateTime QuietHoursRelease(SecuritySettings s, DateTime now)
	{
		TimeSpan timeSpan = ParseTime((s == null) ? "" : s.QuietHoursStartTime, 22);
		TimeSpan timeSpan2 = ParseTime((s == null) ? "" : s.QuietHoursEndTime, 7);
		if (timeSpan < timeSpan2)
		{
			return now.Date.Add(timeSpan2);
		}
		if (!(now.TimeOfDay >= timeSpan))
		{
			return now.Date.Add(timeSpan2);
		}
		return now.Date.AddDays(1.0).Add(timeSpan2);
	}

	public static bool AwayActive(SecuritySettings s, DateTime now)
	{
		if (s == null || !string.Equals(s.AwayModeEnabled, "on", StringComparison.OrdinalIgnoreCase))
		{
			return false;
		}
		if (DateTime.TryParse(s.AwayModeUntil, out var result))
		{
			return result > now;
		}
		return false;
	}

	public static void ExpireAwayIfNeeded(SecuritySettings s, DateTime now)
	{
		if (s != null && string.Equals(s.AwayModeEnabled, "on", StringComparison.OrdinalIgnoreCase) && (!DateTime.TryParse(s.AwayModeUntil, out var result) || !(result > now)))
		{
			s.AwayModeEnabled = "off";
			AppData.SaveSettings(s);
		}
	}

	public static bool UserQuietActive(SecuritySettings s, DateTime now)
	{
		if (!AwayActive(s, now))
		{
			return QuietHoursActive(s, now);
		}
		return true;
	}

	public static bool TryHold(Reminder r, DateTime due, string key, SecuritySettings s, DateTime now)
	{
		if (r == null || IsProtected(r))
		{
			return false;
		}
		if (AwayActive(s, now))
		{
			AppData.SetState(key, "away-held", null);
			AppData.AddHistory(r, "Held while Away Mode was active", key, due);
			return true;
		}
		if (QuietHoursActive(s, now))
		{
			DateTime value = QuietHoursRelease(s, now);
			AppData.SetState(key, "quiet-held", value);
			AppData.AddHistory(r, "Held until Quiet Hours ended", key, due);
			return true;
		}
		return false;
	}

	private static bool TryParseOccurrenceKey(string key, out string reminderId, out DateTime scheduled)
	{
		reminderId = "";
		scheduled = DateTime.MinValue;
		if (string.IsNullOrWhiteSpace(key))
		{
			return false;
		}
		int num = key.LastIndexOf('|');
		if (num <= 0 || num >= key.Length - 1)
		{
			return false;
		}
		reminderId = key.Substring(0, num);
		string s = key.Substring(num + 1);
		if (!DateTime.TryParseExact(s, "yyyy-MM-ddTHH:mm", CultureInfo.InvariantCulture, DateTimeStyles.None, out scheduled))
		{
			return DateTime.TryParse(s, out scheduled);
		}
		return true;
	}

	private static AlertWorkItem WorkFromState(StateEntry st, List<Reminder> reminders)
	{
		if (st == null || !TryParseOccurrenceKey(st.Key, out var reminderId, out var scheduled))
		{
			return null;
		}
		Reminder reminder = reminders.FirstOrDefault((Reminder x) => x != null && string.Equals(x.Id, reminderId, StringComparison.Ordinal));
		if (reminder == null || reminder.Deleted || !reminder.Enabled)
		{
			return null;
		}
		return new AlertWorkItem
		{
			Reminder = reminder,
			Scheduled = scheduled,
			Key = st.Key
		};
	}

	public static List<AlertWorkItem> GetReadyQuietWork(DateTime now, SecuritySettings s)
	{
		List<AlertWorkItem> list = new List<AlertWorkItem>();
		StateStore stateStore = AppData.LoadState();
		List<Reminder> reminders = AppData.LoadReminders().Items ?? new List<Reminder>();
		foreach (StateEntry item in stateStore.Items.Where((StateEntry x) => x != null && string.Equals(x.Status, "quiet-held", StringComparison.OrdinalIgnoreCase)).ToList())
		{
			if (!DateTime.TryParse(item.Until, out var result) || now < result)
			{
				continue;
			}
			AlertWorkItem alertWorkItem = WorkFromState(item, reminders);
			if (alertWorkItem != null)
			{
				if (AwayActive(s, now) && !IsProtected(alertWorkItem.Reminder))
				{
					AppData.SetState(item.Key, "away-held", null);
					AppData.AddHistory(alertWorkItem.Reminder, "Moved from Quiet Hours hold to Away Mode", item.Key, alertWorkItem.Scheduled);
				}
				else if (QuietHoursActive(s, now) && !IsProtected(alertWorkItem.Reminder))
				{
					AppData.SetState(item.Key, "quiet-held", QuietHoursRelease(s, now));
				}
				else
				{
					list.Add(alertWorkItem);
				}
			}
		}
		return list.OrderBy((AlertWorkItem x) => x.Scheduled).ToList();
	}

	public static List<AlertWorkItem> GetAwayHeldWork()
	{
		List<AlertWorkItem> list = new List<AlertWorkItem>();
		StateStore stateStore = AppData.LoadState();
		List<Reminder> reminders = AppData.LoadReminders().Items ?? new List<Reminder>();
		foreach (StateEntry item in stateStore.Items.Where((StateEntry x) => x != null && string.Equals(x.Status, "away-held", StringComparison.OrdinalIgnoreCase)))
		{
			AlertWorkItem alertWorkItem = WorkFromState(item, reminders);
			if (alertWorkItem != null)
			{
				list.Add(alertWorkItem);
			}
		}
		return list.OrderBy((AlertWorkItem x) => x.Scheduled).ToList();
	}

	public static int AwayHeldCount()
	{
		try
		{
			return GetAwayHeldWork().Count;
		}
		catch
		{
			return 0;
		}
	}

	public static void EndAwayMode()
	{
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		securitySettings.AwayModeEnabled = "off";
		AppData.SaveSettings(securitySettings);
		AppData.AddSystemHistory("Away Mode", "Ended - home again", "away-mode-ended-" + DateTime.Now.ToString("yyyyMMddHHmm"), DateTime.Now);
	}

	public static string HomeStatus(SecuritySettings s, DateTime now)
	{
		ExpireAwayIfNeeded(s, now);
		int num = AwayHeldCount();
		if (AwayActive(s, now) && DateTime.TryParse(s.AwayModeUntil, out var result))
		{
			return "AWAY MODE • UNTIL " + result.ToString("ddd h:mm tt");
		}
		if (num > 0)
		{
			return "REVIEW " + num + " HELD ITEM" + ((num == 1) ? "" : "S");
		}
		if (QuietHoursActive(s, now))
		{
			return "QUIET HOURS • UNTIL " + QuietHoursRelease(s, now).ToString("h:mm tt");
		}
		return "QUIET / AWAY";
	}
}
