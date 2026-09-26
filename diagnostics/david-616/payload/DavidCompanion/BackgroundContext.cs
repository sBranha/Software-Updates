using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using System.Windows.Forms;

namespace DavidCompanion;

public class BackgroundContext : ApplicationContext
{
	private const int CatchUpHours = 6;

	private NotifyIcon tray;

	private System.Windows.Forms.Timer timer;

	private Mutex mutex;

	private int cloudBusy;

	private int awarenessBusy;

	private int updateCheckBusy;

	private int reminderDialogOpen;

	private object morningOverviewSpeech;

	public bool AlreadyRunning { get; private set; }

	public BackgroundContext()
	{
		mutex = new Mutex(initiallyOwned: true, "David_Background_Process_v300", out var createdNew);
		if (!createdNew)
		{
			AlreadyRunning = true;
			mutex.Dispose();
			mutex = null;
			return;
		}
		tray = new NotifyIcon();
		tray.Text = "David is running";
		tray.Icon = Program.TryLoadIcon();
		tray.Visible = true;
		ContextMenuStrip contextMenuStrip = new ContextMenuStrip();
		ToolStripItem toolStripItem = contextMenuStrip.Items.Add("Open David");
		ToolStripItem toolStripItem2 = contextMenuStrip.Items.Add("Caregiver Manager");
		contextMenuStrip.Items.Add(new ToolStripSeparator());
		ToolStripItem toolStripItem3 = contextMenuStrip.Items.Add("Exit David");
		tray.ContextMenuStrip = contextMenuStrip;
		toolStripItem.Click += (object param0, EventArgs param1) =>
		{
			Program.StartHomeProcess();
		};
		tray.DoubleClick += (object param0, EventArgs param1) =>
		{
			Program.StartHomeProcess();
		};
		tray.BalloonTipClicked += (object param0, EventArgs param1) =>
		{
			Program.StartHomeProcess();
		};
		toolStripItem2.Click += (object param0, EventArgs param1) =>
		{
			Program.StartCaregiverProcess();
		};
		toolStripItem3.Click += (object param0, EventArgs param1) =>
		{
			if (PinSecurity.Prompt("Enter the caregiver PIN to stop David."))
			{
				ExitThread();
			}
		};
		timer = new System.Windows.Forms.Timer();
		timer.Interval = 20000;
		timer.Tick += (object param0, EventArgs param1) =>
		{
			CheckDailySupport();
			CheckAppointmentNotices();
			CheckConnectedCare();
			CheckReminders();
			CheckAwareness();
			CheckUpdates();
			CheckLicense();
			AppData.AutoBackupIfNeeded();
		};
		timer.Start();
		CheckDailySupport();
		CheckAppointmentNotices();
		CheckReminders();
		CheckConnectedCare();
		CheckAwareness();
		CheckUpdates();
		CheckLicense();
		AppData.AutoBackupIfNeeded();
	}

	private void CheckLicense()
	{
		try
		{
			LicenseService.ValidateIfDue(force: false, out var _);
		}
		catch
		{
		}
	}

	private void CheckUpdates()
	{
		try
		{
			SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
			ServiceControl.Refresh(force: false);
			if (ServiceControl.IsSuspended() || string.Equals(securitySettings.UpdateAutoCheckEnabled, "off", StringComparison.OrdinalIgnoreCase) || (DateTime.TryParse(securitySettings.UpdateLastCheckUtc, out var result) && (DateTime.UtcNow - result.ToUniversalTime()).TotalHours < 6.0) || Interlocked.Exchange(ref updateCheckBusy, 1) == 1)
			{
				return;
			}
			ThreadPool.QueueUserWorkItem((object param0) =>
			{
				try
				{
					bool flag = UpdateService.Check(out var manifest, out var _);
					SecuritySettings securitySettings2 = AppData.LoadSettings() ?? new SecuritySettings();
					securitySettings2.UpdateLastCheckUtc = DateTime.UtcNow.ToString("o");
					if (flag && manifest != null && UpdateService.IsNewer(manifest.version, "6.1.6"))
					{
						if (!string.Equals(securitySettings2.UpdateLastNotifiedVersion, manifest.version, StringComparison.OrdinalIgnoreCase))
						{
							securitySettings2.UpdateLastNotifiedVersion = manifest.version;
							try
							{
								tray.ShowBalloonTip(15000, "Caregiver update available", "David " + manifest.version + " is ready. Open Caregiver Manager and choose CHECK UPDATES.", ToolTipIcon.Info);
							}
							catch
							{
							}
						}
						AppData.SaveSettings(securitySettings2);
						UpdateService.NotifyLinkedCaregiver(manifest);
					}
					else
					{
						AppData.SaveSettings(securitySettings2);
					}
				}
				catch
				{
				}
				finally
				{
					Interlocked.Exchange(ref updateCheckBusy, 0);
				}
			});
		}
		catch
		{
			Interlocked.Exchange(ref updateCheckBusy, 0);
		}
	}

