using System;
using System.Collections.Generic;

namespace DavidCompanion;

[Serializable]
public class ReminderStore
{
	public List<Reminder> Items { get; set; }

	public ReminderStore()
	{
		Items = new List<Reminder>();
	}
}
