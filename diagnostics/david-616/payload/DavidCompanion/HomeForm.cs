using System;
using System.Collections.Generic;
using System.Drawing;
using System.Linq;
using System.Windows.Forms;

namespace DavidCompanion;

public class HomeForm : Form
{
	private Label clock = new Label();

	private Label date = new Label();

	private Label greeting = new Label();

	private Label nextText = new Label();

	private Label nowText = new Label();

	private Label progressText = new Label();

	private Label encouragement = new Label();

	private Label cloudPill = new Label();

	private Button whatNextButton = new Button();

	private Button weekAheadButton = new Button();

	private Button morningButton = new Button();

	private Button eveningButton = new Button();

	private Button caregiverMessageButton = new Button();

	private Button missedSummaryButton = new Button();

	private ProgressRing ring = new ProgressRing();

	private FlowLayoutPanel todayFlow = new FlowLayoutPanel();

	private Timer timer = new Timer();

	private bool large;

	public HomeForm()
	{
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		large = string.Equals(securitySettings.LargeTextPreference, "on", StringComparison.OrdinalIgnoreCase);
		Text = "David - Daily Companion 6.1.6";
		StartPosition = FormStartPosition.CenterScreen;
		Size = new Size(1220, 790);
		MinimumSize = new Size(1120, 720);
		BackColor = Theme.Background;
		Font = new Font("Segoe UI", 10f);
		Icon = Program.TryLoadIcon();
		ShowInTaskbar = true;
		if (string.Equals(securitySettings.HomeFullScreenPreference, "on", StringComparison.OrdinalIgnoreCase))
		{
			FormBorderStyle = FormBorderStyle.None;
			WindowState = FormWindowState.Maximized;
		}
		GradientPanel gradientPanel = new GradientPanel();
		gradientPanel.SetBounds(24, 20, 1160, 205);
		gradientPanel.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
		Controls.Add(gradientPanel);
		Label label = Ui.Label("DAVID  •  DAILY COMPANION", 9, bold: true, Color.FromArgb(244, 184, 81));
		label.SetBounds(32, 20, 330, 24);
		gradientPanel.Controls.Add(label);
		greeting = Ui.Label("Good evening, David", large ? 27 : 24, bold: true, Color.White);
		greeting.SetBounds(30, 48, 650, 48);
		gradientPanel.Controls.Add(greeting);
		date = Ui.Label("", large ? 12 : 10, bold: false, Color.FromArgb(205, 219, 235));
		date.SetBounds(33, 99, 680, 28);
		gradientPanel.Controls.Add(date);
		encouragement = Ui.Label("You're right on track.", large ? 13 : 11, bold: true, Color.FromArgb(229, 236, 245));
		encouragement.SetBounds(33, 137, 600, 30);
		gradientPanel.Controls.Add(encouragement);
		nextText = Ui.Label("", large ? 11 : 10, bold: false, Color.FromArgb(193, 209, 228));
		nextText.SetBounds(33, 166, 610, 25);
		gradientPanel.Controls.Add(nextText);
		clock = Ui.Label("", large ? 38 : 34, bold: true, Color.White);
		clock.TextAlign = ContentAlignment.MiddleRight;
		clock.SetBounds(735, 28, 390, 62);
		clock.Anchor = AnchorStyles.Top | AnchorStyles.Right;
		gradientPanel.Controls.Add(clock);
		cloudPill = Ui.Pill("LOCAL", Color.FromArgb(52, 68, 91), Color.FromArgb(205, 220, 236));
		cloudPill.SetBounds(970, 92, 155, 30);
		cloudPill.Anchor = AnchorStyles.Top | AnchorStyles.Right;
		gradientPanel.Controls.Add(cloudPill);
		Button button = Ui.Button("CHAT", Theme.Blue, 10);
		button.SetBounds(660, 137, 145, 48);
		button.Anchor = AnchorStyles.Top | AnchorStyles.Right;
		gradientPanel.Controls.Add(button);
		button.Click += (object param0, EventArgs param1) =>
		{
			using ChatForm chatForm = new ChatForm();
			chatForm.ShowDialog(this);
		};
		Button button2 = Ui.Button("HELP", Theme.Danger, 10);
		button2.SetBounds(819, 137, 130, 48);
		button2.Anchor = AnchorStyles.Top | AnchorStyles.Right;
		gradientPanel.Controls.Add(button2);
		button2.Click += (object param0, EventArgs param1) =>
		{
			ShowEmergency();
		};
		Button button3 = Ui.Button("CAREGIVER", Color.FromArgb(77, 96, 122), 10);
		button3.SetBounds(963, 137, 162, 48);
		button3.Anchor = AnchorStyles.Top | AnchorStyles.Right;
		gradientPanel.Controls.Add(button3);
		button3.Click += (object param0, EventArgs param1) =>
		{
			Program.OpenCaregiverProtected(this);
		};
		Label label2 = Ui.Label("DAVID 6.1.6", 8, bold: true, Color.FromArgb(175, 197, 222));
		label2.SetBounds(810, 97, 145, 20);
		label2.TextAlign = ContentAlignment.MiddleRight;
		label2.Anchor = AnchorStyles.Top | AnchorStyles.Right;
		gradientPanel.Controls.Add(label2);
		RoundedPanel roundedPanel = Ui.Card(Theme.Surface);
		roundedPanel.SetBounds(24, 244, 410, 165);
		Controls.Add(roundedPanel);
		Label label3 = Ui.Label("NOW", 9, bold: true, Theme.Gold);
		label3.SetBounds(24, 18, 100, 22);
		roundedPanel.Controls.Add(label3);
		nowText = Ui.Label("You're all caught up.", large ? 19 : 16, bold: true, Theme.Text);
		nowText.SetBounds(24, 50, 360, 62);
		roundedPanel.Controls.Add(nowText);
		Label label4 = Ui.Label("David will let you know when something needs your attention.", 9, bold: false, Theme.Muted);
		label4.SetBounds(24, 119, 360, 35);
		roundedPanel.Controls.Add(label4);
		RoundedPanel roundedPanel2 = Ui.Card(Theme.Surface);
		roundedPanel2.SetBounds(452, 244, 300, 165);
		Controls.Add(roundedPanel2);
		Label label5 = Ui.Label("TODAY'S PROGRESS", 9, bold: true, Theme.Muted);
		label5.SetBounds(20, 16, 220, 24);
		roundedPanel2.Controls.Add(label5);
		ring.SetBounds(18, 42, 112, 112);
		roundedPanel2.Controls.Add(ring);
		progressText = Ui.Label("", large ? 13 : 11, bold: true, Theme.Text);
		progressText.SetBounds(146, 61, 135, 60);
		roundedPanel2.Controls.Add(progressText);
		RoundedPanel roundedPanel3 = Ui.Card(Theme.Surface);
		roundedPanel3.SetBounds(770, 244, 414, 165);
		roundedPanel3.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
		Controls.Add(roundedPanel3);
		Label label6 = Ui.Label("NEXT", 9, bold: true, Theme.Green);
		label6.SetBounds(24, 18, 100, 22);
		roundedPanel3.Controls.Add(label6);
		Label label7 = Ui.Label("Coming up", 9, bold: false, Theme.Muted);
		label7.SetBounds(24, 45, 180, 24);
		roundedPanel3.Controls.Add(label7);
		Label label8 = Ui.Label("", large ? 19 : 16, bold: true, Theme.Text);
		label8.Name = "NextBig";
		label8.SetBounds(24, 72, 360, 38);
		roundedPanel3.Controls.Add(label8);
		Label label9 = Ui.Label("", large ? 12 : 10, bold: false, Theme.Muted);
		label9.Name = "NextWhen";
		label9.SetBounds(24, 116, 360, 30);
		roundedPanel3.Controls.Add(label9);
		whatNextButton = Ui.Button("WHAT DO I NEED TO DO?", Theme.Blue, 11);
		whatNextButton.SetBounds(24, 425, 410, 58);
		Controls.Add(whatNextButton);
		whatNextButton.Click += (object param0, EventArgs param1) =>
		{
			ShowWhatNext();
		};
		caregiverMessageButton = Ui.Button("MESSAGE FROM CAREGIVER — TAP TO READ", Theme.Gold, 9);
		caregiverMessageButton.SetBounds(24, 425, 410, 58);
		caregiverMessageButton.Visible = false;
		Controls.Add(caregiverMessageButton);
		caregiverMessageButton.Click += (object param0, EventArgs param1) =>
		{
			ShowCaregiverMessage();
		};
		missedSummaryButton = Ui.Button("REVIEW MISSED ITEMS", Color.FromArgb(166, 101, 52), 10);
		missedSummaryButton.SetBounds(24, 425, 410, 58);
		missedSummaryButton.Visible = false;
		Controls.Add(missedSummaryButton);
		missedSummaryButton.Click += (object param0, EventArgs param1) =>
		{
			using (MissedSummaryForm missedSummaryForm = new MissedSummaryForm(DateTime.Now))
			{
				missedSummaryForm.ShowDialog(this);
			}
			RefreshDashboard();
		};
		weekAheadButton = Ui.Button("WHAT'S COMING UP?", Color.FromArgb(61, 119, 157), 11);
		weekAheadButton.SetBounds(446, 425, 410, 58);
		Controls.Add(weekAheadButton);
		weekAheadButton.Click += (object param0, EventArgs param1) =>
		{
			using (WeekAheadForm weekAheadForm = new WeekAheadForm())
			{
				weekAheadForm.ShowDialog(this);
			}
			RefreshDashboard();
		};
		morningButton = Ui.Button("MORNING", Color.FromArgb(191, 126, 42), 9);
		morningButton.SetBounds(868, 425, 150, 58);
		Controls.Add(morningButton);
		morningButton.Click += (object param0, EventArgs param1) =>
		{
			OpenRoutine(morning: true);
		};
		eveningButton = Ui.Button("EVENING", Color.FromArgb(92, 104, 158), 9);
		eveningButton.SetBounds(1030, 425, 154, 58);
		Controls.Add(eveningButton);
		eveningButton.Click += (object param0, EventArgs param1) =>
		{
			OpenRoutine(morning: false);
		};
		Label label10 = Ui.Label("TODAY", 13, bold: true, Theme.Text);
		label10.SetBounds(27, 496, 120, 28);
		Controls.Add(label10);
		Label label11 = Ui.Label("Your day at a glance", 9, bold: false, Theme.Muted);
		label11.SetBounds(104, 499, 260, 24);
		Controls.Add(label11);
		todayFlow.SetBounds(24, 530, 1160, 205);
		todayFlow.Anchor = AnchorStyles.Top | AnchorStyles.Bottom | AnchorStyles.Left | AnchorStyles.Right;
		todayFlow.AutoScroll = true;
		todayFlow.FlowDirection = FlowDirection.TopDown;
		todayFlow.WrapContents = false;
		todayFlow.BackColor = Theme.Background;
		todayFlow.Padding = new Padding(0, 0, 8, 0);
		Controls.Add(todayFlow);
		timer.Interval = 10000;
		timer.Tick += (object param0, EventArgs param1) =>
		{
			RefreshDashboard();
		};
		timer.Start();
		Shown += (object param0, EventArgs param1) =>
		{
			RefreshDashboard();
			Program.MarkHealthyStart();
		};
		FormClosing += (object sender, FormClosingEventArgs e) =>
		{
			timer.Stop();
		};
	}

