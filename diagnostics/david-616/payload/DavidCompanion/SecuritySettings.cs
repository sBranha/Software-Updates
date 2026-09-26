using System;

namespace DavidCompanion;

[Serializable]
public class SecuritySettings
{
	public string PinSalt { get; set; }

	public string PinHash { get; set; }

	public string StartupPreference { get; set; }

	public string FullScreenPreference { get; set; }

	public string LargeTextPreference { get; set; }

	public string QuietDashboardPreference { get; set; }

	public string HomeFullScreenPreference { get; set; }

	public string WakeScreenPreference { get; set; }

	public string VoiceName { get; set; }

	public int VoiceRate { get; set; }

	public int VoiceVolume { get; set; }

	public string EmergencyName { get; set; }

	public string EmergencyPhone { get; set; }

	public int EscalationMinutes { get; set; }

	public string CloudUrl { get; set; }

	public string CloudDeviceId { get; set; }

	public string CloudDeviceToken { get; set; }

	public string CloudPairCode { get; set; }

	public string CloudPairExpiresAt { get; set; }

	public string CloudLastSyncUtc { get; set; }

	public string CloudConnectedPreference { get; set; }

	public string CloudApiMode { get; set; }

	public string CloudPersonId { get; set; }

	public string PersonFullName { get; set; }

	public string PersonPreferredName { get; set; }

	public string PersonLocation { get; set; }

	public string PersonRoom { get; set; }

	public string LastChatNotifiedId { get; set; }

	public string HomeMessageId { get; set; }

	public string HomeMessageFrom { get; set; }

	public string HomeMessageText { get; set; }

	public string HomeMessageDismissedId { get; set; }

	public string SetupCompletedPreference { get; set; }

	public string MorningRoutineEnabled { get; set; }

	public string MorningRoutineName { get; set; }

	public string MorningRoutineTime { get; set; }

	public string EveningRoutineEnabled { get; set; }

	public string EveningRoutineName { get; set; }

	public string EveningRoutineTime { get; set; }

	public string CheckInEnabled { get; set; }

	public string CheckInTime { get; set; }

	public int CheckInGraceMinutes { get; set; }

	public string SmartQuietingEnabled { get; set; }

	public int SmartQuietAfterMinutes { get; set; }

	public string MissedSummaryEnabled { get; set; }

	public string MissedSummaryTime { get; set; }

	public string CaregiverDailySummaryEnabled { get; set; }

	public string CaregiverDailySummaryTime { get; set; }

	public string InactivityWatchEnabled { get; set; }

	public int InactivityWatchHours { get; set; }

	public string InactivityWatchStartTime { get; set; }

	public string InactivityWatchEndTime { get; set; }

	public string AutoBackupEnabled { get; set; }

	public int AutoBackupRetention { get; set; }

	public string QuietHoursEnabled { get; set; }

	public string QuietHoursStartTime { get; set; }

	public string QuietHoursEndTime { get; set; }

	public string AwayModeEnabled { get; set; }

	public string AwayModeUntil { get; set; }

	public string AwayModeNote { get; set; }

	public string MorningOverviewEnabled { get; set; }

	public string MorningOverviewTime { get; set; }

	public string UpdateAutoCheckEnabled { get; set; }

	public string UpdateLastCheckUtc { get; set; }

	public string UpdateLastNotifiedVersion { get; set; }

	public string UpdateLastCaregiverNotifiedVersion { get; set; }

	public string LicenseId { get; set; }

	public string LicensePublicId { get; set; }

	public string LicenseType { get; set; }

	public string LicenseStatus { get; set; }

	public string LicenseCustomerName { get; set; }

	public string LicenseEncryptedToken { get; set; }

	public string LicenseFingerprint { get; set; }

	public string LicenseActivatedAt { get; set; }

	public string LicenseLastValidatedUtc { get; set; }

	public string LicenseLastCheckAttemptUtc { get; set; }

	public string LicenseExpiresAt { get; set; }

	public string LicenseCloudPlan { get; set; }

	public string LicenseNeverExpires { get; set; }

	public string ServiceMode { get; set; }

	public string ServiceSupportMessage { get; set; }

	public string ServiceSupportUrl { get; set; }

	public string ServiceSuspendedMessage { get; set; }

	public string ServiceLastCheckAttemptUtc { get; set; }

	public string ServiceLastCheckUtc { get; set; }

	public SecuritySettings()
	{
		PinSalt = "";
		PinHash = "";
		StartupPreference = "";
		FullScreenPreference = "";
		LargeTextPreference = "";
		QuietDashboardPreference = "";
		HomeFullScreenPreference = "";
		WakeScreenPreference = "on";
		VoiceName = "";
		VoiceRate = 0;
		VoiceVolume = 100;
		EmergencyName = "";
		EmergencyPhone = "";
		EscalationMinutes = 15;
		CloudUrl = "";
		CloudDeviceId = "";
		CloudDeviceToken = "";
		CloudPairCode = "";
		CloudPairExpiresAt = "";
		CloudLastSyncUtc = "";
		CloudConnectedPreference = "off";
		CloudApiMode = "";
		CloudPersonId = "";
		PersonFullName = "David";
		PersonPreferredName = "David";
		PersonLocation = "";
		PersonRoom = "";
		LastChatNotifiedId = "";
		HomeMessageId = "";
		HomeMessageFrom = "";
		HomeMessageText = "";
		HomeMessageDismissedId = "";
		SetupCompletedPreference = "";
		MorningRoutineEnabled = "off";
		MorningRoutineName = "Morning";
		MorningRoutineTime = "08:00";
		EveningRoutineEnabled = "off";
		EveningRoutineName = "Evening";
		EveningRoutineTime = "20:00";
		CheckInEnabled = "off";
		CheckInTime = "12:00";
		CheckInGraceMinutes = 30;
		SmartQuietingEnabled = "on";
		SmartQuietAfterMinutes = 30;
		MissedSummaryEnabled = "on";
		MissedSummaryTime = "19:00";
		CaregiverDailySummaryEnabled = "off";
		CaregiverDailySummaryTime = "20:30";
		InactivityWatchEnabled = "off";
		InactivityWatchHours = 4;
		InactivityWatchStartTime = "08:00";
		InactivityWatchEndTime = "22:00";
		AutoBackupEnabled = "on";
		AutoBackupRetention = 7;
		QuietHoursEnabled = "off";
		QuietHoursStartTime = "22:00";
		QuietHoursEndTime = "07:00";
		AwayModeEnabled = "off";
		AwayModeUntil = "";
		AwayModeNote = "";
		MorningOverviewEnabled = "off";
		MorningOverviewTime = "08:15";
		UpdateAutoCheckEnabled = "on";
		UpdateLastCheckUtc = "";
		UpdateLastNotifiedVersion = "";
		UpdateLastCaregiverNotifiedVersion = "";
		LicenseId = "";
		LicensePublicId = "";
		LicenseType = "";
		LicenseStatus = "";
		LicenseCustomerName = "";
		LicenseEncryptedToken = "";
		LicenseFingerprint = "";
		LicenseActivatedAt = "";
		LicenseLastValidatedUtc = "";
		LicenseLastCheckAttemptUtc = "";
		LicenseExpiresAt = "";
		LicenseCloudPlan = "";
		LicenseNeverExpires = "";
		ServiceMode = "on";
		ServiceSupportMessage = "";
		ServiceSupportUrl = "";
		ServiceSuspendedMessage = "";
		ServiceLastCheckAttemptUtc = "";
		ServiceLastCheckUtc = "";
	}
}
