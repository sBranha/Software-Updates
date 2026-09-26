using System;
using System.Collections.Generic;
using System.Linq;

namespace DavidCompanion;

public static class DailySupport
{
	public static DateTime TimeToday(string text, DateTime day, int fallbackHour)
	{
		if (!TimeSpan.TryParse(text, out var result))
		{
			result = TimeSpan.FromHours(fallbackHour);
		}
		return day.Date.Add(result);
	}

	public static List<Occurrence> GetRoutineOccurrences(string routineName, DateTime now)
	{
		if (string.IsNullOrWhiteSpace(routineName))
		{
			return new List<Occurrence>();
		}
		return (from o in Scheduler.Today(now)
			where o != null && o.Reminder != null && string.Equals((o.Reminder.RoutineName ?? "").Trim(), routineName.Trim(), StringComparison.OrdinalIgnoreCase)
			orderby (o.Reminder.RoutineOrder <= 0) ? 999 : o.Reminder.RoutineOrder, o.Due
			select o).ToList();
	}

	public static List<AlertWorkItem> GetRoutineWork(string routineName, DateTime now)
	{
		List<AlertWorkItem> list = new List<AlertWorkItem>();
		foreach (Occurrence routineOccurrence in GetRoutineOccurrences(routineName, now))
		{
			StateEntry state = AppData.GetState(routineOccurrence.Key);
			if (state == null || (!(state.Status == "done") && !(state.Status == "dismissed")))
			{
				list.Add(new AlertWorkItem
				{
					Reminder = routineOccurrence.Reminder,
					Scheduled = routineOccurrence.Due,
					Key = routineOccurrence.Key
				});
			}
		}
		return list;
	}

	public static List<AlertWorkItem> GetDueWork(DateTime now)
	{
		List<AlertWorkItem> list = new List<AlertWorkItem>();
		foreach (Occurrence item in Scheduler.Today(now))
		{
			if (item == null || item.Reminder == null || item.Due > now || !Scheduler.RoutineReady(item.Reminder, item.Due))
			{
				continue;
			}
			StateEntry state = AppData.GetState(item.Key);
			if (state == null || (!(state.Status == "done") && !(state.Status == "dismissed") && !(state.Status == "quiet-missed") && !(state.Status == "quiet-held") && !(state.Status == "away-held") && (!(state.Status == "snoozed") || !DateTime.TryParse(state.Until, out var result) || !(now < result))))
			{
				SecuritySettings s = AppData.LoadSettings() ?? new SecuritySettings();
				if (QuietAwayService.IsProtected(item.Reminder) || !QuietAwayService.UserQuietActive(s, now))
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

	public static string RoutineStateKey(bool morning, DateTime day)
	{
		return "daily-support-routine-" + (morning ? "morning-" : "evening-") + day.ToString("yyyy-MM-dd");
	}

	public static string CheckInStateKey(DateTime day)
	{
		return "daily-support-checkin-" + day.ToString("yyyy-MM-dd");
	}
}
