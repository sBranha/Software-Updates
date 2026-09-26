using System;
using System.Collections.Generic;
using System.Drawing;
using System.Media;
using System.Threading;
using System.Windows.Forms;

namespace DavidCompanion;

public class ReminderBatchForm : Form
{
	private List<AlertWorkItem> items;

	private int index = -1;

	private object speech;

	private System.Windows.Forms.Timer pulse;

	private System.Windows.Forms.Timer attention;

	private System.Windows.Forms.Timer sharedStateTimer;

	private bool remoteAdvanceBusy;

	private Panel band;

	private Color accent;

	private bool pulseBright;

	private bool finished;

	private bool allowClose;

	private SecuritySettings settings;

	public ReminderBatchForm(List<AlertWorkItem> dueItems)
	{
		items = dueItems ?? new List<AlertWorkItem>();
		settings = AppData.LoadSettings() ?? new SecuritySettings();
		Text = "David - Reminders";
		StartPosition = FormStartPosition.CenterScreen;
		TopMost = true;
		BackColor = Color.FromArgb(7, 13, 24);
		Font = new Font("Segoe UI", 11f);
		Icon = Program.TryLoadIcon();
		if (!string.Equals(settings.FullScreenPreference, "off", StringComparison.OrdinalIgnoreCase))
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
		Shown += (object param0, EventArgs param1) =>
		{
			StartSharedStateWatch();
			ShowNext();
		};
		FormClosing += OnBatchClosing;
		FormClosed += (object param0, FormClosedEventArgs param1) =>
		{
			StopEffects();
			if (sharedStateTimer != null)
			{
				sharedStateTimer.Stop();
				sharedStateTimer.Dispose();
				sharedStateTimer = null;
			}
		};
	}

	private void OnBatchClosing(object sender, FormClosingEventArgs e)
	{
		if (!allowClose && e.CloseReason == CloseReason.UserClosing)
		{
			if (finished)
			{
				allowClose = true;
				return;
			}
			e.Cancel = true;
			HandleClosedCurrent();
		}
	}

	private void ClearWindow()
	{
		StopEffects();
		while (Controls.Count > 0)
		{
			Controls[0].Dispose();
		}
	}

	private void StopEffects()
	{
		if (pulse != null)
		{
			pulse.Stop();
			pulse.Dispose();
			pulse = null;
		}
		if (attention != null)
		{
			attention.Stop();
			attention.Dispose();
			attention = null;
		}
		VoiceService.Stop(speech);
		speech = null;
	}

	private string PersonName()
	{
		SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
		if (!string.IsNullOrWhiteSpace(securitySettings.PersonPreferredName))
		{
			return securitySettings.PersonPreferredName;
		}
		if (!string.IsNullOrWhiteSpace(securitySettings.PersonFullName))
		{
			return securitySettings.PersonFullName;
		}
		return "David";
	}

	private bool IsSharedDone(AlertWorkItem item)
	{
		if (item == null || string.IsNullOrWhiteSpace(item.Key))
		{
			return false;
		}
		StateEntry stateEntry = AppData.GetState(item.Key);
		if (stateEntry != null)
		{
			return string.Equals(stateEntry.Status, "done", StringComparison.OrdinalIgnoreCase);
		}
		return false;
	}

	private void StartSharedStateWatch()
	{
		if (sharedStateTimer != null)
		{
			return;
		}
		sharedStateTimer = new System.Windows.Forms.Timer();
		sharedStateTimer.Interval = 900;
		sharedStateTimer.Tick += (object sender, EventArgs e) =>
		{
			if (!IsDisposed && Visible && !remoteAdvanceBusy && !finished && index >= 0 && index < items.Count)
			{
				AlertWorkItem alertWorkItem = items[index];
				if (alertWorkItem != null && !string.IsNullOrWhiteSpace(alertWorkItem.Key))
				{
					if (IsSharedDone(alertWorkItem))
					{
						StopEffects();
						ShowNext();
					}
					else
					{
						remoteAdvanceBusy = true;
						string watchedKey = alertWorkItem.Key;
						ThreadPool.QueueUserWorkItem((object _) =>
						{
							bool done = false;
							try
							{
								done = CloudClient.OccurrenceDoneRemote(watchedKey);
							}
							catch
							{
							}
							try
							{
								BeginInvoke((MethodInvoker)(() =>
								{
									try
									{
										if (done && !finished && index >= 0 && index < items.Count && items[index] != null && string.Equals(items[index].Key, watchedKey, StringComparison.Ordinal))
										{
											StopEffects();
											ShowNext();
										}
									}
									finally
									{
										remoteAdvanceBusy = false;
									}
								}));
							}
							catch
							{
								remoteAdvanceBusy = false;
							}
						});
					}
				}
			}
		};
		sharedStateTimer.Start();
	}