	private void CheckDailySupport()
	{
		try
		{
			DateTime now = DateTime.Now;
			SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
			if (string.Equals(securitySettings.CheckInEnabled, "on", StringComparison.OrdinalIgnoreCase))
			{
				DateTime dateTime = DailySupport.TimeToday(securitySettings.CheckInTime, now, 12);
				string text = DailySupport.CheckInStateKey(now);
				StateEntry state = AppData.GetState(text);
				if (now >= dateTime && (now - dateTime).TotalHours <= 12.0 && state == null)
				{
					using CheckInForm checkInForm = new CheckInForm(text, dateTime, (securitySettings.CheckInGraceMinutes <= 0) ? 30 : securitySettings.CheckInGraceMinutes);
					checkInForm.ShowDialog();
				}
			}
			CheckMorningOverview(securitySettings, now);
			CheckRoutinePrompt(morning: true, securitySettings, now);
			CheckRoutinePrompt(morning: false, securitySettings, now);
			CheckMissedSummary(securitySettings, now);
			CheckCaregiverDailySummary(securitySettings, now);
		}
		catch
		{
		}
	}

	private void CheckMorningOverview(SecuritySettings s, DateTime now)
	{
		if (QuietAwayService.UserQuietActive(s, now) || !string.Equals(s.MorningOverviewEnabled, "on", StringComparison.OrdinalIgnoreCase))
		{
			return;
		}
		DateTime dateTime = DailySupport.TimeToday(s.MorningOverviewTime, now, 8);
		if (!(now < dateTime) && !((now - dateTime).TotalHours > 3.0))
		{
			string key = WeekAheadService.MorningOverviewKey(now);
			if (AppData.GetState(key) == null)
			{
				AppData.SetState(key, "spoken", null);
				AppData.AddSystemHistory("Morning Overview", "Spoken", key, dateTime);
				VoiceService.Stop(morningOverviewSpeech);
				morningOverviewSpeech = VoiceService.Speak(WeekAheadService.SpokenMorningOverview(now), s.VoiceName, s.VoiceRate, s.VoiceVolume);
			}
		}
	}

	private void CheckMissedSummary(SecuritySettings s, DateTime now)
	{
		if (QuietAwayService.UserQuietActive(s, now) || !string.Equals(s.MissedSummaryEnabled, "on", StringComparison.OrdinalIgnoreCase))
		{
			return;
		}
		DateTime dateTime = DailySupport.TimeToday(s.MissedSummaryTime, now, 19);
		if (now < dateTime || (now - dateTime).TotalHours > 5.0)
		{
			return;
		}
		List<AlertWorkItem> missedWork = DailySummary.GetMissedWork(now);
		if (missedWork.Count == 0)
		{
			return;
		}
		string key = DailySummary.MissedSummaryKey(now);
		if (AppData.GetState(key) != null)
		{
			return;
		}
		AppData.SetState(key, "shown", null);
		AppData.AddSystemHistory("Missed Items Summary", "Shown with " + missedWork.Count + " item" + ((missedWork.Count == 1) ? "" : "s"), key, dateTime);
		using MissedSummaryForm missedSummaryForm = new MissedSummaryForm(now);
		missedSummaryForm.ShowDialog();
	}

