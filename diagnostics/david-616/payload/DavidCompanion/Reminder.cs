using System;

namespace DavidCompanion;

[Serializable]
public class Reminder
{
	public string Id { get; set; }

	public string Title { get; set; }

	public string Message { get; set; }

	public string Category { get; set; }

	public string Schedule { get; set; }

	public string Time { get; set; }

	public string EndTime { get; set; }

	public int IntervalHours { get; set; }

	public string WeeklyDay { get; set; }

	public string OnceDate { get; set; }

	public int SnoozeMinutes { get; set; }

	public bool Enabled { get; set; }

	public bool Voice { get; set; }

	public bool RepeatUntilDone { get; set; }

	public string Importance { get; set; }

	public string SpokenText { get; set; }

	public string CaregiverNote { get; set; }

	public string PicturePath { get; set; }

	public string RoutineName { get; set; }

	public int RoutineOrder { get; set; }

	public bool ConfirmDone { get; set; }

	public string SkipDates { get; set; }

	public string DateOverrides { get; set; }

	public string MedicationDose { get; set; }

	public string MedicationInstructions { get; set; }

	public string AppointmentWhere { get; set; }

	public int AppointmentPrepareMinutes { get; set; }

	public int AppointmentLeaveMinutes { get; set; }

	public string ModifiedUtc { get; set; }

	public bool Deleted { get; set; }
}
