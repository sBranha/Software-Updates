using System;
using System.Drawing;
using System.Media;
using System.Windows.Forms;

namespace DavidCompanion;

public class ReminderAlertForm : Form
{
	private AlertResult result = AlertResult.Closed;

	private object speech;

	private Timer pulse = new Timer();

	private Timer attention = new Timer();

	private Panel band = new Panel();

	private Color accent;

	private bool pulseBright;

	private Reminder reminder;

	private DateTime scheduled;

	public AlertResult ResultValue => result;

	public ReminderAlertForm(Reminder r, DateTime scheduledFor)
	{
		ReminderAlertForm reminderAlertForm = this;
		ReminderAlertForm reminderAlertForm2 = this;
		reminder = r;
		scheduled = scheduledFor;
		accent = Theme.Category(r.Category);
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		if (!string.Equals(securitySettings.WakeScreenPreference, "off", StringComparison.OrdinalIgnoreCase))
		{
			DisplayWake.Wake();
		}
		Text = "David - Reminder";
		StartPosition = FormStartPosition.CenterScreen;
		TopMost = true;
		BackColor = Color.FromArgb(7, 13, 24);
		Font = new Font("Segoe UI", 11f);
		Icon = Program.TryLoadIcon();
		if (!string.Equals(securitySettings.FullScreenPreference, "off", StringComparison.OrdinalIgnoreCase))
		{
			FormBorderStyle = FormBorderStyle.None;
			WindowState = FormWindowState.Maximized;
		}
		else
		{
			Size = new Size(1080, 790);
			MinimumSize = new Size(1000, 720);
			FormBorderStyle = FormBorderStyle.FixedDialog;
			MaximizeBox = false;
			MinimizeBox = false;
		}
		band.Dock = DockStyle.Top;
		band.Height = 12;
		band.BackColor = accent;
		Controls.Add(band);
		RoundedPanel card = new RoundedPanel();
		card.Radius = 30;
		card.BackColor = Color.FromArgb(20, 31, 48);
		card.BorderColor = Color.FromArgb(50, 70, 96);
		card.BorderWidth = 1;
		card.Size = new Size(980, 650);
		card.Anchor = AnchorStyles.None;
		Controls.Add(card);
		card.Location = new Point((ClientSize.Width - card.Width) / 2, (ClientSize.Height - card.Height) / 2);
		SizeChanged += (object param0, EventArgs param1) =>
		{
			card.Location = new Point((reminderAlertForm.ClientSize.Width - card.Width) / 2, (reminderAlertForm.ClientSize.Height - card.Height) / 2);
		};
		SecuritySettings securitySettings2 = AppData.LoadSettings() ?? new SecuritySettings();
		string person = ((!string.IsNullOrWhiteSpace(securitySettings2.PersonPreferredName)) ? securitySettings2.PersonPreferredName : (string.IsNullOrWhiteSpace(securitySettings2.PersonFullName) ? "David" : securitySettings2.PersonFullName));
		Label label = Ui.Label("REMINDER FOR " + person.ToUpperInvariant(), 9, bold: true, Color.FromArgb(199, 213, 231));
		label.SetBounds(42, 28, 560, 24);
		card.Controls.Add(label);
		Label label2 = Ui.Pill((Theme.CategoryBadge(r.Category) + "  " + (r.Category ?? "Other")).ToUpperInvariant(), Color.FromArgb(42, 54, 74), accent);
		label2.SetBounds(760, 24, 170, 32);
		card.Controls.Add(label2);
		string text = AppData.ResolvePicture(r.PicturePath);
		int num = 190;
		if (!string.IsNullOrEmpty(text))
		{
			PictureBox pictureBox = new PictureBox();
			pictureBox.SetBounds(42, 78, 118, 118);
			pictureBox.SizeMode = PictureBoxSizeMode.Zoom;
			pictureBox.BackColor = Color.FromArgb(14, 23, 38);
			try
			{
				using Image original = Image.FromFile(text);
				pictureBox.Image = new Bitmap(original);
			}
			catch
			{
			}
			card.Controls.Add(pictureBox);
		}
		else
		{
			RoundedPanel roundedPanel = new RoundedPanel();
			roundedPanel.Radius = 26;
			roundedPanel.BackColor = accent;
			roundedPanel.SetBounds(42, 78, 118, 118);
			Label label3 = Ui.Label(Theme.CategoryBadge(r.Category), 22, bold: true, Color.White);
			label3.TextAlign = ContentAlignment.MiddleCenter;
			label3.Dock = DockStyle.Fill;
			roundedPanel.Controls.Add(label3);
			card.Controls.Add(roundedPanel);
		}
		Label label4 = Ui.Label(person + ", it’s time", 15, bold: true, Color.FromArgb(188, 204, 225));
		label4.SetBounds(num, 78, 700, 32);
		card.Controls.Add(label4);
		Label label5 = Ui.Label(r.Title ?? "Reminder", 31, bold: true, Color.White);
		label5.SetBounds(num, 111, 735, 66);
		card.Controls.Add(label5);
		string text2 = (string.IsNullOrWhiteSpace(r.RoutineName) ? "" : ("  •  " + r.RoutineName + ((r.RoutineOrder > 0) ? (" step " + r.RoutineOrder) : "")));
		Label label6 = Ui.Label((string.IsNullOrWhiteSpace(r.Importance) ? "Normal" : r.Importance) + text2 + "  •  " + scheduledFor.ToString("h:mm tt"), 10, bold: true, accent);
		label6.SetBounds(num, 174, 710, 26);
		card.Controls.Add(label6);
		RoundedPanel roundedPanel2 = Ui.Card(Color.FromArgb(27, 41, 61));
		roundedPanel2.SetBounds(42, 220, 888, 128);
		card.Controls.Add(roundedPanel2);
		string text3 = (string.IsNullOrWhiteSpace(r.Message) ? "It’s time for this reminder." : r.Message);
		if (string.Equals(r.Category, "Medication", StringComparison.OrdinalIgnoreCase))
		{
			string text4 = (string.IsNullOrWhiteSpace(r.MedicationDose) ? "" : ("Dose: " + r.MedicationDose));
			string text5 = (string.IsNullOrWhiteSpace(r.MedicationInstructions) ? "" : r.MedicationInstructions);
			string text6 = (text4 + ((string.IsNullOrWhiteSpace(text4) || string.IsNullOrWhiteSpace(text5)) ? "" : "  •  ") + text5).Trim();
			if (!string.IsNullOrWhiteSpace(text6))
			{
				text3 = text3 + "\r\n\r\n" + text6;
			}
		}
		Label label7 = Ui.Label(text3, string.Equals(r.Category, "Medication", StringComparison.OrdinalIgnoreCase) ? 14 : 18, bold: false, Color.FromArgb(232, 238, 245));
		label7.SetBounds(22, 16, 844, 100);
		roundedPanel2.Controls.Add(label7);
		if (!string.IsNullOrWhiteSpace(r.CaregiverNote))
		{
			Label label8 = Ui.Label("CAREGIVER NOTE", 8, bold: true, Color.FromArgb(174, 192, 215));
			label8.SetBounds(48, 365, 180, 22);
			card.Controls.Add(label8);
			Label label9 = Ui.Label(r.CaregiverNote, 11, bold: false, Color.FromArgb(211, 223, 236));
			label9.SetBounds(48, 389, 865, 46);
			card.Controls.Add(label9);
		}
		double totalMinutes = (DateTime.Now - scheduledFor).TotalMinutes;
		bool flag = totalMinutes >= (double)((securitySettings.EscalationMinutes > 0) ? securitySettings.EscalationMinutes : 15);
		Label label10 = Ui.Pill(flag ? "STILL NEEDS ATTENTION" : (string.Equals(r.Importance, "Critical", StringComparison.OrdinalIgnoreCase) ? "IMPORTANT" : "READY WHEN YOU ARE"), flag ? Color.FromArgb(92, 42, 48) : Color.FromArgb(37, 61, 75), flag ? Color.FromArgb(255, 166, 170) : accent);
		label10.SetBounds(48, 447, 220, 34);
		card.Controls.Add(label10);
		if (string.Equals(r.Category, "Medication", StringComparison.OrdinalIgnoreCase) && totalMinutes > 30.0)
		{
			Label label11 = Ui.Label("This medicine reminder is overdue. Follow the medicine label or caregiver/clinician instructions before taking a possibly missed dose.", 9, bold: true, Color.FromArgb(255, 171, 171));
			label11.SetBounds(290, 444, 630, 42);
			card.Controls.Add(label11);
		}
		Button button = Ui.Button(string.Equals(r.Category, "Medication", StringComparison.OrdinalIgnoreCase) ? "YES, I TOOK IT" : "DONE", Theme.Green, 15);
		button.SetBounds(42, 520, 330, 78);
		card.Controls.Add(button);
		Button button2 = Ui.Button("REMIND ME IN " + ((r.SnoozeMinutes > 0) ? r.SnoozeMinutes : 10) + " MIN", Color.FromArgb(197, 132, 45), 11);
		button2.SetBounds(389, 520, 335, 78);
		card.Controls.Add(button2);
		Button button3 = Ui.Button("I NEED HELP", Theme.Danger, 10);
		button3.SetBounds(741, 520, 189, 78);
		card.Controls.Add(button3);
		button.Click += (object param0, EventArgs param1) =>
		{
			if (CompletionHelper.Confirm(reminderAlertForm, r))
			{
				reminderAlertForm.ShowSuccess(card, person);
			}
		};
		button2.Click += (object param0, EventArgs param1) =>
		{
			reminderAlertForm2.result = AlertResult.Snooze;
			reminderAlertForm2.Close();
		};
		button3.Click += (object param0, EventArgs param1) =>
		{
			CloudResponse cloudResponse = CloudClient.StartHelp();
			MessageBox.Show((cloudResponse != null && cloudResponse.ok) ? "Your caregivers were notified." : "David could not send the Help request right now.", "David");
		};
		pulse.Interval = 700;
		pulse.Tick += (object param0, EventArgs param1) =>
		{
			reminderAlertForm2.pulseBright = !reminderAlertForm2.pulseBright;
			reminderAlertForm2.band.BackColor = (reminderAlertForm2.pulseBright ? ControlPaint.Light(reminderAlertForm2.accent, 0.24f) : reminderAlertForm2.accent);
		};
		pulse.Start();
		int interval = (string.Equals(r.Importance, "Critical", StringComparison.OrdinalIgnoreCase) ? 15000 : ((string.Equals(r.Importance, "Important", StringComparison.OrdinalIgnoreCase) || string.Equals(r.Category, "Medication", StringComparison.OrdinalIgnoreCase)) ? 30000 : 60000));
		attention.Interval = interval;
		attention.Tick += (object param0, EventArgs param1) =>
		{
			try
			{
				SystemSounds.Exclamation.Play();
			}
			catch
			{
			}
		};
		attention.Start();
		Shown += (object param0, EventArgs param1) =>
		{
			reminderAlertForm.BringToFront();
			reminderAlertForm.Activate();
			reminderAlertForm.StartVoice(person);
		};
		FormClosed += (object param0, FormClosedEventArgs param1) =>
		{
			reminderAlertForm2.pulse.Stop();
			reminderAlertForm2.attention.Stop();
			VoiceService.Stop(reminderAlertForm2.speech);
		};
	}