	private void ShowNext()
	{
		do
		{
			index++;
			if (index >= items.Count)
			{
				ShowFinished();
				return;
			}
		}
		while (items[index] == null || items[index].Reminder == null || IsSharedDone(items[index]));
		RenderReminder(items[index]);
	}

	private void RenderReminder(AlertWorkItem item)
	{
		ClearWindow();
		finished = false;
		Reminder r = item.Reminder;
		DateTime scheduled = item.Scheduled;
		accent = Theme.Category(r.Category);
		if (!string.Equals(settings.WakeScreenPreference, "off", StringComparison.OrdinalIgnoreCase))
		{
			DisplayWake.Wake();
		}
		band = new Panel();
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
			if (!card.IsDisposed)
			{
				card.Location = new Point((ClientSize.Width - card.Width) / 2, (ClientSize.Height - card.Height) / 2);
			}
		};
		string person = PersonName();
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
		string text3 = (string.IsNullOrWhiteSpace(r.Importance) ? "Normal" : r.Importance);
		Label label6 = Ui.Label(text3 + text2 + "  •  " + scheduled.ToString("h:mm tt") + "  •  " + (index + 1) + " of " + items.Count, 10, bold: true, accent);
		label6.SetBounds(num, 174, 710, 26);
		card.Controls.Add(label6);
		RoundedPanel roundedPanel2 = Ui.Card(Color.FromArgb(27, 41, 61));
		roundedPanel2.SetBounds(42, 220, 888, 128);
		card.Controls.Add(roundedPanel2);
		string text4 = (string.IsNullOrWhiteSpace(r.Message) ? "It’s time for this reminder." : r.Message);
		if (string.Equals(r.Category, "Medication", StringComparison.OrdinalIgnoreCase))
		{
			string text5 = (string.IsNullOrWhiteSpace(r.MedicationDose) ? "" : ("Dose: " + r.MedicationDose));
			string text6 = (string.IsNullOrWhiteSpace(r.MedicationInstructions) ? "" : r.MedicationInstructions);
			string text7 = (text5 + ((string.IsNullOrWhiteSpace(text5) || string.IsNullOrWhiteSpace(text6)) ? "" : "  •  ") + text6).Trim();
			if (!string.IsNullOrWhiteSpace(text7))
			{
				text4 = text4 + "\r\n\r\n" + text7;
			}
		}
		Label label7 = Ui.Label(text4, string.Equals(r.Category, "Medication", StringComparison.OrdinalIgnoreCase) ? 14 : 18, bold: false, Color.FromArgb(232, 238, 245));
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
		double totalMinutes = (DateTime.Now - scheduled).TotalMinutes;
		int num2 = ((settings.EscalationMinutes > 0) ? settings.EscalationMinutes : 15);
		bool flag = totalMinutes >= (double)num2;
		string text8;
		if (flag)
		{
			text8 = "STILL NEEDS ATTENTION";
		}
		else
		{
			text8 = (string.Equals(r.Importance, "Critical", StringComparison.OrdinalIgnoreCase) ? "IMPORTANT" : "READY WHEN YOU ARE");
		}
		Label label10 = Ui.Pill(text8, flag ? Color.FromArgb(92, 42, 48) : Color.FromArgb(37, 61, 75), flag ? Color.FromArgb(255, 166, 170) : accent);
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
			if (CompletionHelper.Confirm(this, r))
			{
				MarkDone(item);
				ShowCompletedThenNext(person);
			}
		};
		button2.Click += (object param0, EventArgs param1) =>
		{
			MarkSnoozed(item);
			ShowNext();
		};
		button3.Click += (object param0, EventArgs param1) =>
		{
			CloudResponse cloudResponse = CloudClient.StartHelp();
			MessageBox.Show((cloudResponse != null && cloudResponse.ok) ? "Your caregivers were notified." : "David could not send the Help request right now.", "David");
		};
		pulse = new System.Windows.Forms.Timer();
		pulse.Interval = 700;
		pulse.Tick += (object param0, EventArgs param1) =>
		{
			if (band != null && !band.IsDisposed)
			{
				pulseBright = !pulseBright;
				band.BackColor = (pulseBright ? ControlPaint.Light(accent, 0.24f) : accent);
			}
		};
		pulse.Start();
		int interval;
		if (string.Equals(r.Importance, "Critical", StringComparison.OrdinalIgnoreCase))
		{
			interval = 15000;
		}
		else
		{
			interval = ((string.Equals(r.Importance, "Important", StringComparison.OrdinalIgnoreCase) || string.Equals(r.Category, "Medication", StringComparison.OrdinalIgnoreCase)) ? 30000 : 60000);
		}
		attention = new System.Windows.Forms.Timer();
		attention.Interval = interval;
		attention.Tick += (object param0, EventArgs param1) =>
		{
			if (!IsDisposed && Visible)
			{
				try
				{
					BringToFront();
					Activate();
				}
				catch
				{
				}
				VoiceService.Stop(speech);
				speech = null;
				StartVoice(r, person);
			}
		};
		attention.Start();
		BringToFront();
		Activate();
		StartVoice(r, person);
	}

	private void StartVoice(Reminder r, string person)
	{
		try
		{
			SystemSounds.Exclamation.Play();
		}
		catch
		{
		}
		if (r.Voice)
		{
			SecuritySettings securitySettings = AppData.LoadSettings() ?? new SecuritySettings();
			string text = (string.IsNullOrWhiteSpace(r.SpokenText) ? (person + ", it's time for " + (r.Title ?? "your reminder") + ". " + r.Message + ((string.Equals(r.Category, "Medication", StringComparison.OrdinalIgnoreCase) && !string.IsNullOrWhiteSpace(r.MedicationDose)) ? (" Dose: " + r.MedicationDose + ".") : "")) : r.SpokenText.Trim());
			speech = VoiceService.Speak(text, securitySettings.VoiceName, securitySettings.VoiceRate, securitySettings.VoiceVolume);
		}
	}

	private void MarkDone(AlertWorkItem item)
	{
		AppData.SetState(item.Key, "done", null);
		AppData.AddHistory(item.Reminder, "Done", item.Key, item.Scheduled);
		CloudSyncService.MarkOccurrenceDoneAsync(item.Key, (item.Reminder == null) ? "" : (item.Reminder.Id ?? ""), item.Scheduled);
	}

	private void MarkSnoozed(AlertWorkItem item)
	{
		int num = ((item.Reminder.SnoozeMinutes > 0) ? item.Reminder.SnoozeMinutes : 10);
		AppData.SetState(item.Key, "snoozed", DateTime.Now.AddMinutes(num));
		AppData.AddHistory(item.Reminder, "Snoozed", item.Key, item.Scheduled);
	}

	private void HandleClosedCurrent()
	{
		if (index < 0 || index >= items.Count)
		{
			ShowFinished();
			return;
		}
		AlertWorkItem alertWorkItem = items[index];
		Reminder reminder = alertWorkItem.Reminder;
		if (reminder.RepeatUntilDone || string.Equals(reminder.Importance, "Important", StringComparison.OrdinalIgnoreCase) || string.Equals(reminder.Importance, "Critical", StringComparison.OrdinalIgnoreCase) || string.Equals(reminder.Category, "Medication", StringComparison.OrdinalIgnoreCase))
		{
			int num = (string.Equals(reminder.Importance, "Critical", StringComparison.OrdinalIgnoreCase) ? 3 : 5);
			AppData.SetState(alertWorkItem.Key, "snoozed", DateTime.Now.AddMinutes(num));
			AppData.AddHistory(reminder, "Closed - repeats in " + num + " minutes", alertWorkItem.Key, alertWorkItem.Scheduled);
		}
		else
		{
			AppData.SetState(alertWorkItem.Key, "dismissed", null);
			AppData.AddHistory(reminder, "Dismissed", alertWorkItem.Key, alertWorkItem.Scheduled);
		}
		ShowNext();
	}

	private void ShowCompletedThenNext(string person)
	{
		ClearWindow();
		RoundedPanel roundedPanel = new RoundedPanel();
		roundedPanel.Radius = 30;
		roundedPanel.BackColor = Color.FromArgb(20, 31, 48);
		roundedPanel.BorderColor = Color.FromArgb(50, 70, 96);
		roundedPanel.BorderWidth = 1;
		roundedPanel.Size = new Size(820, 420);
		roundedPanel.Anchor = AnchorStyles.None;
		Controls.Add(roundedPanel);
		roundedPanel.Location = new Point((ClientSize.Width - roundedPanel.Width) / 2, (ClientSize.Height - roundedPanel.Height) / 2);
		Label label = Ui.Label("✓", 58, bold: true, Theme.Green);
		label.TextAlign = ContentAlignment.MiddleCenter;
		label.SetBounds(0, 65, roundedPanel.Width, 90);
		roundedPanel.Controls.Add(label);
		Label label2 = Ui.Label("Nice work, " + person + "!", 28, bold: true, Color.White);
		label2.TextAlign = ContentAlignment.MiddleCenter;
		label2.SetBounds(0, 165, roundedPanel.Width, 60);
		roundedPanel.Controls.Add(label2);
		Label label3 = Ui.Label((index + 1 >= items.Count) ? "That was the last reminder." : "Moving to the next reminder…", 14, bold: false, Color.FromArgb(185, 198, 216));
		label3.TextAlign = ContentAlignment.MiddleCenter;
		label3.SetBounds(0, 235, roundedPanel.Width, 45);
		roundedPanel.Controls.Add(label3);
		System.Windows.Forms.Timer t = new System.Windows.Forms.Timer();
		t.Interval = 850;
		t.Tick += (object param0, EventArgs param1) =>
		{
			t.Stop();
			t.Dispose();
			ShowNext();
		};
		t.Start();
	}

	private void ShowFinished()
	{
		ClearWindow();
		finished = true;
		Text = "David - All Done";
		RoundedPanel roundedPanel = new RoundedPanel();
		roundedPanel.Radius = 30;
		roundedPanel.BackColor = Color.FromArgb(20, 31, 48);
		roundedPanel.BorderColor = Color.FromArgb(50, 70, 96);
		roundedPanel.BorderWidth = 1;
		roundedPanel.Size = new Size(820, 470);
		roundedPanel.Anchor = AnchorStyles.None;
		Controls.Add(roundedPanel);
		roundedPanel.Location = new Point((ClientSize.Width - roundedPanel.Width) / 2, (ClientSize.Height - roundedPanel.Height) / 2);
		Label label = Ui.Label("✓", 62, bold: true, Theme.Green);
		label.TextAlign = ContentAlignment.MiddleCenter;
		label.SetBounds(0, 55, roundedPanel.Width, 100);
		roundedPanel.Controls.Add(label);
		Label label2 = Ui.Label("ALL DONE FOR NOW", 28, bold: true, Color.White);
		label2.TextAlign = ContentAlignment.MiddleCenter;
		label2.SetBounds(0, 160, roundedPanel.Width, 60);
		roundedPanel.Controls.Add(label2);
		Label label3 = Ui.Label("You’re caught up. David will let you know when it’s time for the next reminder.", 14, bold: false, Color.FromArgb(190, 205, 224));
		label3.TextAlign = ContentAlignment.MiddleCenter;
		label3.SetBounds(75, 230, roundedPanel.Width - 150, 65);
		roundedPanel.Controls.Add(label3);
		Button button = Ui.Button("OK", Theme.Green, 14);
		button.SetBounds(250, 330, 320, 72);
		roundedPanel.Controls.Add(button);
		button.Click += (object param0, EventArgs param1) =>
		{
			allowClose = true;
			Close();
		};
		BringToFront();
		Activate();
	}
}