	private void CheckCaregiverDailySummary(SecuritySettings s, DateTime now)
	{
		if (!string.Equals(s.CaregiverDailySummaryEnabled, "on", StringComparison.OrdinalIgnoreCase) || !CloudClient.IsConnected())
		{
			return;
		}
		DateTime when = DailySupport.TimeToday(s.CaregiverDailySummaryTime, now, 20);
		if (now < when || (now - when).TotalHours > 8.0)
		{
			return;
		}
		string key = DailySummary.CaregiverSummaryKey(now);
		StateEntry state = AppData.GetState(key);
		if ((state != null && string.Equals(state.Status, "sent", StringComparison.OrdinalIgnoreCase)) || (state != null && (string.Equals(state.Status, "sending", StringComparison.OrdinalIgnoreCase) || string.Equals(state.Status, "failed", StringComparison.OrdinalIgnoreCase)) && DateTime.TryParse(state.Updated, out var result) && (now - result).TotalMinutes < 15.0))
		{
			return;
		}
		AppData.SetState(key, "sending", null);
		string text = DailySummary.CaregiverText(now);
		ThreadPool.QueueUserWorkItem((object param0) =>
		{
			try
			{
				CloudResponse cloudResponse = CloudClient.ReplyChat(text, "", "daily-summary");
				if (cloudResponse != null && cloudResponse.ok)
				{
					AppData.SetState(key, "sent", null);
					AppData.AddSystemHistory("Caregiver Daily Summary", "Sent", key, when);
				}
				else
				{
					AppData.SetState(key, "failed", null);
				}
			}
			catch
			{
				AppData.SetState(key, "failed", null);
			}
		});
	}

	private void CheckRoutinePrompt(bool morning, SecuritySettings s, DateTime now)
	{
		if (QuietAwayService.UserQuietActive(s, now) || !string.Equals(morning ? s.MorningRoutineEnabled : s.EveningRoutineEnabled, "on", StringComparison.OrdinalIgnoreCase))
		{
			return;
		}
		string text = (morning ? s.MorningRoutineName : s.EveningRoutineName);
		if (string.IsNullOrWhiteSpace(text))
		{
			text = (morning ? "Morning" : "Evening");
		}
		if (DailySupport.GetRoutineOccurrences(text, now).Count == 0)
		{
			return;
		}
		DateTime dateTime = DailySupport.TimeToday(morning ? s.MorningRoutineTime : s.EveningRoutineTime, now, morning ? 8 : 20);
		if (now < dateTime || (now - dateTime).TotalHours > 3.0)
		{
			return;
		}
		string key = DailySupport.RoutineStateKey(morning, now);
		if (AppData.GetState(key) != null)
		{
			return;
		}
		AppData.SetState(key, "shown", null);
		using RoutineModeForm routineModeForm = new RoutineModeForm(text, key, auto: true);
		routineModeForm.ShowDialog();
	}

	private void CheckAppointmentNotices()
	{
		try
		{
			DateTime now = DateTime.Now;
			foreach (Reminder item in AppData.LoadReminders().Items ?? new List<Reminder>())
			{
				if (item == null || !item.Enabled || item.Deleted || !string.Equals(item.Category, "Appointment", StringComparison.OrdinalIgnoreCase))
				{
					continue;
				}
				for (int i = 0; i <= 1; i++)
				{
					DateTime? dateTime = Scheduler.DueForDate(item, now.Date.AddDays(i));
					if (dateTime.HasValue)
					{
						DateTime value = dateTime.Value;
						CheckAppointmentStage(item, value, "tomorrow", value.Date.AddDays(-1.0).AddHours(9.0), now, 720);
						if (item.AppointmentPrepareMinutes > 0)
						{
							CheckAppointmentStage(item, value, "prepare", value.AddMinutes(-item.AppointmentPrepareMinutes), now, 60);
						}
						if (item.AppointmentLeaveMinutes > 0)
						{
							CheckAppointmentStage(item, value, "leave", value.AddMinutes(-item.AppointmentLeaveMinutes), now, 60);
						}
					}
				}
			}
		}
		catch
		{
		}
	}

	private void CheckAppointmentStage(Reminder r, DateTime due, string stage, DateTime noticeAt, DateTime now, int windowMinutes)
	{
		if (now >= due || now < noticeAt || (now - noticeAt).TotalMinutes > (double)windowMinutes)
		{
			return;
		}
		string key = "appointment-notice-" + stage + "-" + Scheduler.MakeKey(r, due);
		if (AppData.GetState(key) != null)
		{
			return;
		}
		AppData.SetState(key, "shown", null);
		AppData.AddSystemHistory(r.Title ?? "Appointment", "Appointment notice: " + stage, key, due);
		using AppointmentNoticeForm appointmentNoticeForm = new AppointmentNoticeForm(r, due, stage);
		appointmentNoticeForm.ShowDialog();
	}