	private void StartVoice(string person)
	{
		try
		{
			SystemSounds.Exclamation.Play();
		}
		catch
		{
		}
		if (reminder.Voice)
		{
			SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
			string text = (string.IsNullOrWhiteSpace(reminder.SpokenText) ? (person + ", it's time for " + (reminder.Title ?? "your reminder") + ". " + reminder.Message) : reminder.SpokenText.Trim());
			speech = VoiceService.Speak(text, securitySettings.VoiceName, securitySettings.VoiceRate, securitySettings.VoiceVolume);
		}
	}

	private void ShowSuccess(Panel card, string person)
	{
		result = AlertResult.Done;
		foreach (Control control in card.Controls)
		{
			control.Visible = false;
		}
		Label label = Ui.Label("✓", 58, bold: true, Theme.Green);
		label.TextAlign = ContentAlignment.MiddleCenter;
		label.SetBounds(0, 150, card.Width, 90);
		card.Controls.Add(label);
		Label label2 = Ui.Label("Nice work, " + person + "!", 30, bold: true, Color.White);
		label2.TextAlign = ContentAlignment.MiddleCenter;
		label2.SetBounds(0, 250, card.Width, 65);
		card.Controls.Add(label2);
		Label label3 = Ui.Label("That reminder is complete.", 15, bold: false, Color.FromArgb(185, 198, 216));
		label3.TextAlign = ContentAlignment.MiddleCenter;
		label3.SetBounds(0, 320, card.Width, 45);
		card.Controls.Add(label3);
		Timer t = new Timer();
		t.Interval = 1200;
		t.Tick += (object param0, EventArgs param1) =>
		{
			t.Stop();
			t.Dispose();
			Close();
		};
		t.Start();
	}
}