	private void ShowEmergency()
	{
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		if (CloudClient.IsConnected())
		{
			if (MessageBox.Show("Do you need help?\r\n\r\nPress YES and David will immediately notify all connected caregivers on their phones.", "David - Get Help", MessageBoxButtons.YesNo, MessageBoxIcon.Question) != DialogResult.Yes)
			{
				return;
			}
			Cursor = Cursors.WaitCursor;
			CloudResponse cloudResponse = CloudClient.StartHelp();
			Cursor = Cursors.Default;
			if (cloudResponse != null && cloudResponse.ok && cloudResponse.help != null)
			{
				using (HelpWaitingForm helpWaitingForm = new HelpWaitingForm(cloudResponse.help))
				{
					helpWaitingForm.ShowDialog(this);
					return;
				}
			}
			string text = ((cloudResponse == null) ? "The caregiver service could not be reached." : cloudResponse.error);
			string text2 = (string.IsNullOrWhiteSpace(securitySettings.EmergencyName) ? "Please get help from a nearby trusted person." : ("Please get help from " + securitySettings.EmergencyName + (string.IsNullOrWhiteSpace(securitySettings.EmergencyPhone) ? "." : (" at " + securitySettings.EmergencyPhone + "."))));
			MessageBox.Show("David could not send the phone notification right now.\r\n\r\n" + text + "\r\n\r\n" + text2, "David - Help", MessageBoxButtons.OK, MessageBoxIcon.Exclamation);
		}
		else if (string.IsNullOrWhiteSpace(securitySettings.EmergencyName) && string.IsNullOrWhiteSpace(securitySettings.EmergencyPhone))
		{
			MessageBox.Show("Connected Care has not been set up yet. Please get help from a nearby trusted person.", "David - Help", MessageBoxButtons.OK, MessageBoxIcon.Asterisk);
		}
		else
		{
			string text3 = (string.IsNullOrWhiteSpace(securitySettings.EmergencyName) ? "your caregiver" : securitySettings.EmergencyName);
			string text4 = securitySettings.EmergencyPhone ?? "";
			MessageBox.Show("Connected Care is not enabled on this computer.\r\n\r\nPlease get help from " + text3 + (string.IsNullOrWhiteSpace(text4) ? "." : (" at:\r\n\r\n" + text4)), "David - Help", MessageBoxButtons.OK, MessageBoxIcon.Exclamation);
		}
	}

