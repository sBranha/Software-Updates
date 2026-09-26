using System;
using System.Collections.Generic;
using System.Linq;

namespace DavidCompanion;

public static class DailySummary
{
	public static bool IsHighPriority(Reminder r)
	{
		if (r == null)
		{
			return false;
		}
		if (!r.RepeatUntilDone && !string.Equals(r.Importance, "Important", StringComparison.OrdinalIgnoreCase) && !string.Equals(r.Importance, "Critical", StringComparison.OrdinalIgnoreCase))
		{
			return string.Equals(r.Category, "Medication", StringComparison.OrdinalIgnoreCase);
		}
		return true;
	}

	public static List<AlertWorkItem> GetMissedWork(DateTime now)
	{
		List<AlertWorkItem> list = new List<AlertWorkItem>();
		foreach (Occurrence item in Scheduler.Today(now))
		{
			if (item != null && item.Reminder != null && !(item.Due > now))
			{
				StateEntry state = AppData.GetState(item.Key);
				if (state != null && (string.Equals(state.Status, "dismissed", StringComparison.OrdinalIgnoreCase) || string.Equals(state.Status, "quiet-missed", StringComparison.OrdinalIgnoreCase)))
				{
					list.Add(new AlertWorkItem
					{
						Reminder = item.Reminder,
						Scheduled = item.Due,
						Key = item.Key
					});
				}
			}
		}
		return list.OrderBy((AlertWorkItem x) => x.Scheduled).ToList();
	}

	public static string CaregiverText(DateTime now)
	{
		List<Occurrence> list = Scheduler.Today(now);
		int count = list.Count;
		int num = 0;
		int num2 = 0;
		int num3 = 0;
		int num4 = 0;
		int num5 = 0;
		foreach (Occurrence item in list)
		{
			StateEntry state = AppData.GetState(item.Key);
			bool flag = state != null && string.Equals(state.Status, "done", StringComparison.OrdinalIgnoreCase);
			bool flag2 = state != null && (string.Equals(state.Status, "dismissed", StringComparison.OrdinalIgnoreCase) || string.Equals(state.Status, "quiet-missed", StringComparison.OrdinalIgnoreCase) || string.Equals(state.Status, "quiet-held", StringComparison.OrdinalIgnoreCase) || string.Equals(state.Status, "away-held", StringComparison.OrdinalIgnoreCase));
			if (flag)
			{
				num++;
			}
			else if (flag2)
			{
				num2++;
			}
			else if (item.Due <= now)
			{
				num3++;
			}
			if (string.Equals(item.Reminder.Category, "Medication", StringComparison.OrdinalIgnoreCase))
			{
				num4++;
				if (flag)
				{
					num5++;
				}
			}
		}
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		string text;
		if (string.IsNullOrWhiteSpace(securitySettings.PersonPreferredName))
		{
			text = (string.IsNullOrWhiteSpace(securitySettings.PersonFullName) ? "David" : securitySettings.PersonFullName);
		}
		else
		{
			text = securitySettings.PersonPreferredName;
		}
		string text2 = "Not scheduled";
		if (string.Equals(securitySettings.CheckInEnabled, "on", StringComparison.OrdinalIgnoreCase))
		{
			StateEntry state2 = AppData.GetState(DailySupport.CheckInStateKey(now));
			if (state2 == null)
			{
				text2 = "Waiting";
			}
			else if (string.Equals(state2.Status, "done", StringComparison.OrdinalIgnoreCase))
			{
				text2 = "OK";
			}
			else
			{
				text2 = ((!string.Equals(state2.Status, "missed", StringComparison.OrdinalIgnoreCase)) ? (state2.Status ?? "Waiting") : "MISSED");
			}
		}
		string text3 = ((num4 == 0) ? "No medication reminders today" : (num5 + " of " + num4 + " medication reminders marked taken"));
		return text + " daily summary — " + now.ToString("dddd, MMM d") + "\r\n" + count + " scheduled • " + num + " completed • " + num2 + " missed/put aside • " + num3 + " still waiting\r\n" + text3 + "\r\nI'M OKAY check-in: " + text2;
	}

	public static string MissedSummaryKey(DateTime day)
	{
		return "daily-missed-summary-" + day.ToString("yyyy-MM-dd");
	}

	public static string CaregiverSummaryKey(DateTime day)
	{
		return "caregiver-daily-summary-" + day.ToString("yyyy-MM-dd");
	}
}