	private void CheckConnectedCare()
	{
		if (!CloudClient.IsConnected() || Interlocked.Exchange(ref cloudBusy, 1) == 1)
		{
			return;
		}
		ThreadPool.QueueUserWorkItem((object param0) =>
		{
			try
			{
				CloudResponse cloudResponse = CloudClient.SyncNow();
				if (cloudResponse != null && cloudResponse.ok && cloudResponse.chat != null)
				{
					CloudChatMessage cloudChatMessage = cloudResponse.chat.LastOrDefault((CloudChatMessage x) => x != null && string.Equals(x.senderKind, "caregiver", StringComparison.OrdinalIgnoreCase));
					if (cloudChatMessage != null)
					{
						SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
						if (!string.Equals(securitySettings.LastChatNotifiedId, cloudChatMessage.id, StringComparison.Ordinal))
						{
							securitySettings.LastChatNotifiedId = cloudChatMessage.id ?? "";
							securitySettings.HomeMessageId = cloudChatMessage.id ?? "";
							securitySettings.HomeMessageFrom = (string.IsNullOrWhiteSpace(cloudChatMessage.senderName) ? "Caregiver" : cloudChatMessage.senderName);
							securitySettings.HomeMessageText = cloudChatMessage.body ?? "";
							AppData.SaveSettings(securitySettings);
							try
							{
								tray.ShowBalloonTip(12000, (string.IsNullOrWhiteSpace(cloudChatMessage.senderName) ? "Caregiver" : cloudChatMessage.senderName) + " sent a message", cloudChatMessage.body ?? "Open David Chat to reply.", ToolTipIcon.Info);
								return;
							}
							catch
							{
								return;
							}
						}
					}
				}
			}
			catch
			{
			}
			finally
			{
				Interlocked.Exchange(ref cloudBusy, 0);
			}
		});
	}

	private void CheckAwareness()
	{
		try
		{
			DateTime now = DateTime.Now;
			SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
			if (SystemAwareness.LowBattery())
			{
				string key = "system-low-battery-" + now.ToString("yyyy-MM-dd");
				if (AppData.GetState(key) == null)
				{
					AppData.SetState(key, "shown", null);
					try
					{
						tray.ShowBalloonTip(12000, "David needs power", "Battery is low. Please plug this computer in.", ToolTipIcon.Warning);
					}
					catch
					{
					}
				}
			}
			if (!string.Equals(securitySettings.InactivityWatchEnabled, "on", StringComparison.OrdinalIgnoreCase) || !CloudClient.IsConnected() || !SystemAwareness.InWatchWindow(securitySettings, now, out var _))
			{
				return;
			}
			double num = SystemAwareness.EffectiveWatchIdleHours(securitySettings, now);
			int num2 = ((securitySettings.InactivityWatchHours <= 0) ? 4 : securitySettings.InactivityWatchHours);
			string key2 = "caregiver-inactivity-alert-" + now.ToString("yyyy-MM-dd");
			string key3 = "caregiver-inactivity-recovered-" + now.ToString("yyyy-MM-dd");
			StateEntry state = AppData.GetState(key2);
			StateEntry state2 = AppData.GetState(key3);
			if (num >= (double)num2)
			{
				if (state == null || string.Equals(state.Status, "failed", StringComparison.OrdinalIgnoreCase))
				{
					SendAwareness(key2, "Activity notice: David's Windows computer has not received keyboard or mouse input for about " + Math.Max(num2, (int)Math.Floor(num)) + " hours during the caregiver watch period. This does not confirm an emergency. Please check in if appropriate.", "Inactivity notice sent");
				}
			}
			else if (state != null && string.Equals(state.Status, "sent", StringComparison.OrdinalIgnoreCase) && state2 == null && SystemAwareness.IdleMinutes() < 10.0)
			{
				SendAwareness(key3, "Activity update: David's Windows computer is active again.", "Activity resumed notice sent");
			}
		}
		catch
		{
		}
	}