	private void ShowCaregiverMessage()
	{
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		if (string.IsNullOrWhiteSpace(securitySettings.HomeMessageId) || string.Equals(securitySettings.HomeMessageId, securitySettings.HomeMessageDismissedId, StringComparison.Ordinal))
		{
			RefreshDashboard();
			return;
		}
		using (CaregiverHomeMessageForm caregiverHomeMessageForm = new CaregiverHomeMessageForm(securitySettings.HomeMessageId, securitySettings.HomeMessageFrom, securitySettings.HomeMessageText))
		{
			caregiverHomeMessageForm.ShowDialog(this);
		}
		RefreshDashboard();
	}

	private void ShowWhatNext()
	{
		List<AlertWorkItem> dueWork = DailySupport.GetDueWork(DateTime.Now);
		if (dueWork.Count > 0)
		{
			using (ReminderBatchForm reminderBatchForm = new ReminderBatchForm(dueWork))
			{
				reminderBatchForm.ShowDialog(this);
			}
			RefreshDashboard();
			return;
		}
		Occurrence occurrence = Scheduler.Next(DateTime.Now);
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
		VoiceService.Speak((occurrence == null) ? (text + ", you are all caught up. There is nothing you need to do right now.") : (text + ", you are all caught up right now. Your next thing is " + occurrence.Reminder.Title + " at " + occurrence.Due.ToString("h:mm tt") + "."), securitySettings.VoiceName, securitySettings.VoiceRate, securitySettings.VoiceVolume);
		using WhatNextForm whatNextForm = new WhatNextForm(occurrence);
		whatNextForm.ShowDialog(this);
	}

