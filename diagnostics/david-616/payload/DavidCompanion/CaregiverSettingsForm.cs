using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Windows.Forms;

namespace DavidCompanion;

public class CaregiverSettingsForm : Form
{
	private CheckBox startup = new CheckBox();

	private CheckBox fullscreen = new CheckBox();

	private CheckBox large = new CheckBox();

	private CheckBox homeFull = new CheckBox();

	private CheckBox wake = new CheckBox();

	private ComboBox voiceBox = new ComboBox();

	private NumericUpDown rate = new NumericUpDown();

	private NumericUpDown volume = new NumericUpDown();

	private NumericUpDown escalation = new NumericUpDown();

	private TextBox emergencyName = new TextBox();

	private TextBox emergencyPhone = new TextBox();

	private List<VoiceChoice> voices = new List<VoiceChoice>();

	private object previewSpeech;

	private CheckBox morningEnabled = new CheckBox();

	private CheckBox eveningEnabled = new CheckBox();

	private CheckBox checkInEnabled = new CheckBox();

	private TextBox morningName = new TextBox();

	private TextBox eveningName = new TextBox();

	private DateTimePicker morningTime = new DateTimePicker();

	private DateTimePicker eveningTime = new DateTimePicker();

	private DateTimePicker checkInTime = new DateTimePicker();

	private NumericUpDown checkInGrace = new NumericUpDown();

	private CheckBox smartQuieting = new CheckBox();

	private NumericUpDown smartQuietAfter = new NumericUpDown();

	private CheckBox missedSummaryEnabled = new CheckBox();

	private DateTimePicker missedSummaryTime = new DateTimePicker();

	private CheckBox caregiverSummaryEnabled = new CheckBox();

	private DateTimePicker caregiverSummaryTime = new DateTimePicker();

	private CheckBox inactivityWatch = new CheckBox();

	private NumericUpDown inactivityHours = new NumericUpDown();

	private DateTimePicker inactivityStart = new DateTimePicker();

	private DateTimePicker inactivityEnd = new DateTimePicker();

	private CheckBox autoBackup = new CheckBox();

	private NumericUpDown backupRetention = new NumericUpDown();

	private CheckBox quietHours = new CheckBox();

	private DateTimePicker quietHoursStart = new DateTimePicker();

	private DateTimePicker quietHoursEnd = new DateTimePicker();

	private CheckBox awayMode = new CheckBox();

	private DateTimePicker awayUntil = new DateTimePicker();

	private TextBox awayNote = new TextBox();

	private CheckBox morningOverviewEnabled = new CheckBox();

	private DateTimePicker morningOverviewTime = new DateTimePicker();

