using System;

namespace DavidCompanion;

[Serializable]
public class HistoryEntry
{
	public string Timestamp { get; set; }

	public string ReminderId { get; set; }

	public string Title { get; set; }

	public string Action { get; set; }

	public string OccurrenceKey { get; set; }

	public string ScheduledFor { get; set; }
}