	private void SendAwareness(string key, string text, string history)
	{
		StateEntry state = AppData.GetState(key);
		if ((state != null && string.Equals(state.Status, "sending", StringComparison.OrdinalIgnoreCase) && DateTime.TryParse(state.Updated, out var result) && (DateTime.Now - result).TotalMinutes < 15.0) || Interlocked.Exchange(ref awarenessBusy, 1) == 1)
		{
			return;
		}
		AppData.SetState(key, "sending", null);
		ThreadPool.QueueUserWorkItem((object param0) =>
		{
			try
			{
				CloudResponse cloudResponse = CloudClient.ReplyChat(text, "", "awareness");
				if (cloudResponse != null && cloudResponse.ok)
				{
					AppData.SetState(key, "sent", null);
					AppData.AddSystemHistory("Caregiver Awareness", history, key, DateTime.Now);
				}
				else
				{
					AppData.SetState(key, "failed", null);
				}
			}
			catch
			{
				AppData.SetState(key, "failed", null);
			}
			finally
			{
				Interlocked.Exchange(ref awarenessBusy, 0);
			}
		});
	}

	private void CheckReminders()
	{
		if (Interlocked.CompareExchange(ref reminderDialogOpen, 0, 0) == 1)
		{
			return;
		}
		try
		{
			DateTime now = DateTime.Now;
			SecuritySettings s = AppData.LoadSettings() ?? new SecuritySettings();
			QuietAwayService.ExpireAwayIfNeeded(s, now);
			s = AppData.LoadSettings() ?? new SecuritySettings();
			List<AlertWorkItem> readyQuietWork = QuietAwayService.GetReadyQuietWork(now, s);
			foreach (Reminder item in AppData.LoadReminders().Items ?? new List<Reminder>())
			{
				Occurrence occurrence = Scheduler.GetOccurrence(item, now);
				if (occurrence == null || now < occurrence.Due || (now - occurrence.Due).TotalHours > 6.0 || !Scheduler.RoutineReady(item, occurrence.Due))
				{
					continue;
				}
				StateEntry state = AppData.GetState(occurrence.Key);
				if ((state == null || (!(state.Status == "done") && !(state.Status == "dismissed") && !(state.Status == "quiet-missed") && !(state.Status == "quiet-held") && !(state.Status == "away-held") && (!(state.Status == "snoozed") || !DateTime.TryParse(state.Until, out var result) || !(now < result)))) && (state != null || !QuietAwayService.TryHold(item, occurrence.Due, occurrence.Key, s, now)))
				{
					bool flag = string.Equals(s.SmartQuietingEnabled, "on", StringComparison.OrdinalIgnoreCase);
					int num = ((s.SmartQuietAfterMinutes <= 0) ? 30 : s.SmartQuietAfterMinutes);
					if (flag && state == null && !QuietAwayService.IsProtected(item) && (now - occurrence.Due).TotalMinutes >= (double)num)
					{
						AppData.SetState(occurrence.Key, "quiet-missed", null);
						AppData.AddHistory(item, "Missed - saved for summary", occurrence.Key, occurrence.Due);
						continue;
					}
					readyQuietWork.Add(new AlertWorkItem
					{
						Reminder = item,
						Scheduled = occurrence.Due,
						Key = occurrence.Key
					});
				}
			}
			readyQuietWork = (from x in readyQuietWork
				group x by x.Key into g
				select g.First() into x
				orderby x.Scheduled
				select x).ToList();
			if (readyQuietWork.Count <= 0 || Interlocked.Exchange(ref reminderDialogOpen, 1) == 1)
			{
				return;
			}
			try
			{
				using ReminderBatchForm reminderBatchForm = new ReminderBatchForm(readyQuietWork);
				reminderBatchForm.ShowDialog();
			}
			finally
			{
				Interlocked.Exchange(ref reminderDialogOpen, 0);
			}
		}
		catch
		{
		}
	}

	protected override void ExitThreadCore()
	{
		if (timer != null)
		{
			timer.Stop();
			timer.Dispose();
		}
		if (tray != null)
		{
			tray.Visible = false;
			tray.Dispose();
		}
		if (mutex != null)
		{
			try
			{
				mutex.ReleaseMutex();
			}
			catch
			{
			}
			mutex.Dispose();
		}
		base.ExitThreadCore();
	}
}