	public CaregiverSettingsForm()
	{
		Text = "David - Caregiver Settings";
		StartPosition = FormStartPosition.CenterParent;
		Size = new Size(790, 760);
		FormBorderStyle = FormBorderStyle.FixedDialog;
		MaximizeBox = false;
		MinimizeBox = false;
		BackColor = Theme.Background;
		Font = new Font("Segoe UI", 10f);
		Icon = Program.TryLoadIcon();
		Label label = Ui.Label("Caregiver Settings", 20, bold: true, Theme.Text);
		label.SetBounds(26, 18, 520, 42);
		Controls.Add(label);
		Label label2 = Ui.Label("These controls are protected by the caregiver PIN.", 10, bold: false, Theme.Muted);
		label2.SetBounds(29, 60, 620, 28);
		Controls.Add(label2);
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		TabControl tabControl = new TabControl();
		tabControl.SetBounds(24, 98, 740, 515);
		Controls.Add(tabControl);
		TabPage tabPage = new TabPage("General");
		TabPage tabPage2 = new TabPage("Voice & Contact");
		TabPage tabPage3 = new TabPage("Safety");
		TabPage tabPage4 = new TabPage("Daily Support");
		TabPage tabPage5 = new TabPage("Orientation");
		TabPage tabPage6 = new TabPage("Summaries");
		TabPage tabPage7 = new TabPage("Awareness");
		TabPage tabPage8 = new TabPage("Quiet & Away");
		TabPage tabPage9 = new TabPage("Recovery");
		tabControl.TabPages.Add(tabPage);
		tabControl.TabPages.Add(tabPage2);
		tabControl.TabPages.Add(tabPage3);
		tabControl.TabPages.Add(tabPage4);
		tabControl.TabPages.Add(tabPage5);
		tabControl.TabPages.Add(tabPage6);
		tabControl.TabPages.Add(tabPage7);
		tabControl.TabPages.Add(tabPage8);
		tabControl.TabPages.Add(tabPage9);
		tabPage.BackColor = Theme.Background;
		tabPage2.BackColor = Theme.Background;
		tabPage3.BackColor = Theme.Background;
		tabPage4.BackColor = Theme.Background;
		tabPage5.BackColor = Theme.Background;
		tabPage6.BackColor = Theme.Background;
		tabPage7.BackColor = Theme.Background;
		tabPage8.BackColor = Theme.Background;
		tabPage9.BackColor = Theme.Background;
		startup.Text = "Start David automatically when this Windows user signs in";
		startup.ForeColor = Theme.Text;
		startup.SetBounds(28, 30, 650, 32);
		startup.Checked = Program.IsAutoStartEnabled();
		tabPage.Controls.Add(startup);
		fullscreen.Text = "Use full-screen reminder alerts";
		fullscreen.ForeColor = Theme.Text;
		fullscreen.SetBounds(28, 75, 650, 32);
		fullscreen.Checked = !string.Equals(securitySettings.FullScreenPreference, "off", StringComparison.OrdinalIgnoreCase);
		tabPage.Controls.Add(fullscreen);
		homeFull.Text = "Open David's home dashboard full screen";
		homeFull.ForeColor = Theme.Text;
		homeFull.SetBounds(28, 120, 650, 32);
		homeFull.Checked = string.Equals(securitySettings.HomeFullScreenPreference, "on", StringComparison.OrdinalIgnoreCase);
		tabPage.Controls.Add(homeFull);
		large.Text = "Use larger text on David's home screen";
		large.ForeColor = Theme.Text;
		large.SetBounds(28, 165, 650, 32);
		large.Checked = string.Equals(securitySettings.LargeTextPreference, "on", StringComparison.OrdinalIgnoreCase);
		tabPage.Controls.Add(large);
		Button button = Ui.Button("BACK UP DAVID'S DATA", Color.FromArgb(65, 130, 174), 10);
		button.SetBounds(28, 230, 280, 48);
		tabPage.Controls.Add(button);
		button.Click += (object param0, EventArgs param1) =>
		{
			try
			{
				string text = AppData.Backup();
				MessageBox.Show("Backup created in:\r\n" + text, "David");
				try
				{
					Process.Start(text);
				}
				catch
				{
				}
			}
			catch (Exception ex)
			{
				MessageBox.Show("Backup could not be created.\r\n" + ex.Message, "David");
			}
		};
		Button button2 = Ui.Button("CHANGE CAREGIVER PIN", Color.FromArgb(73, 91, 117), 10);
		button2.SetBounds(326, 230, 300, 48);
		tabPage.Controls.Add(button2);
		button2.Click += (object param0, EventArgs param1) =>
		{
			if (!PinSecurity.Prompt("Enter the current PIN before changing it."))
			{
				return;
			}
			using SetPinForm setPinForm = new SetPinForm(changing: true);
			if (setPinForm.ShowDialog(this) == DialogResult.OK && !string.IsNullOrEmpty(setPinForm.PinValue))
			{
				PinSecurity.SetPin(setPinForm.PinValue);
				MessageBox.Show("Caregiver PIN changed.", "David");
			}
		};
		Label label3 = Ui.Label("Reminder voice", 11, bold: true, Theme.Text);
		label3.SetBounds(28, 24, 200, 28);
		tabPage2.Controls.Add(label3);
		voices = VoiceService.GetVoices();
		voiceBox.SetBounds(28, 58, 460, 32);
		voiceBox.DropDownStyle = ComboBoxStyle.DropDownList;
		foreach (VoiceChoice voice in voices)
		{
			voiceBox.Items.Add(voice.Name);
		}
		tabPage2.Controls.Add(voiceBox);
		int num = -1;
		for (int num2 = 0; num2 < voiceBox.Items.Count; num2++)
		{
			if (string.Equals(Convert.ToString(voiceBox.Items[num2]), securitySettings.VoiceName, StringComparison.OrdinalIgnoreCase))
			{
				num = num2;
			}
		}
		if (num < 0 && voiceBox.Items.Count > 0)
		{
			for (int num3 = 0; num3 < voiceBox.Items.Count; num3++)
			{
				if (Convert.ToString(voiceBox.Items[num3]).IndexOf("David", StringComparison.OrdinalIgnoreCase) >= 0)
				{
					num = num3;
					break;
				}
			}
			if (num < 0)
			{
				num = 0;
			}
		}
		if (num >= 0)
		{
			voiceBox.SelectedIndex = num;
		}
		Button button3 = Ui.Button("PREVIEW VOICE", Theme.Green, 9);
		button3.SetBounds(505, 57, 185, 34);
		tabPage2.Controls.Add(button3);
		button3.Click += (object param0, EventArgs param1) =>
		{
			VoiceService.Stop(previewSpeech);
			previewSpeech = VoiceService.Speak("Hi David. This is what your reminders will sound like.", Convert.ToString(voiceBox.SelectedItem), (int)rate.Value, (int)volume.Value);
		};
		Label label4 = Ui.Label("Speaking speed", 9, bold: true, Theme.Text);
		label4.SetBounds(28, 118, 150, 25);
		tabPage2.Controls.Add(label4);
		rate.SetBounds(180, 114, 90, 30);
		rate.Minimum = -10m;
		rate.Maximum = 10m;
		rate.Value = Math.Max(rate.Minimum, Math.Min(rate.Maximum, securitySettings.VoiceRate));
		tabPage2.Controls.Add(rate);
		Label label5 = Ui.Label("Volume", 9, bold: true, Theme.Text);
		label5.SetBounds(300, 118, 100, 25);
		tabPage2.Controls.Add(label5);
		volume.SetBounds(390, 114, 90, 30);
		volume.Minimum = 0m;
		volume.Maximum = 100m;
		volume.Value = Math.Max(0, Math.Min(100, securitySettings.VoiceVolume));
		tabPage2.Controls.Add(volume);
		Label label6 = Ui.Label("David lists the voices Windows exposes to desktop apps. For more choices, install another Windows speech voice, then reopen these settings.", 9, bold: false, Theme.Muted);
		label6.SetBounds(28, 162, 650, 48);
		tabPage2.Controls.Add(label6);
		Button button4 = Ui.Button("OPEN WINDOWS VOICE SETTINGS", Color.FromArgb(73, 91, 117), 9);
		button4.SetBounds(28, 216, 280, 40);
		tabPage2.Controls.Add(button4);
		button4.Click += (object param0, EventArgs param1) =>
		{
			try
			{
				Process.Start(new ProcessStartInfo
				{
					FileName = "ms-settings:speech",
					UseShellExecute = true
				});
			}
			catch
			{
			}
		};
		Label label7 = Ui.Label("Caregiver / emergency contact", 11, bold: true, Theme.Text);
		label7.SetBounds(28, 294, 300, 28);
		tabPage2.Controls.Add(label7);
		Label label8 = Ui.Label("Name", 9, bold: true, Theme.Text);
		label8.SetBounds(28, 336, 80, 25);
		tabPage2.Controls.Add(label8);
		emergencyName.SetBounds(110, 332, 260, 30);
		emergencyName.Text = securitySettings.EmergencyName ?? "";
		tabPage2.Controls.Add(emergencyName);
		Label label9 = Ui.Label("Phone", 9, bold: true, Theme.Text);
		label9.SetBounds(390, 336, 70, 25);
		tabPage2.Controls.Add(label9);
		emergencyPhone.SetBounds(460, 332, 230, 30);
		emergencyPhone.Text = securitySettings.EmergencyPhone ?? "";
		tabPage2.Controls.Add(emergencyPhone);
		wake.Text = "Wake / turn on the display for reminder alerts when Windows allows it";
		wake.ForeColor = Theme.Text;
		wake.SetBounds(28, 34, 650, 32);
		wake.Checked = !string.Equals(securitySettings.WakeScreenPreference, "off", StringComparison.OrdinalIgnoreCase);
		tabPage3.Controls.Add(wake);
		Label label10 = Ui.Label("Escalate an overdue reminder after", 9, bold: true, Theme.Text);
		label10.SetBounds(28, 91, 270, 25);
		tabPage3.Controls.Add(label10);
		escalation.SetBounds(300, 86, 80, 30);
		escalation.Minimum = 5m;
		escalation.Maximum = 120m;
		escalation.Value = ((securitySettings.EscalationMinutes <= 0) ? 15 : Math.Max(5, Math.Min(120, securitySettings.EscalationMinutes)));
		tabPage3.Controls.Add(escalation);
		Label label11 = Ui.Label("minutes", 9, bold: false, Theme.Muted);
		label11.SetBounds(390, 91, 100, 25);
		tabPage3.Controls.Add(label11);
		Label label12 = Ui.Label("Important and Critical reminders repeat more aggressively. Medication reminders also receive stronger overdue handling.", 9, bold: false, Theme.Muted);
		label12.SetBounds(28, 140, 650, 48);
		tabPage3.Controls.Add(label12);
		Button button5 = Ui.Button("STOP DAVID", Theme.Danger, 10);
		button5.SetBounds(28, 230, 280, 48);
		tabPage3.Controls.Add(button5);
		button5.Click += (object param0, EventArgs param1) =>
		{
			if (PinSecurity.Prompt("Enter the caregiver PIN to stop David."))
			{
				Program.StopBackgroundProcesses();
				MessageBox.Show("David has been stopped for this Windows session. Automatic startup is still " + (Program.IsAutoStartEnabled() ? "ON" : "OFF") + ".", "David");
			}
		};
		Button button6 = Ui.Button("UNINSTALL DAVID", Color.FromArgb(120, 72, 78), 10);
		button6.SetBounds(326, 230, 300, 48);
		tabPage3.Controls.Add(button6);
		button6.Click += (object param0, EventArgs param1) =>
		{
			Program.StartUninstallerProcess();
		};
		Label label13 = Ui.Label("Windows administrators can still remove software manually. The PIN protects David's normal caregiver controls and uninstall path.", 9, bold: false, Theme.Muted);
		label13.SetBounds(28, 310, 650, 48);
		tabPage3.Controls.Add(label13);
		Label label14 = Ui.Label("Morning & evening routines", 13, bold: true, Theme.Text);
		label14.SetBounds(28, 22, 400, 30);
		tabPage4.Controls.Add(label14);
		Label label15 = Ui.Label("Routine Mode groups reminders by the Routine name set under a reminder's Advanced tab.", 9, bold: false, Theme.Muted);
		label15.SetBounds(28, 54, 655, 38);
		tabPage4.Controls.Add(label15);
		morningEnabled.Text = "Automatically offer the morning routine";
		morningEnabled.ForeColor = Theme.Text;
		morningEnabled.SetBounds(28, 100, 330, 30);
		morningEnabled.Checked = string.Equals(securitySettings.MorningRoutineEnabled, "on", StringComparison.OrdinalIgnoreCase);
		tabPage4.Controls.Add(morningEnabled);
		Label label16 = Ui.Label("Routine name", 9, bold: true, Theme.Text);
		label16.SetBounds(48, 138, 110, 24);
		tabPage4.Controls.Add(label16);
		morningName.SetBounds(160, 134, 220, 30);
		morningName.Text = (string.IsNullOrWhiteSpace(securitySettings.MorningRoutineName) ? "Morning" : securitySettings.MorningRoutineName);
		tabPage4.Controls.Add(morningName);
		Label label17 = Ui.Label("Start at", 9, bold: true, Theme.Text);
		label17.SetBounds(405, 138, 75, 24);
		tabPage4.Controls.Add(label17);
		SetupTimePicker(morningTime, securitySettings.MorningRoutineTime, 8);
		morningTime.SetBounds(482, 134, 150, 30);
		tabPage4.Controls.Add(morningTime);
		eveningEnabled.Text = "Automatically offer the evening routine";
		eveningEnabled.ForeColor = Theme.Text;
		eveningEnabled.SetBounds(28, 183, 330, 30);
		eveningEnabled.Checked = string.Equals(securitySettings.EveningRoutineEnabled, "on", StringComparison.OrdinalIgnoreCase);
		tabPage4.Controls.Add(eveningEnabled);
		Label label18 = Ui.Label("Routine name", 9, bold: true, Theme.Text);
		label18.SetBounds(48, 221, 110, 24);
		tabPage4.Controls.Add(label18);
		eveningName.SetBounds(160, 217, 220, 30);
		eveningName.Text = (string.IsNullOrWhiteSpace(securitySettings.EveningRoutineName) ? "Evening" : securitySettings.EveningRoutineName);
		tabPage4.Controls.Add(eveningName);
		Label label19 = Ui.Label("Start at", 9, bold: true, Theme.Text);
		label19.SetBounds(405, 221, 75, 24);
		tabPage4.Controls.Add(label19);
		SetupTimePicker(eveningTime, securitySettings.EveningRoutineTime, 20);
		eveningTime.SetBounds(482, 217, 150, 30);
		tabPage4.Controls.Add(eveningTime);
		Label label20 = Ui.Label("I'M OKAY check-in", 13, bold: true, Theme.Text);
		label20.SetBounds(28, 282, 300, 30);
		tabPage4.Controls.Add(label20);
		checkInEnabled.Text = "Ask for a daily I'M OKAY check-in";
		checkInEnabled.ForeColor = Theme.Text;
		checkInEnabled.SetBounds(28, 320, 310, 30);
		checkInEnabled.Checked = string.Equals(securitySettings.CheckInEnabled, "on", StringComparison.OrdinalIgnoreCase);
		tabPage4.Controls.Add(checkInEnabled);
		Label label21 = Ui.Label("Check-in time", 9, bold: true, Theme.Text);
		label21.SetBounds(48, 360, 110, 24);
		tabPage4.Controls.Add(label21);
		SetupTimePicker(checkInTime, securitySettings.CheckInTime, 12);
		checkInTime.SetBounds(160, 356, 150, 30);
		tabPage4.Controls.Add(checkInTime);
		Label label22 = Ui.Label("Alert caregiver after", 9, bold: true, Theme.Text);
		label22.SetBounds(340, 360, 145, 24);
		tabPage4.Controls.Add(label22);
		checkInGrace.SetBounds(487, 356, 75, 30);
		checkInGrace.Minimum = 5m;
		checkInGrace.Maximum = 180m;
		checkInGrace.Value = ((securitySettings.CheckInGraceMinutes <= 0) ? 30 : Math.Max(5, Math.Min(180, securitySettings.CheckInGraceMinutes)));
		tabPage4.Controls.Add(checkInGrace);
		Label label23 = Ui.Label("min", 9, bold: false, Theme.Muted);
		label23.SetBounds(570, 360, 50, 24);
		tabPage4.Controls.Add(label23);
		Label label24 = Ui.Label("If unanswered, David records a missed check-in and sends a Connected Care chat alert when connected. The check-in stays on screen so David can still answer later.", 9, bold: false, Theme.Muted);
		label24.SetBounds(48, 402, 600, 60);
		tabPage4.Controls.Add(label24);
		Label label25 = Ui.Label("Week Ahead & Orientation", 13, bold: true, Theme.Text);
		label25.SetBounds(28, 22, 420, 30);
		tabPage5.Controls.Add(label25);
		Label label26 = Ui.Label("WHAT'S COMING UP on David's home screen opens a simple seven-day agenda. The optional morning overview speaks once and does not open another popup window.", 9, bold: false, Theme.Muted);
		label26.SetBounds(28, 58, 650, 64);
		tabPage5.Controls.Add(label26);
		morningOverviewEnabled.Text = "Speak one short morning overview each day";
		morningOverviewEnabled.ForeColor = Theme.Text;
		morningOverviewEnabled.SetBounds(28, 145, 420, 30);
		morningOverviewEnabled.Checked = string.Equals(securitySettings.MorningOverviewEnabled, "on", StringComparison.OrdinalIgnoreCase);
		tabPage5.Controls.Add(morningOverviewEnabled);
		Label label27 = Ui.Label("Speak at", 9, bold: true, Theme.Text);
		label27.SetBounds(48, 193, 85, 24);
		tabPage5.Controls.Add(label27);
		SetupTimePicker(morningOverviewTime, securitySettings.MorningOverviewTime, 8);
		morningOverviewTime.SetBounds(135, 189, 160, 30);
		tabPage5.Controls.Add(morningOverviewTime);
		Label label28 = Ui.Label("Example: “Good morning, David. Today is Monday. You have three things scheduled today. Your next item is at 9:30. Tomorrow you have one thing planned.”", 9, bold: false, Theme.Muted);
		label28.SetBounds(48, 238, 610, 82);
		tabPage5.Controls.Add(label28);
		Label label29 = Ui.Label("The automatic overview respects Quiet Hours and Away Mode. It is OFF by default. David never reads medication instructions or other private reminder notes in the overview.", 9, bold: false, Theme.Muted);
		label29.SetBounds(48, 342, 610, 72);
		tabPage5.Controls.Add(label29);
		Label label30 = Ui.Label("Smart quieting & summaries", 13, bold: true, Theme.Text);
		label30.SetBounds(28, 22, 420, 30);
		tabPage6.Controls.Add(label30);
		Label label31 = Ui.Label("Keep old non-critical reminders from turning into a pile of popups, then review them together later.", 9, bold: false, Theme.Muted);
		label31.SetBounds(28, 54, 655, 42);
		tabPage6.Controls.Add(label31);
		smartQuieting.Text = "Use Smart Quieting for old non-critical reminders";
		smartQuieting.ForeColor = Theme.Text;
		smartQuieting.SetBounds(28, 108, 440, 30);
		smartQuieting.Checked = !string.Equals(securitySettings.SmartQuietingEnabled, "off", StringComparison.OrdinalIgnoreCase);
		tabPage6.Controls.Add(smartQuieting);
		Label label32 = Ui.Label("Move an unanswered normal reminder to the summary after", 9, bold: true, Theme.Text);
		label32.SetBounds(48, 151, 390, 24);
		tabPage6.Controls.Add(label32);
		smartQuietAfter.SetBounds(445, 147, 75, 30);
		smartQuietAfter.Minimum = 10m;
		smartQuietAfter.Maximum = 180m;
		smartQuietAfter.Value = ((securitySettings.SmartQuietAfterMinutes <= 0) ? 30 : Math.Max(10, Math.Min(180, securitySettings.SmartQuietAfterMinutes)));
		tabPage6.Controls.Add(smartQuietAfter);
		Label label33 = Ui.Label("min", 9, bold: false, Theme.Muted);
		label33.SetBounds(528, 151, 50, 24);
		tabPage6.Controls.Add(label33);
		missedSummaryEnabled.Text = "Show one evening missed-items summary";
		missedSummaryEnabled.ForeColor = Theme.Text;
		missedSummaryEnabled.SetBounds(28, 205, 360, 30);
		missedSummaryEnabled.Checked = !string.Equals(securitySettings.MissedSummaryEnabled, "off", StringComparison.OrdinalIgnoreCase);
		tabPage6.Controls.Add(missedSummaryEnabled);
		Label label34 = Ui.Label("Summary time", 9, bold: true, Theme.Text);
		label34.SetBounds(405, 209, 100, 24);
		tabPage6.Controls.Add(label34);
		SetupTimePicker(missedSummaryTime, securitySettings.MissedSummaryTime, 19);
		missedSummaryTime.SetBounds(510, 205, 150, 30);
		tabPage6.Controls.Add(missedSummaryTime);
		Label label35 = Ui.Label("Medication, Important, Critical, and Repeat Until Done reminders continue to alert normally and are never silently moved to this summary.", 9, bold: false, Theme.Muted);
		label35.SetBounds(48, 249, 610, 58);
		tabPage6.Controls.Add(label35);
		caregiverSummaryEnabled.Text = "Send one daily summary to caregivers through Connected Care";
		caregiverSummaryEnabled.ForeColor = Theme.Text;
		caregiverSummaryEnabled.SetBounds(28, 324, 470, 30);
		caregiverSummaryEnabled.Checked = string.Equals(securitySettings.CaregiverDailySummaryEnabled, "on", StringComparison.OrdinalIgnoreCase);
		tabPage6.Controls.Add(caregiverSummaryEnabled);
		Label label36 = Ui.Label("Send at", 9, bold: true, Theme.Text);
		label36.SetBounds(420, 368, 75, 24);
		tabPage6.Controls.Add(label36);
		SetupTimePicker(caregiverSummaryTime, securitySettings.CaregiverDailySummaryTime, 20);
		caregiverSummaryTime.SetBounds(500, 364, 160, 30);
		tabPage6.Controls.Add(caregiverSummaryTime);
		Label label37 = Ui.Label("The caregiver summary includes scheduled/completed/missed counts, medication completion, and I’M OKAY check-in status. It sends at most once per day.", 9, bold: false, Theme.Muted);
		label37.SetBounds(48, 408, 610, 58);
		tabPage6.Controls.Add(label37);
		Label label38 = Ui.Label("Connection & activity awareness", 13, bold: true, Theme.Text);
		label38.SetBounds(28, 22, 420, 30);
		tabPage7.Controls.Add(label38);
		Label label39 = Ui.Label("David shows power, internet, and Connected Care health on the home screen without creating extra popup windows.", 9, bold: false, Theme.Muted);
		label39.SetBounds(28, 54, 650, 48);
		tabPage7.Controls.Add(label39);
		inactivityWatch.Text = "Alert caregivers after unusually long computer inactivity";
		inactivityWatch.ForeColor = Theme.Text;
		inactivityWatch.SetBounds(28, 122, 470, 30);
		inactivityWatch.Checked = string.Equals(securitySettings.InactivityWatchEnabled, "on", StringComparison.OrdinalIgnoreCase);
		tabPage7.Controls.Add(inactivityWatch);
		Label label40 = Ui.Label("No keyboard/mouse activity for", 9, bold: true, Theme.Text);
		label40.SetBounds(48, 166, 225, 24);
		tabPage7.Controls.Add(label40);
		inactivityHours.SetBounds(275, 162, 75, 30);
		inactivityHours.Minimum = 2m;
		inactivityHours.Maximum = 12m;
		inactivityHours.Value = ((securitySettings.InactivityWatchHours <= 0) ? 4 : Math.Max(2, Math.Min(12, securitySettings.InactivityWatchHours)));
		tabPage7.Controls.Add(inactivityHours);
		Label label41 = Ui.Label("hours", 9, bold: false, Theme.Muted);
		label41.SetBounds(360, 166, 60, 24);
		tabPage7.Controls.Add(label41);
		Label label42 = Ui.Label("Only watch between", 9, bold: true, Theme.Text);
		label42.SetBounds(48, 214, 140, 24);
		tabPage7.Controls.Add(label42);
		SetupTimePicker(inactivityStart, securitySettings.InactivityWatchStartTime, 8);
		inactivityStart.SetBounds(190, 210, 150, 30);
		tabPage7.Controls.Add(inactivityStart);
		Label label43 = Ui.Label("and", 9, bold: false, Theme.Muted);
		label43.SetBounds(355, 214, 40, 24);
		tabPage7.Controls.Add(label43);
		SetupTimePicker(inactivityEnd, securitySettings.InactivityWatchEndTime, 22);
		inactivityEnd.SetBounds(400, 210, 150, 30);
		tabPage7.Controls.Add(inactivityEnd);
		Label label44 = Ui.Label("The watch is OFF by default. It never treats inactivity as proof of an emergency. When enabled, Connected Care sends one notice after the threshold and one short 'active again' update when keyboard/mouse activity resumes.", 9, bold: false, Theme.Muted);
		label44.SetBounds(48, 268, 610, 86);
		tabPage7.Controls.Add(label44);
		Label label45 = Ui.Label("Low battery, internet-offline, and delayed Connected Care details are available from CAREGIVER → COMPUTER & SAFETY → TODAY STATUS. Low battery uses one tray notice instead of repeated popups.", 9, bold: false, Theme.Muted);
		label45.SetBounds(48, 382, 610, 70);
		tabPage7.Controls.Add(label45);
		Label label46 = Ui.Label("QUIET HOURS", 12, bold: true, Theme.Blue);
		label46.SetBounds(28, 24, 260, 28);
		tabPage8.Controls.Add(label46);
		quietHours.Text = "Hold ordinary reminders during these hours";
		quietHours.ForeColor = Theme.Text;
		quietHours.SetBounds(28, 62, 520, 30);
		quietHours.Checked = string.Equals(securitySettings.QuietHoursEnabled, "on", StringComparison.OrdinalIgnoreCase);
		tabPage8.Controls.Add(quietHours);
		Label label47 = Ui.Label("Start", 9, bold: true, Theme.Text);
		label47.SetBounds(48, 112, 80, 24);
		tabPage8.Controls.Add(label47);
		SetupTimePicker(quietHoursStart, securitySettings.QuietHoursStartTime, 22);
		quietHoursStart.SetBounds(130, 108, 150, 30);
		tabPage8.Controls.Add(quietHoursStart);
		Label label48 = Ui.Label("End", 9, bold: true, Theme.Text);
		label48.SetBounds(320, 112, 80, 24);
		tabPage8.Controls.Add(label48);
		SetupTimePicker(quietHoursEnd, securitySettings.QuietHoursEndTime, 7);
		quietHoursEnd.SetBounds(400, 108, 150, 30);
		tabPage8.Controls.Add(quietHoursEnd);
		Label label49 = Ui.Label("Ordinary reminders wait until Quiet Hours end and then use David's same single reminder window. Medication, Appointment, Important, Critical, and Repeat Until Done reminders always alert.", 9, bold: false, Theme.Muted);
		label49.SetBounds(48, 156, 610, 72);
		tabPage8.Controls.Add(label49);
		Label label50 = Ui.Label("AWAY MODE", 12, bold: true, Theme.Gold);
		label50.SetBounds(28, 244, 260, 28);
		tabPage8.Controls.Add(label50);
		awayMode.Text = "Save ordinary reminders while David is away";
		awayMode.ForeColor = Theme.Text;
		awayMode.SetBounds(28, 282, 520, 30);
		awayMode.Checked = string.Equals(securitySettings.AwayModeEnabled, "on", StringComparison.OrdinalIgnoreCase) && QuietAwayService.AwayActive(securitySettings, DateTime.Now);
		tabPage8.Controls.Add(awayMode);
		Label label51 = Ui.Label("Away until", 9, bold: true, Theme.Text);
		label51.SetBounds(48, 330, 100, 24);
		tabPage8.Controls.Add(label51);
		awayUntil.Format = DateTimePickerFormat.Custom;
		awayUntil.CustomFormat = "MMM d, yyyy  h:mm tt";
		awayUntil.Value = DateTime.Now.AddHours(8.0);
		if (DateTime.TryParse(securitySettings.AwayModeUntil, out var result) && result > DateTime.Now)
		{
			awayUntil.Value = result;
		}
		awayUntil.SetBounds(150, 326, 255, 30);
		tabPage8.Controls.Add(awayUntil);
		Label label52 = Ui.Label("Optional note", 9, bold: true, Theme.Text);
		label52.SetBounds(48, 376, 100, 24);
		tabPage8.Controls.Add(label52);
		awayNote.SetBounds(150, 372, 500, 58);
		awayNote.Multiline = true;
		awayNote.Text = securitySettings.AwayModeNote ?? "";
		tabPage8.Controls.Add(awayNote);
		Label label53 = Ui.Label("Away Mode does not dump old windows onto the screen when David comes home. Held reminders stay in one return-home review until David chooses to go through them.", 9, bold: false, Theme.Muted);
		label53.SetBounds(48, 438, 610, 56);
		tabPage8.Controls.Add(label53);
		Label label54 = Ui.Label("LOCAL BACKUP & RECOVERY", 12, bold: true, Theme.Green);
		label54.SetBounds(28, 26, 330, 28);
		tabPage9.Controls.Add(label54);
		autoBackup.Text = "Create one automatic local backup each day";
		autoBackup.ForeColor = Theme.Text;
		autoBackup.SetBounds(28, 72, 520, 30);
		autoBackup.Checked = !string.Equals(securitySettings.AutoBackupEnabled, "off", StringComparison.OrdinalIgnoreCase);
		tabPage9.Controls.Add(autoBackup);
		Label label55 = Ui.Label("Keep the newest", 9, bold: true, Theme.Text);
		label55.SetBounds(48, 122, 130, 24);
		tabPage9.Controls.Add(label55);
		backupRetention.SetBounds(182, 118, 72, 30);
		backupRetention.Minimum = 2m;
		backupRetention.Maximum = 30m;
		backupRetention.Value = ((securitySettings.AutoBackupRetention <= 0) ? 7 : Math.Max(2, Math.Min(30, securitySettings.AutoBackupRetention)));
		tabPage9.Controls.Add(backupRetention);
		Label label56 = Ui.Label("automatic backups", 9, bold: false, Theme.Muted);
		label56.SetBounds(265, 122, 150, 24);
		tabPage9.Controls.Add(label56);
		Label label57 = Ui.Label("Automatic backups are stored in Documents → David Backups. David also keeps a small last-good copy of each XML data file before replacing it, so a damaged write can recover automatically.", 9, bold: false, Theme.Muted);
		label57.SetBounds(48, 166, 610, 72);
		tabPage9.Controls.Add(label57);
		Button button7 = Ui.Button("BACK UP NOW", Theme.Blue, 9);
		button7.SetBounds(28, 260, 190, 46);
		tabPage9.Controls.Add(button7);
		button7.Click += (object param0, EventArgs param1) =>
		{
			try
			{
				string text = AppData.Backup();
				MessageBox.Show("Backup created in:\r\n" + text, "David");
			}
			catch (Exception ex)
			{
				MessageBox.Show(ex.Message, "David");
			}
		};
		Button button8 = Ui.Button("BACKUP & RESTORE", Theme.Green, 9);
		button8.SetBounds(235, 260, 210, 46);
		tabPage9.Controls.Add(button8);
		button8.Click += (object param0, EventArgs param1) =>
		{
			using RecoveryForm recoveryForm = new RecoveryForm();
			recoveryForm.ShowDialog(this);
		};
		Button button9 = Ui.Button("OPEN BACKUPS", Color.FromArgb(73, 91, 117), 9);
		button9.SetBounds(462, 260, 175, 46);
		tabPage9.Controls.Add(button9);
		button9.Click += (object param0, EventArgs param1) =>
		{
			try
			{
				Directory.CreateDirectory(AppData.BackupsRoot);
				Process.Start(AppData.BackupsRoot);
			}
			catch
			{
			}
		};
		Label label58 = Ui.Label("PROGRAM ROLLBACK", 11, bold: true, Theme.Gold);
		label58.SetBounds(28, 344, 250, 28);
		tabPage9.Controls.Add(label58);
		Label label59 = Ui.Label("Every successful David installer now keeps the previous working David.exe as the last-known-good program. Rolling back the program does not erase reminders or settings.", 9, bold: false, Theme.Muted);
		label59.SetBounds(48, 382, 600, 60);
		tabPage9.Controls.Add(label59);
		Button button10 = Ui.Button("RESTORE PREVIOUS DAVID PROGRAM", Color.FromArgb(120, 72, 78), 9);
		button10.SetBounds(28, 450, 330, 46);
		tabPage9.Controls.Add(button10);
		button10.Enabled = Program.HasLastKnownGood();
		button10.Click += (object param0, EventArgs param1) =>
		{
			Program.RestoreLastKnownGood(this);
		};
		Button button11 = Ui.Button("SAVE ALL SETTINGS", Theme.Green, 11);
		button11.SetBounds(500, 633, 264, 48);
		Controls.Add(button11);
		button11.Click += (object param0, EventArgs param1) =>
		{
			if (awayMode.Checked && awayUntil.Value <= DateTime.Now.AddMinutes(1.0))
			{
				MessageBox.Show("Choose an Away Mode end time in the future, or turn Away Mode off.", "David");
			}
			else
			{
				SecuritySettings securitySettings2 = AppData.LoadSettings() ?? new SecuritySettings();
				securitySettings2.StartupPreference = (startup.Checked ? "on" : "off");
				securitySettings2.FullScreenPreference = (fullscreen.Checked ? "on" : "off");
				securitySettings2.HomeFullScreenPreference = (homeFull.Checked ? "on" : "off");
				securitySettings2.LargeTextPreference = (large.Checked ? "on" : "off");
				securitySettings2.WakeScreenPreference = (wake.Checked ? "on" : "off");
				securitySettings2.VoiceName = Convert.ToString(voiceBox.SelectedItem);
				securitySettings2.VoiceRate = (int)rate.Value;
				securitySettings2.VoiceVolume = (int)volume.Value;
				securitySettings2.EmergencyName = emergencyName.Text.Trim();
				securitySettings2.EmergencyPhone = emergencyPhone.Text.Trim();
				securitySettings2.EscalationMinutes = (int)escalation.Value;
				securitySettings2.MorningRoutineEnabled = (morningEnabled.Checked ? "on" : "off");
				securitySettings2.MorningRoutineName = (string.IsNullOrWhiteSpace(morningName.Text) ? "Morning" : morningName.Text.Trim());
				securitySettings2.MorningRoutineTime = morningTime.Value.ToString("HH:mm");
				securitySettings2.EveningRoutineEnabled = (eveningEnabled.Checked ? "on" : "off");
				securitySettings2.EveningRoutineName = (string.IsNullOrWhiteSpace(eveningName.Text) ? "Evening" : eveningName.Text.Trim());
				securitySettings2.EveningRoutineTime = eveningTime.Value.ToString("HH:mm");
				securitySettings2.CheckInEnabled = (checkInEnabled.Checked ? "on" : "off");
				securitySettings2.CheckInTime = checkInTime.Value.ToString("HH:mm");
				securitySettings2.CheckInGraceMinutes = (int)checkInGrace.Value;
				securitySettings2.SmartQuietingEnabled = (smartQuieting.Checked ? "on" : "off");
				securitySettings2.SmartQuietAfterMinutes = (int)smartQuietAfter.Value;
				securitySettings2.MissedSummaryEnabled = (missedSummaryEnabled.Checked ? "on" : "off");
				securitySettings2.MissedSummaryTime = missedSummaryTime.Value.ToString("HH:mm");
				securitySettings2.CaregiverDailySummaryEnabled = (caregiverSummaryEnabled.Checked ? "on" : "off");
				securitySettings2.CaregiverDailySummaryTime = caregiverSummaryTime.Value.ToString("HH:mm");
				securitySettings2.InactivityWatchEnabled = (inactivityWatch.Checked ? "on" : "off");
				securitySettings2.InactivityWatchHours = (int)inactivityHours.Value;
				securitySettings2.InactivityWatchStartTime = inactivityStart.Value.ToString("HH:mm");
				securitySettings2.InactivityWatchEndTime = inactivityEnd.Value.ToString("HH:mm");
				securitySettings2.AutoBackupEnabled = (autoBackup.Checked ? "on" : "off");
				securitySettings2.AutoBackupRetention = (int)backupRetention.Value;
				securitySettings2.QuietHoursEnabled = (quietHours.Checked ? "on" : "off");
				securitySettings2.QuietHoursStartTime = quietHoursStart.Value.ToString("HH:mm");
				securitySettings2.QuietHoursEndTime = quietHoursEnd.Value.ToString("HH:mm");
				securitySettings2.AwayModeEnabled = (awayMode.Checked ? "on" : "off");
				securitySettings2.AwayModeUntil = (awayMode.Checked ? awayUntil.Value.ToString("o") : securitySettings2.AwayModeUntil);
				securitySettings2.AwayModeNote = awayNote.Text.Trim();
				securitySettings2.MorningOverviewEnabled = (morningOverviewEnabled.Checked ? "on" : "off");
				securitySettings2.MorningOverviewTime = morningOverviewTime.Value.ToString("HH:mm");
				AppData.SaveSettings(securitySettings2);
				Program.SetAutoStart(startup.Checked);
				MessageBox.Show("Caregiver settings saved.", "David");
			}
		};
		FormClosed += (object param0, FormClosedEventArgs param1) =>
		{
			VoiceService.Stop(previewSpeech);
		};
	}

	private void SetupTimePicker(DateTimePicker picker, string value, int fallbackHour)
	{
		picker.Format = DateTimePickerFormat.Custom;
		picker.CustomFormat = "h:mm tt";
		picker.ShowUpDown = true;
		if (!TimeSpan.TryParse(value, out var result))
		{
			result = TimeSpan.FromHours(fallbackHour);
		}
		picker.Value = DateTime.Today.Add(result);
	}
}
