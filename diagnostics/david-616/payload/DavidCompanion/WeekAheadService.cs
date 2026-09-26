using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace DavidCompanion;

public static class WeekAheadService
{
	public static List<Occurrence> NextDays(DateTime now, int days)
	{
		List<Occurrence> list = new List<Occurrence>();
		List<Reminder> list2 = AppData.LoadReminders().Items ?? new List<Reminder>();
		int num = Math.Max(1, Math.Min(14, days));
		for (int i = 0; i < num; i++)
		{
			DateTime date = now.Date.AddDays(i);
			foreach (Reminder item in list2)
			{
				DateTime? dateTime = Scheduler.DueForDate(item, date);
				if (dateTime.HasValue)
				{
					list.Add(new Occurrence
					{
						Due = dateTime.Value,
						Key = Scheduler.MakeKey(item, dateTime.Value),
						Reminder = item
					});
				}
			}
		}
		return (from x in list
			orderby x.Due, (x.Reminder != null) ? x.Reminder.RoutineOrder : 0
			select x).ToList();
	}

	public static string FriendlyDay(DateTime day, DateTime now)
	{
		if (day.Date == now.Date)
		{
			return "TODAY  •  " + day.ToString("dddd, MMMM d");
		}
		if (day.Date == now.Date.AddDays(1.0))
		{
			return "TOMORROW  •  " + day.ToString("dddd, MMMM d");
		}
		return day.ToString("dddd, MMMM d").ToUpperInvariant();
	}

	public static string Status(Occurrence o, DateTime now)
	{
		if (o == null)
		{
			return "";
		}
		StateEntry state = AppData.GetState(o.Key);
		if (state != null && string.Equals(state.Status, "done", StringComparison.OrdinalIgnoreCase))
		{
			return "DONE";
		}
		if (state != null && string.Equals(state.Status, "snoozed", StringComparison.OrdinalIgnoreCase))
		{
			return "SNOOZED";
		}
		if (state != null && (string.Equals(state.Status, "quiet-held", StringComparison.OrdinalIgnoreCase) || string.Equals(state.Status, "away-held", StringComparison.OrdinalIgnoreCase)))
		{
			return "HELD";
		}
		if (o.Due < now)
		{
			return "WAITING";
		}
		return "SCHEDULED";
	}

	public static string MorningOverviewKey(DateTime day)
	{
		return "orientation-morning-" + day.ToString("yyyy-MM-dd");
	}

	public static string SpokenMorningOverview(DateTime now)
	{
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
		List<Occurrence> source = NextDays(now, 2);
		List<Occurrence> list = source.Where((Occurrence x) => x.Due.Date == now.Date).ToList();
		List<Occurrence> list2 = source.Where((Occurrence x) => x.Due.Date == now.Date.AddDays(1.0)).ToList();
		Occurrence occurrence = list.FirstOrDefault((Occurrence x) => x.Due >= now);
		string text2 = "Good morning, " + text + ". Today is " + now.ToString("dddd, MMMM d") + ". ";
		if (list.Count == 0)
		{
			text2 += "You have nothing scheduled today. ";
		}
		else
		{
			object obj = text2;
			text2 = string.Concat(obj, "You have ", list.Count, " ", (list.Count == 1) ? "thing" : "things", " scheduled today. ");
		}
		if (occurrence != null)
		{
			string text3 = text2;
			text2 = text3 + "Your next item is " + ((occurrence.Reminder == null) ? "a reminder" : occurrence.Reminder.Title) + " at " + occurrence.Due.ToString("h:mm tt") + ". ";
		}
		if (list2.Count == 0)
		{
			return text2 + "Nothing is scheduled for tomorrow.";
		}
		object obj2 = text2;
		return string.Concat(obj2, "Tomorrow you have ", list2.Count, " ", (list2.Count == 1) ? "thing" : "things", " planned.");
	}

	public static string SpokenWeekOverview(DateTime now)
	{
		List<Occurrence> list = NextDays(now, 7);
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.Append("Here is what is coming up. ");
		for (int i = 0; i < 7; i++)
		{
			DateTime day = now.Date.AddDays(i);
			List<Occurrence> list2 = list.Where((Occurrence x) => x.Due.Date == day).ToList();
			string text = i switch
			{
				1 => "Tomorrow", 
				0 => "Today", 
				_ => day.ToString("dddd"), 
			};
			if (list2.Count != 0)
			{
				stringBuilder.Append(text + " you have " + list2.Count + " " + ((list2.Count == 1) ? "thing" : "things") + ". ");
				Occurrence occurrence = list2.FirstOrDefault((Occurrence x) => x.Reminder != null && string.Equals(x.Reminder.Category, "Appointment", StringComparison.OrdinalIgnoreCase));
				if (occurrence != null)
				{
					stringBuilder.Append("Your appointment is " + occurrence.Reminder.Title + " at " + occurrence.Due.ToString("h:mm tt") + ". ");
				}
			}
		}
		if (list.Count == 0)
		{
			stringBuilder.Append("Nothing is scheduled for the next seven days.");
		}
		return stringBuilder.ToString();
	}
}