	private void OpenRoutine(bool morning)
	{
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		string text = (morning ? securitySettings.MorningRoutineName : securitySettings.EveningRoutineName);
		if (string.IsNullOrWhiteSpace(text))
		{
			text = (morning ? "Morning" : "Evening");
		}
		using (RoutineModeForm routineModeForm = new RoutineModeForm(text, "manual-routine-" + (morning ? "morning" : "evening") + "-" + DateTime.Now.ToString("yyyy-MM-dd"), auto: false))
		{
			routineModeForm.ShowDialog(this);
		}
		RefreshDashboard();
	}

	private void RefreshDashboard()
	{
		DateTime now = DateTime.Now;
		string text;
		if (now.Hour >= 12)
		{
			text = ((now.Hour < 18) ? "Good afternoon" : "Good evening");
		}
		else
		{
			text = "Good morning";
		}
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		string text2;
		if (string.IsNullOrWhiteSpace(securitySettings.PersonPreferredName))
		{
			text2 = (string.IsNullOrWhiteSpace(securitySettings.PersonFullName) ? "David" : securitySettings.PersonFullName);
		}
		else
		{
			text2 = securitySettings.PersonPreferredName;
		}
		greeting.Text = text + ", " + text2;
		string text3 = (securitySettings.PersonLocation + (string.IsNullOrWhiteSpace(securitySettings.PersonRoom) ? "" : ((string.IsNullOrWhiteSpace(securitySettings.PersonLocation) ? "" : " — ") + securitySettings.PersonRoom))).Trim();
		date.Text = now.ToString("dddd, MMMM d, yyyy") + (string.IsNullOrWhiteSpace(text3) ? "" : ("  •  " + text3));
		clock.Text = now.ToString("h:mm tt");
		QuietAwayService.ExpireAwayIfNeeded(securitySettings, now);
		securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		bool flag = SystemAwareness.InternetAvailable();
		double num = SystemAwareness.LastSyncAgeMinutes(securitySettings);
		if (!flag)
		{
			cloudPill.Text = "INTERNET OFFLINE";
			cloudPill.BackColor = Color.FromArgb(114, 55, 58);
			cloudPill.ForeColor = Color.FromArgb(255, 214, 216);
		}
		else if (CloudClient.IsConnected() && num > 10.0)
		{
			cloudPill.Text = "CARE DELAYED";
			cloudPill.BackColor = Color.FromArgb(112, 78, 35);
			cloudPill.ForeColor = Color.FromArgb(255, 229, 177);
		}
		else
		{
			cloudPill.Text = (CloudClient.IsConnected() ? "CONNECTED CARE" : "LOCAL MODE");
			cloudPill.BackColor = (CloudClient.IsConnected() ? Color.FromArgb(31, 108, 78) : Color.FromArgb(52, 68, 91));
			cloudPill.ForeColor = (CloudClient.IsConnected() ? Color.FromArgb(202, 244, 227) : Color.FromArgb(205, 220, 236));
		}
		bool flag2 = !string.IsNullOrWhiteSpace(securitySettings.HomeMessageId) && !string.Equals(securitySettings.HomeMessageId, securitySettings.HomeMessageDismissedId, StringComparison.Ordinal) && !string.IsNullOrWhiteSpace(securitySettings.HomeMessageText);
		int count = DailySummary.GetMissedWork(now).Count;
		caregiverMessageButton.Visible = flag2;
		missedSummaryButton.Visible = !flag2 && count > 0;
		whatNextButton.Visible = !flag2 && count == 0;
		if (flag2)
		{
			string text4 = (string.IsNullOrWhiteSpace(securitySettings.HomeMessageFrom) ? "CAREGIVER" : securitySettings.HomeMessageFrom.ToUpperInvariant());
			string text5 = (securitySettings.HomeMessageText ?? "").Replace("\r", " ").Replace("\n", " ").Trim();
			if (text5.Length > 48)
			{
				text5 = text5.Substring(0, 48) + "…";
			}
			caregiverMessageButton.Text = "MESSAGE FROM " + text4 + ":  " + text5;
		}
		if (count > 0)
		{
			missedSummaryButton.Text = "REVIEW " + count + " MISSED ITEM" + ((count == 1) ? "" : "S");
		}
		List<Occurrence> list = Scheduler.Today(now);
		StateStore stateStore = AppData.LoadState();
		int count2 = list.Count;
		int num2 = 0;
		Occurrence occurrence = null;
		foreach (Occurrence o in list)
		{
			List<StateEntry> items = stateStore.Items;
			Func<StateEntry, bool> predicate = (StateEntry x) => x.Key == o.Key;
			StateEntry stateEntry = items.FirstOrDefault(predicate);
			if (stateEntry != null && stateEntry.Status == "done")
			{
				num2++;
			}
			if (occurrence == null && o.Due <= now && Scheduler.RoutineReady(o.Reminder, o.Due) && (stateEntry == null || (stateEntry.Status != "done" && stateEntry.Status != "dismissed" && stateEntry.Status != "quiet-missed" && stateEntry.Status != "quiet-held" && stateEntry.Status != "away-held")))
			{
				occurrence = o;
			}
		}
		int value = ((count2 == 0) ? 100 : ((int)Math.Round((double)num2 * 100.0 / (double)count2)));
		ring.Value = value;
		progressText.Text = ((count2 == 0) ? "Clear day\nNo tasks" : (num2 + " of " + count2 + "\ncomplete"));
		if (occurrence != null)
		{
			int num3 = Math.Max(0, (int)(now - occurrence.Due).TotalMinutes);
			nowText.Text = occurrence.Reminder.Title + ((num3 >= 15) ? "\nStill needs attention" : "\nIt's time now");
			encouragement.Text = ((num3 >= 15) ? "This one still needs your attention." : ("You've got this, " + text2 + "."));
		}
		else
		{
			nowText.Text = ((count2 > 0 && num2 == count2) ? "Nice work!\nYou're done for today." : "You're all caught up.");
			encouragement.Text = ((count2 > 0 && num2 == count2) ? "Everything for today is complete." : "You're right on track.");
		}
		Occurrence occurrence2 = Scheduler.Next(now);
		Panel panel = Controls.OfType<Panel>().FirstOrDefault((Panel p) => p.Controls.Find("NextBig", searchAllChildren: false).Length != 0);
		if (occurrence2 != null && panel != null)
		{
			Label label = panel.Controls.Find("NextBig", searchAllChildren: false)[0] as Label;
			Label label2 = panel.Controls.Find("NextWhen", searchAllChildren: false)[0] as Label;
			if (label != null)
			{
				label.Text = occurrence2.Reminder.Title;
			}
			if (label2 != null)
			{
				label2.Text = ((occurrence2.Due.Date == now.Date) ? (occurrence2.Due.ToString("h:mm tt") + "  •  " + FriendlyUntil(occurrence2.Due - now)) : occurrence2.Due.ToString("dddd, h:mm tt"));
			}
			nextText.Text = "Next: " + occurrence2.Reminder.Title + " at " + ((occurrence2.Due.Date == now.Date) ? occurrence2.Due.ToString("h:mm tt") : occurrence2.Due.ToString("ddd h:mm tt"));
		}
		else
		{
			nextText.Text = "No more reminders are scheduled right now.";
		}
		string routineName = (string.IsNullOrWhiteSpace(securitySettings.MorningRoutineName) ? "Morning" : securitySettings.MorningRoutineName);
		string routineName2 = (string.IsNullOrWhiteSpace(securitySettings.EveningRoutineName) ? "Evening" : securitySettings.EveningRoutineName);
		int count3 = DailySupport.GetRoutineOccurrences(routineName, now).Count;
		int count4 = DailySupport.GetRoutineOccurrences(routineName2, now).Count;
		morningButton.Text = "MORNING" + ((count3 > 0) ? ("  (" + count3 + ")") : "");
		eveningButton.Text = "EVENING" + ((count4 > 0) ? ("  (" + count4 + ")") : "");
		morningButton.Enabled = count3 > 0;
		eveningButton.Enabled = count4 > 0;
		todayFlow.SuspendLayout();
		todayFlow.Controls.Clear();
		if (list.Count == 0)
		{
			RoundedPanel roundedPanel = Ui.Card(Theme.Surface);
			roundedPanel.Width = 1100;
			roundedPanel.Height = 86;
			Label label3 = Ui.Label("Nothing is scheduled for today.", large ? 16 : 13, bold: true, Theme.Muted);
			label3.SetBounds(24, 24, 950, 34);
			roundedPanel.Controls.Add(label3);
			todayFlow.Controls.Add(roundedPanel);
		}
		else
		{
			foreach (Occurrence o2 in list)
			{
				Occurrence o3 = o2;
				List<StateEntry> items2 = stateStore.Items;
				Func<StateEntry, bool> predicate2 = (StateEntry x) => x.Key == o2.Key;
				TodayCard todayCard = new TodayCard(o3, items2.FirstOrDefault(predicate2), large, RefreshDashboard);
				todayCard.Width = 1100;
				todayFlow.Controls.Add(todayCard);
			}
		}
		todayFlow.ResumeLayout();
	}

	private string FriendlyUntil(TimeSpan t)
	{
		if (t.TotalMinutes < 1.0)
		{
			return "less than a minute";
		}
		if (t.TotalMinutes < 60.0)
		{
			return (int)Math.Ceiling(t.TotalMinutes) + " min";
		}
		if (t.TotalHours < 2.0)
		{
			return "about 1 hour";
		}
		return "about " + (int)Math.Round(t.TotalHours) + " hours";
	}
}
