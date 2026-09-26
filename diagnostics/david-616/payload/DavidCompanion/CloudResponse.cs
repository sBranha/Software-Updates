using System.Collections.Generic;

namespace DavidCompanion;

public class CloudResponse
{
	public bool ok { get; set; }

	public string error { get; set; }

	public bool done { get; set; }

	public string state { get; set; }

	public string occurrenceKey { get; set; }

	public string deviceId { get; set; }

	public string deviceToken { get; set; }

	public string licenseToken { get; set; }

	public string pairCode { get; set; }

	public string pairExpiresAt { get; set; }

	public string serverTime { get; set; }

	public string testedAt { get; set; }

	public List<Reminder> reminders { get; set; }

	public List<CloudOccurrenceState> occurrenceStates { get; set; }

	public CloudHelp help { get; set; }

	public CloudPersonProfile profile { get; set; }

	public CloudPersonProfile person { get; set; }

	public DavidLicenseInfo license { get; set; }

	public CloudDeviceInfo device { get; set; }

	public List<CloudDeviceInfo> devices { get; set; }

	public List<CloudChatMessage> chat { get; set; }

	public string helpId { get; set; }

	public string status { get; set; }

	public string startedAt { get; set; }

	public string claimedBy { get; set; }

	public string claimedName { get; set; }

	public string claimedAt { get; set; }

	public string resolvedAt { get; set; }
}
