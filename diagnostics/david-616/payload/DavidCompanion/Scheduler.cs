using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;

namespace DavidCompanion;

public static class Scheduler
{
	public static bool IsIntervalSchedule(string schedule)
	{
		string text = (schedule ?? "").Trim();
		if (text.StartsWith("Every ", StringComparison.OrdinalIgnoreCase))
		{
			return text.IndexOf("hour", StringComparison.OrdinalIgnoreCase) >= 0;
		}
		return false;
	}

	public static int IntervalHours(Reminder r)
	{
		if (r != null && r.IntervalHours > 0)
		{
			return Math.Min(24, Math.Max(1, r.IntervalHours));
		}
		string text = ((r == null) ? "" : (r.Schedule ?? ""));
		if (string.Equals(text, "Every hour", StringComparison.OrdinalIgnoreCase))
		{
			return 1;
		}
		if (!int.TryParse(new string(text.Where(char.IsDigit).ToArray()), out var result))
		{
			return 1;
		}
		return Math.Min(24, Math.Max(1, result));
	}

	private static bool AppliesOnDate(Reminder r, DateTime date)
	{
		string a = date.ToString("yyyy-MM-dd");
		string text = r.Schedule ?? "";
		if (IsIntervalSchedule(text))
		{
			return true;
		}
		switch (text)
		{
		case "Daily":
			return true;
		case "Weekdays":
			if (date.DayOfWeek != DayOfWeek.Saturday)
			{
				return date.DayOfWeek != DayOfWeek.Sunday;
			}
			return false;
		case "Weekly":
			return string.Equals(date.DayOfWeek.ToString(), r.WeeklyDay, StringComparison.OrdinalIgnoreCase);
		case "Once":
			return string.Equals(a, r.OnceDate, StringComparison.Ordinal);
		default:
			return false;
		}
	}

	public static List<DateTime> DueTimesForDate(Reminder r, DateTime date)
	{
		List<DateTime> list = new List<DateTime>();
		if (r == null || !r.Enabled || r.Deleted)
		{
			return list;
		}
		string date2 = date.ToString("yyyy-MM-dd");
		if (DateListed(r.SkipDates, date2) || !AppliesOnDate(r, date))
		{
			return list;
		}
		TimeSpan result;
		if (TryGetOverride(r.DateOverrides, date2, out var time))
		{
			result = time;
		}
		else if (!TimeSpan.TryParseExact(r.Time ?? "", "hh\\:mm", CultureInfo.InvariantCulture, out result) && !TimeSpan.TryParse(r.Time ?? "", out result))
		{
			return list;
		}
		if (!IsIntervalSchedule(r.Schedule))
		{
			list.Add(date.Date.Add(result));
			return list;
		}
		if (!TimeSpan.TryParseExact(r.EndTime ?? "", "hh\\:mm", CultureInfo.InvariantCulture, out var result2) && !TimeSpan.TryParse(r.EndTime ?? "", out result2))
		{
			result2 = result;
		}
		if (result2 < result)
		{
			result2 = result;
		}
		int num = IntervalHours(r);
		TimeSpan timeSpan = result;
		while (timeSpan <= result2)
		{
			list.Add(date.Date.Add(timeSpan));
			timeSpan = timeSpan.Add(TimeSpan.FromHours(num));
		}
		return list;
	}

	public static DateTime? DueForDate(Reminder r, DateTime date)
	{
		List<DateTime> list = DueTimesForDate(r, date);
		if (list.Count != 0)
		{
			return list[0];
		}
		return null;
	}

	private static bool DateListed(string value, string date)
	{
		if (string.IsNullOrWhiteSpace(value))
		{
			return false;
		}
		string[] array = value.Split(new char[4] { ',', ';', '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries);
		for (int i = 0; i < array.Length; i++)
		{
			if (string.Equals(array[i].Trim(), date, StringComparison.OrdinalIgnoreCase))
			{
				return true;
			}
		}
		return false;
	}

	private static bool TryGetOverride(string value, string date, out TimeSpan time)
	{
		time = TimeSpan.Zero;
		if (string.IsNullOrWhiteSpace(value))
		{
			return false;
		}
		string[] array = value.Split(new char[3] { ';', '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries);
		for (int i = 0; i < array.Length; i++)
		{
			string[] array2 = array[i].Split(new char[1] { '=' }, 2);
			if (array2.Length == 2 && string.Equals(array2[0].Trim(), date, StringComparison.OrdinalIgnoreCase))
			{
				return TimeSpan.TryParse(array2[1].Trim(), out time);
			}
		}
		return false;
	}

	public static Occurrence GetOccurrence(Reminder r, DateTime now)
	{
		List<DateTime> list = DueTimesForDate(r, now.Date);
		if (list.Count == 0)
		{
			return null;
		}
		DateTime due = list[0];
		foreach (DateTime item in list)
		{
			if (item <= now)
			{
				due = item;
				continue;
			}
			break;
		}
		return new Occurrence
		{
			Due = due,
			Key = MakeKey(r, due),
			Reminder = r
		};
	}

	public static string MakeKey(Reminder r, DateTime due)
	{
		return r.Id + "|" + due.ToString("yyyy-MM-ddTHH:mm");
	}

	public static List<Occurrence> Today(DateTime now)
	{
		List<Occurrence> list = new List<Occurrence>();
		foreach (Reminder item in AppData.LoadReminders().Items ?? new List<Reminder>())
		{
			foreach (DateTime item2 in DueTimesForDate(item, now.Date))
			{
				list.Add(new Occurrence
				{
					Due = item2,
					Key = MakeKey(item, item2),
					Reminder = item
				});
			}
		}
		return (from x in list
			orderby x.Due, x.Reminder.RoutineOrder
			select x).ToList();
	}

	public static Occurrence Next(DateTime now)
	{
		List<Reminder> list = AppData.LoadReminders().Items ?? new List<Reminder>();
		Occurrence occurrence = null;
		for (int i = 0; i < 8; i++)
		{
			DateTime date = now.Date.AddDays(i);
			foreach (Reminder item in list)
			{
				foreach (DateTime item2 in DueTimesForDate(item, date))
				{
					if (!(item2 <= now))
					{
						string key = MakeKey(item, item2);
						StateEntry state = AppData.GetState(key);
						if ((state == null || (!(state.Status == "done") && !(state.Status == "dismissed"))) && (occurrence == null || item2 < occurrence.Due))
						{
							occurrence = new Occurrence
							{
								Due = item2,
								Key = key,
								Reminder = item
							};
						}
					}
				}
			}
			if (occurrence != null)
			{
				break;
			}
		}
		return occurrence;
	}

	public static bool RoutineReady(Reminder r, DateTime due)
	{
		if (r == null || string.IsNullOrWhiteSpace(r.RoutineName) || r.RoutineOrder <= 1)
		{
			return true;
		}
		foreach (Reminder item in AppData.LoadReminders().Items ?? new List<Reminder>())
		{
			if (!item.Enabled || item.Deleted || !string.Equals(item.RoutineName ?? "", r.RoutineName ?? "", StringComparison.OrdinalIgnoreCase) || item.RoutineOrder <= 0 || item.RoutineOrder >= r.RoutineOrder)
			{
				continue;
			}
			List<DateTime> list = (from x in DueTimesForDate(item, due.Date)
				where x <= due
				select x).ToList();
			if (list.Count != 0)
			{
				DateTime due2 = list[list.Count - 1];
				StateEntry state = AppData.GetState(MakeKey(item, due2));
				if (state == null || state.Status != "done")
				{
					return false;
				}
			}
		}
		return true;
	}
}
